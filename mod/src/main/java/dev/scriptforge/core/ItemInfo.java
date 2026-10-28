package dev.scriptforge.core;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import org.mozilla.javascript.Scriptable;

/** Plain-data view of an item stack. Build on the client thread, convert with toJs() on the script thread. */
public record ItemInfo(int slot, String id, String name, int count) {
    public static ItemInfo of(int slot, ItemStack stack) {
        return new ItemInfo(slot, Registries.ITEM.getId(stack.getItem()).toString(),
                stack.getName().getString(), stack.getCount());
    }

    public Scriptable toJs() {
        return JsUtil.obj("slot", slot, "id", id, "name", name, "count", count);
    }
}
