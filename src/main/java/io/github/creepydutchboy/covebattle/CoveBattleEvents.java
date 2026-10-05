package io.github.creepydutchboy.covebattle;

import io.github.creepydutchboy.covebattle.command.CoveBattleCommands;
import io.github.creepydutchboy.covebattle.game.Announcer;
import io.github.creepydutchboy.covebattle.game.BattleGame;
import io.github.creepydutchboy.covebattle.game.BattleManager;
import io.github.creepydutchboy.covebattle.lobby.LobbyState;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import io.github.creepydutchboy.covebattle.update.UpdateOutcome;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
        RulesState.load(event.getServer().getServerDirectory().resolve("config"));
        BattleManager.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LobbyState.onPlayerLeave(player);
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        BattleManager.onServerStopping();
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        BattleGame game = BattleManager.game();
        if (game != null) game.tick();
        io.github.creepydutchboy.covebattle.mirage.MirageManager.tick(event.getServer());
        campfireHealing(event.getServer());
        ghostBootsSpeed(event.getServer());
    }

    private static final net.minecraft.resources.ResourceLocation GHOST_SPEED =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "ghost_boots_speed");

    /** Ghost Boots make you quicker while they are on, and only while they are on. */
    private static void ghostBootsSpeed(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed == null) continue;
            boolean worn = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)
                    .is(io.github.creepydutchboy.covebattle.registry.CBItems.GHOST_BOOTS.get());
            boolean applied = speed.getModifier(GHOST_SPEED) != null;
            if (worn && !applied) {
                speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                        GHOST_SPEED, 0.12D,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (!worn && applied) {
                speed.removeModifier(GHOST_SPEED);
            }
        }
    }

    /** A lit campfire you are standing near knits you back together, slowly. */
    private static int campfireTicks;

    private static void campfireHealing(net.minecraft.server.MinecraftServer server) {
        if (++campfireTicks < 40) return;
        campfireTicks = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.getHealth() >= player.getMaxHealth()) continue;
            if (nearLitCampfire(player)) {
                player.heal(1.0F);
                player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                        player.getX(), player.getY() + 1.8, player.getZ(), 1, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    private static boolean nearLitCampfire(ServerPlayer player) {
        net.minecraft.core.BlockPos origin = player.blockPosition();
        net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = -4; x <= 4; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -4; z <= 4; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    var state = player.level().getBlockState(cursor);
                    if (state.getBlock() instanceof net.minecraft.world.level.block.CampfireBlock
                            && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static MutableComponent prefix() {
        return Component.literal("[" + CoveBattle.MOD_NAME + "] ").withStyle(ChatFormatting.GOLD);
    }
}
