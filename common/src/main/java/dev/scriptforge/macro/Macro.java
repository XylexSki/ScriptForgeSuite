package dev.scriptforge.macro;

import java.util.ArrayList;
import java.util.List;

/** A recorded macro: a header plus one Frame per tick. Stored as JSON Lines by MacroIO (.sfmacro). */
public final class Macro {
    public static final int VERSION = 2;
    public static final String EXTENSION = ".sfmacro";

    /** Bit order of Frame.keys. */
    public static final String[] KEYS = {"forward", "back", "left", "right", "jump", "sneak", "sprint", "attack", "use"};

    public String mc = "";
    public String name = "";
    public long created;
    public String dimension = "";
    public double startX, startY, startZ;
    public float startYaw, startPitch;
    public final List<Frame> frames = new ArrayList<>();

    public int ticks() {
        return frames.size();
    }

    public double seconds() {
        return frames.size() / 20.0;
    }

    public Frame last() {
        return frames.isEmpty() ? null : frames.get(frames.size() - 1);
    }

    /** Horizontal distance between where the macro starts and where it ends (loops work best near 0). */
    public double endDrift() {
        Frame l = last();
        if (l == null) return 0;
        return Math.hypot(l.x - startX, l.z - startZ);
    }

    public int eventCount() {
        int n = 0;
        for (Frame f : frames) n += f.events.size();
        return n;
    }

    public static String safeName(String name) {
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
