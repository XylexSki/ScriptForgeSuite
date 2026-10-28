package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import dev.scriptforge.core.Snapshot;
import net.minecraft.util.math.MathHelper;
import org.mozilla.javascript.Scriptable;

/** move.* : walking helpers built on input + player. walkTo() is straight-line (no pathfinding). */
public class MoveApi {
    private static final String[] MOVE_KEYS = {"forward", "back", "left", "right", "jump", "sneak", "sprint"};

    private final ScriptContext ctx;
    private final InputApi in;

    public MoveApi(ScriptContext ctx) {
        this.ctx = ctx;
        this.in = new InputApi(ctx);
    }

    /** Hold a direction ("forward", "back", "left", "right") for N ticks (20 ticks = 1 second). */
    public void walk(String direction, double ticks) {
        in.hold(direction);
        try {
            ctx.waitTicks((int) ticks);
        } finally {
            in.release(direction);
        }
    }

    public void forward(double ticks) { walk("forward", ticks); }
    public void back(double ticks) { walk("back", ticks); }
    public void left(double ticks) { walk("left", ticks); }
    public void right(double ticks) { walk("right", ticks); }

    public void jump() {
        in.tap("jump");
    }

    public void sprint(boolean on) {
        if (on) in.hold("sprint"); else in.release("sprint");
    }

    public void sneak(boolean on) {
        if (on) in.hold("sneak"); else in.release("sneak");
    }

    /** Release every movement key. */
    public void stop() {
        for (String k : MOVE_KEYS) in.release(k);
    }

    public boolean walkTo(double x, double z) {
        return walkTo(x, z, null);
    }

    /**
     * Walk in a straight line to (x, z), turning the camera and jumping over obstacles.
     * opts: {sprint:false, radius:0.6, timeout:30000 (ms), jump:true, turnSpeed:25 (deg/tick)}
     * Returns true if it arrived, false on timeout.
     */
    public boolean walkTo(double x, double z, Scriptable opts) {
        double radius = JsUtil.num(opts, "radius", 0.6);
        long timeout = (long) JsUtil.num(opts, "timeout", 30000);
        boolean sprint = JsUtil.bool(opts, "sprint", false);
        boolean autoJump = JsUtil.bool(opts, "jump", true);
        float turn = (float) JsUtil.num(opts, "turnSpeed", 25);
        long end = System.currentTimeMillis() + timeout;
        try {
            in.hold("forward");
            if (sprint) in.hold("sprint");
            while (System.currentTimeMillis() < end) {
                Snapshot s = Mc.snap;
                if (!s.inWorld()) throw new ScriptError("You are not in a world");
                double dx = x - s.x(), dz = z - s.z();
                if (Math.sqrt(dx * dx + dz * dz) <= radius) return true;
                float target = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float diff = MathHelper.wrapDegrees(target - s.yaw());
                PlayerApi.rotate(s.yaw() + MathHelper.clamp(diff, -turn, turn), s.pitch());
                if (autoJump && s.collided() && s.onGround()) in.tap("jump");
                else ctx.waitTicks(1);
            }
            return false;
        } finally {
            stop();
        }
    }
}
