package io.github.creepydutchboy.covebattle.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The flashbang.
 *
 * <p>Two parts, because one alone is not convincing: a full-screen white wash that actually stops
 * you seeing anything, and the {@code cove_flash} post-processing shader underneath it which blows
 * out whatever is still visible as it fades. The wash holds solid for the first third and then
 * falls away, so sight returns gradually rather than snapping back.
 */
public final class FlashOverlay {

    private static int ticksLeft;
    private static int total;

    private FlashOverlay() {}

    public static void start(int ticks) {
        ticksLeft = Math.max(ticksLeft, ticks);
        total = Math.max(total, ticks);
    }

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
        if (ticksLeft == 0) total = 0;
    }

    public static boolean active() {
        return ticksLeft > 0;
    }

    public static void clear() {
        ticksLeft = 0;
        total = 0;
    }

    /** 1.0 at full blind, falling to 0. */
    public static float intensity(float partialTick) {
        if (ticksLeft <= 0 || total <= 0) return 0f;
        float remaining = (ticksLeft - partialTick) / total;
        if (remaining > 0.66f) return 1f;
        return Math.max(0f, remaining / 0.66f);
    }

    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        float intensity = intensity(partialTick);
        if (intensity <= 0f) return;
        int alpha = (int) (Math.min(1f, intensity) * 255f);
        graphics.fill(0, 0, width, height, (alpha << 24) | 0xFFFFFF);
    }
}
