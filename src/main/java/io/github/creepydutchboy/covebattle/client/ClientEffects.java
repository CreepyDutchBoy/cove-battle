package io.github.creepydutchboy.covebattle.client;

import io.github.creepydutchboy.covebattle.CoveBattle;
import io.github.creepydutchboy.covebattle.CoveBattleConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.border.BorderStatus;
import net.minecraft.world.level.border.WorldBorder;

import javax.annotation.Nullable;

/**
 * Full-screen shader effects.
 *
 * <p>Both states are inferred from what the client already knows, so no packets are needed:
 * spectating means you are out of the round, and a shrinking world border means the showdown is
 * on. Every call into the shader pipeline is guarded — if a chain fails to load, the effect is
 * disabled for the session and the game carries on unaffected.
 */
public final class ClientEffects {

    private static final ResourceLocation ELIMINATED =
            ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "shaders/post/cove_eliminated.json");
    private static final ResourceLocation SHOWDOWN =
            ResourceLocation.fromNamespaceAndPath(CoveBattle.MODID, "shaders/post/cove_showdown.json");

    /** Below this border size a shrink is treated as the showdown rather than ordinary world setup. */
    private static final double SHOWDOWN_BORDER_SIZE = 2000.0D;

    @Nullable
    private static ResourceLocation active;
    private static boolean broken;

    private ClientEffects() {}

    public static void tick(Minecraft minecraft) {
        if (broken || !CoveBattleConfig.clientScreenEffects) {
            apply(minecraft, null);
            return;
        }
        if (minecraft.level == null || minecraft.player == null) {
            apply(minecraft, null);
            return;
        }

        ResourceLocation wanted = null;
        if (minecraft.player.isSpectator()) {
            wanted = ELIMINATED;
        } else {
            WorldBorder border = minecraft.level.getWorldBorder();
            if (border.getStatus() == BorderStatus.SHRINKING && border.getSize() < SHOWDOWN_BORDER_SIZE) {
                wanted = SHOWDOWN;
            }
        }
        apply(minecraft, wanted);
    }

    private static void apply(Minecraft minecraft, @Nullable ResourceLocation wanted) {
        if (java.util.Objects.equals(active, wanted)) return;
        try {
            if (wanted == null) {
                minecraft.gameRenderer.shutdownEffect();
            } else {
                minecraft.gameRenderer.loadEffect(wanted);
            }
            active = wanted;
        } catch (Throwable t) {
            // A broken shader must never take the game with it.
            broken = true;
            active = null;
            CoveBattle.LOGGER.error("Disabling Cove Battle screen effects after a failure", t);
            try {
                minecraft.gameRenderer.shutdownEffect();
            } catch (Throwable ignored) {
                // nothing further to do
            }
        }
    }

    /** Called when leaving a world so an effect cannot leak into the menus. */
    public static void clear(Minecraft minecraft) {
        apply(minecraft, null);
    }
}
