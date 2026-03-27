package de.podolak.games.siedler.shared.model;

import java.util.List;

public record RoadState(
        String roadId,
        String ownerPlayerId,
        List<RoadVertexCoordinate> path
) {
}
