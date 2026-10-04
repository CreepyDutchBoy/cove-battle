package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-side wiring: the main menu entry point and the screen effects. */
@EventBusSubscriber(modid = CoveBattle.MODID, value = Dist.CLIENT)
public final class CoveBattleClientEvents {

    private CoveBattleClientEvents() {}

    /**
     * Adds the main menu button. Placed in the top-left rather than inside the vanilla column so it
     * cannot collide with another mod's layout, and wrapped so a failure here can never stop the
     * menu from opening.
     */
    @SubscribeEvent
    static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!CoveBattleConfig.titleScreenButton) return;
        if (!(event.getScreen() instanceof TitleScreen)) return;
        try {
            event.addListener(Button.builder(Component.literal("Cove Battle"),
                            button -> Minecraft.getInstance()
                                    .setScreen(new CoveBattleHubScreen(Minecraft.getInstance().screen)))
                    .bounds(6, 6, 110, 20)
                    .build());
        } catch (Throwable t) {
            CoveBattle.LOGGER.error("Could not add the Cove Battle button to the title screen", t);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientEffects.tick(Minecraft.getInstance());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientEffects.clear(Minecraft.getInstance());
    }
}
