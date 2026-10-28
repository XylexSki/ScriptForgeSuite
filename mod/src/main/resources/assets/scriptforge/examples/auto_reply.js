// @description Replies once (with a cooldown) when someone mentions your name in chat
const ME = player.name().toLowerCase();
const REPLY = "Sorry, I'm away from keyboard right now!";
const COOLDOWN_MS = 30000;
let last = 0;

events.on("chat", function (e) {
    if (e.text.indexOf(REPLY) !== -1) return;                 // ignore our own echo
    if (e.sender && e.sender.toLowerCase() === ME) return;    // ignore ourselves
    if (e.text.toLowerCase().indexOf(ME) === -1) return;      // not about us
    if (Date.now() - last < COOLDOWN_MS) return;              // cooldown
    last = Date.now();
    sleep(1200 + Math.random() * 1500);                       // small human-like delay
    chat.send(REPLY);
});

chat.print("&a[AutoReply] &fActive. Stop it with /script stop auto_reply");
