// @description Custom GUI: text field + buttons that drive chat and movement
const ui = gui.screen("Macro Control Panel");

ui.label("status", 0, -52, "Idle");
ui.textField("msg", 0, -34, 200, 20, "Message to send 3 times...");

ui.button(-52, -4, 100, 20, "Send x3", function () {
    const text = ui.getText("msg");
    if (!text) { ui.setText("status", "Type a message first"); return; }
    for (let i = 1; i <= 3; i++) {
        ui.setText("status", "Sending " + i + "/3");
        chat.send(text);
        sleep(1500);          // sleeping inside a button callback is fine
    }
    ui.setText("status", "Done");
});

ui.button(52, -4, 100, 20, "Jump", function () { move.jump(); });
ui.button(0, 24, 204, 20, "Close", function () { ui.close(); });

ui.onClose(function () { exit(); }); // closing the screen ends the script
ui.open();
