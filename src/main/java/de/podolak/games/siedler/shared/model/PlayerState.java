package de.podolak.games.siedler.shared.model;

public record PlayerState(
        String playerId,
        String displayName,
        PlayerColor color,
        boolean connected,
        int wood,
        int stone
) {
}
