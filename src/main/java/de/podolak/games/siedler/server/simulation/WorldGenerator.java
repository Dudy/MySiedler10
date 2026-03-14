package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.MapDimensions;
import de.podolak.games.siedler.shared.model.ResourceNodeState;
import de.podolak.games.siedler.shared.model.ResourceType;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.WorldSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class WorldGenerator {
    private WorldGenerator() {
    }

    public static WorldSnapshot createInitialWorld(GameConfig config) {
        MapDimensions dimensions = config.mapDimensions();
        List<TerrainTile> tiles = new ArrayList<>(dimensions.width() * dimensions.height());

        for (int y = 0; y < dimensions.height(); y++) {
            for (int x = 0; x < dimensions.width(); x++) {
                tiles.add(new TerrainTile(new TileCoordinate(x, y), terrainFor(x, y), (x + y) % 3));
            }
        }

        List<ResourceNodeState> nodes = List.of(
                new ResourceNodeState("wood-1", ResourceType.WOOD, new TileCoordinate(4, 5), 800),
                new ResourceNodeState("stone-1", ResourceType.STONE, new TileCoordinate(12, 6), 600),
                new ResourceNodeState("coal-1", ResourceType.COAL, new TileCoordinate(18, 10), 400)
        );

        return new WorldSnapshot(0, dimensions, tiles, nodes, List.of(), List.of());
    }

    public static List<TileCoordinate> createStartPositions(MapDimensions dimensions) {
        int marginX = Math.max(2, dimensions.width() / 10);
        int marginY = Math.max(2, dimensions.height() / 10);
        int maxX = dimensions.width() - 1;
        int maxY = dimensions.height() - 1;

        return List.of(
                new TileCoordinate(marginX, marginY),
                new TileCoordinate(Math.max(0, maxX - marginX), marginY),
                new TileCoordinate(marginX, Math.max(0, maxY - marginY)),
                new TileCoordinate(Math.max(0, maxX - marginX), Math.max(0, maxY - marginY))
        );
    }

    private static TerrainType terrainFor(int x, int y) {
        if (x == 0 || y == 0 || x % 11 == 0) {
            return TerrainType.WATER;
        }
        if ((x + y) % 9 == 0) {
            return TerrainType.MOUNTAIN;
        }
        if ((x * y) % 7 == 0) {
            return TerrainType.FOREST;
        }
        if ((x + y) % 13 == 0) {
            return TerrainType.SAND;
        }
        return TerrainType.GRASSLAND;
    }
}
