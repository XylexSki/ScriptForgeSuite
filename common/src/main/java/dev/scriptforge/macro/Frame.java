package dev.scriptforge.macro;

import java.util.ArrayList;
import java.util.List;

/** The player's state at the end of one client tick (20 per second) plus the events that happened in it. */
public final class Frame {
    public double x, y, z;
    public float yaw, pitch;
    public boolean onGround, collided, sneaking, sprinting;
    public int slot;
    /** Bit i set = Macro.KEYS[i] was held. Used by the in-game replay; the headless client uses the fields above. */
    public int keys;
    public List<Event> events = new ArrayList<>();

    public boolean key(int index) {
        return ((keys >> index) & 1) != 0;
    }
}
