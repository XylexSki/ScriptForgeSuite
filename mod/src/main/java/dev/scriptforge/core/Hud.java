package dev.scriptforge.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/** Script-controlled HUD elements (text and rectangles), drawn every frame by the HUD render callback. */
public final class Hud {
    private Hud() {}

    public record Item(boolean rect, int x, int y, int w, int h, int color, String text) {}

    private static final Map<String, Item> ITEMS = new ConcurrentSkipListMap<>();

    public static void put(String owner, String id, Item item) {
        ITEMS.put(owner + "\u0000" + id, item);
    }

    public static void remove(String owner, String id) {
        ITEMS.remove(owner + "\u0000" + id);
    }

    public static void clear(String owner) {
        ITEMS.keySet().removeIf(k -> k.startsWith(owner + "\u0000"));
    }

    public static void render(DrawContext g, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (ITEMS.isEmpty() || mc.options.hudHidden) return;
        int sw = g.getScaledWindowWidth();
        int sh = g.getScaledWindowHeight();
        // rectangles first so text is always on top
        for (Item i : ITEMS.values()) {
            if (!i.rect()) continue;
            int x = i.x() < 0 ? sw + i.x() - i.w() : i.x();
            int y = i.y() < 0 ? sh + i.y() - i.h() : i.y();
            g.fill(x, y, x + i.w(), y + i.h(), i.color());
        }
        for (Item i : ITEMS.values()) {
            if (i.rect()) continue;
            int x = i.x() < 0 ? sw + i.x() - mc.textRenderer.getWidth(i.text()) : i.x();
            int y = i.y() < 0 ? sh + i.y() - mc.textRenderer.fontHeight : i.y();
            g.drawTextWithShadow(mc.textRenderer, Text.literal(i.text()), x, y, i.color());
        }
    }
}
