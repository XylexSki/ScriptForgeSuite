package dev.scriptforge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** A screen assembled from script-defined labels, buttons and text fields. (0,0) of each element is the screen center. */
public class ScriptScreen extends Screen {
    public enum Kind { LABEL, BUTTON, FIELD }

    public static final class Elem {
        public final Kind kind;
        public final String id;
        public final int x, y, w, h;
        public volatile String text;      // label text / button text / field placeholder
        public volatile String value = ""; // field contents
        public final Runnable action;

        public Elem(Kind kind, String id, int x, int y, int w, int h, String text, Runnable action) {
            this.kind = kind;
            this.id = id;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.text = text;
            this.action = action;
        }
    }

    private final List<Elem> elems = new CopyOnWriteArrayList<>();
    private final Map<String, Elem> byId = new ConcurrentHashMap<>();
    private final Map<Elem, ClickableWidget> widgets = new HashMap<>();
    private volatile Runnable onClosed;

    public ScriptScreen(String title) {
        super(Text.literal(title));
    }

    public void add(Elem e) {
        elems.add(e);
        byId.put(e.id, e);
    }

    public Elem get(String id) {
        return byId.get(id);
    }

    public void onClosed(Runnable r) {
        this.onClosed = r;
    }

    public void rebuild() {
        clearAndInit();
    }

    @Override
    protected void init() {
        widgets.clear();
        int cx = width / 2, cy = height / 2;
        for (Elem e : elems) {
            int px = cx + e.x - e.w / 2;
            int py = cy + e.y - e.h / 2;
            ClickableWidget widget = null;
            if (e.kind == Kind.BUTTON) {
                widget = ButtonWidget.builder(Text.literal(e.text), b -> {
                    if (e.action != null) e.action.run();
                }).dimensions(px, py, e.w, e.h).build();
            } else if (e.kind == Kind.FIELD) {
                TextFieldWidget f = new TextFieldWidget(textRenderer, px, py, e.w, e.h, Text.literal(e.id));
                f.setMaxLength(256);
                f.setPlaceholder(Text.literal(e.text));
                f.setText(e.value);
                f.setChangedListener(s -> e.value = s);
                widget = f;
            }
            if (widget != null) {
                addDrawableChild(widget);
                widgets.put(e, widget);
            }
        }
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        // keep widgets in sync with values the script changed via setText()
        for (Map.Entry<Elem, ClickableWidget> en : widgets.entrySet()) {
            Elem e = en.getKey();
            ClickableWidget w = en.getValue();
            if (w instanceof TextFieldWidget f) {
                String v = e.value;
                if (!f.getText().equals(v)) f.setText(v);
            } else if (!w.getMessage().getString().equals(e.text)) {
                w.setMessage(Text.literal(e.text));
            }
        }
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredTextWithShadow(textRenderer, title, width / 2, 14, 0xFFFFFFFF);
        int cx = width / 2, cy = height / 2;
        for (Elem e : elems) {
            if (e.kind == Kind.LABEL) g.drawCenteredTextWithShadow(textRenderer, Text.literal(e.text), cx + e.x, cy + e.y, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Runnable r = onClosed;
        if (r != null) r.run();
    }
}
