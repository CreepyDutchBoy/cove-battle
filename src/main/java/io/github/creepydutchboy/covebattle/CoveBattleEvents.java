package io.github.creepydutchboy.covebattle;

import io.github.creepydutchboy.covebattle.command.CoveBattleCommands;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

/** Game-bus events, server side. */
@EventBusSubscriber(modid = CoveBattle.MODID)
public final class CoveBattleEvents {

    private CoveBattleEvents() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CoveBattleCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        CoveBattle.LOGGER.info("{} {} ready (Minecraft {}).",
                CoveBattle.MOD_NAME, UpdateBridge.modVersion(), UpdateBridge.mcVersion());
    }

    @SubscribeEvent
    static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!CoveBattleConfig.notifyInChat) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UpdateOutcome outcome = UpdateBridge.last();
        if (!outcome.isActionable()) return;

        switch (outcome.status()) {
            case UPDATE_INSTALLED -> player.sendSystemMessage(prefix()
                    .append(Component.literal("updated to " + outcome.version() + " — restart the game to apply.")
                            .withStyle(ChatFormatting.GREEN)));
            case UPDATE_AVAILABLE -> player.sendSystemMessage(prefix()
                    .append(Component.literal("version " + outcome.version() + " is available.")
                            .withStyle(ChatFormatting.YELLOW)));
            default -> {
                // nothing worth interrupting the player for
            }
        }
    }

    private static MutableComponent prefix() {
        return Component.literal("[" + CoveBattle.MOD_NAME + "] ").withStyle(ChatFormatting.GOLD);
    }
}
