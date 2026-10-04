package io.github.creepydutchboy.covebattle.game;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import io.github.creepydutchboy.covebattle.loot.ContainerRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The match itself: one state machine ticked once per server tick.
 *
 * <p>Shape follows the console Battle mini game — a frozen grace period on the platform, open
 * fighting while a timer runs, then a closing border to force the finish — played as a best of
 * three with a round win going to the last player standing.
 */
public final class BattleGame {

    private static final double VANILLA_BORDER_SIZE = 59999968.0D;
    private static final int TIE_HOLD_SECONDS = 30;
    private static final double BORDER_STEP_FACTOR = 0.75D;

    private final MinecraftServer server;
    private final BattleHud hud = new BattleHud();

    private GamePhase phase = GamePhase.LOBBY;
    private int phaseTicks;
    private int round;

    private final List<UUID> participants = new ArrayList<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, PlayerStats> stats = new LinkedHashMap<>();

    private ContainerRegistry registry;
    private double borderRadius;
    private int borderStepTicks;
    private int restockTicks;
    private int tieTicks;
    private int pingUntilTick = -1;
    private int lastPingSecond = -1;
    private UUID roundWinner;

    public BattleGame(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ state

    public GamePhase phase() {
        return phase;
    }

    public int round() {
        return round;
    }

    public ContainerRegistry registry() {
        return registry;
    }

    public ServerLevel level() {
        return server.overworld();
    }

    public BlockPos centre() {
        return new BlockPos(CoveBattleConfig.centreX, CoveBattleConfig.centreY, CoveBattleConfig.centreZ);
    }

    public int participantCount() {
        return participants.size();
    }

    public int aliveCount() {
        return alive().size();
    }

    public Map<UUID, PlayerStats> stats() {
        return stats;
    }

    public double borderRadius() {
        return borderRadius;
    }

    // ------------------------------------------------------------------ control

    /** Starts a match with everyone currently playing. Returns false and explains why if it cannot. */
    public boolean start(Consumer<Component> feedback) {
        if (phase != GamePhase.LOBBY) {
            feedback.accept(Component.literal("A match is already running. Stop it first.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GameType mode = player.gameMode.getGameModeForPlayer();
            if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) candidates.add(player);
        }
        if (candidates.size() < CoveBattleConfig.minPlayers) {
            feedback.accept(Component.literal("Need at least " + CoveBattleConfig.minPlayers
                            + " player(s) in survival or adventure to start; found " + candidates.size() + ".")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        participants.clear();
        stats.clear();
        for (ServerPlayer player : candidates) {
            participants.add(player.getUUID());
            stats.put(player.getUUID(), new PlayerStats(player.getGameProfile().getName()));
        }

        registry = ContainerRegistry.scan(level(), centre(), CoveBattleConfig.playRadius,
                CoveBattleConfig.centreTierRadius);
        if (registry.size() == 0) {
            Announcer.broadcast(server, Announcer.prefix().append(Component
                    .literal("No chests or barrels found in the arena — loot will be empty. Check the arena centre in the config.")
                    .withStyle(ChatFormatting.RED)));
        }

        round = 0;
        Announcer.broadcast(server, Announcer.prefix().append(Component
                .literal("Match starting — best of " + (CoveBattleConfig.roundsToWin * 2 - 1) + ".")
                .withStyle(ChatFormatting.YELLOW)));
        startRound();
        return true;
    }

    /** Ends everything and returns to the lobby. */
    public void stop(boolean announce) {
        if (announce && phase != GamePhase.LOBBY) {
            Announcer.broadcast(server, Announcer.prefix()
                    .append(Component.literal("Match stopped.").withStyle(ChatFormatting.RED)));
        }
        for (ServerPlayer player : onlineParticipants()) {
            player.setInvulnerable(false);
            player.removeEffect(MobEffects.GLOWING);
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.ADVENTURE);
                player.teleportTo(centre().getX() + 0.5, centre().getY(), centre().getZ() + 0.5);
            }
        }
        if (registry != null) registry.clearAll(level());
        toLobby();
    }

    /** Full reset: lobby, border restored, sidebar gone, loot cleared, stats dropped. */
    public void reset() {
        stop(false);
        stats.clear();
        participants.clear();
        eliminated.clear();
        registry = null;
        round = 0;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        hud.syncAudience(server);
        enforceGameMode();

        switch (phase) {
            case LOBBY -> { /* nothing to drive */ }
            case GRACE -> tickGrace();
            case FIGHT -> tickFight();
            case SHOWDOWN -> tickShowdown();
            case ROUND_END -> tickRoundEnd();
            case MATCH_END -> tickMatchEnd();
        }
        phaseTicks++;
    }

    private void tickGrace() {
        int total = CoveBattleConfig.graceSeconds * 20;
        int remaining = total - phaseTicks;

        List<ServerPlayer> players = onlineParticipants();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            holdOnPodium(player, i, players.size());
            BattleHud.actionBar(player, Component.literal("Released in " + Math.max(0, (remaining + 19) / 20) + "s")
                    .withStyle(ChatFormatting.YELLOW));
        }

        hud.setBar(Component.literal("Round " + round + " — grace period")
                .withStyle(ChatFormatting.YELLOW), total == 0 ? 0f : (float) remaining / total,
                BossEvent.BossBarColor.YELLOW);

        int second = (remaining + 19) / 20;
        if (second <= 5 && second >= 1 && second != lastPingSecond) {
            lastPingSecond = second;
            for (ServerPlayer player : players) {
                BattleHud.title(player, Component.literal(String.valueOf(second)).withStyle(ChatFormatting.GOLD),
                        Component.empty(), 0, 15, 5);
                BattleHud.sound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1f, 1.2f);
            }
        }

        if (remaining <= 0) beginFight();
    }

    private void beginFight() {
        phase = GamePhase.FIGHT;
        phaseTicks = 0;
        lastPingSecond = -1;
        for (ServerPlayer player : onlineParticipants()) {
            player.setInvulnerable(false);
            BattleHud.title(player, Component.literal("FIGHT!").withStyle(ChatFormatting.RED),
                    Component.literal("Loot the centre — it holds the best gear").withStyle(ChatFormatting.GRAY),
                    0, 25, 10);
            BattleHud.sound(player, SoundEvents.ENDER_DRAGON_GROWL, 1f, 1.4f);
        }
    }

    private void tickFight() {
        int total = CoveBattleConfig.matchSeconds * 20;
        int remaining = Math.max(0, total - phaseTicks);

        hud.setBar(Component.literal("Round " + round + "  ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(BattleHud.clock(remaining)).withStyle(ChatFormatting.WHITE)),
                total == 0 ? 0f : (float) remaining / total, BossEvent.BossBarColor.RED);

        // The main timer is only spelled out on the action bar every 30 seconds, then it gets out
        // of the way again so the fight HUD is readable.
        int second = remaining / 20;
        if (second > 0 && second % 30 == 0 && second != lastPingSecond) {
            lastPingSecond = second;
            pingUntilTick = phaseTicks + 60;
            for (ServerPlayer player : onlineParticipants()) {
                BattleHud.sound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);
            }
        }

        boolean showTimer = phaseTicks <= pingUntilTick;
        for (ServerPlayer player : onlineAlive()) {
            BattleHud.actionBar(player, showTimer ? timerLine(remaining) : statusLine(player));
        }

        tickRestock();
        if (checkRoundOver()) return;
        if (remaining <= 0) beginShowdown();
    }

    private void beginShowdown() {
        phase = GamePhase.SHOWDOWN;
        phaseTicks = 0;
        borderStepTicks = 0;
        tieTicks = 0;

        WorldBorder border = level().getWorldBorder();
        borderRadius = CoveBattleConfig.playRadius;
        border.setCenter(centre().getX() + 0.5, centre().getZ() + 0.5);
        border.setSize(borderRadius * 2);
        border.setWarningBlocks(8);
        border.setWarningTime(5);
        border.setDamagePerBlock(0.8D);
        border.setDamageSafeZone(1.0D);

        for (ServerPlayer player : onlineParticipants()) {
            BattleHud.title(player, Component.literal("SHOWDOWN").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.literal("The border is closing").withStyle(ChatFormatting.GRAY), 5, 40, 10);
            BattleHud.sound(player, SoundEvents.WITHER_SPAWN, 0.7f, 1.4f);
        }
        Announcer.broadcast(server, Announcer.prefix().append(Component
                .literal("Time is up — the border is closing in.").withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    private void tickShowdown() {
        WorldBorder border = level().getWorldBorder();

        if (CoveBattleConfig.showdownGlow && phaseTicks % 40 == 0) {
            for (ServerPlayer player : onlineAlive()) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, false));
            }
        }

        borderStepTicks++;
        if (borderStepTicks >= CoveBattleConfig.borderStepSeconds * 20 && borderRadius > CoveBattleConfig.minRadius) {
            borderStepTicks = 0;
            double next = Math.max(CoveBattleConfig.minRadius, borderRadius * BORDER_STEP_FACTOR);
            long millis = CoveBattleConfig.borderStepSeconds * 1000L;
            border.lerpSizeBetween(borderRadius * 2, next * 2, millis);
            borderRadius = next;
        }

        boolean atFloor = borderRadius <= CoveBattleConfig.minRadius + 0.01D;
        float progress = (float) ((borderRadius - CoveBattleConfig.minRadius)
                / Math.max(1.0D, CoveBattleConfig.playRadius - CoveBattleConfig.minRadius));
        hud.setBar(Component.literal("Showdown — border ")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(Math.round(borderRadius) + " blocks").withStyle(ChatFormatting.WHITE)),
                atFloor ? 0f : progress, BossEvent.BossBarColor.PURPLE);

        for (ServerPlayer player : onlineAlive()) {
            BattleHud.actionBar(player, statusLine(player));
        }

        tickRestock();
        if (checkRoundOver()) return;

        if (atFloor) {
            tieTicks++;
            if (tieTicks >= TIE_HOLD_SECONDS * 20) {
                endRound(null, "Neither player fell — the round is a tie and scores nothing.");
            }
        }
    }

    private void tickRestock() {
        if (registry == null) return;
        restockTicks++;
        if (restockTicks < CoveBattleConfig.restockSeconds * 20) return;
        restockTicks = 0;
        int refilled = registry.restock(level(), CoveBattleConfig.restockCount, 6.0D, level().random);
        if (refilled > 0) {
            for (ServerPlayer player : onlineAlive()) {
                BattleHud.sound(player, SoundEvents.CHEST_OPEN, 0.4f, 1.6f);
            }
            Announcer.broadcast(server, Announcer.prefix().append(Component
                    .literal(refilled + " container(s) restocked.").withStyle(ChatFormatting.DARK_AQUA)));
        }
    }

    private void tickRoundEnd() {
        int total = CoveBattleConfig.roundEndSeconds * 20;
        int remaining = Math.max(0, total - phaseTicks);
        hud.setBar(Component.literal("Next round in " + ((remaining + 19) / 20) + "s")
                .withStyle(ChatFormatting.GRAY), (float) remaining / total, BossEvent.BossBarColor.WHITE);

        if (remaining > 0) return;

        if (roundWinner != null && stats.containsKey(roundWinner)
                && stats.get(roundWinner).roundWins >= CoveBattleConfig.roundsToWin) {
            endMatch();
        } else {
            startRound();
        }
    }

    private void tickMatchEnd() {
        int total = 10 * 20;
        int remaining = Math.max(0, total - phaseTicks);
        hud.setBar(Component.literal("Returning to the lobby").withStyle(ChatFormatting.GRAY),
                (float) remaining / total, BossEvent.BossBarColor.WHITE);
        if (remaining <= 0) toLobby();
    }

    // ------------------------------------------------------------------ rounds

    private void startRound() {
        round++;
        eliminated.clear();
        roundWinner = null;
        phase = GamePhase.GRACE;
        phaseTicks = 0;
        restockTicks = 0;
        tieTicks = 0;
        lastPingSecond = -1;
        pingUntilTick = -1;

        WorldBorder border = level().getWorldBorder();
        border.setCenter(centre().getX() + 0.5, centre().getZ() + 0.5);
        border.setSize(CoveBattleConfig.playRadius * 2.0D);
        border.setWarningBlocks(4);
        border.setDamagePerBlock(0.8D);
        border.setDamageSafeZone(1.0D);
        borderRadius = CoveBattleConfig.playRadius;

        int filled = registry == null ? 0 : registry.fillAll(level(), level().random);
        CoveBattle.LOGGER.info("Round {} starting: {} containers stocked", round, filled);

        List<ServerPlayer> players = onlineParticipants();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);
            resetPlayer(player);
            placeOnPodium(player, i, players.size());
            BattleHud.title(player, Component.literal("ROUND " + round).withStyle(ChatFormatting.GOLD),
                    Component.literal("Best of " + (CoveBattleConfig.roundsToWin * 2 - 1)).withStyle(ChatFormatting.GRAY),
                    5, 30, 10);
        }
        updateSidebar();
    }

    private void endRound(UUID winner, String message) {
        phase = GamePhase.ROUND_END;
        phaseTicks = 0;
        roundWinner = winner;

        if (winner != null) {
            PlayerStats won = stats.get(winner);
            if (won != null) won.roundWins++;
        }
        updateSidebar();

        for (ServerPlayer player : onlineParticipants()) {
            boolean isWinner = winner != null && player.getUUID().equals(winner);
            if (winner == null) {
                BattleHud.title(player, Component.literal("TIE").withStyle(ChatFormatting.GRAY),
                        Component.empty(), 5, 50, 15);
            } else {
                BattleHud.title(player,
                        Component.literal(isWinner ? "ROUND WON" : "ROUND LOST")
                                .withStyle(isWinner ? ChatFormatting.GREEN : ChatFormatting.RED),
                        Component.literal(scoreLine()).withStyle(ChatFormatting.GRAY), 5, 50, 15);
                BattleHud.sound(player, isWinner ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WITHER_DEATH, 0.8f, 1.2f);
            }
            player.setInvulnerable(false);
        }
        Announcer.broadcast(server, Announcer.prefix().append(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
    }

    private void endMatch() {
        phase = GamePhase.MATCH_END;
        phaseTicks = 0;

        Announcer.broadcast(server, Component.empty());
        Announcer.broadcast(server, Component.literal("  MATCH OVER")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        stats.forEach((id, s) -> Announcer.broadcast(server, Component.literal("  " + s.name + "  ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("rounds " + s.roundWins).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   kills " + s.kills).withStyle(ChatFormatting.RED))
                .append(Component.literal("   looted " + s.containersLooted).withStyle(ChatFormatting.GOLD))));
        Announcer.broadcast(server, Component.empty());

        for (ServerPlayer player : onlineParticipants()) {
            player.removeEffect(MobEffects.GLOWING);
            player.setInvulnerable(false);
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.ADVENTURE);
            }
            placeOnPodium(player, 0, 1);
        }
        if (registry != null) registry.clearAll(level());
    }

    private void toLobby() {
        phase = GamePhase.LOBBY;
        phaseTicks = 0;
        round = 0;
        eliminated.clear();
        roundWinner = null;

        WorldBorder border = level().getWorldBorder();
        border.setSize(VANILLA_BORDER_SIZE);
        border.setDamagePerBlock(0.2D);
        border.setWarningBlocks(5);
        borderRadius = VANILLA_BORDER_SIZE / 2;

        hud.hideBar();
        BattleHud.hideSidebar(server);
        Announcer.postLobbyPrompt(server);
    }

    private boolean checkRoundOver() {
        if (!phase.allowsCombat()) return false;
        if (participants.size() < 2) return false;

        List<UUID> alive = alive();
        if (alive.size() > 1) return false;

        if (alive.size() == 1) {
            PlayerStats winner = stats.get(alive.get(0));
            endRound(alive.get(0), (winner == null ? "Someone" : winner.name) + " takes the round.");
        } else {
            endRound(null, "Everyone fell — the round is a tie and scores nothing.");
        }
        return true;
    }

    // ------------------------------------------------------------------ player events

    /** @return true when the death was consumed by the game and should not kill the player */
    public boolean onDeath(ServerPlayer player, ServerPlayer killer) {
        if (!phase.allowsCombat()) return false;
        if (!participants.contains(player.getUUID())) return false;
        if (eliminated.contains(player.getUUID())) return false;

        eliminated.add(player.getUUID());
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.getInventory().clearContent();
        player.setGameMode(GameType.SPECTATOR);

        if (killer != null && !killer.getUUID().equals(player.getUUID())) {
            PlayerStats ks = stats.get(killer.getUUID());
            if (ks != null) ks.kills++;
        }

        PlayerStats ds = stats.get(player.getUUID());
        Announcer.broadcast(server, Announcer.prefix().append(Component
                .literal((ds == null ? player.getGameProfile().getName() : ds.name) + " was eliminated.")
                .withStyle(ChatFormatting.RED)));

        checkRoundOver();
        return true;
    }

    public void onContainerOpened(ServerPlayer player, BlockPos pos) {
        if (!phase.allowsCombat() || registry == null) return;
        if (!registry.contains(pos)) return;
        registry.markLooted(pos);
        PlayerStats s = stats.get(player.getUUID());
        if (s != null) s.containersLooted++;
    }

    public void onPlayerJoin(ServerPlayer player) {
        hud.syncAudience(server);
        if (phase == GamePhase.LOBBY) {
            Announcer.postLobbyPrompt(player);
        } else {
            player.sendSystemMessage(Announcer.prefix().append(Component
                    .literal("A match is in progress (round " + round + ", " + phase.name().toLowerCase() + ").")
                    .withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ testing hooks

    /** Ends the current phase on the next tick, so phase transitions can be exercised without waiting. */
    public void debugSkipPhase() {
        phaseTicks = Integer.MAX_VALUE / 4;
    }

    /** Rescans the arena for containers. */
    public int debugRescan() {
        registry = ContainerRegistry.scan(level(), centre(), CoveBattleConfig.playRadius,
                CoveBattleConfig.centreTierRadius);
        return registry.size();
    }

    /** Applies the arena border at a given radius, as the showdown phase would. */
    public void debugBorder(double radius) {
        WorldBorder border = level().getWorldBorder();
        border.setCenter(centre().getX() + 0.5, centre().getZ() + 0.5);
        border.setSize(radius * 2);
        border.setWarningBlocks(8);
        border.setDamagePerBlock(0.8D);
        border.setDamageSafeZone(1.0D);
        borderRadius = radius;
    }

    /** Stocks every known container without starting a match. */
    public int debugFill() {
        if (registry == null) debugRescan();
        return registry.fillAll(level(), level().random);
    }

    // ------------------------------------------------------------------ helpers

    private void enforceGameMode() {
        if (!CoveBattleConfig.forceAdventure) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Creative and spectator are left alone so building and moderating still work.
            if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                player.setGameMode(GameType.ADVENTURE);
            }
        }
    }

    private void resetPlayer(ServerPlayer player) {
        player.setGameMode(GameType.ADVENTURE);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setInvulnerable(true);
        player.setRemainingFireTicks(0);
        player.fallDistance = 0;
    }

    private void placeOnPodium(ServerPlayer player, int index, int count) {
        BlockPos centre = centre();
        double angle = count <= 1 ? 0 : (2 * Math.PI * index) / count;
        double x = centre.getX() + 0.5 + Math.cos(angle) * CoveBattleConfig.podiumRadius;
        double z = centre.getZ() + 0.5 + Math.sin(angle) * CoveBattleConfig.podiumRadius;
        double dx = (centre.getX() + 0.5) - x;
        double dz = (centre.getZ() + 0.5) - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.teleportTo(level(), x, centre.getY(), z, Set.of(), yaw, 0f);
    }

    /** Keeps a player on their podium during the grace period without constant rubber-banding. */
    private void holdOnPodium(ServerPlayer player, int index, int count) {
        BlockPos centre = centre();
        double angle = count <= 1 ? 0 : (2 * Math.PI * index) / count;
        double x = centre.getX() + 0.5 + Math.cos(angle) * CoveBattleConfig.podiumRadius;
        double z = centre.getZ() + 0.5 + Math.sin(angle) * CoveBattleConfig.podiumRadius;
        double distSq = player.distanceToSqr(x, player.getY(), z);
        if (distSq > 0.75D) {
            placeOnPodium(player, index, count);
        }
        player.setInvulnerable(true);
    }

    private Component timerLine(int remainingTicks) {
        return Component.literal("TIME  ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(BattleHud.clock(remainingTicks)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  until the border closes").withStyle(ChatFormatting.GRAY));
    }

    private Component statusLine(ServerPlayer player) {
        PlayerStats s = stats.get(player.getUUID());
        Component base = Component.literal("❤ " + (int) Math.ceil(player.getHealth()))
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("   looted " + (s == null ? 0 : s.containersLooted))
                        .withStyle(ChatFormatting.GOLD));

        ServerPlayer opponent = null;
        for (UUID id : alive()) {
            if (id.equals(player.getUUID())) continue;
            opponent = server.getPlayerList().getPlayer(id);
            if (opponent != null) break;
        }
        if (opponent == null) return base;
        return ((net.minecraft.network.chat.MutableComponent) base)
                .append(Component.literal("   " + opponent.getGameProfile().getName() + " ❤ "
                        + (int) Math.ceil(opponent.getHealth())).withStyle(ChatFormatting.GRAY));
    }

    private void updateSidebar() {
        Map<String, Integer> rows = new LinkedHashMap<>();
        stats.forEach((id, s) -> rows.put(s.name, s.roundWins));
        BattleHud.showSidebar(server, "Round " + round + " / best of " + (CoveBattleConfig.roundsToWin * 2 - 1), rows);
    }

    private String scoreLine() {
        StringBuilder sb = new StringBuilder();
        stats.forEach((id, s) -> {
            if (sb.length() > 0) sb.append("  -  ");
            sb.append(s.name).append(' ').append(s.roundWins);
        });
        return sb.toString();
    }

    private List<UUID> alive() {
        List<UUID> result = new ArrayList<>();
        for (UUID id : participants) {
            if (eliminated.contains(id)) continue;
            if (server.getPlayerList().getPlayer(id) == null) continue;
            result.add(id);
        }
        return result;
    }

    private List<ServerPlayer> onlineParticipants() {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) result.add(player);
        }
        return result;
    }

    private List<ServerPlayer> onlineAlive() {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : alive()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) result.add(player);
        }
        return result;
    }
}
