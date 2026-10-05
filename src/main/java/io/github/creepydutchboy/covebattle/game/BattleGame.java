package io.github.creepydutchboy.covebattle.game;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import io.github.creepydutchboy.covebattle.block.MarkerKind;
import io.github.creepydutchboy.covebattle.loot.ContainerRegistry;
import io.github.creepydutchboy.covebattle.rules.MatchMode;
import io.github.creepydutchboy.covebattle.rules.Mutators;
import io.github.creepydutchboy.covebattle.rules.RulesState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The match itself: one state machine ticked once per server tick.
 *
 * <p>Shape follows the console Battle mini game — a frozen grace period on the spawns, open
 * fighting while a timer runs, then a closing border to force the finish — played as a best of
 * three. A round goes to the last <em>side</em> standing, where a side is one player in
 * {@link BattleMode#SOLO} and a whole team in {@link BattleMode#TEAMS}.
 */
public final class BattleGame {

    public static final String TEAM_RED = "cb_red";
    public static final String TEAM_BLUE = "cb_blue";

    private static final double VANILLA_BORDER_SIZE = 59999968.0D;
    private static final int TIE_HOLD_SECONDS = 30;
    /** How long a match waits when one side empties out, before awarding it to the side still there. */
    private static final int EMPTY_SIDE_GRACE_TICKS = 20 * 20;
    private static final double BORDER_STEP_FACTOR = 0.75D;

    private final MinecraftServer server;
    private final BattleHud hud = new BattleHud();

    private static final ResourceLocation HEALTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "mutator_health");
    private static final ResourceLocation SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "mutator_speed");

    private GamePhase phase = GamePhase.LOBBY;
    private BattleMode mode = BattleMode.SOLO;
    /** Snapshot taken at match start so sliders moved mid-match cannot destabilise a round. */
    private Mutators rules = Mutators.remastered();
    private MatchMode ruleMode = MatchMode.REMASTERED;
    private int phaseTicks;
    private int round;

    private final List<UUID> participants = new ArrayList<>();
    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<UUID, PlayerStats> stats = new LinkedHashMap<>();
    private final Map<UUID, String> sides = new LinkedHashMap<>();
    private final Map<String, Integer> sideWins = new LinkedHashMap<>();

    @Nullable
    private ContainerRegistry registry;
    @Nullable
    private ArenaLayout layout;

    private double borderRadius;
    private int borderStepTicks;
    private int restockTicks;
    private int tieTicks;
    private int pingUntilTick = -1;
    private int pausedTicks;
    private int lastPingSecond = -1;
    @Nullable
    private String roundWinnerSide;

    public BattleGame(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------------ state

    public GamePhase phase() {
        return phase;
    }

    public BattleMode mode() {
        return mode;
    }

    /** The rules this match is running under, or the live ones when idle. */
    public Mutators rules() {
        return phase == GamePhase.LOBBY ? RulesState.active() : rules;
    }

    public MatchMode ruleMode() {
        return phase == GamePhase.LOBBY ? RulesState.mode() : ruleMode;
    }

    public int round() {
        return round;
    }

    @Nullable
    public ContainerRegistry registry() {
        return registry;
    }

    @Nullable
    public ArenaLayout layout() {
        return layout;
    }

    /** Null while the server is still coming up, or if it failed to initialise. */
    @Nullable
    public ServerLevel level() {
        return server.overworld();
    }

    /** Marker-defined centre when a map provides one, otherwise the config. */
    public BlockPos centre() {
        return layout != null ? layout.centre() : configCentre();
    }

    private BlockPos configCentre() {
        return new BlockPos(CoveBattleConfig.centreX, CoveBattleConfig.centreY, CoveBattleConfig.centreZ);
    }

    public int playRadius() {
        return layout != null ? layout.radius() : CoveBattleConfig.playRadius;
    }

    public int participantCount() {
        return participants.size();
    }

    public int aliveCount() {
        return alive().size();
    }

    public int aliveSideCount() {
        return aliveSides().size();
    }

    public Map<UUID, PlayerStats> stats() {
        return stats;
    }

    public Map<String, Integer> sideWins() {
        return sideWins;
    }

    public double borderRadius() {
        return borderRadius;
    }

    // ------------------------------------------------------------------ control

    /** Starts a match with everyone currently playing. Returns false and explains why if it cannot. */
    public boolean start(Consumer<Component> feedback, BattleMode requestedMode) {
        if (phase != GamePhase.LOBBY) {
            feedback.accept(Component.literal("A match is already running. Stop it first.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }

        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Anyone sitting in the lobby menu is a spectator of sorts: they are not drafted in.
            if (io.github.creepydutchboy.covebattle.lobby.LobbyState.isInLobby(player.getUUID())) continue;
            GameType gameMode = player.gameMode.getGameModeForPlayer();
            if (gameMode == GameType.SURVIVAL || gameMode == GameType.ADVENTURE) candidates.add(player);
        }
        candidates.addAll(BattleBots.all());
        if (candidates.size() < CoveBattleConfig.minPlayers) {
            feedback.accept(Component.literal("Need at least " + CoveBattleConfig.minPlayers
                            + " player(s) in survival or adventure to start; found " + candidates.size() + ".")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        if (candidates.size() > CoveBattleConfig.maxPlayers) {
            feedback.accept(Component.literal("Too many players: " + candidates.size() + " of a maximum "
                    + CoveBattleConfig.maxPlayers + ".").withStyle(ChatFormatting.RED));
            return false;
        }

        mode = requestedMode;
        rules = RulesState.active();
        ruleMode = RulesState.mode();
        participants.clear();
        stats.clear();
        sides.clear();
        sideWins.clear();

        for (ServerPlayer player : candidates) {
            participants.add(player.getUUID());
            stats.put(player.getUUID(), new PlayerStats(player.getGameProfile().getName()));
        }

        if (mode == BattleMode.TEAMS) {
            if (!assignTeams(candidates, feedback)) return false;
        } else {
            clearTeams();
            for (ServerPlayer player : candidates) sides.put(player.getUUID(), player.getUUID().toString());
        }
        for (String side : sides.values()) sideWins.putIfAbsent(side, 0);

        ArenaScan scan = ArenaScan.run(level(), configCentre(), CoveBattleConfig.playRadius,
                CoveBattleConfig.centreTierRadius);
        layout = scan.layout();
        registry = scan.containers();

        if (registry.size() == 0) {
            Announcer.broadcast(server, Announcer.prefix().append(Component.literal(
                            "No chests or barrels found in the arena — loot will be empty. Place an Arena Centre Marker, or fix the centre in the config.")
                    .withStyle(ChatFormatting.RED)));
        }
        if (!layout.centreFromMarker()) {
            Announcer.broadcast(server, Announcer.prefix().append(Component.literal(
                            "No Arena Centre Marker found; using the config centre.")
                    .withStyle(ChatFormatting.GRAY)));
        }

        round = 0;
        Announcer.broadcast(server, Announcer.prefix().append(Component.literal(
                        ruleMode.label() + " " + mode.label() + " match starting — best of " + rules.bestOf()
                        + " with " + candidates.size() + " player(s).")
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
            clearMutators(player);
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.ADVENTURE);
                sendToLobby(player);
            }
        }
        ServerLevel level = level();
        if (registry != null && level != null) registry.clearAll(level);
        clearTeams();
        toLobby();
    }

    /** Full reset: lobby, border restored, sidebar gone, loot cleared, scores dropped. */
    public void reset() {
        stop(false);
        stats.clear();
        participants.clear();
        eliminated.clear();
        sides.clear();
        sideWins.clear();
        registry = null;
        layout = null;
        round = 0;
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        hud.syncAudience(server);

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
        int total = rules.graceTicks();
        int remaining = total - phaseTicks;

        List<ServerPlayer> players = onlineParticipants();
        for (ServerPlayer player : players) {
            holdAtSpawn(player);
        }
        for (ServerPlayer player : connectedParticipants()) {
            BattleHud.actionBar(player, Component.literal("Released in " + Math.max(0, (remaining + 19) / 20) + "s")
                    .withStyle(ChatFormatting.YELLOW));
        }

        hud.setBar(Component.literal("Round " + round + " — grace period").withStyle(ChatFormatting.YELLOW),
                total == 0 ? 0f : (float) remaining / total, BossEvent.BossBarColor.YELLOW);

        int second = (remaining + 19) / 20;
        if (second <= 5 && second >= 1 && second != lastPingSecond) {
            lastPingSecond = second;
            for (ServerPlayer player : connectedParticipants()) {
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
                    Component.literal("The centre holds the best gear").withStyle(ChatFormatting.GRAY), 0, 25, 10);
            BattleHud.sound(player, SoundEvents.ENDER_DRAGON_GROWL, 1f, 1.4f);
        }
    }

    private void tickFight() {
        if (emptySidePause()) return;
        int total = rules.matchTicks();
        int remaining = Math.max(0, total - phaseTicks);

        hud.setBar(Component.literal("Round " + round + "  ").withStyle(ChatFormatting.RED)
                        .append(Component.literal(BattleHud.clock(remaining)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(scoreSuffix()).withStyle(ChatFormatting.GRAY)),
                total == 0 ? 0f : (float) remaining / total, BossEvent.BossBarColor.RED);

        // The main timer is only spelled out on the action bar every 30 seconds, then it gets out of
        // the way again so the fight HUD stays readable.
        int second = remaining / 20;
        if (second > 0 && second % 30 == 0 && second != lastPingSecond) {
            lastPingSecond = second;
            pingUntilTick = phaseTicks + 60;
            for (ServerPlayer player : connectedParticipants()) {
                BattleHud.sound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);
            }
        }

        boolean showTimer = phaseTicks <= pingUntilTick;
        for (ServerPlayer player : connectedAlive()) {
            BattleHud.actionBar(player, showTimer ? timerLine(remaining) : statusLine(player));
        }

        tickHunger();
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
        borderRadius = playRadius();
        if (!rules.closingBorder()) {
            for (ServerPlayer player : connectedParticipants()) {
                BattleHud.title(player, Component.literal("SHOWDOWN").withStyle(ChatFormatting.LIGHT_PURPLE),
                        Component.literal("Everyone is marked").withStyle(ChatFormatting.GRAY), 5, 40, 10);
            }
            Announcer.broadcast(server, Announcer.prefix().append(Component
                    .literal("Showdown — everyone is marked.").withStyle(ChatFormatting.LIGHT_PURPLE)));
            return;
        }
        border.setCenter(centre().getX() + 0.5, centre().getZ() + 0.5);
        border.setSize(borderRadius * 2);
        border.setWarningBlocks(8);
        border.setWarningTime(5);
        border.setDamagePerBlock(0.8D);
        border.setDamageSafeZone(1.0D);

        for (ServerPlayer player : connectedParticipants()) {
            BattleHud.title(player, Component.literal("SHOWDOWN").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.literal("The border is closing").withStyle(ChatFormatting.GRAY), 5, 40, 10);
            BattleHud.sound(player, SoundEvents.WITHER_SPAWN, 0.7f, 1.4f);
        }
        Announcer.broadcast(server, Announcer.prefix().append(Component
                .literal("Time is up — the border is closing in.").withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    private void tickShowdown() {
        if (emptySidePause()) return;
        WorldBorder border = level().getWorldBorder();

        if (rules.showdownGlow() && phaseTicks % 40 == 0) {
            for (ServerPlayer player : onlineAlive()) {
                player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, false));
            }
        }

        if (!rules.closingBorder()) {
            // Classic: no shrinking border. Glowing marks the endgame and the tie clock ends it.
            hud.setBar(Component.literal("Showdown — last one standing").withStyle(ChatFormatting.LIGHT_PURPLE),
                    1f, BossEvent.BossBarColor.PURPLE);
            for (ServerPlayer player : onlineAlive()) BattleHud.actionBar(player, statusLine(player));
            tickRestock();
            if (checkRoundOver()) return;
            tieTicks++;
            if (tieTicks >= TIE_HOLD_SECONDS * 4 * 20) {
                endRound(null, "Nobody fell — the round is a tie and scores nothing.");
            }
            return;
        }

        borderStepTicks++;
        if (borderStepTicks >= rules.borderStepSeconds() * 20 && borderRadius > rules.minRadius()) {
            borderStepTicks = 0;
            double next = Math.max(rules.minRadius(), borderRadius * BORDER_STEP_FACTOR);
            border.lerpSizeBetween(borderRadius * 2, next * 2, rules.borderStepSeconds() * 1000L);
            borderRadius = next;
        }

        boolean atFloor = borderRadius <= rules.minRadius() + 0.01D;
        float progress = (float) ((borderRadius - rules.minRadius())
                / Math.max(1.0D, playRadius() - rules.minRadius()));
        hud.setBar(Component.literal("Showdown — border ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal(Math.round(borderRadius) + " blocks").withStyle(ChatFormatting.WHITE)),
                atFloor ? 0f : progress, BossEvent.BossBarColor.PURPLE);

        for (ServerPlayer player : connectedAlive()) {
            BattleHud.actionBar(player, statusLine(player));
        }

        tickRestock();
        if (checkRoundOver()) return;

        if (atFloor) {
            tieTicks++;
            if (tieTicks >= TIE_HOLD_SECONDS * 20) {
                endRound(null, "Nobody fell — the round is a tie and scores nothing.");
            }
        }
    }

    /** Keeps the food bar pinned when the hunger mutator is off. */
    private void tickHunger() {
        if (rules.hunger()) return;
        for (ServerPlayer player : onlineAlive()) {
            if (player.getFoodData().getFoodLevel() < 20) player.getFoodData().setFoodLevel(20);
        }
    }

    private void tickRestock() {
        if (registry == null) return;
        restockTicks++;
        if (restockTicks < rules.restockSeconds() * 20) return;
        restockTicks = 0;
        int refilled = registry.restock(level(), rules.restockCount(), 6.0D, level().random, rules);
        if (refilled > 0) {
            for (ServerPlayer player : connectedAlive()) {
                BattleHud.sound(player, SoundEvents.CHEST_OPEN, 0.4f, 1.6f);
            }
            Announcer.broadcast(server, Announcer.prefix().append(Component
                    .literal(refilled + " container(s) restocked.").withStyle(ChatFormatting.DARK_AQUA)));
        }
    }

    private void tickRoundEnd() {
        int total = CoveBattleConfig.roundEndSeconds * 20;
        int remaining = Math.max(0, total - phaseTicks);
        hud.setBar(Component.literal("Next round in " + ((remaining + 19) / 20) + "s").withStyle(ChatFormatting.GRAY),
                (float) remaining / total, BossEvent.BossBarColor.WHITE);
        if (remaining > 0) return;

        if (roundWinnerSide != null && sideWins.getOrDefault(roundWinnerSide, 0) >= rules.roundsToWin()) {
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
        roundWinnerSide = null;
        phase = GamePhase.GRACE;
        phaseTicks = 0;
        restockTicks = 0;
        tieTicks = 0;
        lastPingSecond = -1;
        pingUntilTick = -1;

        WorldBorder border = level().getWorldBorder();
        border.setCenter(centre().getX() + 0.5, centre().getZ() + 0.5);
        border.setSize(playRadius() * 2.0D);
        border.setWarningBlocks(4);
        border.setDamagePerBlock(0.8D);
        border.setDamageSafeZone(1.0D);
        borderRadius = playRadius();

        int filled = registry == null ? 0 : registry.fillAll(level(), level().random, rules);
        CoveBattle.LOGGER.info("Round {} starting: {} containers stocked, mode {}", round, filled, mode);

        for (ServerPlayer player : onlineParticipants()) {
            resetPlayer(player);
            placeAtSpawn(player);
            BattleHud.title(player, Component.literal("ROUND " + round).withStyle(ChatFormatting.GOLD),
                    Component.literal(mode.label() + " — best of " + rules.bestOf())
                            .withStyle(ChatFormatting.GRAY), 5, 30, 10);
        }
        updateSidebar();
    }

    private void endRound(@Nullable String winnerSide, String message) {
        phase = GamePhase.ROUND_END;
        phaseTicks = 0;
        roundWinnerSide = winnerSide;

        if (winnerSide != null) {
            sideWins.merge(winnerSide, 1, Integer::sum);
        }
        updateSidebar();

        for (ServerPlayer player : onlineParticipants()) {
            boolean won = winnerSide != null && winnerSide.equals(sides.get(player.getUUID()));
            if (winnerSide == null) {
                BattleHud.title(player, Component.literal("TIE").withStyle(ChatFormatting.GRAY),
                        Component.empty(), 5, 50, 15);
            } else {
                BattleHud.title(player,
                        Component.literal(won ? "ROUND WON" : "ROUND LOST")
                                .withStyle(won ? ChatFormatting.GREEN : ChatFormatting.RED),
                        Component.literal(scoreLine()).withStyle(ChatFormatting.GRAY), 5, 50, 15);
                BattleHud.sound(player, won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.WITHER_DEATH, 0.8f, 1.2f);
            }
            player.setInvulnerable(false);
        }
        Announcer.broadcast(server, Announcer.prefix()
                .append(Component.literal(message).withStyle(ChatFormatting.YELLOW)));
    }

    private void endMatch() {
        phase = GamePhase.MATCH_END;
        phaseTicks = 0;

        Announcer.broadcast(server, Component.empty());
        Announcer.broadcast(server, Component.literal("  MATCH OVER — " + mode.label())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (mode == BattleMode.TEAMS) {
            sideWins.forEach((side, wins) -> Announcer.broadcast(server,
                    Component.literal("  " + sideLabel(side) + "  ").withStyle(ChatFormatting.WHITE)
                            .append(Component.literal("rounds " + wins).withStyle(ChatFormatting.GREEN))));
        }
        stats.forEach((id, s) -> Announcer.broadcast(server, Component.literal("  " + s.name + "  ")
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal("rounds " + sideWins.getOrDefault(sides.get(id), 0))
                        .withStyle(ChatFormatting.GREEN))
                .append(Component.literal("   kills " + s.kills).withStyle(ChatFormatting.RED))
                .append(Component.literal("   looted " + s.containersLooted).withStyle(ChatFormatting.GOLD))));
        Announcer.broadcast(server, Component.empty());

        for (ServerPlayer player : onlineParticipants()) {
            player.removeEffect(MobEffects.GLOWING);
            player.setInvulnerable(false);
            clearMutators(player);
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.ADVENTURE);
            }
            sendToLobby(player);
        }
        if (registry != null) registry.clearAll(level());
    }

    private void toLobby() {
        phase = GamePhase.LOBBY;
        phaseTicks = 0;
        round = 0;
        pausedTicks = 0;
        eliminated.clear();
        roundWinnerSide = null;

        ServerLevel level = level();
        if (level != null) {
            WorldBorder border = level.getWorldBorder();
            border.setSize(VANILLA_BORDER_SIZE);
            border.setDamagePerBlock(0.2D);
            border.setWarningBlocks(5);
            borderRadius = VANILLA_BORDER_SIZE / 2;
        }

        hud.hideBar();
        BattleHud.hideSidebar(server);
        Announcer.postLobbyPrompt(server);
    }

    private boolean checkRoundOver() {
        if (!phase.allowsCombat()) return false;
        if (participants.size() < 2) return false;

        Set<String> aliveSides = aliveSides();
        if (aliveSides.size() > 1) return false;

        if (aliveSides.size() == 1) {
            String winner = aliveSides.iterator().next();
            endRound(winner, sideLabel(winner) + " takes the round.");
        } else {
            endRound(null, "Everyone fell — the round is a tie and scores nothing.");
        }
        return true;
    }

    // ------------------------------------------------------------------ teams

    private boolean assignTeams(List<ServerPlayer> players, Consumer<Component> feedback) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam red = team(scoreboard, TEAM_RED, "Red", ChatFormatting.RED);
        PlayerTeam blue = team(scoreboard, TEAM_BLUE, "Blue", ChatFormatting.BLUE);

        int redCount = 0;
        int blueCount = 0;
        for (ServerPlayer player : players) {
            PlayerTeam existing = scoreboard.getPlayersTeam(player.getScoreboardName());
            String target;
            if (existing != null && TEAM_RED.equals(existing.getName()) && redCount < CoveBattleConfig.maxPerTeam) {
                target = TEAM_RED;
            } else if (existing != null && TEAM_BLUE.equals(existing.getName()) && blueCount < CoveBattleConfig.maxPerTeam) {
                target = TEAM_BLUE;
            } else if (redCount <= blueCount && redCount < CoveBattleConfig.maxPerTeam) {
                target = TEAM_RED;
            } else if (blueCount < CoveBattleConfig.maxPerTeam) {
                target = TEAM_BLUE;
            } else {
                feedback.accept(Component.literal("Both teams are full at " + CoveBattleConfig.maxPerTeam
                        + " players each.").withStyle(ChatFormatting.RED));
                return false;
            }

            if (TEAM_RED.equals(target)) redCount++;
            else blueCount++;
            scoreboard.addPlayerToTeam(player.getScoreboardName(), TEAM_RED.equals(target) ? red : blue);
            sides.put(player.getUUID(), target);
        }

        if (redCount == 0 || blueCount == 0) {
            feedback.accept(Component.literal("Team mode needs players on both sides.").withStyle(ChatFormatting.RED));
            return false;
        }
        Announcer.info(server, "Teams: Red " + redCount + " vs Blue " + blueCount + ".");
        return true;
    }

    private PlayerTeam team(Scoreboard scoreboard, String name, String display, ChatFormatting colour) {
        PlayerTeam existing = scoreboard.getPlayerTeam(name);
        PlayerTeam playerTeam = existing != null ? existing : scoreboard.addPlayerTeam(name);
        playerTeam.setDisplayName(Component.literal(display));
        playerTeam.setColor(colour);
        playerTeam.setAllowFriendlyFire(rules.friendlyFire());
        playerTeam.setSeeFriendlyInvisibles(true);
        return playerTeam;
    }

    private void clearTeams() {
        Scoreboard scoreboard = server.getScoreboard();
        for (String name : List.of(TEAM_RED, TEAM_BLUE)) {
            PlayerTeam playerTeam = scoreboard.getPlayerTeam(name);
            if (playerTeam != null) scoreboard.removePlayerTeam(playerTeam);
        }
    }

    private String sideLabel(String side) {
        if (TEAM_RED.equals(side)) return "Red";
        if (TEAM_BLUE.equals(side)) return "Blue";
        try {
            PlayerStats s = stats.get(UUID.fromString(side));
            if (s != null) return s.name;
        } catch (IllegalArgumentException ignored) {
            // not a uuid-shaped side; fall through
        }
        return side;
    }

    // ------------------------------------------------------------------ lobby interaction

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    public boolean isEliminated(UUID id) {
        return eliminated.contains(id);
    }

    /** "Red", "Blue", or the player's own name in free-for-all. Empty when not in the match. */
    public String sideLabelOf(UUID id) {
        String side = sides.get(id);
        return side == null ? "" : sideLabel(side);
    }

    /**
     * Someone went to the lobby or disconnected. They are out of the running match; everyone else
     * carries on, and if that empties a side the match pauses rather than ending on the spot.
     */
    public void onParticipantLeft(ServerPlayer player) {
        if (!participants.contains(player.getUUID())) return;
        if (phase.isLive() && !eliminated.contains(player.getUUID())) {
            eliminated.add(player.getUUID());
            Announcer.broadcast(server, Announcer.prefix().append(Component
                    .literal(player.getGameProfile().getName() + " left the match.")
                    .withStyle(ChatFormatting.GRAY)));
        }
        clearMutators(player);
        player.setInvulnerable(false);
        player.removeEffect(MobEffects.GLOWING);
        if (!emptySidePause()) checkRoundOver();
    }

    /**
     * Holds the match when one side has nobody left in it, giving someone time to take their place.
     * Console Battle has nothing like this; it exists because the lobby lets people come and go.
     *
     * @return true when the match is paused and the phase should not advance
     */
    private boolean emptySidePause() {
        if (!phase.isLive()) return false;

        Set<String> present = new LinkedHashSet<>();
        for (UUID id : alive()) {
            String side = sides.get(id);
            if (side != null) present.add(side);
        }
        Set<String> allSides = new LinkedHashSet<>(sides.values());
        boolean someoneMissing = allSides.size() > 1 && present.size() < allSides.size() && !present.isEmpty();

        if (!someoneMissing) {
            if (pausedTicks > 0) {
                pausedTicks = 0;
                Announcer.broadcast(server, Announcer.prefix()
                        .append(Component.literal("Both sides are back — play on.").withStyle(ChatFormatting.GREEN)));
            }
            return false;
        }

        pausedTicks++;
        int remaining = EMPTY_SIDE_GRACE_TICKS - pausedTicks;
        if (remaining <= 0) {
            String winner = present.iterator().next();
            endRound(winner, sideLabel(winner) + " takes it — the other side had nobody left.");
            pausedTicks = 0;
            return true;
        }

        hud.setBar(Component.literal("Waiting for players — " + ((remaining + 19) / 20) + "s")
                .withStyle(ChatFormatting.YELLOW), (float) remaining / EMPTY_SIDE_GRACE_TICKS,
                BossEvent.BossBarColor.YELLOW);
        for (ServerPlayer player : connectedAlive()) {
            BattleHud.actionBar(player, Component.literal("Paused — waiting for an opponent ("
                    + ((remaining + 19) / 20) + "s)").withStyle(ChatFormatting.YELLOW));
        }
        return true;
    }

    // ------------------------------------------------------------------ player events

    /** @return true when the death was consumed by the game and should not kill the player */
    public boolean onDeath(ServerPlayer player, @Nullable ServerPlayer killer) {
        if (!phase.allowsCombat()) return false;
        if (!participants.contains(player.getUUID())) return false;
        if (eliminated.contains(player.getUUID())) return false;

        eliminated.add(player.getUUID());
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.getInventory().clearContent();
        if (!BattleBots.isBot(player.getUUID())) player.setGameMode(GameType.SPECTATOR);

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
                    .literal("A " + mode.label() + " match is in progress (round " + round + ", "
                            + phase.name().toLowerCase() + ").").withStyle(ChatFormatting.GRAY)));
        }
    }

    // ------------------------------------------------------------------ testing hooks

    /** Ends the current phase on the next tick, so phase transitions can be exercised without waiting. */
    public void debugSkipPhase() {
        phaseTicks = Integer.MAX_VALUE / 4;
    }

    /** Rescans the arena for containers and markers. */
    public int debugRescan() {
        ArenaScan scan = ArenaScan.run(level(), configCentre(), CoveBattleConfig.playRadius,
                CoveBattleConfig.centreTierRadius);
        layout = scan.layout();
        registry = scan.containers();
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
        return registry == null ? 0 : registry.fillAll(level(), level().random, RulesState.active());
    }

    // ------------------------------------------------------------------ helpers

    /** Health and speed are applied as real attribute modifiers rather than potion effects. */
    private void applyMutators(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_MODIFIER);
            double delta = rules.maxHealthHearts() * 2.0D - 20.0D;
            if (Math.abs(delta) > 0.01D) {
                health.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, delta,
                        AttributeModifier.Operation.ADD_VALUE));
            }
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
            double delta = rules.moveSpeed() - 1.0D;
            if (Math.abs(delta) > 0.01D) {
                speed.addPermanentModifier(new AttributeModifier(SPEED_MODIFIER, delta,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    /** Takes the mutator attributes back off, so leaving a match leaves no trace on a player. */
    public static void clearMutators(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(HEALTH_MODIFIER);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_MODIFIER);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private void resetPlayer(ServerPlayer player) {
        applyMutators(player);
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

    /** Spawn markers first, then an evenly spread ring around the centre. */
    private BlockPos spawnFor(ServerPlayer player) {
        List<UUID> order = new ArrayList<>(participants);
        int index = Math.max(0, order.indexOf(player.getUUID()));

        if (layout != null) {
            String side = sides.get(player.getUUID());
            List<BlockPos> marked;
            if (mode == BattleMode.TEAMS && TEAM_RED.equals(side)) {
                marked = layout.spawns(MarkerKind.TEAM_A_SPAWN);
            } else if (mode == BattleMode.TEAMS && TEAM_BLUE.equals(side)) {
                marked = layout.spawns(MarkerKind.TEAM_B_SPAWN);
            } else {
                marked = layout.spawns(MarkerKind.SOLO_SPAWN);
            }
            if (!marked.isEmpty()) {
                int slot = mode == BattleMode.TEAMS ? indexWithinSide(player) : index;
                return marked.get(slot % marked.size()).above();
            }
        }
        return null;
    }

    private int indexWithinSide(ServerPlayer player) {
        String side = sides.get(player.getUUID());
        int i = 0;
        for (UUID id : participants) {
            if (!java.util.Objects.equals(sides.get(id), side)) continue;
            if (id.equals(player.getUUID())) return i;
            i++;
        }
        return 0;
    }

    private void placeAtSpawn(ServerPlayer player) {
        BlockPos marked = spawnFor(player);
        if (marked != null) {
            faceCentre(player, marked.getX() + 0.5, marked.getY(), marked.getZ() + 0.5);
            return;
        }
        // Fallback: spread everyone around the centre. In team mode each side gets its own half.
        List<UUID> order = new ArrayList<>(participants);
        int index = Math.max(0, order.indexOf(player.getUUID()));
        int count = Math.max(1, order.size());
        double angle;
        if (mode == BattleMode.TEAMS) {
            boolean red = TEAM_RED.equals(sides.get(player.getUUID()));
            int within = indexWithinSide(player);
            int sideSize = Math.max(1, (int) sides.values().stream()
                    .filter(s -> s.equals(sides.get(player.getUUID()))).count());
            double spread = Math.PI * 0.8;
            double base = red ? -Math.PI / 2 : Math.PI / 2;
            angle = base - spread / 2 + (sideSize == 1 ? spread / 2 : spread * within / (sideSize - 1));
        } else {
            angle = (2 * Math.PI * index) / count;
        }
        BlockPos centre = centre();
        double x = centre.getX() + 0.5 + Math.cos(angle) * CoveBattleConfig.podiumRadius;
        double z = centre.getZ() + 0.5 + Math.sin(angle) * CoveBattleConfig.podiumRadius;
        faceCentre(player, x, centre.getY(), z);
    }

    private void faceCentre(ServerPlayer player, double x, double y, double z) {
        BlockPos centre = centre();
        double dx = (centre.getX() + 0.5) - x;
        double dz = (centre.getZ() + 0.5) - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        if (BattleBots.isBot(player.getUUID())) {
            // A fake player has no connection, so the packet-based teleport would do nothing.
            player.moveTo(x, y, z, yaw, 0f);
            return;
        }
        player.teleportTo(level(), x, y, z, Set.of(), yaw, 0f);
    }

    /** Keeps a player at their spawn during the grace period without constant rubber-banding. */
    private void holdAtSpawn(ServerPlayer player) {
        player.setInvulnerable(true);
        BlockPos marked = spawnFor(player);
        double x;
        double z;
        if (marked != null) {
            x = marked.getX() + 0.5;
            z = marked.getZ() + 0.5;
        } else {
            return; // ring fallback: a drift of a block or two during grace is harmless
        }
        if (player.distanceToSqr(x, player.getY(), z) > 0.75D) {
            placeAtSpawn(player);
        }
    }

    private void sendToLobby(ServerPlayer player) {
        BlockPos lobby = layout != null ? layout.lobby() : null;
        BlockPos target = lobby != null ? lobby.above() : centre();
        faceCentre(player, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }

    private Component timerLine(int remainingTicks) {
        return Component.literal("TIME  ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(BattleHud.clock(remainingTicks)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("  until the border closes").withStyle(ChatFormatting.GRAY));
    }

    private Component statusLine(ServerPlayer player) {
        PlayerStats s = stats.get(player.getUUID());
        var line = Component.literal("❤ " + (int) Math.ceil(player.getHealth())).withStyle(ChatFormatting.RED)
                .append(Component.literal("   looted " + (s == null ? 0 : s.containersLooted))
                        .withStyle(ChatFormatting.GOLD));

        if (mode == BattleMode.TEAMS) {
            String side = sides.get(player.getUUID());
            long mates = aliveOnSide(side);
            long foes = participants.size() - eliminated.size() - mates;
            return line.append(Component.literal("   team " + mates + " v " + Math.max(0, foes))
                    .withStyle(TEAM_RED.equals(side) ? ChatFormatting.RED : ChatFormatting.BLUE));
        }
        return line.append(Component.literal("   alive " + aliveCount()).withStyle(ChatFormatting.GRAY));
    }

    private long aliveOnSide(String side) {
        return alive().stream().filter(id -> java.util.Objects.equals(sides.get(id), side)).count();
    }

    private void updateSidebar() {
        Map<String, Integer> rows = new LinkedHashMap<>();
        if (mode == BattleMode.TEAMS) {
            sideWins.forEach((side, wins) -> rows.put(sideLabel(side), wins));
        } else {
            stats.forEach((id, s) -> rows.put(s.name, sideWins.getOrDefault(sides.get(id), 0)));
        }
        BattleHud.showSidebar(server, "Round " + round + " / best of " + rules.bestOf(), rows);
    }

    private String scoreLine() {
        StringBuilder sb = new StringBuilder();
        sideWins.forEach((side, wins) -> {
            if (sb.length() > 0) sb.append("  -  ");
            sb.append(sideLabel(side)).append(' ').append(wins);
        });
        return sb.toString();
    }

    private String scoreSuffix() {
        if (mode != BattleMode.TEAMS) return "";
        return "   Red " + sideWins.getOrDefault(TEAM_RED, 0) + " - " + sideWins.getOrDefault(TEAM_BLUE, 0) + " Blue";
    }

    private Set<String> aliveSides() {
        Set<String> result = new LinkedHashSet<>();
        for (UUID id : alive()) {
            String side = sides.get(id);
            if (side != null) result.add(side);
        }
        return result;
    }

    private List<UUID> alive() {
        List<UUID> result = new ArrayList<>();
        for (UUID id : participants) {
            if (eliminated.contains(id)) continue;
            if (resolve(id) == null) continue;
            result.add(id);
        }
        return result;
    }

    /** Participants resolve through the player list first, then the test bots. */
    @Nullable
    private ServerPlayer resolve(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        return player != null ? player : BattleBots.get(id);
    }

    private List<ServerPlayer> onlineParticipants() {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : participants) {
            ServerPlayer player = resolve(id);
            if (player != null) result.add(player);
        }
        return result;
    }

    /** Only real players get packets; bots have no connection. */
    private List<ServerPlayer> connectedParticipants() {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : participants) {
            if (BattleBots.isBot(id)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) result.add(player);
        }
        return result;
    }

    private List<ServerPlayer> onlineAlive() {
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID id : alive()) {
            ServerPlayer player = resolve(id);
            if (player != null) result.add(player);
        }
        return result;
    }

    /** Alive participants that can actually receive a HUD. */
    private List<ServerPlayer> connectedAlive() {
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : onlineAlive()) {
            if (!BattleBots.isBot(player.getUUID())) result.add(player);
        }
        return result;
    }
}
