package de.podolak.games.siedler.shared.model;

public record MapDimensions(int width, int height) {
    public MapDimensions {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Map dimensions must be positive");
        }
    }
}
