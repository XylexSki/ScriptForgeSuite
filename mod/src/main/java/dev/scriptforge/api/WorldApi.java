package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.mozilla.javascript.Scriptable;

import java.util.LinkedHashMap;
import java.util.Map;

/** world.* : a few read-only world queries. */
public class WorldApi {
    public WorldApi(ScriptContext ctx) { }

    /** Block id at a position, e.g. "minecraft:stone" (only loaded chunks; unloaded reads as air). */
    public String blockAt(double x, double y, double z) {
        BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        return Mc.call(() -> {
            Mc.player();
            return Registries.BLOCK.getId(Mc.client().world.getBlockState(pos).getBlock()).toString();
        });
    }

    /** What the crosshair points at: {type:"block", x,y,z, block, side} | {type:"entity", entity, name} | null. */
    public Scriptable target() {
        Map<String, Object> data = Mc.call(() -> {
            MinecraftClient mc = Mc.client();
            HitResult hit = mc.crosshairTarget;
            if (hit == null) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
                BlockPos p = b.getBlockPos();
                m.put("type", "block");
                m.put("x", p.getX());
                m.put("y", p.getY());
                m.put("z", p.getZ());
                m.put("block", Registries.BLOCK.getId(mc.world.getBlockState(p).getBlock()).toString());
                m.put("side", b.getSide().asString());
                return m;
            }
            if (hit instanceof EntityHitResult e) {
                Entity en = e.getEntity();
                m.put("type", "entity");
                m.put("entity", Registries.ENTITY_TYPE.getId(en.getType()).toString());
                m.put("name", en.getName().getString());
                return m;
            }
            return null;
        });
        return data == null ? null : JsUtil.fromMap(data);
    }

    /** Ticks since the world was created. */
    public double time() {
        return Mc.call(() -> (double) Mc.client().world.getTime());
    }

    /** Time of day in ticks (0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight). */
    public double dayTime() {
        return Mc.call(() -> (double) (Mc.client().world.getTimeOfDay() % 24000L));
    }

    public boolean raining() {
        return Mc.call(() -> {
            ClientWorld w = Mc.client().world;
            return w != null && w.isRaining();
        });
    }

    /** Server address, or "singleplayer". */
    public String server() {
        return Mc.call(() -> {
            ServerInfo info = Mc.client().getCurrentServerEntry();
            return info == null ? "singleplayer" : info.address;
        });
    }
}
