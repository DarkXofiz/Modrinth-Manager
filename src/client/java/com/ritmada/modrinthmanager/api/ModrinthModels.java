package com.ritmada.modrinthmanager.api;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Plain data holders mapped directly from the Modrinth API v2 JSON shapes.
 * https://docs.modrinth.com/api/operations/searchprojects/
 */
public final class ModrinthModels {

    private ModrinthModels() {
    }

    public static final class SearchResponse {
        public List<SearchHit> hits;
        @SerializedName("total_hits")
        public int totalHits;
        public int offset;
        public int limit;
    }

    public static final class SearchHit {
        @SerializedName("project_id")
        public String projectId;
        public String slug;
        public String title;
        public String description;
        @SerializedName("icon_url")
        public String iconUrl;
        public List<String> versions;
        public List<String> categories;
        public List<String> loaders;
        public String author;
        public long downloads;
        @SerializedName("date_modified")
        public String dateModified;
        @SerializedName("project_type")
        public String projectType;
    }

    public static final class ProjectVersion {
        public String id;
        @SerializedName("project_id")
        public String projectId;
        @SerializedName("version_number")
        public String versionNumber;
        public String name;
        @SerializedName("game_versions")
        public List<String> gameVersions;
        public List<String> loaders;
        @SerializedName("version_type")
        public String versionType;
        public List<VersionFile> files;
        public List<Dependency> dependencies;
        @SerializedName("date_published")
        public String datePublished;
    }

    public static final class VersionFile {
        public Hashes hashes;
        public String url;
        public String filename;
        public boolean primary;
        public long size;
    }

    public static final class Hashes {
        public String sha512;
        public String sha1;
    }

    public static final class Dependency {
        @SerializedName("project_id")
        public String projectId;
        @SerializedName("version_id")
        public String versionId;
        @SerializedName("dependency_type")
        public String dependencyType; // required, optional, incompatible, embedded
    }
}
