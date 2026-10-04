package io.github.creepydutchboy.covebattle;

import io.github.creepydutchboy.covebattle.command.CoveBattleCommands;
import io.github.creepydutchboy.covebattle.game.Announcer;
import io.github.creepydutchboy.covebattle.game.BattleGame;
import io.github.creepydutchboy.covebattle.game.BattleManager;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Game-bus events: the match tick, deaths, container opens, joins and the lobby prompt. */
@EventBusSubscriber(modid = CoveBattle.MODID)
public final class CoveBattleEvents {

    private CoveBattleEvents() {}

    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        CoveBattleCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        CoveBattle.LOGGER.info("{} {} ready (Minecraft {}).",
                CoveBattle.MOD_NAME, UpdateBridge.modVersion(), UpdateBridge.mcVersion());
        BattleManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        BattleManager.onServerStopping();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        BattleGame game = BattleManager.game();
        if (game != null) game.tick();
    }

    /**
     * Fires on {@code /reload} as well as on join, which is how the start prompt reappears after a
     * reload without the mod needing to be reloaded itself.
     */
    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        if (event.getPlayerList().getPlayers().isEmpty()) return;
        Announcer.postLobbyPrompt(event.getPlayerList().getServer());
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BattleGame game = BattleManager.game();
        if (game == null) return;

        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        if (game.onDeath(player, killer)) {
            // The game turns a death into an elimination, so the vanilla death screen never appears.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BattleGame game = BattleManager.game();
        if (game != null) game.onContainerOpened(player, event.getPos());
    }

    @SubscribeEvent
    static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BattleGame game = BattleManager.game();
        if (game != null) game.onPlayerJoin(player);

        if (!CoveBattleConfig.notifyInChat) return;
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
