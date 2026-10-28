package dev.scriptforge.api;

import dev.scriptforge.core.ItemInfo;
import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import dev.scriptforge.core.Snapshot;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.List;

/** player.* : state, inventory, hotbar and camera rotation. State getters read the per-tick snapshot (instant). */
public class PlayerApi {
    private final ScriptContext ctx;

    public PlayerApi(ScriptContext ctx) {
        this.ctx = ctx;
    }

    private static Snapshot s() {
        Snapshot s = Mc.snap;
        if (!s.inWorld()) throw new ScriptError("You are not in a world");
        return s;
    }

    private static String itemId(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    // ---- state
    public boolean inWorld() { return Mc.snap.inWorld(); }
    public String name() { return s().name(); }
    public double x() { return s().x(); }
    public double y() { return s().y(); }
    public double z() { return s().z(); }
    public Scriptable pos() { Snapshot s = s(); return JsUtil.obj("x", s.x(), "y", s.y(), "z", s.z()); }
    public double yaw() { return s().yaw(); }
    public double pitch() { return s().pitch(); }
    public double health() { return s().health(); }
    public double maxHealth() { return s().maxHealth(); }
    public double food() { return s().food(); }
    public boolean onGround() { return s().onGround(); }
    public boolean sprinting() { return s().sprinting(); }
    public boolean sneaking() { return s().sneaking(); }
    public String dimension() { return s().dimension(); }
    public String gamemode() { return s().gamemode(); }

    /** Horizontal speed in blocks per second. */
    public double speed() {
        Snapshot s = s();
        return Math.sqrt(s.vx() * s.vx() + s.vz() * s.vz()) * 20.0;
    }

    // ---- inventory / hotbar
    public double slot() { return s().slot(); }

    public void selectSlot(double slot) {
        int n = MathHelper.clamp((int) slot, 0, 8);
        Mc.run(() -> Mc.player().getInventory().setSelectedSlot(n));
    }

    /** The held stack: {slot, id, name, count} (id is "minecraft:air" when empty). */
    public Scriptable heldItem() {
        ItemInfo info = Mc.call(() -> {
            PlayerInventory inv = Mc.player().getInventory();
            int sel = inv.getSelectedSlot();
            return ItemInfo.of(sel, inv.getStack(sel));
        });
        return info.toJs();
    }

    /** All non-empty slots of the main inventory + hotbar (0-8 hotbar, 9-35 inventory). */
    public Scriptable inventory() {
        List<ItemInfo> infos = Mc.call(() -> {
            PlayerInventory inv = Mc.player().getInventory();
            List<ItemInfo> out = new ArrayList<>();
            for (int i = 0; i < 36; i++) {
                ItemStack st = inv.getStack(i);
                if (!st.isEmpty()) out.add(ItemInfo.of(i, st));
            }
            return out;
        });
        List<Object> js = new ArrayList<>();
        for (ItemInfo i : infos) js.add(i.toJs());
        return JsUtil.arr(js);
    }

    /** Total count of an item id (e.g. "diamond" or "minecraft:diamond") across the inventory. */
    public double count(String id) {
        String want = itemId(id);
        int total = 0;
        List<ItemInfo> infos = Mc.call(() -> {
            PlayerInventory inv = Mc.player().getInventory();
            List<ItemInfo> out = new ArrayList<>();
            for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty()) out.add(ItemInfo.of(i, inv.getStack(i)));
            return out;
        });
        for (ItemInfo i : infos) if (i.id().equals(want)) total += i.count();
        return total;
    }

    /** First slot (0-35) holding the item, or -1. */
    public double findItem(String id) {
        String want = itemId(id);
        List<ItemInfo> infos = Mc.call(() -> {
            PlayerInventory inv = Mc.player().getInventory();
            List<ItemInfo> out = new ArrayList<>();
            for (int i = 0; i < 36; i++) if (!inv.getStack(i).isEmpty()) out.add(ItemInfo.of(i, inv.getStack(i)));
            return out;
        });
        for (ItemInfo i : infos) if (i.id().equals(want)) return i.slot();
        return -1;
    }

    // ---- camera
    static void rotate(double yaw, double pitch) {
        Mc.run(() -> {
            ClientPlayerEntity p = Mc.player();
            p.setYaw((float) yaw);
            p.setPitch(MathHelper.clamp((float) pitch, -90f, 90f));
        });
    }

    /** Instantly set the camera. yaw 0 = south, 90 = west, 180 = north, -90 = east. pitch -90 = up, 90 = down. */
    public void setRotation(double yaw, double pitch) {
        rotate(yaw, pitch);
    }

    /** Smoothly turn the camera over the given number of ticks (blocks until done). */
    public void lookTo(double yaw, double pitch, double ticks) {
        Snapshot s = s();
        float y0 = s.yaw(), p0 = s.pitch();
        float dy = MathHelper.wrapDegrees((float) yaw - y0);
        float dp = (float) pitch - p0;
        int n = Math.max(1, (int) ticks);
        for (int i = 1; i <= n; i++) {
            float t = (float) i / n;
            t = t * t * (3 - 2 * t); // smoothstep
            rotate(y0 + dy * t, p0 + dp * t);
            ctx.waitTicks(1);
        }
    }

    public void lookAt(double x, double y, double z) {
        double[] r = anglesTo(x, y, z);
        rotate(r[0], r[1]);
    }

    public void lookAt(double x, double y, double z, double ticks) {
        double[] r = anglesTo(x, y, z);
        lookTo(r[0], r[1], ticks);
    }

    private static double[] anglesTo(double x, double y, double z) {
        Snapshot s = s();
        double dx = x - s.x(), dy = y - s.eyeY(), dz = z - s.z();
        double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        return new double[]{yaw, pitch};
    }
}
