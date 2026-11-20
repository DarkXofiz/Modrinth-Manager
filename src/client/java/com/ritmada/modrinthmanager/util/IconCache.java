package com.ritmada.modrinthmanager.util;

import com.ritmada.modrinthmanager.ModrinthManagerClient;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads a project's square icon off-thread, decodes it to a {@link NativeImage}
 * and registers it as a GPU texture on the render thread, where OpenGL calls are legal.
 */
public final class IconCache {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Identifier MISSING = new Identifier(ModrinthManagerClient.MOD_ID, "missing_icon");

    private IconCache() {
    }

    /** Returns the cached texture, or MISSING while a fetch is pending/failed, kicking off the fetch if new. */
    public static Identifier get(String projectId, String iconUrl) {
        if (iconUrl == null || iconUrl.isBlank()) return MISSING;

        Identifier cached = READY.get(projectId);
        if (cached != null) return cached;

        if (IN_FLIGHT.add(projectId)) {
            ModrinthClient.IO.submit(() -> fetchAndRegister(projectId, iconUrl));
        }
        return MISSING;
    }

    private static void fetchAndRegister(String projectId, String iconUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(iconUrl))
                    .header("User-Agent", "Ritmada/modrinth-manager/1.0.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                IN_FLIGHT.remove(projectId);
                return;
            }

            NativeImage image;
            try (InputStream in = response.body()) {
                image = NativeImage.read(in);
            }

            MinecraftClient.getInstance().execute(() -> {
                try {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                    Identifier id = new Identifier(ModrinthManagerClient.MOD_ID, "icon/" + sanitize(projectId));
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                    READY.put(projectId, id);
                } finally {
                    IN_FLIGHT.remove(projectId);
                }
            });
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            IN_FLIGHT.remove(projectId);
        }
    }

    private static String sanitize(String projectId) {
        return projectId.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }
}
