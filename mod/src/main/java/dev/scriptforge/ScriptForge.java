package dev.scriptforge;

import dev.scriptforge.core.ChatHooks;
import dev.scriptforge.core.Hud;
import dev.scriptforge.core.MacroRecorder;
import dev.scriptforge.core.RecorderHooks;
import dev.scriptforge.core.Mc;
import dev.scriptforge.core.ScriptManager;
import dev.scriptforge.gui.ScriptsScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScriptForge implements ClientModInitializer {
    public static final String ID = "scriptforge";
    public static final Logger LOG = LoggerFactory.getLogger("ScriptForge");

    private static ScriptManager manager;

    public static ScriptManager scripts() {
        return manager;
    }

    @Override
    public void onInitializeClient() {
        manager = new ScriptManager(FabricLoader.getInstance().getGameDir().resolve("scriptforge"));
        manager.init();

        // 1.21.9+: key bindings belong to a Category (translation key: key.category.scriptforge.main)
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(ID, "main"));
        KeyBinding stopKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.scriptforge.stop_all", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_END, category));
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.scriptforge.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_HOME, category));

        ClientTickEvents.START_CLIENT_TICK.register(manager::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Mc.updateSnapshot(client);
            MacroRecorder.sample(client);
            manager.onEndTick(client);
            while (stopKey.wasPressed()) {
                manager.stopAll();
                Mc.chat("\u00a77[ScriptForge] \u00a7cAll scripts stopped");
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ScriptsScreen());
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> manager.stopAll());

        ChatHooks.register(manager);
        ScriptCommands.register();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(ID, "script_hud"), Hud::render);
        RecorderHooks.register();

        LOG.info("ScriptForge ready. Scripts folder: {}", manager.scriptsDir);
    }
}
