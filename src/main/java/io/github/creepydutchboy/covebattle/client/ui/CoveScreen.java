package io.github.creepydutchboy.covebattle.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base for the Cove screens: own backdrop, own header, own footer line. */
public abstract class CoveScreen extends Screen {

    protected final Screen parent;
    private final String heading;
    protected String footerNote = "";

    protected CoveScreen(Screen parent, String heading) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
    }

    /** Content area inside the outer frame. */
    protected int frameX() {
        return Math.max(8, (this.width - contentWidth()) / 2);
    }

    protected int frameY() {
        return Math.max(8, (this.height - contentHeight()) / 2);
    }

    protected int contentWidth() {
        return Math.min(this.width - 16, 380);
    }

    protected int contentHeight() {
        return Math.min(this.height - 16, 220);
    }

    protected String headerNote() {
        return "";
    }

    /**
     * The frame and the panel content are drawn here, before the widgets. Doing it in render()
     * after super.render() painted the panel straight over every button.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        CoveUI.dim(graphics, this.width, this.height);

        int x = frameX();
        int y = frameY();
        int w = contentWidth();
        int h = contentHeight();
        CoveUI.panel(graphics, x - 6, y - 6, w + 12, h + 12, CoveTheme.PANEL_SUNKEN);
        CoveUI.header(graphics, this.font, x, y, w, headingText(), headerNote());

        renderContent(graphics, x, y + CoveTheme.HEADER - 12, w, h - (CoveTheme.HEADER - 12),
                mouseX, mouseY, partialTick);
    }

    protected String headingText() {
        return heading;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!footerNote.isEmpty()) {
            graphics.drawString(this.font, Component.literal(footerNote),
                    frameX(), frameY() + contentHeight() - 9, CoveTheme.TEXT_FAINT, false);
        }
    }

    protected void renderContent(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }
}
