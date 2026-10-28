package dev.scriptforge.core;

/**
 * Immutable copy of the player/game state, refreshed at the end of every client tick.
 * Scripts read this instead of touching the game thread, so getters like player.x() are instant.
 */
public record Snapshot(
        boolean inWorld,
        double x, double y, double z, double eyeY,
        double vx, double vy, double vz,
        float yaw, float pitch,
        float health, float maxHealth, int food,
        boolean onGround, boolean sprinting, boolean sneaking, boolean collided,
        int slot,
        String name, String dimension, String gamemode,
        String screenTitle, String screenType,
        double mouseX, double mouseY,
        int fps) {

    public static final Snapshot EMPTY = new Snapshot(false,
            0, 0, 0, 0, 0, 0, 0, 0f, 0f, 0f, 0f, 0, false, false, false, false, 0,
            "", "", "", null, null, 0, 0, 0);
}
