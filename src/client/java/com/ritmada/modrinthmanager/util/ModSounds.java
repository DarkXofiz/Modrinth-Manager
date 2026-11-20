package com.ritmada.modrinthmanager.util;

import com.ritmada.modrinthmanager.ModrinthManagerClient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {

    public static final SoundEvent CHRISTMAS_THEME = register("christmas_theme");

    private ModSounds() {
    }

    private static SoundEvent register(String path) {
        Identifier id = new Identifier(ModrinthManagerClient.MOD_ID, path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        // referencing CHRISTMAS_THEME above is enough to run the static registration;
        // this method just gives the client entrypoint something explicit to call.
    }
}
