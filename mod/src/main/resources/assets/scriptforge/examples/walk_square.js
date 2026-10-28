// @description Walks a 6x6 square using walkTo (movement + smooth camera + jumping)
const start = player.pos();
const corners = [[6, 0], [6, 6], [0, 6], [0, 0]];

for (let i = 0; i < corners.length; i++) {
    const tx = start.x + corners[i][0];
    const tz = start.z + corners[i][1];
    chat.print("&7Walking to " + tx.toFixed(1) + ", " + tz.toFixed(1));
    if (!move.walkTo(tx, tz, { sprint: true, timeout: 15000 })) {
        chat.print("&cCould not reach the corner, giving up.");
        break;
    }
    sleep(300);
}

for (let i = 0; i < 4; i++) {
    player.lookTo(player.yaw() + 90, 0, 10); // four smooth quarter turns = one full spin
}
move.jump();
