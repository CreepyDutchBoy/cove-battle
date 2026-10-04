package io.github.creepydutchboy.covebattle;

import com.mojang.logging.LogUtils;
import io.github.creepydutchboy.covebattle.registry.CBBlockEntities;
import io.github.creepydutchboy.covebattle.registry.CBBlocks;
import io.github.creepydutchboy.covebattle.registry.CBItems;
import io.github.creepydutchboy.covebattle.registry.CBTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Entry point. Phase 1 deliberately registers no game content: it exists to prove the build,
 * both distributions, the config, the command tree and the self-updater.
 */
@Mod(CoveBattle.MODID)
public class CoveBattle {

    public static final String MODID = "covebattle";
    public static final String MOD_NAME = "Cove Battle";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CoveBattle(IEventBus modEventBus, ModContainer modContainer) {
        CBBlocks.BLOCKS.register(modEventBus);
        CBItems.ITEMS.register(modEventBus);
        CBBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CBTabs.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, CoveBattleConfig.SPEC);
        modEventBus.addListener(CoveBattleConfig::onLoad);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("{} {} starting on Minecraft {}", MOD_NAME, UpdateBridge.modVersion(), UpdateBridge.mcVersion());
        LOGGER.info("Updates are managed at {}", UpdateBridge.settings().modsDir().resolve(UpdateBridge.JAR_NAME));
        UpdateBridge.kickOffStartupCheck();
    }
}
