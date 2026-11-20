package com.ritmada.modrinthmanager.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ritmada.modrinthmanager.ModrinthManagerClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Thin wrapper around the real Modrinth API v2 (https://api.modrinth.com/v2).
 * All network calls run off the client's render/tick thread via {@link #IO}.
 */
public final class ModrinthClient {

    private static final String BASE_URL = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "Ritmada/modrinth-manager/1.0.0 (https://github.com/Ritmada/modrinth-manager)";

    public static final ExecutorService IO = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "modrinth-manager-io");
        t.setDaemon(true);
        return t;
    });

    private static final Gson GSON = new GsonBuilder().create();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // query signature -> response, cheap 2 minute TTL cache to avoid hammering the API on every keystroke
    private static final Map<String, CachedEntry<ModrinthModels.SearchResponse>> SEARCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CachedEntry<List<ModrinthModels.ProjectVersion>>> VERSION_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 120_000L;

    private ModrinthClient() {
    }

    public static CompletableFuture<ModrinthModels.SearchResponse> search(
            Category category, String query, String minecraftVersion, int offset, int limit) {

        String cacheKey = category.name() + '|' + query + '|' + minecraftVersion + '|' + offset + '|' + limit;
        CachedEntry<ModrinthModels.SearchResponse> cached = SEARCH_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String facets = buildFacets(category, minecraftVersion);
                String url = BASE_URL + "/search"
                        + "?query=" + encode(query)
                        + "&limit=" + limit
                        + "&offset=" + offset
                        + "&index=relevance"
                        + (facets.isEmpty() ? "" : "&facets=" + encode(facets));

                HttpRequest request = baseRequest(url).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new ModrinthApiException("search failed: HTTP " + response.statusCode() + " " + truncate(response.body()));
                }
                ModrinthModels.SearchResponse parsed = GSON.fromJson(response.body(), ModrinthModels.SearchResponse.class);
                SEARCH_CACHE.put(cacheKey, new CachedEntry<>(parsed));
                return parsed;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new ModrinthApiException("network error during search: " + e.getMessage(), e);
            }
        }, IO);
    }

    public static CompletableFuture<List<ModrinthModels.ProjectVersion>> getVersions(
            String projectId, String minecraftVersion, String loader) {

        String cacheKey = projectId + '|' + minecraftVersion + '|' + loader;
        CachedEntry<List<ModrinthModels.ProjectVersion>> cached = VERSION_CACHE.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder url = new StringBuilder(BASE_URL).append("/project/").append(encode(projectId)).append("/version?");
                url.append("game_versions=").append(encode("[\"" + minecraftVersion + "\"]"));
                if (loader != null) {
                    url.append("&loaders=").append(encode("[\"" + loader + "\"]"));
                }

                HttpRequest request = baseRequest(url.toString()).GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() != 200) {
                    throw new ModrinthApiException("version lookup failed: HTTP " + response.statusCode() + " " + truncate(response.body()));
                }
                List<ModrinthModels.ProjectVersion> parsed = List.of(GSON.fromJson(response.body(), ModrinthModels.ProjectVersion[].class));
                VERSION_CACHE.put(cacheKey, new CachedEntry<>(parsed));
                return parsed;
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                throw new ModrinthApiException("network error during version lookup: " + e.getMessage(), e);
            }
        }, IO);
    }

    private static String buildFacets(Category category, String minecraftVersion) {
        // facets format: [["a"],["b"]] == AND across groups, OR within a group
        StringBuilder sb = new StringBuilder("[[\"project_type:").append(category.projectType).append("\"]");
        if (minecraftVersion != null && !minecraftVersion.isBlank()) {
            sb.append(",[\"versions:").append(minecraftVersion).append("\"]");
        }
        if (category.loaderFacet() != null) {
            sb.append(",[\"categories:").append(category.loaderFacet()).append("\"]");
        }
        sb.append(']');
        return sb.toString();
    }

    private static HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    public static void logError(String context, Throwable t) {
        // pass the throwable itself (not t.toString()) so the full stack trace lands in
        // latest.log — that's what you actually need to grep for when the GUI only shows
        // a one-line summary.
        ModrinthManagerClient.LOGGER.error("[modrinth-manager] {}", context, t);
    }

    private static final class CachedEntry<T> {
        final T value;
        final long createdAt;

        CachedEntry(T value) {
            this.value = value;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > CACHE_TTL_MS;
        }
    }

    public static final class ModrinthApiException extends RuntimeException {
        public ModrinthApiException(String message) {
            super(message);
        }

        public ModrinthApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
