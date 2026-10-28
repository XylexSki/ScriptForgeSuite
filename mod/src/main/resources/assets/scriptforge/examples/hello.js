// @description Prints info about you, then says something only you can see
print("Hello, " + player.name() + "!");
print("Position: " + player.x().toFixed(1) + ", " + player.y().toFixed(1) + ", " + player.z().toFixed(1));
print("Health: " + player.health() + "  Food: " + player.food() + "  Dimension: " + player.dimension());
chat.print("&aThis line is local-only and supports &cc&6o&el&2o&9r&5s&a.");
