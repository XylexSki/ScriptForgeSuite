package dev.scriptforge.api;

import dev.scriptforge.core.ItemInfo;
import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import dev.scriptforge.core.Snapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.mozilla.javascript.Scriptable;

import java.util.ArrayList;
import java.util.List;

/** gui.* : inspect/close screens, click inventory slots, toasts, and build your own screens. */
public class GuiApi {
    private final ScriptContext ctx;
    private final InputApi input;

    public GuiApi(ScriptContext ctx) {
        this.ctx = ctx;
        this.input = new InputApi(ctx);
    }

    /** The open screen as {title, type} (type: container|chat|pause|script|other), or null when none. */
    public Scriptable current() {
        Snapshot s = Mc.snap;
        if (s.screenTitle() == null) return null;
        return JsUtil.obj("title", s.screenTitle(), "type", s.screenType());
    }

    public boolean isOpen() {
        return Mc.snap.screenTitle() != null;
    }

    /** Close the current screen (containers are closed properly so the server knows). */
    public void close() {
        Mc.run(() -> {
            MinecraftClient c = Mc.client();
            if (c.currentScreen == null) return;
            if (c.player != null && c.currentScreen instanceof HandledScreen<?>) c.player.closeHandledScreen();
            else c.setScreen(null);
        });
    }

    public void openInventory() {
        input.tap("inventory");
    }

    /** Slots of the open container / inventory screen: [{slot, id, name, count}] (empty slots omitted). */
    public Scriptable slots() {
        List<ItemInfo> infos = Mc.call(() -> {
            ScreenHandler h = Mc.player().currentScreenHandler;
            List<ItemInfo> out = new ArrayList<>();
            for (int i = 0; i < h.slots.size(); i++) {
                ItemStack st = h.getSlot(i).getStack();
                if (!st.isEmpty()) out.add(ItemInfo.of(i, st));
            }
            return out;
        });
        List<Object> js = new ArrayList<>();
        for (ItemInfo i : infos) js.add(i.toJs());
        return JsUtil.arr(js);
    }

    public void clickSlot(double slot) {
        clickSlot(slot, 0, "pickup");
    }

    public void shiftClick(double slot) {
        clickSlot(slot, 0, "quick_move");
    }

    /**
     * Click a slot of the open container. button: 0 = left, 1 = right (for "swap": hotbar index 0-8).
     * mode: pickup | quick_move | swap | clone | throw | pickup_all
     */
    public void clickSlot(double slot, double button, String mode) {
        SlotActionType type = switch (mode.toLowerCase()) {
            case "pickup" -> SlotActionType.PICKUP;
            case "quick_move", "shift" -> SlotActionType.QUICK_MOVE;
            case "swap" -> SlotActionType.SWAP;
            case "clone" -> SlotActionType.CLONE;
            case "throw", "drop" -> SlotActionType.THROW;
            case "pickup_all" -> SlotActionType.PICKUP_ALL;
            default -> throw new ScriptError("Unknown click mode '" + mode + "'");
        };
        Mc.run(() -> {
            ClientPlayerEntity p = Mc.player();
            Mc.client().interactionManager.clickSlot(p.currentScreenHandler.syncId, (int) slot, (int) button, type, p);
        });
    }

    /** Small pop-up in the top right corner. */
    public void toast(String title, String message) {
        Mc.run(() -> SystemToast.add(Mc.client().getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                Text.literal(title), Text.literal(message)));
    }

    /** Start building a custom screen: gui.screen("Title").button(...).textField(...).open() */
    public ScreenBuilder screen(String title) {
        return new ScreenBuilder(ctx, title);
    }
}
