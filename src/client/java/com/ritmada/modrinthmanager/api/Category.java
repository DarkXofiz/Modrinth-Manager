package com.ritmada.modrinthmanager.api;

public enum Category {
    MODS("mod", "mods", "Mods"),
    SHADERPACKS("shader", "shaderpacks", "ShaderPacks"),
    RESOURCEPACKS("resourcepack", "resourcepacks", "Texture Packs");

    public final String projectType;
    public final String installFolder;
    public final String displayName;

    Category(String projectType, String installFolder, String displayName) {
        this.projectType = projectType;
        this.installFolder = installFolder;
        this.displayName = displayName;
    }

    /** Modrinth loader facet to use for this category's search. Shaders/resourcepacks aren't loader-bound. */
    public String loaderFacet() {
        return this == MODS ? "fabric" : null;
    }
}
