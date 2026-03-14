package de.podolak.games.siedler.shared.network.ws;

import de.podolak.games.siedler.shared.model.GameStateSnapshot;

public record TickUpdateMessage(
        String gameId,
        int tick,
        GameStateSnapshot snapshot
) {
}
