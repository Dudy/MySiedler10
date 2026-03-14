package de.podolak.games.siedler.shared.model;

public record ResourceNodeState(
        String nodeId,
        ResourceType resourceType,
        TileCoordinate coordinate,
        int remainingAmount
) {
}
