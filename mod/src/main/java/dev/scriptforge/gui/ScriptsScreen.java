package dev.scriptforge.gui;

import dev.scriptforge.ScriptForge;
import dev.scriptforge.core.ScriptInfo;
import dev.scriptforge.core.ScriptManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Built-in script manager: click a script to start or stop it. Open with Home or /script gui. */
public class ScriptsScreen extends Screen {
    private int page;

    public ScriptsScreen() {
        super(Text.literal("ScriptForge"));
    }

    @Override
    protected void init() {
        ScriptManager mgr = ScriptForge.scripts();
        List<ScriptInfo> all = mgr.list();
        int perPage = Math.max(1, (height - 90) / 24);
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);

        int row = 0;
        for (int i = page * perPage; i < Math.min(all.size(), (page + 1) * perPage); i++, row++) {
            ScriptInfo info = all.get(i);
            boolean on = mgr.isRunning(info.name());
            String label = (on ? "\u00a7a[Running] \u00a7f" : "\u00a77[Stopped] \u00a7f") + info.name();
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                mgr.toggle(info.name());
                clearAndInit();
            }).dimensions(width / 2 - 120, 36 + row * 24, 240, 20).build());
        }

        int y = height - 46;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(width / 2 - 120, y, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            clearAndInit();
        }).dimensions(width / 2 + 80, y, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Stop all"), b -> {
            mgr.stopAll();
            clearAndInit();
        }).dimensions(width / 2 - 75, y, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(width / 2 + 5, y, 70, 20).build());
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredTextWithShadow(textRenderer, Text.literal("ScriptForge - scripts"), width / 2, 14, 0xFFFFFFFF);
        g.drawCenteredTextWithShadow(textRenderer, Text.literal("Folder: .minecraft/scriptforge/scripts"), width / 2, 24, 0xFFAAAAAA);
    }
}
