// @description Records ALL chat (incoming + what you type) to scriptforge/recordings/chat.txt, with keyword alerts
// @hotkey F7
// Press F7 to start, press F7 again to stop (stopping the script closes the file).

const KEYWORDS = ["diamond", "trade", player.name()];

chat.record("chat.txt", { outgoing: true });
chat.print("&a[Recorder] &fRecording to recordings/chat.txt. Press F7 again to stop.");

let count = 0;
events.on("chat", function (e) {
    count++;
    const lower = e.text.toLowerCase();
    for (let i = 0; i < KEYWORDS.length; i++) {
        if (lower.indexOf(KEYWORDS[i].toLowerCase()) !== -1) {
            gui.toast("Chat alert: " + KEYWORDS[i], e.text);
            break;
        }
    }
    hud.text("count", -6, 6, "Recording: " + count + " lines", "#FF5555");
});
