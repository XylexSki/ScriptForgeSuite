package dev.scriptforge.core;

import dev.scriptforge.macro.Event;
import net.fabricmc.fabric.api.client.event.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Fabric API callbacks that feed the macro recorder. No mixins needed. */
public final class RecorderHooks {
    private RecorderHooks() {}

    private static boolean mine(PlayerEntity player, World world) {
        return MacroRecorder.isRecording() && world.isClient() && player == MinecraftClient.getInstance().player;
    }

    private static int hand(Hand h) {
        return h == Hand.MAIN_HAND ? 0 : 1;
    }

    public static void register() {
        // Left-click on a block: digging starts.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (mine(player, world)) {
                MacroRecorder.event(Event.dig("start", pos.getX(), pos.getY(), pos.getZ(), direction.ordinal()));
            }
            return ActionResult.PASS;
        });

        // The block was actually broken on the client: digging finished.
        ClientPlayerBlockBreakEvents.AFTER.register((world, player, pos, state) -> {
            if (MacroRecorder.isRecording() && player == MinecraftClient.getInstance().player) {
                MacroRecorder.event(Event.dig("finish", pos.getX(), pos.getY(), pos.getZ(), 1));
            }
        });

        // Right-click on a block: place a block / open a container / press a button ...
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (mine(player, world)) {
                BlockPos bp = hit.getBlockPos();
                Vec3d hp = hit.getPos();
                MacroRecorder.event(Event.useBlock(hand(hand), bp.getX(), bp.getY(), bp.getZ(),
                        hit.getSide().ordinal(),
                        (float) (hp.x - bp.getX()), (float) (hp.y - bp.getY()), (float) (hp.z - bp.getZ()),
                        hit.isInsideBlock()));
            }
            return ActionResult.PASS;
        });

        // Right-click in the air with an item (eat, throw, bow, ...).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (mine(player, world)) MacroRecorder.event(Event.useItem(hand(hand)));
            return ActionResult.PASS;
        });
    }
}
