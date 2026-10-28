package dev.scriptforge.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Key name lookup: game actions ("forward", "jump", ...) and physical keys ("r", "f6", "left.shift", "mouse.left"). */
public final class Keys {
    private Keys() {}

    public static final String[] NAMES = {
            "forward", "back", "left", "right", "jump", "sneak", "sprint",
            "attack", "use", "pick", "drop", "swap", "inventory", "hotbar1..hotbar9"};

    /** A game action key binding by script name. */
    public static KeyBinding binding(String name) {
        GameOptions o = MinecraftClient.getInstance().options;
        String n = name.toLowerCase(Locale.ROOT).trim();
        if (n.startsWith("hotbar")) {
            try {
                int i = Integer.parseInt(n.substring(6));
                if (i >= 1 && i <= 9) return o.hotbarKeys[i - 1];
            } catch (NumberFormatException ignored) {
                // fall through to the error below
            }
        }
        return switch (n) {
            case "forward" -> o.forwardKey;
            case "back" -> o.backKey;
            case "left" -> o.leftKey;
            case "right" -> o.rightKey;
            case "jump" -> o.jumpKey;
            case "sneak" -> o.sneakKey;
            case "sprint" -> o.sprintKey;
            case "attack" -> o.attackKey;
            case "use" -> o.useKey;
            case "pick" -> o.pickItemKey;
            case "drop" -> o.dropKey;
            case "swap" -> o.swapHandsKey;
            case "inventory" -> o.inventoryKey;
            default -> throw new ScriptError("Unknown key '" + name + "'. Valid: " + String.join(", ", NAMES));
        };
    }

    /** A physical key: "r", "f6", "left.shift", "space", "mouse.left", or a full "key.keyboard.r". */
    public static InputUtil.Key physical(String name) {
        InputUtil.Key k = physicalOrNull(name);
        if (k == null) throw new ScriptError("Unknown physical key '" + name + "'");
        return k;
    }

    public static InputUtil.Key physicalOrNull(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT).trim();
        String tk = n.startsWith("key.") ? n : n.startsWith("mouse.") ? "key." + n : "key.keyboard." + n;
        try {
            return InputUtil.fromTranslationKey(tk);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Client thread only. */
    public static boolean isDown(InputUtil.Key key) {
        if (key.getCode() < 0) return false;
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return switch (key.getCategory()) {
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getCode()) == GLFW.GLFW_PRESS;
            case KEYSYM -> GLFW.glfwGetKey(window, key.getCode()) == GLFW.GLFW_PRESS;
            default -> false;
        };
    }

    /** Registers one "press event" so wasPressed() fires once (needed for attack/use/drop/...). Client thread only. */
    public static void bump(KeyBinding kb) {
        KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey()));
    }

    /** Sets a binding back to whatever the physical keyboard says. Client thread only. */
    public static void restore(KeyBinding kb) {
        kb.setPressed(isDown(InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey())));
    }
}
