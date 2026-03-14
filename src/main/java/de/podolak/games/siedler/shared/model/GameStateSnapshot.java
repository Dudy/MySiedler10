package de.podolak.games.siedler.shared.model;

import java.time.Instant;
import java.util.List;

public record GameStateSnapshot(
        String gameId,
        Instant serverTime,
        GameConfig config,
        List<PlayerState> players,
        WorldSnapshot world
) {
}
