package io.github.creepydutchboy.covebattle.game;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Collection;
import java.util.Map;

/**
 * Everything the players see: the boss bar, the action bar, title cards and the sidebar.
 *
 * <p>All of it is vanilla presentation driven from the server, which means it works on a vanilla
 * client and needs no networking of our own. Custom client-side rendering is a later phase; this
 * layer is what makes the game playable without it.
 */
public final class BattleHud {

    private static final String OBJECTIVE = "covebattle";

    private final ServerBossEvent bar =
            new ServerBossEvent(Component.literal("Cove Battle"), BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);

    public BattleHud() {
        bar.setVisible(false);
    }

    // ---- boss bar ----

    public void setBar(Component name, float progress, BossEvent.BossBarColor colour) {
        bar.setName(name);
        bar.setProgress(Math.max(0f, Math.min(1f, progress)));
        bar.setColor(colour);
        bar.setVisible(true);
    }

    public void hideBar() {
        bar.setVisible(false);
    }

    /** Boss bars are per-player subscriptions, so keep the audience in step with who is online. */
    public void syncAudience(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            bar.addPlayer(player);
        }
    }

    public void removeAll() {
        bar.removeAllPlayers();
        bar.setVisible(false);
    }

    // ---- per-player bits ----

    public static void actionBar(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    public static void title(ServerPlayer player, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title == null ? Component.empty() : title));
    }

    public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    // ---- sidebar ----

    /** Shows round wins in the sidebar. Scores are player names, as on the console leaderboard. */
    public static void showSidebar(MinecraftServer server, String title, Map<String, Integer> rows) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                    Component.literal(title).withStyle(ChatFormatting.GOLD),
                    ObjectiveCriteria.RenderType.INTEGER, true, null);
        } else {
            objective.setDisplayName(Component.literal(title).withStyle(ChatFormatting.GOLD));
        }
        scoreboard.setDisplayObjective(DisplaySlot.SIDEBAR, objective);

        for (Map.Entry<String, Integer> row : rows.entrySet()) {
            scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(row.getKey()), objective).set(row.getValue());
        }
    }

    public static void hideSidebar(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective != null) {
            scoreboard.removeObjective(objective);
        }
    }

    /** Formats a tick count as m:ss. */
    public static String clock(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }
}
