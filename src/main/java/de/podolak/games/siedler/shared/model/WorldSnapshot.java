package de.podolak.games.siedler.shared.model;

import java.util.List;

public record WorldSnapshot(
        int tick,
        MapDimensions dimensions,
        List<TerrainTile> terrainTiles,
        List<ResourceNodeState> resourceNodes,
        List<BuildingState> buildings,
        List<RoadState> roads
) {
}
