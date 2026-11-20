package com.ritmada.modrinthmanager.util;

import net.minecraft.client.gui.DrawContext;

import java.util.Random;

/**
 * Cheap decorative snowfall drawn behind the screen content. Pure CPU-side quads,
 * no particle system hookup, so it costs nothing outside of this screen's render pass.
 */
public final class SnowOverlay {

    private static final int FLAKE_COUNT = 60;
    private static final int COLOR = 0x55FFFFFF;

    private final Flake[] flakes;
    private final Random random = new Random();

    public SnowOverlay(int width, int height) {
        flakes = new Flake[FLAKE_COUNT];
        for (int i = 0; i < FLAKE_COUNT; i++) {
            flakes[i] = spawn(width, height, random.nextFloat() * height);
        }
    }

    private Flake spawn(int width, int height, float startY) {
        Flake f = new Flake();
        f.x = random.nextFloat() * width;
        f.y = startY;
        f.speed = 8f + random.nextFloat() * 18f;
        f.drift = (random.nextFloat() - 0.5f) * 6f;
        f.size = 1 + random.nextInt(2);
        f.phase = random.nextFloat() * (float) (Math.PI * 2);
        return f;
    }

    public void tickAndRender(DrawContext context, int width, int height, float deltaSeconds) {
        for (int i = 0; i < flakes.length; i++) {
            Flake f = flakes[i];
            f.phase += deltaSeconds * 1.5f;
            f.y += f.speed * deltaSeconds;
            f.x += (f.drift + Math.sin(f.phase)) * deltaSeconds;

            if (f.y > height) {
                flakes[i] = spawn(width, height, -4f);
                continue;
            }
            context.fill((int) f.x, (int) f.y, (int) f.x + f.size, (int) f.y + f.size, COLOR);
        }
    }

    private static final class Flake {
        float x, y, speed, drift, phase;
        int size;
    }
}
