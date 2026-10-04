package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.UpdateBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Phase 1 only proves the client distribution loads; the HUD, scoreboard
 * panels and team screens arrive in phase 2.
 */
@EventBusSubscriber(modid = CoveBattle.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CoveBattleClient {

    private CoveBattleClient() {}

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        CoveBattle.LOGGER.info("{} client ready ({}).", CoveBattle.MOD_NAME, UpdateBridge.modVersion());
    }
}
