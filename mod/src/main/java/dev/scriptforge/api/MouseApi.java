package dev.scriptforge.api;

import dev.scriptforge.core.JsUtil;
import dev.scriptforge.core.Keys;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptContext;
import dev.scriptforge.core.ScriptError;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.MouseInput;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.mozilla.javascript.Scriptable;

/**
 * mouse.* : cursor position and clicks.
 * In the world, clicks map to attack/use/pick; with a screen open they are delivered to that screen.
 * GUI coordinates are in scaled GUI pixels (what screens use), origin at the top-left.
 */
public class MouseApi {
    private final ScriptContext ctx;
    private final InputApi input;

    public MouseApi(ScriptContext ctx) {
        this.ctx = ctx;
        this.input = new InputApi(ctx);
    }

    private static int button(String name) {
        return switch (name.toLowerCase()) {
            case "left", "l", "0" -> 0;
            case "right", "r", "1" -> 1;
            case "middle", "m", "2" -> 2;
            default -> throw new ScriptError("Unknown mouse button '" + name + "' (left/right/middle)");
        };
    }

    private static String action(int button) {
        return button == 0 ? "attack" : button == 1 ? "use" : "pick";
    }

    public double x() { return Mc.snap.mouseX(); }
    public double y() { return Mc.snap.mouseY(); }

    public Scriptable pos() {
        return JsUtil.obj("x", Mc.snap.mouseX(), "y", Mc.snap.mouseY());
    }

    /** Is the physical mouse button held? */
    public boolean isDown(String button) {
        var key = InputUtil.Type.MOUSE.createFromCode(button(button));
        return Mc.call(() -> Keys.isDown(key));
    }

    /** Turn the camera as if the mouse moved by (dx, dy) pixels. Positive dx = right, positive dy = down. */
    public void look(double dx, double dy) {
        Mc.run(() -> Mc.player().changeLookDirection(dx, dy));
    }

    /** Move the OS cursor to GUI coordinates (only has an effect while a screen is open). */
    public void moveTo(double x, double y) {
        Mc.run(() -> {
            MinecraftClient c = Mc.client();
            double sf = c.getWindow().getScaleFactor();
            GLFW.glfwSetCursorPos(c.getWindow().getHandle(), x * sf, y * sf);
        });
        ctx.waitTicks(1); // let the game pick up the new cursor position
    }

    public void click() {
        click("left");
    }

    /** Click at the current cursor position (screen open) or with the crosshair (in the world). */
    public void click(String button) {
        int b = button(button);
        boolean screenOpen = Mc.call(() -> Mc.client().currentScreen != null);
        if (screenOpen) {
            Mc.run(() -> {
                MinecraftClient c = Mc.client();
                double sf = c.getWindow().getScaleFactor();
                deliver(c.currentScreen, c.mouse.getX() / sf, c.mouse.getY() / sf, b);
            });
        } else {
            input.tap(action(b));
        }
    }

    /** Click the open screen at GUI coordinates without moving the real cursor. */
    public void clickAt(double x, double y, String button) {
        int b = button(button);
        Mc.run(() -> {
            Screen s = Mc.client().currentScreen;
            if (s == null) throw new ScriptError("No screen is open");
            deliver(s, x, y, b);
        });
    }

    private static void deliver(Screen s, double x, double y, int b) {
        Click click = new Click(x, y, new MouseInput(b, 0));
        s.mouseClicked(click, false);
        s.mouseReleased(click);
    }

    /** Positive = scroll up. In the world this changes the hotbar slot. */
    public void scroll(double amount) {
        Mc.run(() -> {
            MinecraftClient c = Mc.client();
            if (c.currentScreen != null) {
                double sf = c.getWindow().getScaleFactor();
                c.currentScreen.mouseScrolled(c.mouse.getX() / sf, c.mouse.getY() / sf, 0, amount);
            } else {
                var inv = Mc.player().getInventory();
                inv.setSelectedSlot(Math.floorMod(inv.getSelectedSlot() - (int) Math.signum(amount), 9));
            }
        });
    }

    /** Hold left/right/middle in the world (until release or script end). */
    public void hold(String button) {
        input.hold(action(button(button)));
    }

    public void release(String button) {
        input.release(action(button(button)));
    }
}
