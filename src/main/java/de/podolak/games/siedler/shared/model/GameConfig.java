package de.podolak.games.siedler.shared.model;

public record GameConfig(
        int maxPlayers,
        int tickDurationMillis,
        MapDimensions mapDimensions
) {
    public static GameConfig defaultConfig() {
        return new GameConfig(4, 200, new MapDimensions(1000, 1000));
    }
}
