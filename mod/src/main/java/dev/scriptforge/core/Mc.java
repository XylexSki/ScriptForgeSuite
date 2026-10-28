package dev.scriptforge.core;

import dev.scriptforge.gui.ScriptScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Bridge between script threads and the Minecraft client thread. All game access goes through here. */
public final class Mc {
    private Mc() {}

    /** Latest tick snapshot. Safe to read from any thread. */
    public static volatile Snapshot snap = Snapshot.EMPTY;

    public static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    /** Run on the client thread and wait for the result (runs inline if already on it). */
    public static <T> T call(Supplier<T> task) {
        MinecraftClient mc = client();
        if (mc.isOnThread()) return task.get();
        CompletableFuture<T> future = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new ScriptStopped();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new ScriptError(String.valueOf(cause));
        } catch (TimeoutException e) {
            throw new ScriptError("Timed out waiting for the game thread");
        }
    }

    public static void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }

    /** Fire-and-forget on the client thread. Never blocks. */
    public static void later(Runnable task) {
        client().execute(task);
    }

    /** Client thread only. */
    public static ClientPlayerEntity player() {
        MinecraftClient mc = client();
        if (mc.player == null || mc.world == null) throw new ScriptError("You are not in a world");
        return mc.player;
    }

    /** Prints a local chat line (not sent to the server). */
    public static void chat(String text) {
        later(() -> {
            MinecraftClient mc = client();
            if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal(text));
        });
    }

    /** Called on the client thread at the end of every tick. */
    public static void updateSnapshot(MinecraftClient c) {
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) {
            snap = Snapshot.EMPTY;
            return;
        }
        Window w = c.getWindow();
        String title = null, type = null;
        Screen s = c.currentScreen;
        if (s != null) {
            title = s.getTitle().getString();
            type = s instanceof HandledScreen<?> ? "container"
                    : s instanceof ChatScreen ? "chat"
                    : s instanceof GameMenuScreen ? "pause"
                    : s instanceof ScriptScreen ? "script" : "other";
        }
        Vec3d v = p.getVelocity();
        snap = new Snapshot(true,
                p.getX(), p.getY(), p.getZ(), p.getEyeY(),
                v.x, v.y, v.z,
                p.getYaw(), p.getPitch(),
                p.getHealth(), p.getMaxHealth(), p.getHungerManager().getFoodLevel(),
                p.isOnGround(), p.isSprinting(), p.isSneaking(), p.horizontalCollision,
                p.getInventory().getSelectedSlot(),
                p.getName().getString(),
                c.world.getRegistryKey().getValue().toString(),
                c.interactionManager != null ? c.interactionManager.getCurrentGameMode().asString() : "",
                title, type,
                c.mouse.getX() / w.getScaleFactor(), c.mouse.getY() / w.getScaleFactor(),
                c.getCurrentFps());
    }
}
