package dev.shirokai.essentials.persistence;

public record Position(String dimension, double x, double y, double z, float yaw, float pitch, long savedAt) {
    public Position {
        if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9/._-]+") || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch) || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 30_000_000) throw new IllegalArgumentException("Posição inválida");
    }
}
