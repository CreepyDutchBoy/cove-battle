package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.map.MapInstaller;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * The hub reached from the main menu: install and play the bundled Cove map, or go and join
 * someone else's game.
 *
 * <p>Hosting is deliberately not a separate button. In Minecraft you host by opening a world to
 * LAN from the pause menu, so the honest flow is "play the map, then open it to LAN" rather than a
 * button that pretends to stand up a server.
 */
public class CoveBattleHubScreen extends Screen {

    private final Screen parent;
    private Component status = Component.empty();

    public CoveBattleHubScreen(Screen parent) {
        super(Component.literal("Cove Battle"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centreX = this.width / 2;
        int y = Math.max(70, this.height / 4 + 8);

        boolean mapBundled = MapInstaller.isAvailable();

        Button play = Button.builder(
                        Component.literal(mapBundled ? "Play the Cove map" : "Bundled map unavailable"),
                        button -> installAndPlay())
                .bounds(centreX - 102, y, 204, 20)
                .build();
        play.active = mapBundled;
        addRenderableWidget(play);

        addRenderableWidget(Button.builder(Component.literal("Join a Cove Battle server"),
                        button -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)))
                .bounds(centreX - 102, y + 26, 204, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Install the map only"),
                        button -> install(false))
                .bounds(centreX - 102, y + 52, 204, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Modes & Mutators"),
                        button -> this.minecraft.setScreen(new MutatorScreen(this)))
                .bounds(centreX - 102, y + 78, 204, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Back"),
                        button -> this.minecraft.setScreen(parent))
                .bounds(centreX - 102, y + 108, 204, 20)
                .build());
    }

    private void installAndPlay() {
        install(true);
    }

    private void install(boolean play) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Path saves = minecraft.gameDirectory.toPath().resolve("saves");
            Path made = MapInstaller.install(saves, MapInstaller.DEFAULT_FOLDER,
                    message -> CoveBattle.LOGGER.info(message));
            String levelId = made.getFileName().toString();
            status = Component.literal("Installed as \"" + levelId + "\"").withStyle(ChatFormatting.GREEN);
            if (play) {
                minecraft.createWorldOpenFlows().openWorld(levelId, () -> minecraft.setScreen(this));
            }
        } catch (Exception e) {
            CoveBattle.LOGGER.error("Could not install the bundled map", e);
            status = Component.literal("Install failed: " + e.getMessage()).withStyle(ChatFormatting.RED);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centreX = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, centreX, 28, 0xFFD24A);
        graphics.drawCenteredString(this.font,
                Component.literal("Legacy console Battle, remastered").withStyle(ChatFormatting.GRAY),
                centreX, 42, 0xAAAAAA);

        int bottom = Math.max(70, this.height / 4 + 8) + 134;
        graphics.drawCenteredString(this.font,
                Component.literal("To host: play the map, then Esc → Open to LAN").withStyle(ChatFormatting.DARK_GRAY),
                centreX, bottom, 0x888888);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, centreX, bottom + 14, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
