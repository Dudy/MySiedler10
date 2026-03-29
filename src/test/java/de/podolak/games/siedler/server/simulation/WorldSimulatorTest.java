package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingProductionRules;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.MapDimensions;
import de.podolak.games.siedler.shared.model.ResourceType;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldSimulatorTest {

    @Test
    void lumberjackProducesWoodAfterFiftyTicksAtFullCoverage() {
        TileCoordinate center = new TileCoordinate(4, 4);
        WorldSimulator simulator = new WorldSimulator(worldWithLumberjackCoverage(center, 36));
        simulator.addBuilding(new BuildingState("lumberjack-1", BuildingType.LUMBERJACK, "player-1", center, 100));

        boolean producedResources = false;
        for (int tick = 1; tick <= 50; tick++) {
            producedResources = simulator.advanceTick(tick);
        }

        BuildingState lumberjack = simulator.snapshot().buildings().getFirst();
        assertEquals(1, lumberjack.storedAmount(ResourceType.WOOD));
        assertEquals(true, producedResources);
    }

    @Test
    void lumberjackNeedsDoubleTimeAtHalfCoverage() {
        TileCoordinate center = new TileCoordinate(4, 4);
        WorldSimulator simulator = new WorldSimulator(worldWithLumberjackCoverage(center, 18));
        simulator.addBuilding(new BuildingState("lumberjack-1", BuildingType.LUMBERJACK, "player-1", center, 100));

        for (int tick = 1; tick <= 99; tick++) {
            simulator.advanceTick(tick);
        }
        assertEquals(0, simulator.snapshot().buildings().getFirst().storedAmount(ResourceType.WOOD));

        simulator.advanceTick(100);
        assertEquals(1, simulator.snapshot().buildings().getFirst().storedAmount(ResourceType.WOOD));
    }

    @Test
    void remainingTicksUsesCurrentCoverageAndProgress() {
        TileCoordinate center = new TileCoordinate(4, 4);
        WorldSnapshot world = worldWithLumberjackCoverage(center, 18);
        BuildingState building = new BuildingState(
                "lumberjack-1",
                BuildingType.LUMBERJACK,
                "player-1",
                center,
                100,
                null,
                10.0
        );

        assertEquals(18, BuildingProductionRules.coveredSourceTiles(world, building));
        assertEquals(80, BuildingProductionRules.remainingTicksUntilNextUnit(world, building).orElseThrow());
    }

    private WorldSnapshot worldWithLumberjackCoverage(TileCoordinate center, int forestTileCount) {
        MapDimensions dimensions = new MapDimensions(9, 9);
        List<TileCoordinate> influenceTiles = influenceTiles(center, dimensions);
        List<TerrainTile> terrainTiles = new ArrayList<>(dimensions.width() * dimensions.height());

        for (int y = 0; y < dimensions.height(); y++) {
            for (int x = 0; x < dimensions.width(); x++) {
                TileCoordinate coordinate = new TileCoordinate(x, y);
                TerrainType terrainType = TerrainType.GRASSLAND;
                if (influenceTiles.indexOf(coordinate) >= 0 && influenceTiles.indexOf(coordinate) < forestTileCount) {
                    terrainType = TerrainType.FOREST;
                }
                terrainTiles.add(new TerrainTile(coordinate, terrainType, 0));
            }
        }

        return new WorldSnapshot(0, dimensions, terrainTiles, List.of(), List.of(), List.of());
    }

    private List<TileCoordinate> influenceTiles(TileCoordinate center, MapDimensions dimensions) {
        List<TileCoordinate> coordinates = new ArrayList<>();
        int radius = 3;
        int minX = Math.max(0, center.x() - radius);
        int maxX = Math.min(dimensions.width() - 1, center.x() + radius);
        int minY = Math.max(0, center.y() - radius);
        int maxY = Math.min(dimensions.height() - 1, center.y() + radius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TileCoordinate candidate = new TileCoordinate(x, y);
                if (!candidate.equals(center) && hexDistance(center, candidate) <= radius) {
                    coordinates.add(candidate);
                }
            }
        }
        return coordinates;
    }

    private int hexDistance(TileCoordinate a, TileCoordinate b) {
        CubeCoordinate cubeA = toCube(a);
        CubeCoordinate cubeB = toCube(b);
        return Math.max(
                Math.abs(cubeA.x - cubeB.x),
                Math.max(Math.abs(cubeA.y - cubeB.y), Math.abs(cubeA.z - cubeB.z))
        );
    }

    private CubeCoordinate toCube(TileCoordinate coordinate) {
        int q = coordinate.x() - ((coordinate.y() - (coordinate.y() & 1)) / 2);
        int z = coordinate.y();
        int x = q;
        int y = -x - z;
        return new CubeCoordinate(x, y, z);
    }

    private record CubeCoordinate(int x, int y, int z) {
    }
}
