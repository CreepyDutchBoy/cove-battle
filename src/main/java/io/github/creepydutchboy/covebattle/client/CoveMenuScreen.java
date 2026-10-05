package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.client.ui.CoveButton;
import io.github.creepydutchboy.covebattle.client.ui.CoveScreen;
import io.github.creepydutchboy.covebattle.client.ui.CoveTheme;
import io.github.creepydutchboy.covebattle.client.ui.CoveUI;
import io.github.creepydutchboy.covebattle.map.MapInstaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/** The front door: host a Cove server, join one, or set the rules. */
public class CoveMenuScreen extends CoveScreen {

    private String status = "";

    public CoveMenuScreen(net.minecraft.client.gui.screens.Screen parent) {
        super(parent, "Cove Battle");
        this.footerNote = "Legacy console Battle, remastered";
    }

    @Override
    protected int contentHeight() {
        return Math.min(this.height - 16, 170);
    }

    @Override
    protected int contentWidth() {
        return Math.min(this.width - 16, 300);
    }

    @Override
    protected String headerNote() {
        return CoveBattle.MOD_NAME + " " + io.github.creepydutchboy.covebattle.UpdateBridge.modVersion();
    }

    @Override
    protected void init() {
        int x = frameX();
        int y = frameY() + CoveTheme.HEADER;
        int w = contentWidth();

        boolean mapBundled = MapInstaller.isAvailable();

        CoveButton host = new CoveButton(x, y, w, 26,
                Component.literal("Host a Cove Server"), this::host);
        host.subtitle("Open the lobby and let players join").active = mapBundled;
        addRenderableWidget(host);

        addRenderableWidget(new CoveButton(x, y + 30, w, 26,
                Component.literal("Join a Cove Server"),
                () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)))
                .subtitle("Connect to someone else's lobby")
                .accent(CoveTheme.TEAL));

        addRenderableWidget(new CoveButton(x, y + 60, w, 26,
                Component.literal("Modes & Mutators"),
                () -> this.minecraft.setScreen(new MutatorScreen(this)))
                .subtitle("Classic, Remastered, or tune every number")
                .accent(CoveTheme.BLUE));

        addRenderableWidget(new CoveButton(x, y + 94, w, 18,
                Component.literal("Back"), this::onClose));
    }

    /** Puts the map in place if needed, loads it, and drops straight into the lobby. */
    private void host() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Path saves = minecraft.gameDirectory.toPath().resolve("saves");
            Path existing = saves.resolve(MapInstaller.DEFAULT_FOLDER);
            Path world = java.nio.file.Files.isDirectory(existing)
                    ? existing
                    : MapInstaller.install(saves, MapInstaller.DEFAULT_FOLDER, CoveBattle.LOGGER::info);

            CoveLobbyScreen.openOnLoad();
            minecraft.createWorldOpenFlows().openWorld(world.getFileName().toString(),
                    () -> minecraft.setScreen(this));
        } catch (Exception e) {
            CoveBattle.LOGGER.error("Could not host the Cove server", e);
            status = "Could not start: " + e.getMessage();
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY, float partialTick) {
        if (!status.isEmpty()) {
            graphics.drawString(this.font, Component.literal(status), x, y + height - 20, CoveTheme.RED, false);
        }
        if (!MapInstaller.isAvailable()) {
            graphics.drawString(this.font, Component.literal("The bundled map is missing from this jar."),
                    x, y + height - 20, CoveTheme.RED, false);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.setScreen(parent);
    }

    static {
        CoveUI.class.getName();
    }
}
