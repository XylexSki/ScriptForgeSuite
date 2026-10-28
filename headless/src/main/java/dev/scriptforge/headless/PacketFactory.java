package dev.scriptforge.headless;

import dev.scriptforge.macro.Event;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundAcceptTeleportationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundPlayerInputPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerStatusOnlyPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSetCarriedItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;

import java.time.Instant;
import java.util.BitSet;

/**
 * Every outgoing packet the headless client sends is built here, in one place. If a future MCProtocolLib release
 * changes a constructor, this is the only file (besides Bot's inbound handlers) that needs touching.
 */
final class PacketFactory {
    static final int INPUT_FORWARD = 1, INPUT_BACK = 2, INPUT_LEFT = 4, INPUT_RIGHT = 8,
            INPUT_JUMP = 16, INPUT_SNEAK = 32, INPUT_SPRINT = 64;

    private static Hand hand(int h) {
        return h == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND;
    }

    private static Direction face(int f) {
        return Direction.values()[Math.floorMod(f, 6)];
    }

    // ---- movement (same packet choice as the vanilla client)
    Packet statusOnly(boolean onGround, boolean collided) {
        return new ServerboundMovePlayerStatusOnlyPacket(onGround, collided);
    }

    Packet pos(boolean onGround, boolean collided, double x, double y, double z) {
        return new ServerboundMovePlayerPosPacket(onGround, collided, x, y, z);
    }

    Packet rot(boolean onGround, boolean collided, float yaw, float pitch) {
        return new ServerboundMovePlayerRotPacket(onGround, collided, yaw, pitch);
    }

    Packet posRot(boolean onGround, boolean collided, double x, double y, double z, float yaw, float pitch) {
        return new ServerboundMovePlayerPosRotPacket(onGround, collided, x, y, z, yaw, pitch);
    }

    /** Held movement keys, as INPUT_* bits (1.21.2+ sends these instead of sneak/jump commands). */
    Packet input(int bits) {
        return new ServerboundPlayerInputPacket(
                (bits & INPUT_FORWARD) != 0, (bits & INPUT_BACK) != 0, (bits & INPUT_LEFT) != 0, (bits & INPUT_RIGHT) != 0,
                (bits & INPUT_JUMP) != 0, (bits & INPUT_SNEAK) != 0, (bits & INPUT_SPRINT) != 0);
    }

    Packet sprint(int entityId, boolean start) {
        return new ServerboundPlayerCommandPacket(entityId, start ? PlayerState.START_SPRINTING : PlayerState.STOP_SPRINTING);
    }

    Packet heldSlot(int slot) {
        return new ServerboundSetCarriedItemPacket(slot);
    }

    Packet teleportConfirm(int teleportId) {
        return new ServerboundAcceptTeleportationPacket(teleportId);
    }

    /** Sent once after joining; since 1.21.5 the server ignores movement packets until it has this (or a timeout). */
    Packet playerLoaded() {
        return ServerboundPlayerLoadedPacket.INSTANCE;
    }

    /** The vanilla client sends this at the end of every client tick. */
    Packet tickEnd() {
        return ServerboundClientTickEndPacket.INSTANCE;
    }

    Packet respawn() {
        return new ServerboundClientCommandPacket(ClientCommand.RESPAWN);
    }

    // ---- actions
    Packet swing(int hand) {
        return new ServerboundSwingPacket(hand(hand));
    }

    /** action: "start" | "finish" | "cancel". Block coordinates are already shifted. */
    Packet dig(String action, int x, int y, int z, int face, int sequence) {
        PlayerAction a = switch (action) {
            case "start" -> PlayerAction.START_DIGGING;
            case "finish" -> PlayerAction.FINISH_DIGGING;
            default -> PlayerAction.CANCEL_DIGGING;
        };
        return new ServerboundPlayerActionPacket(a, Vector3i.from(x, y, z), face(face), sequence);
    }

    Packet useBlock(Event e, int x, int y, int z, int sequence) {
        return new ServerboundUseItemOnPacket(Vector3i.from(x, y, z), face(e.f), hand(e.hand),
                e.cx, e.cy, e.cz, e.inside, false, sequence);
    }

    Packet useItem(int hand, int sequence, float yaw, float pitch) {
        return new ServerboundUseItemPacket(hand(hand), sequence, yaw, pitch);
    }

    // ---- chat (unsigned: works on offline servers and online servers with enforce-secure-profile=false)
    Packet chat(String message) {
        return new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet(), 0);
    }

    Packet command(String commandWithoutSlash) {
        return new ServerboundChatCommandPacket(commandWithoutSlash);
    }
}
