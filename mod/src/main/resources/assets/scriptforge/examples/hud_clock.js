// @description Draws a clock, coordinates and speed on the HUD
function pad(n) { return n < 10 ? "0" + n : "" + n; }

setInterval(function () {
    const d = new Date();
    const p = player.pos();
    hud.rect("bg", 4, 4, 150, 44, "#80000000");
    hud.text("time", 8, 8, pad(d.getHours()) + ":" + pad(d.getMinutes()) + ":" + pad(d.getSeconds()), "#FFFFFF");
    hud.text("xyz", 8, 20, Math.floor(p.x) + " " + Math.floor(p.y) + " " + Math.floor(p.z), "#55FF55");
    hud.text("spd", 8, 32, player.speed().toFixed(1) + " blocks/s", "#55FFFF");
}, 100);
