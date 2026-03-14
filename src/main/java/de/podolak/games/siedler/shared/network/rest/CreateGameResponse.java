package de.podolak.games.siedler.shared.network.rest;

import de.podolak.games.siedler.shared.model.GameStateSnapshot;

public record CreateGameResponse(
        String gameId,
        String playerId,
        GameStateSnapshot snapshot
) {
}
