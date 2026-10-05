package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.UpdateBridge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only entry point. A second {@code @Mod} class scoped to {@link Dist#CLIENT} is the
 * current NeoForge way to hold client code; it is never constructed on a dedicated server.
 *
 * <p>Phase 1 only proves the client distribution loads. The HUD, scoreboard panels and team
 * screens arrive in phase 2.
 */
@Mod(value = CoveBattle.MODID, dist = Dist.CLIENT)
public final class CoveBattleClient {

    public CoveBattleClient(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::onRegisterRenderers);
    }

    /** The thrown items render as their item, the way vanilla draws a snowball. */
    private void onRegisterRenderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(io.github.creepydutchboy.covebattle.registry.CBEntities.STORM_EGG.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(io.github.creepydutchboy.covebattle.registry.CBEntities.SMOKE_BOMB.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        CoveBattle.LOGGER.info("{} client ready ({}).", CoveBattle.MOD_NAME, UpdateBridge.modVersion());
    }
}
