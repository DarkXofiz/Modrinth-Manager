package com.ritmada.modrinthmanager.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;

/**
 * Background jingle for the ClickGUI. Not positioned in the world (relative + no
 * attenuation), loops until {@link #stop()} flips the done flag on the next tick.
 */
public final class ChristmasMusic extends MovingSoundInstance {

    private static ChristmasMusic active;
    private volatile boolean stopRequested;

    private ChristmasMusic() {
        super(ModSounds.CHRISTMAS_THEME, SoundCategory.MASTER, Random.create());
        this.repeat = true;
        this.repeatDelay = 0;
        this.relative = true;
        this.attenuationType = AttenuationType.NONE;
        this.volume = 0.35f;
        this.pitch = 1.0f;
    }

    @Override
    public void tick() {
        // no per-tick animation needed; isDone() below is what actually stops playback
    }

    @Override
    public boolean isDone() {
        return stopRequested;
    }

    public static void playIfNotRunning() {
        if (active != null && !active.isDone()) return;
        active = new ChristmasMusic();
        MinecraftClient.getInstance().getSoundManager().play(active);
    }

    public static void stop() {
        if (active != null) {
            active.stopRequested = true;
        }
    }

    public static void setVolume(float volume) {
        if (active != null) {
            active.volume = Math.max(0f, Math.min(1f, volume));
        }
    }
}
