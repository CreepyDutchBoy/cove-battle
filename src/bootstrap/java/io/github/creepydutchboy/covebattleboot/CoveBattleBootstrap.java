package io.github.creepydutchboy.covebattleboot;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * A few tens of kilobytes whose only job is to put the real Cove Battle mod in the mods folder.
 *
 * <p>Hand this jar to someone over chat; on first launch it downloads the full mod, verifies it,
 * and asks for a restart. Once the real mod is installed this one steps aside — the full mod keeps
 * itself up to date from then on, so this does nothing but stay available as a fallback.
 */
@Mod(CoveBattleBootstrap.MODID)
public class CoveBattleBootstrap {

    public static final String MODID = "covebattleboot";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile String notice;

    public CoveBattleBootstrap(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        if (ModList.get().isLoaded("covebattle")) {
            LOGGER.info("Cove Battle is installed; the bootstrap has nothing to do.");
            return;
        }

        Thread thread = new Thread(() -> {
            Path mods = FMLPaths.MODSDIR.get();
            String mcVersion = ModList.get().getModContainerById("minecraft")
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");

            LOGGER.info("Cove Battle is not installed. Fetching it for Minecraft {}...", mcVersion);
            BootstrapUpdater.Result result = new BootstrapUpdater(LOGGER::info).run(mods, mcVersion, null);
            notice = result.message();
            if (result.installed()) {
                LOGGER.info("{}", result.message());
            } else {
                LOGGER.warn("Could not install Cove Battle: {}", result.message());
            }
        }, "covebattle-bootstrap");
        thread.setDaemon(true);
        thread.start();
    }

    /** Tells whoever joins what happened, since a fresh install needs a restart to take effect. */
    @EventBusSubscriber(modid = MODID)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
            if (notice == null) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            player.sendSystemMessage(Component.literal("[Cove Battle] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(notice).withStyle(ChatFormatting.GREEN)));
        }
    }
}
