package de.podolak.games.siedler.shared.network.rest;

import de.podolak.games.siedler.shared.model.GameConfig;

public record CreateGameRequest(
        String hostPlayerName,
        GameConfig config
) {
}
