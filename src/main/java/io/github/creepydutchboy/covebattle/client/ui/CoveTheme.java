package io.github.creepydutchboy.covebattle.client.ui;

/**
 * The palette and metrics for Cove Battle's own front end.
 *
 * <p>The approach is borrowed from the competitive-Minecraft launchers and ranked mods: stop
 * dressing the mod in stone-texture vanilla widgets and give it a flat, dark, high-contrast panel
 * language of its own. Nothing here is taken from any of them — these are our own colours,
 * spacing and widgets, drawn with plain fills so there are no textures to ship or mis-scale.
 */
public final class CoveTheme {

    private CoveTheme() {}

    // Surfaces, back to front.
    public static final int BACKDROP = 0xF00B0E14;
    public static final int PANEL = 0xFF141A24;
    public static final int PANEL_RAISED = 0xFF1C2431;
    public static final int PANEL_SUNKEN = 0xFF0E131B;
    public static final int BORDER = 0xFF2A3647;
    public static final int BORDER_BRIGHT = 0xFF3C4C63;

    // Accents.
    public static final int ACCENT = 0xFFFFB54A;
    public static final int ACCENT_DIM = 0xFF8A6228;
    public static final int TEAL = 0xFF4FD1C5;
    public static final int RED = 0xFFE0564F;
    public static final int BLUE = 0xFF5B8DEF;
    public static final int GREEN = 0xFF57C97A;

    // Text.
    public static final int TEXT = 0xFFE8EDF4;
    public static final int TEXT_DIM = 0xFF8A97AA;
    public static final int TEXT_FAINT = 0xFF5A6678;

    // Metrics.
    public static final int PAD = 8;
    public static final int ROW = 18;
    public static final int HEADER = 30;

    /** Blends a colour towards white for hover states. */
    public static int lighten(int argb, float amount) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = (int) Math.min(255, r + (255 - r) * amount);
        g = (int) Math.min(255, g + (255 - g) * amount);
        b = (int) Math.min(255, b + (255 - b) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
