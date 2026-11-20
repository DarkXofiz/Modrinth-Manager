package com.ritmada.modrinthmanager.download;

import com.ritmada.modrinthmanager.api.Category;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import com.ritmada.modrinthmanager.api.ModrinthModels;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads a Modrinth version's primary file into the right game-content folder,
 * verifying the sha1 the API gave us and refusing to clobber unrelated existing files.
 */
public final class DownloadManager {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // projectId -> in-flight/last progress, consumed by the GUI for progress bars
    private static final Map<String, DownloadProgress> PROGRESS = new ConcurrentHashMap<>();

    private DownloadManager() {
    }

    public static DownloadProgress progressFor(String projectId) {
        return PROGRESS.get(projectId);
    }

    public static CompletableFuture<DownloadResult> install(Category category, ModrinthModels.ProjectVersion version) {
        ModrinthModels.VersionFile file = pickPrimaryFile(version);
        if (file == null) {
            return CompletableFuture.completedFuture(DownloadResult.failure("no downloadable file on this version"));
        }

        DownloadProgress progress = new DownloadProgress();
        PROGRESS.put(version.projectId, progress);

        return CompletableFuture.supplyAsync(() -> {
            try {
                Path targetDir = resolveTargetDir(category);
                Files.createDirectories(targetDir);
                Path target = targetDir.resolve(sanitizeFilename(file.filename));

                if (Files.exists(target)) {
                    if (matchesSha1(target, file.hashes != null ? file.hashes.sha1 : null)) {
                        progress.markComplete();
                        return DownloadResult.alreadyInstalled(target);
                    }
                    // different content under the same filename: never overwrite silently
                    target = targetDir.resolve(uniqueName(target));
                }

                Path tmp = Files.createTempFile(targetDir, "mm-download-", ".part");
                try {
                    downloadWithProgress(file.url, tmp, file.size, progress);

                    if (file.hashes != null && file.hashes.sha1 != null && !matchesSha1(tmp, file.hashes.sha1)) {
                        Files.deleteIfExists(tmp);
                        progress.markFailed("checksum mismatch, file discarded");
                        return DownloadResult.failure("sha1 mismatch for " + file.filename + ", download discarded");
                    }

                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    progress.markComplete();
                    return DownloadResult.success(target);
                } finally {
                    Files.deleteIfExists(tmp);
                }
            } catch (Exception e) {
                // broad on purpose: any failure here (IO, checksum, bad URI, permissions, an
                // unexpected runtime exception from a third-party API quirk) must come back as
                // a DownloadResult.Err with a real message instead of blowing up the future.
                String message = e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
                progress.markFailed(message);
                ModrinthClient.logError("download failed for " + version.projectId, e); // full stack trace goes to latest.log
                return DownloadResult.failure(message);
            }
        }, ModrinthClient.IO);
    }

    private static void downloadWithProgress(String url, Path dest, long expectedSize, DownloadProgress progress) throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Ritmada/modrinth-manager/1.0.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
            }

            long total = expectedSize > 0 ? expectedSize
                    : response.headers().firstValueAsLong("Content-Length").orElse(-1);

            try (InputStream in = response.body(); var out = Files.newOutputStream(dest)) {
                byte[] buf = new byte[64 * 1024];
                long read = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    read += n;
                    progress.update(read, total);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("download interrupted", e);
        }
    }

    private static boolean matchesSha1(Path file, String expectedHex) throws IOException, NoSuchAlgorithmException {
        if (expectedHex == null || expectedHex.isBlank()) return true; // nothing to verify against
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) sha1.update(buf, 0, n);
        }
        String actual = HexFormat.of().formatHex(sha1.digest());
        return actual.equalsIgnoreCase(expectedHex);
    }

    private static Path resolveTargetDir(Category category) {
        return FabricLoader.getInstance().getGameDir().resolve(category.installFolder);
    }

    private static ModrinthModels.VersionFile pickPrimaryFile(ModrinthModels.ProjectVersion version) {
        if (version.files == null || version.files.isEmpty()) return null;
        for (ModrinthModels.VersionFile f : version.files) {
            if (f.primary) return f;
        }
        return version.files.get(0);
    }

    private static String sanitizeFilename(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String uniqueName(Path existing) {
        String name = existing.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot >= 0 ? name.substring(0, dot) : name;
        String ext = dot >= 0 ? name.substring(dot) : "";
        return base + "_mm_" + System.currentTimeMillis() + ext;
    }

    public static final class DownloadProgress {
        private volatile long bytesRead;
        private volatile long bytesTotal = -1;
        private volatile State state = State.RUNNING;
        private volatile String errorMessage;

        public void update(long read, long total) {
            this.bytesRead = read;
            this.bytesTotal = total;
        }

        void markComplete() {
            this.state = State.DONE;
        }

        void markFailed(String message) {
            this.state = State.FAILED;
            this.errorMessage = message;
        }

        public float fraction() {
            if (bytesTotal <= 0) return state == State.DONE ? 1f : 0f;
            return Math.min(1f, bytesRead / (float) bytesTotal);
        }

        public State state() {
            return state;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public enum State { RUNNING, DONE, FAILED }
    }

    public sealed interface DownloadResult permits Ok, AlreadyInstalled, Err {
        static DownloadResult success(Path path) {
            return new Ok(path);
        }

        static DownloadResult alreadyInstalled(Path path) {
            return new AlreadyInstalled(path);
        }

        static DownloadResult failure(String message) {
            return new Err(message);
        }
    }

    public record Ok(Path path) implements DownloadResult {
    }

    public record AlreadyInstalled(Path path) implements DownloadResult {
    }

    public record Err(String message) implements DownloadResult {
    }
}
