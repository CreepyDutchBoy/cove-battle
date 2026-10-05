package io.github.creepydutchboy.covebattle.mirage;

import com.mojang.authlib.GameProfile;
import io.github.creepydutchboy.covebattle.CoveBattle;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Decoys that look exactly like the player who made them.
 *
 * <p>They are drawn entirely with packets and never added to the level. That is the whole trick:
 * because the server has no entity at that id, a client that tries to hit one is simply ignored, so
 * the decoys cannot be killed, cannot be damaged and have nothing to drop — without a single
 * special case for it. They copy the owner's skin and visible gear, wander about plausibly, swing
 * at people now and then, and vanish on a timer.
 */
public final class MirageManager {

    private static final List<Mirage> ACTIVE = new ArrayList<>();
    private static final double WANDER_SPEED = 0.17D;

    private MirageManager() {}

    /** One decoy: a fake player used only as an identity, plus where it is pretending to be. */
    private static final class Mirage {
        final ServerPlayer ghost;
        final ServerLevel level;
        final UUID ownerId;
        int ticksLeft;
        Vec3 position;
        Vec3 target;
        float yaw;
        int repickIn;
        int swingIn;
        Behaviour behaviour;

        Mirage(ServerPlayer ghost, ServerLevel level, UUID ownerId, Vec3 start, int ticks) {
            this.ghost = ghost;
            this.level = level;
            this.ownerId = ownerId;
            this.position = start;
            this.target = start;
            this.ticksLeft = ticks;
            this.repickIn = 0;
            this.swingIn = 20 + (int) (Math.random() * 40);
            this.behaviour = Behaviour.WANDER;
        }
    }

    /** What a decoy is pretending to do at any moment. */
    private enum Behaviour { WANDER, CHASE, IDLE }

    public static int activeCount() {
        return ACTIVE.size();
    }

    /**
     * Spawns decoys around a player.
     *
     * @param count how many
     * @param ticks how long they last
     */
    public static void summon(ServerPlayer owner, int count, int ticks) {
        ServerLevel level = owner.serverLevel();
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2 * i) / count + level.random.nextDouble() * 0.6D;
            double distance = 1.6D + level.random.nextDouble() * 1.4D;
            Vec3 start = owner.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);

            // Same name and skin as the owner, different uuid, so it reads as a copy at a glance.
            GameProfile profile = new GameProfile(UUID.randomUUID(), owner.getGameProfile().getName());
            profile.getProperties().putAll(owner.getGameProfile().getProperties());

            ServerPlayer ghost;
            try {
                ghost = FakePlayerFactory.get(level, profile);
            } catch (Exception e) {
                CoveBattle.LOGGER.warn("Could not create a mirage: {}", e.toString());
                return;
            }
            ghost.setPos(start.x, start.y, start.z);
            ghost.setYRot(owner.getYRot());
            ghost.setXRot(0f);

            Mirage mirage = new Mirage(ghost, level, owner.getUUID(), start, ticks);
            mirage.yaw = owner.getYRot();
            ACTIVE.add(mirage);

            copyGear(owner, ghost);
            for (ServerPlayer viewer : level.players()) show(viewer, mirage);
        }
    }

    private static void copyGear(ServerPlayer owner, ServerPlayer ghost) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ghost.setItemSlot(slot, owner.getItemBySlot(slot).copy());
        }
    }

    /** Sends the packets that make a decoy appear for one viewer. */
    private static void show(ServerPlayer viewer, Mirage mirage) {
        try {
            viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(mirage.ghost)));
            // Built field by field: there is no ServerEntity because this is never added to the
            // level, which is exactly what makes it unhittable.
            viewer.connection.send(new ClientboundAddEntityPacket(
                    mirage.ghost.getId(), mirage.ghost.getUUID(),
                    mirage.position.x, mirage.position.y, mirage.position.z,
                    0f, mirage.yaw, net.minecraft.world.entity.EntityType.PLAYER, 0,
                    Vec3.ZERO, mirage.yaw));
            viewer.connection.send(equipment(mirage));
            viewer.connection.send(new ClientboundRotateHeadPacket(mirage.ghost, yawToByte(mirage.yaw)));
        } catch (Exception e) {
            CoveBattle.LOGGER.debug("Could not show a mirage: {}", e.toString());
        }
    }

    private static ClientboundSetEquipmentPacket equipment(Mirage mirage) {
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> items = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            items.add(com.mojang.datafixers.util.Pair.of(slot, mirage.ghost.getItemBySlot(slot).copy()));
        }
        return new ClientboundSetEquipmentPacket(mirage.ghost.getId(), items);
    }

    private static void hide(ServerPlayer viewer, Mirage mirage) {
        try {
            viewer.connection.send(new ClientboundRemoveEntitiesPacket(mirage.ghost.getId()));
            viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.of(mirage.ghost.getUUID())));
        } catch (Exception e) {
            CoveBattle.LOGGER.debug("Could not hide a mirage: {}", e.toString());
        }
    }

    /** Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        List<Mirage> finished = new ArrayList<>();
        for (Mirage mirage : ACTIVE) {
            mirage.ticksLeft--;
            if (mirage.ticksLeft <= 0) {
                finished.add(mirage);
                continue;
            }
            step(mirage);
            broadcast(mirage);
        }

        for (Mirage mirage : finished) {
            for (ServerPlayer viewer : mirage.level.players()) hide(viewer, mirage);
            ACTIVE.remove(mirage);
        }
    }

    /** Chooses and advances the pretend behaviour. */
    private static void step(Mirage mirage) {
        ServerLevel level = mirage.level;

        if (--mirage.repickIn <= 0) {
            mirage.repickIn = 20 + level.random.nextInt(50);
            double roll = level.random.nextDouble();
            if (roll < 0.25D) {
                mirage.behaviour = Behaviour.IDLE;
            } else if (roll < 0.55D) {
                mirage.behaviour = Behaviour.CHASE;
            } else {
                mirage.behaviour = Behaviour.WANDER;
                double angle = level.random.nextDouble() * Math.PI * 2;
                double distance = 3.0D + level.random.nextDouble() * 6.0D;
                mirage.target = mirage.position.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            }
        }

        if (mirage.behaviour == Behaviour.CHASE) {
            ServerPlayer quarry = null;
            double best = Double.MAX_VALUE;
            for (ServerPlayer candidate : level.players()) {
                if (candidate.getUUID().equals(mirage.ownerId)) continue;
                if (candidate.isSpectator()) continue;
                double distance = candidate.position().distanceToSqr(mirage.position);
                if (distance < best) {
                    best = distance;
                    quarry = candidate;
                }
            }
            if (quarry != null && best < 400.0D) {
                mirage.target = quarry.position();
            } else {
                mirage.behaviour = Behaviour.WANDER;
            }
        }

        if (mirage.behaviour != Behaviour.IDLE) {
            Vec3 delta = mirage.target.subtract(mirage.position);
            double length = delta.horizontalDistance();
            if (length > 0.35D) {
                Vec3 stepVec = delta.normalize().scale(WANDER_SPEED);
                Vec3 next = mirage.position.add(stepVec.x, 0, stepVec.z);
                // Follow the ground rather than walking through it.
                next = groundAt(level, next);
                mirage.position = next;
                mirage.yaw = (float) (Mth.atan2(stepVec.z, stepVec.x) * (180F / Math.PI)) - 90F;
            }
        }

        if (--mirage.swingIn <= 0) {
            mirage.swingIn = 25 + level.random.nextInt(55);
            for (ServerPlayer viewer : level.players()) {
                try {
                    viewer.connection.send(new ClientboundAnimatePacket(mirage.ghost, 0));
                } catch (Exception ignored) {
                    // a viewer that dropped out does not matter
                }
            }
        }
    }

    /** Keeps a decoy standing on something, within a couple of blocks of where it was. */
    private static Vec3 groundAt(ServerLevel level, Vec3 wanted) {
        net.minecraft.core.BlockPos.MutableBlockPos cursor =
                new net.minecraft.core.BlockPos.MutableBlockPos(
                        Mth.floor(wanted.x), Mth.floor(wanted.y) + 2, Mth.floor(wanted.z));
        for (int i = 0; i < 5; i++) {
            if (!level.getBlockState(cursor).isAir() && level.getBlockState(cursor.above()).isAir()) {
                return new Vec3(wanted.x, cursor.getY() + 1.0D, wanted.z);
            }
            cursor.move(0, -1, 0);
        }
        return wanted;
    }

    private static void broadcast(Mirage mirage) {
        mirage.ghost.setPos(mirage.position.x, mirage.position.y, mirage.position.z);
        mirage.ghost.setYRot(mirage.yaw);
        for (ServerPlayer viewer : mirage.level.players()) {
            try {
                Packet<?> move = new ClientboundTeleportEntityPacket(mirage.ghost);
                viewer.connection.send(move);
                viewer.connection.send(new ClientboundRotateHeadPacket(mirage.ghost, yawToByte(mirage.yaw)));
            } catch (Exception ignored) {
                // viewer disconnected mid-tick
            }
        }
    }

    private static byte yawToByte(float yaw) {
        return (byte) Mth.floor(yaw * 256.0F / 360.0F);
    }

    /** Drops every decoy, used when a round ends or the server stops. */
    public static void clear() {
        for (Mirage mirage : new ArrayList<>(ACTIVE)) {
            for (ServerPlayer viewer : mirage.level.players()) hide(viewer, mirage);
        }
        ACTIVE.clear();
    }
}
