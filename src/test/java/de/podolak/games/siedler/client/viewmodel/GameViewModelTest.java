package de.podolak.games.siedler.client.viewmodel;

import de.podolak.games.siedler.client.net.ServerApiClient;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.MapDimensions;
import de.podolak.games.siedler.shared.model.ResourceType;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameViewModelTest {

    @Test
    void estimatedServerSecondCountsForwardBetweenSnapshotsAndCorrectsOnNewSnapshot() {
        AtomicLong nowMillis = new AtomicLong(1_000L);
        GameViewModel viewModel = new GameViewModel(new NoopServerApiClient(), nowMillis::get);

        viewModel.updateSnapshot(snapshotWithServerSecond(42L));
        assertEquals(42L, viewModel.estimatedServerSecond());

        nowMillis.addAndGet(3_500L);
        assertEquals(45L, viewModel.estimatedServerSecond());

        viewModel.updateSnapshot(snapshotWithServerSecond(44L));
        assertEquals(44L, viewModel.estimatedServerSecond());

        nowMillis.addAndGet(2_100L);
        assertEquals(46L, viewModel.estimatedServerSecond());
    }

    @Test
    void estimatedBuildingStateProducesAndRestartsCountdownLocally() {
        AtomicLong nowMillis = new AtomicLong(1_000L);
        GameViewModel viewModel = new GameViewModel(new NoopServerApiClient(), nowMillis::get);
        TileCoordinate coordinate = new TileCoordinate(4, 4);
        viewModel.updateSnapshot(snapshotWithLumberjack(coordinate, 49.0));

        nowMillis.addAndGet(200L);
        BuildingState building = viewModel.estimatedBuildingState(coordinate);

        assertNotNull(building);
        assertEquals(1, building.storedAmount(ResourceType.WOOD));
        assertEquals(0.0, building.productionProgressTicks());

        nowMillis.addAndGet(200L);
        BuildingState nextTickBuilding = viewModel.estimatedBuildingState(coordinate);
        assertNotNull(nextTickBuilding);
        assertEquals(1, nextTickBuilding.storedAmount(ResourceType.WOOD));
        assertEquals(1.0, nextTickBuilding.productionProgressTicks());
    }

    private GameStateSnapshot snapshotWithServerSecond(long serverSecond) {
        return new GameStateSnapshot(
                "game-1",
                Instant.parse("2026-03-29T10:15:30Z"),
                serverSecond,
                new GameConfig(4, 200, new MapDimensions(10, 10)),
                List.of(),
                new WorldSnapshot(0, new MapDimensions(10, 10), List.of(), List.of(), List.of(), List.of())
        );
    }

    private GameStateSnapshot snapshotWithLumberjack(TileCoordinate coordinate, double productionProgressTicks) {
        MapDimensions dimensions = new MapDimensions(9, 9);
        List<TerrainTile> terrainTiles = new java.util.ArrayList<>(dimensions.width() * dimensions.height());
        for (int y = 0; y < dimensions.height(); y++) {
            for (int x = 0; x < dimensions.width(); x++) {
                TerrainType terrainType = TerrainType.GRASSLAND;
                if (!coordinate.equals(new TileCoordinate(x, y)) && hexDistance(coordinate, new TileCoordinate(x, y)) <= 3) {
                    terrainType = TerrainType.FOREST;
                }
                terrainTiles.add(new TerrainTile(new TileCoordinate(x, y), terrainType, 0));
            }
        }

        BuildingState building = new BuildingState(
                "lumberjack-1",
                BuildingType.LUMBERJACK,
                "player-1",
                coordinate,
                100,
                null,
                productionProgressTicks
        );

        return new GameStateSnapshot(
                "game-1",
                Instant.parse("2026-03-29T10:15:30Z"),
                42L,
                new GameConfig(4, 200, dimensions),
                List.of(),
                new WorldSnapshot(0, dimensions, terrainTiles, List.of(), List.of(building), List.of())
        );
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

    private static final class NoopServerApiClient implements ServerApiClient {
        @Override
        public CreateGameResponse createGame() {
            return null;
        }

        @Override
        public JoinGameResponse joinGame(String gameId) {
            return null;
        }

        @Override
        public void submit(PlayerCommand command) {
        }
    }
}
