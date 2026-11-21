package com.ritmada.modrinthmanager.util;

import com.ritmada.modrinthmanager.ModrinthManagerClient;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads a project's icon off-thread, decodes it to a {@link NativeImage} and registers
 * it as a GPU texture on the render thread, where OpenGL calls are legal.
 *
 * <p>Modrinth icons come back at whatever size the project owner uploaded (128/256/512px,
 * not always square). {@link Handle} carries the real decoded size so callers pass the
 * correct textureWidth/textureHeight into drawTexture — passing the on-screen draw size
 * there instead (the earlier bug) samples only a small top-left corner of the real image,
 * which is what "glitchy/broken" icons actually was.</p>
 */
public final class IconCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final Map<String, Handle> READY = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();

    // a real bundled placeholder (textures/gui/missing_icon.png), not an unregistered id —
    // an unregistered identifier falls back to vanilla's magenta/black missing-texture
    // checkerboard, which is what was actually showing up as "glitched" icons before.
    private static final Handle MISSING = new Handle(
            new Identifier(ModrinthManagerClient.MOD_ID, "textures/gui/missing_icon.png"), 32, 32);

    private IconCache() {
    }

    public record Handle(Identifier id, int width, int height) {
    }

    /** Returns the cached texture handle, or a fallback while a fetch is pending/failed. */
    public static Handle get(String projectId, String iconUrl) {
        if (iconUrl == null || iconUrl.isBlank()) return MISSING;

        Handle cached = READY.get(projectId);
        if (cached != null) return cached;
        if (FAILED.contains(projectId)) return MISSING; // one attempt per session, don't hammer a dead/blocked CDN every frame

        if (IN_FLIGHT.add(projectId)) {
            ModrinthClient.IO.submit(() -> fetchAndRegister(projectId, iconUrl));
        }
        return MISSING;
    }

    private static void fetchAndRegister(String projectId, String iconUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(iconUrl))
                    .header("User-Agent", "Ritmada/modrinth-manager/1.0.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                FAILED.add(projectId);
                IN_FLIGHT.remove(projectId);
                return;
            }

            NativeImage image;
            try (InputStream in = response.body()) {
                image = NativeImage.read(in);
            }
            int width = image.getWidth();
            int height = image.getHeight();

            MinecraftClient.getInstance().execute(() -> {
                try {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                    Identifier id = new Identifier(ModrinthManagerClient.MOD_ID, "icon/" + sanitize(projectId));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                    READY.put(projectId, new Handle(id, width, height));
                } finally {
                    IN_FLIGHT.remove(projectId);
                }
            });
        } catch (Exception e) {
            // bad/unsupported image format (e.g. a non-PNG icon), network hiccup, blocked CDN
            // domain, whatever — fall back to the placeholder once instead of retrying forever.
            ModrinthClient.logError("icon fetch failed for " + projectId, e);
            FAILED.add(projectId);
            IN_FLIGHT.remove(projectId);
        }
    }

    private static String sanitize(String projectId) {
        return projectId.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }
}
