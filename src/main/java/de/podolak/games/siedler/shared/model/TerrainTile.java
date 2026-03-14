package de.podolak.games.siedler.shared.model;

public record TerrainTile(
        TileCoordinate coordinate,
        TerrainType terrainType,
        int height
) {
}
