package de.podolak.games.siedler.shared.model;

public record BuildingState(
        String buildingId,
        BuildingType buildingType,
        String ownerPlayerId,
        TileCoordinate coordinate,
        int constructionProgress
) {
}
