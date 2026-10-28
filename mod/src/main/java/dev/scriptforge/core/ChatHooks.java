package dev.scriptforge.core;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;

import java.util.HashMap;
import java.util.Map;

/** Feeds incoming and outgoing chat into the ChatLog and fires "chat" / "send" script events. */
public final class ChatHooks {
    private ChatHooks() {}

    public static void register(ScriptManager manager) {
        // System / server messages (most plugin and server output arrives here)
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return; // action bar spam is ignored
            String text = message.getString();
            incoming(manager, "game", "", text, text);
        });

        // Signed player chat
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, timestamp) -> {
            String text = message.getString();
            String content = signed != null ? signed.getContent().getString() : text;
            incoming(manager, "chat", profileName(sender), text, content);
        });

        ClientSendMessageEvents.CHAT.register(text -> outgoing(manager, text, false));
        ClientSendMessageEvents.COMMAND.register(command -> outgoing(manager, "/" + command, true));
    }

    private static void incoming(ScriptManager m, String type, String sender, String text, String content) {
        ChatLog.Line line = ChatLog.add(type, sender, text);
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("sender", sender);
        data.put("text", text);
        data.put("content", content);
        data.put("time", (double) line.time());
        m.fire("chat", data);
    }

    /** GameProfile became a record in 1.21.9 (name()); older versions used getName(). Support both. */
    private static String profileName(Object profile) {
        if (profile == null) return "";
        for (String method : new String[]{"name", "getName"}) {
            try {
                Object v = profile.getClass().getMethod(method).invoke(profile);
                if (v != null) return v.toString();
            } catch (ReflectiveOperationException ignored) {
                // try the next accessor
            }
        }
        return "";
    }

    /** Commands that carry secrets: never written to the chat log, recordings or macros. */
    private static final java.util.regex.Pattern SECRET = java.util.regex.Pattern.compile(
            "^/(login|log|l|register|reg|unregister|password|passwd|changepassword|cp|2fa|totp)(\\s|$)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private static void outgoing(ScriptManager m, String text, boolean command) {
        boolean secret = command && SECRET.matcher(text).find();
        String shown = secret ? text.replaceFirst("^(/\\S+).*$", "$1 ***") : text;
        ChatLog.add("out", "", shown);
        if (!secret) {
            MacroRecorder.event(command ? dev.scriptforge.macro.Event.cmd(text.substring(1))
                    : dev.scriptforge.macro.Event.chat(text));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("text", shown);
        data.put("command", command);
        m.fire("send", data);
    }
}
