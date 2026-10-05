package io.github.creepydutchboy.covebattle.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Flat button in the Cove style: dark plate, accent rail, no vanilla stone texture. */
public class CoveButton extends AbstractButton {

    private final Runnable onPress;
    private int accent = CoveTheme.ACCENT;
    private boolean selected;
    private String subtitle;

    public CoveButton(int x, int y, int width, int height, Component label, Runnable onPress) {
        super(x, y, width, height, label);
        this.onPress = onPress;
    }

    public CoveButton accent(int colour) {
        this.accent = colour;
        return this;
    }

    public CoveButton selected(boolean value) {
        this.selected = value;
        return this;
    }

    public CoveButton subtitle(String value) {
        this.subtitle = value;
        return this;
    }

    @Override
    public void onPress() {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && active;
        int fill = !active ? CoveTheme.PANEL_SUNKEN
                : selected ? CoveTheme.PANEL_RAISED
                : hovered ? CoveTheme.lighten(CoveTheme.PANEL, 0.06f)
                : CoveTheme.PANEL;

        CoveUI.panel(graphics, getX(), getY(), width, height, fill);
        if (selected || hovered) {
            graphics.fill(getX(), getY(), getX() + 2, getY() + height, selected ? accent : CoveTheme.ACCENT_DIM);
        }

        int textColour = !active ? CoveTheme.TEXT_FAINT : selected ? CoveTheme.ACCENT : CoveTheme.TEXT;
        var font = Minecraft.getInstance().font;
        int textY = subtitle == null ? getY() + (height - 8) / 2 : getY() + 4;
        graphics.drawString(font, getMessage(), getX() + 8, textY, textColour, false);
        if (subtitle != null) {
            graphics.drawString(font, Component.literal(subtitle), getX() + 8, getY() + height - 12,
                    CoveTheme.TEXT_DIM, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
