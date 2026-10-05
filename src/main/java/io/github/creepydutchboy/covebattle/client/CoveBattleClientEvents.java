package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import io.github.creepydutchboy.covebattle.client.ui.CoveButton;
import io.github.creepydutchboy.covebattle.net.LobbyActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client wiring: the menu entry point, the lobby, the pause menu, and the screen effects. */
@EventBusSubscriber(modid = CoveBattle.MODID, value = Dist.CLIENT)
public final class CoveBattleClientEvents {

    private static boolean openLobbyWhenReady;

    private CoveBattleClientEvents() {}

    /**
     * Adds the main menu button. Placed in the top-left rather than inside the vanilla column so it
     * cannot collide with another mod's layout, and wrapped so a failure here can never stop the
     * menu from opening.
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof TitleScreen) {
            if (!CoveBattleConfig.titleScreenButton) return;
            try {
                event.addListener(new CoveButton(6, 6, 110, 20,
                        Component.literal("Cove Battle"),
                        () -> Minecraft.getInstance().setScreen(new CoveMenuScreen(Minecraft.getInstance().screen))));
            } catch (Throwable t) {
                CoveBattle.LOGGER.error("Could not add the Cove Battle button to the title screen", t);
            }
            return;
        }

        if (event.getScreen() instanceof PauseScreen) {
            replaceQuitButton(event);
        }
    }

    /**
     * In a Cove session, leaving the world means going back to the lobby, not back to the title.
     * The vanilla button is swapped for one that says so and does that.
     */
    private static void replaceQuitButton(ScreenEvent.Init.Post event) {
        if (!CoveSession.active()) return;
        try {
            String quitLabel = Component.translatable("menu.returnToMenu").getString();
            AbstractWidget quit = null;
            for (GuiEventListener listener : event.getListenersList()) {
                if (listener instanceof AbstractWidget widget
                        && widget.getMessage().getString().equals(quitLabel)) {
                    quit = widget;
                    break;
                }
            }
            if (quit == null) return;

            int x = quit.getX();
            int y = quit.getY();
            int w = quit.getWidth();
            int h = quit.getHeight();
            event.removeListener(quit);
            event.addListener(Button.builder(Component.literal("Go to Lobby"), button -> {
                Minecraft minecraft = Minecraft.getInstance();
                try {
                    PacketDistributor.sendToServer(new LobbyActionPayload(LobbyActionPayload.Action.ENTER_LOBBY, ""));
                } catch (Exception e) {
                    CoveBattle.LOGGER.warn("Could not tell the server we went to the lobby: {}", e.toString());
                }
                minecraft.setScreen(new CoveLobbyScreen(null));
            }).bounds(x, y, w, h).build());
        } catch (Throwable t) {
            CoveBattle.LOGGER.error("Could not adjust the pause menu", t);
        }
    }

    /** Opens the mutator board in game. Client-side, so it needs no server round trip to show. */
    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("cbmutators").executes(ctx -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.tell(() -> minecraft.setScreen(new MutatorScreen(minecraft.screen)));
                    return 1;
                }));
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("cblobby").executes(ctx -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    minecraft.tell(() -> minecraft.setScreen(new CoveLobbyScreen(null)));
                    return 1;
                }));
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientEffects.tick(minecraft);

        // Hosting drops you into the lobby rather than into the world.
        if (openLobbyWhenReady && minecraft.player != null && minecraft.screen == null) {
            openLobbyWhenReady = false;
            minecraft.setScreen(new CoveLobbyScreen(null));
        }
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (CoveLobbyScreen.consumeOpenOnLoad()) openLobbyWhenReady = true;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEffects.clear(Minecraft.getInstance());
        ClientLobby.reset();
        ClientRules.reset();
        openLobbyWhenReady = false;
    }
}
