package dev.scriptforge.headless;

import dev.scriptforge.macro.Event;
import dev.scriptforge.macro.Frame;
import dev.scriptforge.macro.Macro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays a Macro for one Bot, one frame per call to tick() (20 per second), turning the recorded player state and
 * events into protocol packets. It never simulates physics: it re-sends the positions the real client recorded,
 * shifted so the macro starts wherever this bot is standing (mode "relative").
 */
final class MacroPlayer {
    private static final Logger LOG = LoggerFactory.getLogger(MacroPlayer.class);

    private final Bot bot;
    private final Macro macro;
    private final Config cfg;
    private final PacketFactory pf = new PacketFactory();

    private int idx;
    private int loop;
    private int delay;
    private boolean begun;
    private boolean aborted;

    // shift applied to recorded coordinates
    private double ox, oy, oz;
    private int bx, by, bz;

    // what the server last heard from us
    private double lx, ly, lz;
    private float lyaw, lpitch;
    private boolean lGround = true, lCollided;
    private int ticksSincePos;
    private int lSlot = -1;
    private int lInput;
    private boolean lSprint;

    // digging in progress (block coordinates already shifted)
    private boolean digging;
    private int dx, dy, dz, dFace;

    MacroPlayer(Bot bot, Macro macro, Config cfg) {
        this.bot = bot;
        this.macro = macro;
        this.cfg = cfg;
    }

    int loopsDone() {
        return loop;
    }

    int progressPercent() {
        return macro.frames.isEmpty() ? 100 : idx * 100 / macro.frames.size();
    }

    void abort(String why) {
        if (aborted) return;
        aborted = true;
        cancelDig();
        LOG.warn("[{}] macro aborted: {}", bot.name(), why);
    }

    /** The server moved us (teleport / setback): continue from the new position instead of the old offset. */
    void resync() {
        if (!begun || idx == 0 || idx >= macro.frames.size()) return;
        Frame f = macro.frames.get(idx - 1);
        if (cfg.mode.equals("relative")) {
            setOffsets(bot.x() - f.x, bot.y() - f.y, bot.z() - f.z);
        }
        lx = bot.x();
        ly = bot.y();
        lz = bot.z();
        ticksSincePos = 20;
    }

    /** @return true when the macro (all loops) is finished or aborted. */
    boolean tick() {
        if (aborted) return true;
        if (begun && idx >= macro.frames.size()) {
            cancelDig();
            loop++;
            if (cfg.loops != 0 && loop >= cfg.loops) return true;
            idx = 0;
            begun = false;
            delay = cfg.loopDelayTicks;
        }
        if (delay > 0) {
            delay--;
            return false;
        }
        if (!begun) {
            begin();
            begun = true;
        }
        play(macro.frames.get(idx++));
        return false;
    }

    private void begin() {
        if (cfg.mode.equals("relative")) {
            if (bot.positionKnown()) {
                setOffsets(bot.x() - macro.startX, bot.y() - macro.startY, bot.z() - macro.startZ);
            } else {
                LOG.warn("[{}] server position not received yet; replaying at recorded coordinates", bot.name());
                setOffsets(0, 0, 0);
            }
        } else {
            setOffsets(0, 0, 0);
        }
        lx = bot.positionKnown() ? bot.x() : macro.startX + ox;
        ly = bot.positionKnown() ? bot.y() : macro.startY + oy;
        lz = bot.positionKnown() ? bot.z() : macro.startZ + oz;
        lyaw = bot.yaw();
        lpitch = bot.pitch();
        ticksSincePos = 0;
        lSlot = -1;
        lInput = 0;
        lSprint = false;
        LOG.info("[{}] macro loop {} starts at {} {} {} (block shift {} {} {})", bot.name(), loop + 1,
                fmt(lx), fmt(ly), fmt(lz), bx, by, bz);
    }

    /**
     * The macro is shifted by a whole number of blocks, for positions and block targets alike, so the bot walks the
     * same path relative to the block grid that the recording did. The fractional part of where the bot stands is
     * therefore replaced by the recorded one (a snap of at most half a block on the first move).
     */
    private void setOffsets(double x, double y, double z) {
        bx = (int) Math.round(x);
        by = (int) Math.round(y);
        bz = (int) Math.round(z);
        ox = bx;
        oy = by;
        oz = bz;
    }

    private void play(Frame f) {
        // keys -> input packet (+ sprint command), hotbar slot
        int input = inputBits(f);
        if (input != lInput) {
            bot.send(pf.input(input));
            lInput = input;
        }
        if (f.sprinting != lSprint) {
            bot.send(pf.sprint(bot.entityId(), f.sprinting));
            lSprint = f.sprinting;
        }
        if (f.slot != lSlot) {
            bot.send(pf.heldSlot(f.slot));
            lSlot = f.slot;
        }

        // events happen before the movement packet, like in the vanilla client
        boolean startedDigThisTick = false;
        for (Event e : f.events) {
            if (handle(e, f)) startedDigThisTick = true;
        }
        if (digging && !startedDigThisTick) bot.send(pf.swing(0)); // the client swings every tick while mining

        movement(f);
    }

    private static int inputBits(Frame f) {
        int bits = 0;
        if (f.key(0)) bits |= PacketFactory.INPUT_FORWARD;
        if (f.key(1)) bits |= PacketFactory.INPUT_BACK;
        if (f.key(2)) bits |= PacketFactory.INPUT_LEFT;
        if (f.key(3)) bits |= PacketFactory.INPUT_RIGHT;
        if (f.key(4)) bits |= PacketFactory.INPUT_JUMP;
        if (f.key(5)) bits |= PacketFactory.INPUT_SNEAK;
        if (f.key(6)) bits |= PacketFactory.INPUT_SPRINT;
        return bits;
    }

    /** @return true if this event started digging. */
    private boolean handle(Event e, Frame f) {
        switch (e.k) {
            case Event.DIG -> {
                if (e.a.equals("start")) {
                    cancelDig();
                    dx = e.x + bx;
                    dy = e.y + by;
                    dz = e.z + bz;
                    dFace = e.f;
                    digging = true;
                    bot.send(pf.dig("start", dx, dy, dz, dFace, bot.nextSequence()));
                    bot.send(pf.swing(0));
                    return true;
                } else if (digging) {
                    bot.send(pf.dig("finish", e.x + bx, e.y + by, e.z + bz, dFace, bot.nextSequence()));
                    digging = false;
                }
            }
            case Event.USE_BLOCK -> {
                bot.send(pf.useBlock(e, e.x + bx, e.y + by, e.z + bz, bot.nextSequence()));
                bot.send(pf.swing(e.hand));
            }
            case Event.USE_ITEM -> bot.send(pf.useItem(e.hand, bot.nextSequence(), f.yaw, f.pitch));
            case Event.SWING -> bot.send(pf.swing(e.hand));
            case Event.CHAT -> {
                if (cfg.replayChat) bot.send(pf.chat(e.m));
            }
            case Event.CMD -> {
                if (cfg.replayCommands) bot.send(pf.command(e.m));
            }
            default -> LOG.debug("unknown macro event '{}'", e.k);
        }
        return false;
    }

    private void cancelDig() {
        if (digging) {
            bot.send(pf.dig("cancel", dx, dy, dz, dFace, bot.nextSequence()));
            digging = false;
        }
    }

    /** Mirrors the vanilla client's choice of movement packet. */
    private void movement(Frame f) {
        double x = f.x + ox, y = f.y + oy, z = f.z + oz;
        double d2 = (x - lx) * (x - lx) + (y - ly) * (y - ly) + (z - lz) * (z - lz);
        boolean posChanged = d2 > 4.0E-8 || ++ticksSincePos >= 20;
        boolean rotChanged = f.yaw != lyaw || f.pitch != lpitch;

        if (posChanged && rotChanged) {
            bot.send(pf.posRot(f.onGround, f.collided, x, y, z, f.yaw, f.pitch));
        } else if (posChanged) {
            bot.send(pf.pos(f.onGround, f.collided, x, y, z));
        } else if (rotChanged) {
            bot.send(pf.rot(f.onGround, f.collided, f.yaw, f.pitch));
        } else if (f.onGround != lGround || f.collided != lCollided) {
            bot.send(pf.statusOnly(f.onGround, f.collided));
        }

        if (posChanged) {
            lx = x;
            ly = y;
            lz = z;
            ticksSincePos = 0;
            bot.setPosition(x, y, z, f.yaw, f.pitch);
        } else {
            bot.setPosition(lx, ly, lz, f.yaw, f.pitch);
        }
        lyaw = f.yaw;
        lpitch = f.pitch;
        lGround = f.onGround;
        lCollided = f.collided;
    }

    private static String fmt(double d) {
        return String.format("%.1f", d);
    }
}
