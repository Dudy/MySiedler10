package de.podolak.games.siedler.client.viewmodel;

import de.podolak.games.siedler.client.net.ServerApiClient;
import de.podolak.games.siedler.shared.command.CommandType;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingProductionRules;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.RoadRules;
import de.podolak.games.siedler.shared.model.RoadState;
import de.podolak.games.siedler.shared.model.RoadVertexCoordinate;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

@Component
public final class GameViewModel {
    private static final Logger log = LoggerFactory.getLogger(GameViewModel.class);

    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private final ServerApiClient serverApiClient;
    private final LongSupplier currentTimeMillisSupplier;
    private volatile GameStateSnapshot snapshot;
    private volatile String localPlayerId;
    private volatile long lastSnapshotServerSecond;
    private volatile long lastSnapshotReceivedAtMillis;

    @Autowired
    public GameViewModel(ServerApiClient serverApiClient) {
        this(serverApiClient, System::currentTimeMillis);
    }

    GameViewModel(ServerApiClient serverApiClient, LongSupplier currentTimeMillisSupplier) {
        this.serverApiClient = serverApiClient;
        this.currentTimeMillisSupplier = currentTimeMillisSupplier;
    }

    public GameStateSnapshot snapshot() {
        return snapshot;
    }

    public void updateSnapshot(GameStateSnapshot snapshot) {
        GameStateSnapshot oldSnapshot = this.snapshot;
        this.snapshot = snapshot;
        if (snapshot != null) {
            lastSnapshotServerSecond = snapshot.serverSecond();
            lastSnapshotReceivedAtMillis = currentTimeMillisSupplier.getAsLong();
            int tick = snapshot.world() == null ? -1 : snapshot.world().tick();
            int players = snapshot.players() == null ? -1 : snapshot.players().size();
            int buildings = snapshot.world() == null || snapshot.world().buildings() == null
                    ? -1
                    : snapshot.world().buildings().size();
            log.debug(
                    "Snapshot updated gameId={} tick={} serverSecond={} players={} buildings={}",
                    snapshot.gameId(),
                    tick,
                    snapshot.serverSecond(),
                    players,
                    buildings
            );
        }
        propertyChangeSupport.firePropertyChange("snapshot", oldSnapshot, snapshot);
    }

    public void addSnapshotListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener("snapshot", listener);
    }

    public String localPlayerId() {
        return localPlayerId;
    }

    public long estimatedServerSecond() {
        GameStateSnapshot activeSnapshot = snapshot;
        if (activeSnapshot == null) {
            return -1L;
        }
        long elapsedMillis = Math.max(0L, currentTimeMillisSupplier.getAsLong() - lastSnapshotReceivedAtMillis);
        return lastSnapshotServerSecond + (elapsedMillis / 1000L);
    }

    public BuildingState estimatedBuildingState(TileCoordinate coordinate) {
        GameStateSnapshot activeSnapshot = snapshot;
        if (activeSnapshot == null || activeSnapshot.world() == null || coordinate == null) {
            return null;
        }

        BuildingState building = buildingAt(activeSnapshot.world(), coordinate);
        if (building == null) {
            return null;
        }

        int elapsedTicks = elapsedTicksSinceSnapshot(activeSnapshot);
        if (elapsedTicks <= 0) {
            return building;
        }
        return BuildingProductionRules.advanceBuilding(activeSnapshot.world(), building, elapsedTicks);
    }

    public void setLocalPlayerId(String localPlayerId) {
        this.localPlayerId = localPlayerId;
        log.info("Local player id set to {}", localPlayerId);
    }

    public boolean placeBuilding(BuildingType buildingType, TileCoordinate coordinate) {
        if (buildingType == null || coordinate == null) {
            log.warn("placeBuilding ignored reason=missing_input type={} coordinate={}", buildingType, coordinate);
            return false;
        }
        GameStateSnapshot activeSnapshot = snapshot;
        String playerId = localPlayerId;
        if (activeSnapshot == null || playerId == null || playerId.isBlank()) {
            log.warn(
                    "placeBuilding ignored reason=missing_context hasSnapshot={} playerId={}",
                    activeSnapshot != null,
                    playerId
            );
            return false;
        }
        log.info(
                "placeBuilding start gameId={} playerId={} type={} at={},{}",
                activeSnapshot.gameId(),
                playerId,
                buildingType,
                coordinate.x(),
                coordinate.y()
        );
        if (!canPlaceBuilding(activeSnapshot, playerId, buildingType, coordinate)) {
            log.info(
                    "placeBuilding local validation failed gameId={} playerId={} type={} at={},{}",
                    activeSnapshot.gameId(),
                    playerId,
                    buildingType,
                    coordinate.x(),
                    coordinate.y()
            );
            return false;
        }

        PlayerCommand command = new PlayerCommand(
                UUID.randomUUID().toString(),
                activeSnapshot.gameId(),
                playerId,
                CommandType.PLACE_BUILDING,
                Map.of(
                        "buildingType", buildingType.name(),
                        "x", Integer.toString(coordinate.x()),
                        "y", Integer.toString(coordinate.y())
                ),
                Instant.now()
        );
        updateSnapshot(createOptimisticSnapshot(activeSnapshot, playerId, buildingType, coordinate));
        log.info(
                "placeBuilding optimistic update applied gameId={} playerId={} type={} at={},{}",
                activeSnapshot.gameId(),
                playerId,
                buildingType,
                coordinate.x(),
                coordinate.y()
        );
        serverApiClient.submit(command);
        log.info("placeBuilding command submitted commandId={}", command.commandId());
        return true;
    }

    public boolean buildRoad(List<RoadVertexCoordinate> path) {
        if (path == null || path.size() < 2) {
            log.warn("buildRoad ignored reason=missing_or_short_path");
            return false;
        }
        GameStateSnapshot activeSnapshot = snapshot;
        String playerId = localPlayerId;
        if (activeSnapshot == null || playerId == null || playerId.isBlank()) {
            log.warn(
                    "buildRoad ignored reason=missing_context hasSnapshot={} playerId={}",
                    activeSnapshot != null,
                    playerId
            );
            return false;
        }
        List<RoadVertexCoordinate> normalizedPath = List.copyOf(path);
        if (!RoadRules.isValidRoadPath(activeSnapshot.world().dimensions(), activeSnapshot.world().roads(), normalizedPath)) {
            log.info(
                    "buildRoad local validation failed gameId={} playerId={} vertices={}",
                    activeSnapshot.gameId(),
                    playerId,
                    normalizedPath.size()
            );
            return false;
        }

        PlayerCommand command = new PlayerCommand(
                UUID.randomUUID().toString(),
                activeSnapshot.gameId(),
                playerId,
                CommandType.BUILD_ROAD,
                encodeRoadPayload(normalizedPath),
                Instant.now()
        );
        updateSnapshot(createOptimisticRoadSnapshot(activeSnapshot, playerId, normalizedPath));
        log.info(
                "buildRoad optimistic update applied gameId={} playerId={} vertices={}",
                activeSnapshot.gameId(),
                playerId,
                normalizedPath.size()
        );
        serverApiClient.submit(command);
        log.info("buildRoad command submitted commandId={}", command.commandId());
        return true;
    }

    private boolean canPlaceBuilding(
            GameStateSnapshot activeSnapshot,
            String playerId,
            BuildingType buildingType,
            TileCoordinate coordinate
    ) {
        if (!BuildingRules.canBePlacedByPlayer(buildingType)) {
            log.info("Local validation failed reason=forbidden_type type={}", buildingType);
            return false;
        }
        if (coordinate.x() < 0 || coordinate.y() < 0
                || coordinate.x() >= activeSnapshot.world().dimensions().width()
                || coordinate.y() >= activeSnapshot.world().dimensions().height()) {
            log.info("Local validation failed reason=out_of_bounds at={},{}", coordinate.x(), coordinate.y());
            return false;
        }
        for (BuildingState building : activeSnapshot.world().buildings()) {
            if (building.coordinate().x() == coordinate.x() && building.coordinate().y() == coordinate.y()) {
                log.info(
                        "Local validation failed reason=occupied at={},{} existingType={} owner={}",
                        coordinate.x(),
                        coordinate.y(),
                        building.buildingType(),
                        building.ownerPlayerId()
                );
                return false;
            }
        }

        TerrainType terrainType = terrainTypeAt(activeSnapshot.world(), coordinate.x(), coordinate.y());
        if (!BuildingRules.canBePlacedOnTerrain(buildingType, terrainType)) {
            log.info(
                    "Local validation failed reason=terrain_mismatch type={} terrain={} at={},{}",
                    buildingType,
                    terrainType,
                    coordinate.x(),
                    coordinate.y()
            );
            return false;
        }

        PlayerState player = localPlayer(activeSnapshot, playerId);
        if (player == null) {
            log.info("Local validation failed reason=unknown_player playerId={}", playerId);
            return false;
        }
        boolean enoughResources = player.wood() >= BuildingRules.woodCost(buildingType)
                && player.stone() >= BuildingRules.stoneCost(buildingType);
        if (!enoughResources) {
            log.info(
                    "Local validation failed reason=insufficient_resources needWood={} needStone={} haveWood={} haveStone={}",
                    BuildingRules.woodCost(buildingType),
                    BuildingRules.stoneCost(buildingType),
                    player.wood(),
                    player.stone()
            );
        }
        return enoughResources;
    }

    private GameStateSnapshot createOptimisticSnapshot(
            GameStateSnapshot activeSnapshot,
            String playerId,
            BuildingType buildingType,
            TileCoordinate coordinate
    ) {
        List<PlayerState> players = new ArrayList<>(activeSnapshot.players());
        for (int i = 0; i < players.size(); i++) {
            PlayerState candidate = players.get(i);
            if (!playerId.equals(candidate.playerId())) {
                continue;
            }
            players.set(i, new PlayerState(
                    candidate.playerId(),
                    candidate.displayName(),
                    candidate.color(),
                    candidate.connected(),
                    candidate.wood() - BuildingRules.woodCost(buildingType),
                    candidate.stone() - BuildingRules.stoneCost(buildingType)
            ));
            break;
        }

        List<BuildingState> buildings = new ArrayList<>(activeSnapshot.world().buildings());
        buildings.add(new BuildingState(
                "pending-" + UUID.randomUUID(),
                buildingType,
                playerId,
                coordinate,
                100
        ));

        WorldSnapshot world = activeSnapshot.world();
        WorldSnapshot updatedWorld = new WorldSnapshot(
                world.tick(),
                world.dimensions(),
                world.terrainTiles(),
                world.resourceNodes(),
                List.copyOf(buildings),
                world.roads()
        );

        return new GameStateSnapshot(
                activeSnapshot.gameId(),
                activeSnapshot.serverTime(),
                activeSnapshot.serverSecond(),
                activeSnapshot.config(),
                List.copyOf(players),
                updatedWorld
        );
    }

    private GameStateSnapshot createOptimisticRoadSnapshot(
            GameStateSnapshot activeSnapshot,
            String playerId,
            List<RoadVertexCoordinate> path
    ) {
        List<RoadState> roads = new ArrayList<>(activeSnapshot.world().roads());
        roads.add(new RoadState(
                "pending-road-" + UUID.randomUUID(),
                playerId,
                path
        ));

        WorldSnapshot world = activeSnapshot.world();
        WorldSnapshot updatedWorld = new WorldSnapshot(
                world.tick(),
                world.dimensions(),
                world.terrainTiles(),
                world.resourceNodes(),
                world.buildings(),
                List.copyOf(roads)
        );

        return new GameStateSnapshot(
                activeSnapshot.gameId(),
                activeSnapshot.serverTime(),
                activeSnapshot.serverSecond(),
                activeSnapshot.config(),
                activeSnapshot.players(),
                updatedWorld
        );
    }

    private Map<String, String> encodeRoadPayload(List<RoadVertexCoordinate> path) {
        Map<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("vertexCount", Integer.toString(path.size()));
        for (int i = 0; i < path.size(); i++) {
            RoadVertexCoordinate vertex = path.get(i);
            payload.put("vx" + i, Integer.toString(vertex.x()));
            payload.put("vy" + i, Integer.toString(vertex.y()));
        }
        return Map.copyOf(payload);
    }

    private PlayerState localPlayer(GameStateSnapshot activeSnapshot, String playerId) {
        for (PlayerState playerState : activeSnapshot.players()) {
            if (playerId.equals(playerState.playerId())) {
                return playerState;
            }
        }
        return null;
    }

    private TerrainType terrainTypeAt(WorldSnapshot world, int x, int y) {
        int index = y * world.dimensions().width() + x;
        if (index >= 0 && index < world.terrainTiles().size()) {
            TerrainTile tile = world.terrainTiles().get(index);
            if (tile.coordinate().x() == x && tile.coordinate().y() == y) {
                return tile.terrainType();
            }
        }
        for (TerrainTile tile : world.terrainTiles()) {
            if (tile.coordinate().x() == x && tile.coordinate().y() == y) {
                return tile.terrainType();
            }
        }
        return TerrainType.GRASSLAND;
    }

    private int elapsedTicksSinceSnapshot(GameStateSnapshot activeSnapshot) {
        if (activeSnapshot.config() == null || activeSnapshot.config().tickDurationMillis() <= 0) {
            return 0;
        }
        long elapsedMillis = Math.max(0L, currentTimeMillisSupplier.getAsLong() - lastSnapshotReceivedAtMillis);
        return (int) (elapsedMillis / activeSnapshot.config().tickDurationMillis());
    }

    private BuildingState buildingAt(WorldSnapshot world, TileCoordinate coordinate) {
        for (BuildingState building : world.buildings()) {
            if (building.coordinate().equals(coordinate)) {
                return building;
            }
        }
        return null;
    }
}
