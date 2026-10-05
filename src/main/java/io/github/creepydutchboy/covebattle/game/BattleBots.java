package io.github.creepydutchboy.covebattle.game;

import com.mojang.authlib.GameProfile;
import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stand-in players built on NeoForge's {@link net.neoforged.neoforge.common.util.FakePlayer}.
 *
 * <p>They exist so the parts of a match that need more than one participant — team assignment,
 * eliminations, round and match transitions — can be exercised on a server with nobody connected.
 * They are real {@code ServerPlayer} instances, so they travel the same code paths as a human:
 * killing one fires {@code LivingDeathEvent} and the game resolves the round for real.
 *
 * <p>They have no network connection, which is why anything that sends a packet filters them out.
 */
public final class BattleBots {

    private static final Map<UUID, ServerPlayer> BOTS = new LinkedHashMap<>();

    private BattleBots() {}

    public static ServerPlayer spawn(ServerLevel level, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("covebot:" + name).getBytes()), name);
        ServerPlayer bot = FakePlayerFactory.get(level, profile);
        BOTS.put(bot.getUUID(), bot);
        try {
            if (!bot.isAddedToLevel()) level.addFreshEntity(bot);
        } catch (Exception e) {
            CoveBattle.LOGGER.debug("Bot {} stays out of the level: {}", name, e.toString());
        }
        CoveBattle.LOGGER.info("Spawned test bot {} ({})", name, bot.getUUID());
        return bot;
    }

    public static List<ServerPlayer> all() {
        return new ArrayList<>(BOTS.values());
    }

    public static ServerPlayer get(UUID id) {
        return BOTS.get(id);
    }

    public static boolean isBot(UUID id) {
        return BOTS.containsKey(id);
    }

    public static int count() {
        return BOTS.size();
    }

    public static void clear() {
        for (ServerPlayer bot : BOTS.values()) {
            try {
                bot.discard();
            } catch (Exception ignored) {
                // a fake player that was never added to the level has nothing to discard
            }
        }
        BOTS.clear();
    }
}
