package dev.scriptforge.core;

import dev.scriptforge.macro.Event;
import dev.scriptforge.macro.Frame;
import dev.scriptforge.macro.Macro;
import dev.scriptforge.macro.MacroIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Records everything needed to replay a session: the player's state every tick plus discrete events
 * (digging, placing, item use, chat, commands). The result is a shared Macro file that this mod can replay
 * with input emulation and the headless client can replay at protocol level.
 */
public final class MacroRecorder {
    private MacroRecorder() {}

    private static Macro current;                       // non-null while recording
    private static List<Event> pending = new ArrayList<>();

    public static synchronized boolean isRecording() {
        return current != null;
    }

    public static synchronized int recordedTicks() {
        return current == null ? 0 : current.frames.size();
    }

    /** Starts recording from the player's current position. Client thread only. */
    public static synchronized void start() {
        MinecraftClient c = MinecraftClient.getInstance();
        ClientPlayerEntity p = c.player;
        if (p == null || c.world == null) throw new ScriptError("You are not in a world");
        Macro m = new Macro();
        m.mc = FabricLoader.getInstance().getModContainer("minecraft")
                .map(mc -> mc.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
        m.created = System.currentTimeMillis();
        m.dimension = c.world.getRegistryKey().getValue().toString();
        m.startX = p.getX();
        m.startY = p.getY();
        m.startZ = p.getZ();
        m.startYaw = p.getYaw();
        m.startPitch = p.getPitch();
        pending = new ArrayList<>();
        current = m;
    }

    /** Stops recording and returns the macro (named), or null if nothing was being recorded. */
    public static synchronized Macro stop(String name) {
        Macro m = current;
        current = null;
        pending = new ArrayList<>();
        if (m != null) m.name = Macro.safeName(name);
        return m;
    }

    /** Called by the hooks (client thread) when something notable happens. */
    public static synchronized void event(Event e) {
        if (current != null) pending.add(e);
    }

    /** Called at the end of every client tick: records one frame. */
    public static synchronized void sample(MinecraftClient c) {
        if (current == null || c.player == null) return;
        ClientPlayerEntity p = c.player;
        Frame f = new Frame();
        f.x = p.getX();
        f.y = p.getY();
        f.z = p.getZ();
        f.yaw = p.getYaw();
        f.pitch = p.getPitch();
        f.onGround = p.isOnGround();
        f.collided = p.horizontalCollision;
        f.sneaking = p.isSneaking();
        f.sprinting = p.isSprinting();
        f.slot = p.getInventory().getSelectedSlot();
        if (c.currentScreen == null) {
            for (int i = 0; i < Macro.KEYS.length; i++) {
                if (Keys.binding(Macro.KEYS[i]).isPressed()) f.keys |= 1 << i;
            }
        }
        f.events = pending;
        pending = new ArrayList<>();
        current.frames.add(f);
    }

    // ------------------------------------------------------------------ files

    public static Path file(Path macrosDir, String name) {
        return macrosDir.resolve(Macro.safeName(name) + Macro.EXTENSION);
    }

    public static void save(Path macrosDir, Macro m) throws IOException {
        MacroIO.write(file(macrosDir, m.name), m);
    }

    public static List<String> names(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(Macro.EXTENSION))
                    .map(n -> n.substring(0, n.length() - Macro.EXTENSION.length()))
                    .sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
