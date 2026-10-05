package io.github.creepydutchboy.covebattle.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Drawing helpers shared by the Cove screens. Plain fills, no textures. */
public final class CoveUI {

    private CoveUI() {}

    /** A flat panel with a hairline border. */
    public static void panel(GuiGraphics g, int x, int y, int width, int height, int fill) {
        g.fill(x, y, x + width, y + height, fill);
        border(g, x, y, width, height, CoveTheme.BORDER);
    }

    public static void border(GuiGraphics g, int x, int y, int width, int height, int colour) {
        g.fill(x, y, x + width, y + 1, colour);
        g.fill(x, y + height - 1, x + width, y + height, colour);
        g.fill(x, y, x + 1, y + height, colour);
        g.fill(x + width - 1, y, x + width, y + height, colour);
    }

    /** Panel with a coloured rail down its left edge — the house style for anything selectable. */
    public static void railPanel(GuiGraphics g, int x, int y, int width, int height, int fill, int rail) {
        panel(g, x, y, width, height, fill);
        g.fill(x, y, x + 2, y + height, rail);
    }

    /** A screen header: title on the left, optional note on the right, rule underneath. */
    public static void header(GuiGraphics g, Font font, int x, int y, int width, String title, String note) {
        g.drawString(font, Component.literal(title.toUpperCase()), x, y, CoveTheme.ACCENT, false);
        if (note != null && !note.isEmpty()) {
            int w = font.width(note);
            g.drawString(font, Component.literal(note), x + width - w, y, CoveTheme.TEXT_DIM, false);
        }
        g.fill(x, y + 11, x + width, y + 12, CoveTheme.BORDER_BRIGHT);
    }

    /** A label and a value on one line, value right-aligned — used for every stat row. */
    public static void statRow(GuiGraphics g, Font font, int x, int y, int width, String label, String value, int valueColour) {
        g.drawString(font, Component.literal(label), x, y, CoveTheme.TEXT_DIM, false);
        int w = font.width(value);
        g.drawString(font, Component.literal(value), x + width - w, y, valueColour, false);
    }

    /** A small filled tag, for states like LOBBY or IN MATCH. */
    public static int chip(GuiGraphics g, Font font, int x, int y, String text, int colour) {
        int w = font.width(text) + 8;
        g.fill(x, y - 1, x + w, y + 10, (colour & 0x00FFFFFF) | 0x33000000);
        border(g, x, y - 1, w, 11, colour);
        g.drawString(font, Component.literal(text), x + 4, y + 1, colour, false);
        return w;
    }

    public static void dim(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, CoveTheme.BACKDROP);
    }
}
