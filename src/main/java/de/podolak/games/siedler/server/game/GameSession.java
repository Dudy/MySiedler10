package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.simulation.WorldGenerator;
import de.podolak.games.siedler.server.simulation.WorldSimulator;
import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerColor;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public final class GameSession {
    private static final Logger log = LoggerFactory.getLogger(GameSession.class);

    private final String gameId;
    private final GameConfig config;
    private final Queue<PlayerCommand> pendingCommands;
    private final List<PlayerState> players;
    private final List<TileCoordinate> availableStartPositions;
    private final WorldSimulator simulator;
    private final WorldUpdatePublisher updatePublisher;

    private volatile int tick;
    private volatile String lastJoinedPlayerId;

    private GameSession(
            String gameId,
            GameConfig config,
            Queue<PlayerCommand> pendingCommands,
            List<PlayerState> players,
            List<TileCoordinate> availableStartPositions,
            WorldSimulator simulator,
            WorldUpdatePublisher updatePublisher
    ) {
        this.gameId = gameId;
        this.config = config;
        this.pendingCommands = pendingCommands;
        this.players = players;
        this.availableStartPositions = availableStartPositions;
        this.simulator = simulator;
        this.updatePublisher = updatePublisher;
    }

    public static GameSession create(String hostPlayerName, GameConfig config, WorldUpdatePublisher updatePublisher) {
        String gameId = UUID.randomUUID().toString();
        List<PlayerState> players = new ArrayList<>();
        String hostPlayerId = UUID.randomUUID().toString();
        players.add(new PlayerState(hostPlayerId, hostPlayerName, PlayerColor.RED, true, 10, 10));

        WorldSnapshot initialWorld = WorldGenerator.createInitialWorld(config);
        List<TileCoordinate> availableStartPositions = new ArrayList<>(WorldGenerator.createStartPositions(config.mapDimensions()));
        Collections.shuffle(availableStartPositions, ThreadLocalRandom.current());
        GameSession session = new GameSession(
                gameId,
                config,
                new ConcurrentLinkedQueue<>(),
                players,
                availableStartPositions,
                new WorldSimulator(initialWorld),
                updatePublisher
        );
        session.spawnHeadquarters(hostPlayerId);
        session.lastJoinedPlayerId = hostPlayerId;
        return session;
    }

    public synchronized GameSession addPlayer(String playerName) {
        if (players.size() >= config.maxPlayers() || availableStartPositions.isEmpty()) {
            throw new IllegalStateException("Session is full");
        }

        String playerId = UUID.randomUUID().toString();
        PlayerColor color = PlayerColor.values()[players.size()];
        players.add(new PlayerState(playerId, playerName, color, true, 10, 10));
        spawnHeadquarters(playerId);
        lastJoinedPlayerId = playerId;
        log.info("Player added gameId={} playerId={} name={} color={}", gameId, playerId, playerName, color);
        return this;
    }

    public void enqueue(PlayerCommand command) {
        if (command == null) {
            log.warn("Ignoring null command for gameId={}", gameId);
            return;
        }
        pendingCommands.add(command);
        log.info(
                "Queued command gameId={} commandId={} playerId={} type={} payload={}",
                gameId,
                command.commandId(),
                command.playerId(),
                command.commandType(),
                command.payload()
        );
    }

    public synchronized void advanceOneTick() {
        tick += 1;
        List<PlayerCommand> commands = List.copyOf(pendingCommands);
        pendingCommands.clear();
        if (!commands.isEmpty()) {
            log.info("Tick gameId={} tick={} processingCommands={}", gameId, tick, commands.size());
        } else {
            log.debug("Tick gameId={} tick={} processingCommands=0", gameId, tick);
        }
        applyPendingCommands(commands);
        simulator.advanceTick(tick);
        updatePublisher.publish(new TickUpdateMessage(gameId, tick, snapshot()));
    }

    public String gameId() {
        return gameId;
    }

    public String hostPlayerId() {
        return players.getFirst().playerId();
    }

    public String lastJoinedPlayerId() {
        return lastJoinedPlayerId;
    }

    public GameStateSnapshot snapshot() {
        return new GameStateSnapshot(gameId, Instant.now(), config, List.copyOf(players), simulator.snapshot());
    }

    public GameConfig config() {
        return config;
    }

    private void spawnHeadquarters(String playerId) {
        if (availableStartPositions.isEmpty()) {
            throw new IllegalStateException("No start positions left");
        }

        TileCoordinate start = availableStartPositions.removeFirst();
        BuildingState headquarters = new BuildingState(
                "hq-" + playerId,
                BuildingType.HEADQUARTERS,
                playerId,
                start,
                100
        );
        simulator.addBuilding(headquarters);
        log.info("Spawned headquarters gameId={} playerId={} at={},{}", gameId, playerId, start.x(), start.y());
    }

    private void applyPendingCommands(List<PlayerCommand> commands) {
        for (PlayerCommand command : commands) {
            if (command == null) {
                log.warn("Ignoring null pending command in gameId={}", gameId);
                continue;
            }
            if (command.commandType() == null || command.playerId() == null) {
                continue;
            }
            if (command.commandType() == de.podolak.games.siedler.shared.command.CommandType.PLACE_BUILDING) {
                handlePlaceBuilding(command);
            }
        }
    }

    private void handlePlaceBuilding(PlayerCommand command) {
        if (command.payload() == null) {
            log.warn("PLACE_BUILDING ignored gameId={} reason=missing_payload commandId={}", gameId, command.commandId());
            return;
        }
        BuildingType buildingType = parseBuildingType(command.payload().get("buildingType"));
        Integer x = parseCoordinate(command.payload().get("x"));
        Integer y = parseCoordinate(command.payload().get("y"));
        if (buildingType == null || x == null || y == null) {
            log.warn(
                    "PLACE_BUILDING rejected gameId={} playerId={} commandId={} reason=invalid_payload payload={}",
                    gameId,
                    command.playerId(),
                    command.commandId(),
                    command.payload()
            );
            return;
        }
        log.info(
                "PLACE_BUILDING start gameId={} playerId={} commandId={} type={} at={},{}",
                gameId,
                command.playerId(),
                command.commandId(),
                buildingType,
                x,
                y
        );
        if (!BuildingRules.canBePlacedByPlayer(buildingType)) {
            log.warn(
                "PLACE_BUILDING rejected gameId={} playerId={} commandId={} reason=forbidden_building_type type={}",
                gameId,
                command.playerId(),
                command.commandId(),
                buildingType
            );
            return;
        }

        TileCoordinate coordinate = new TileCoordinate(x, y);
        if (!isValidBuildTile(buildingType, coordinate)) {
            log.warn(
                    "PLACE_BUILDING rejected gameId={} playerId={} commandId={} reason=invalid_tile type={} at={},{}",
                    gameId,
                    command.playerId(),
                    command.commandId(),
                    buildingType,
                    x,
                    y
            );
            return;
        }

        int playerIndex = indexOfPlayer(command.playerId());
        if (playerIndex < 0) {
            log.warn(
                    "PLACE_BUILDING rejected gameId={} playerId={} commandId={} reason=unknown_player",
                    gameId,
                    command.playerId(),
                    command.commandId()
            );
            return;
        }

        PlayerState player = players.get(playerIndex);
        int woodCost = BuildingRules.woodCost(buildingType);
        int stoneCost = BuildingRules.stoneCost(buildingType);
        if (player.wood() < woodCost || player.stone() < stoneCost) {
            log.warn(
                    "PLACE_BUILDING rejected gameId={} playerId={} commandId={} reason=insufficient_resources needWood={} needStone={} haveWood={} haveStone={}",
                    gameId,
                    command.playerId(),
                    command.commandId(),
                    woodCost,
                    stoneCost,
                    player.wood(),
                    player.stone()
            );
            return;
        }

        players.set(playerIndex, new PlayerState(
                player.playerId(),
                player.displayName(),
                player.color(),
                player.connected(),
                player.wood() - woodCost,
                player.stone() - stoneCost
        ));

        simulator.addBuilding(new BuildingState(
                "building-" + UUID.randomUUID(),
                buildingType,
                player.playerId(),
                coordinate,
                100
        ));
        log.info(
                "PLACE_BUILDING success gameId={} playerId={} commandId={} type={} at={},{} wood={} stone={}",
                gameId,
                player.playerId(),
                command.commandId(),
                buildingType,
                coordinate.x(),
                coordinate.y(),
                player.wood() - woodCost,
                player.stone() - stoneCost
        );
    }

    private int indexOfPlayer(String playerId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).playerId().equals(playerId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isValidBuildTile(BuildingType buildingType, TileCoordinate coordinate) {
        WorldSnapshot world = simulator.snapshot();
        int x = coordinate.x();
        int y = coordinate.y();
        if (x < 0 || y < 0 || x >= world.dimensions().width() || y >= world.dimensions().height()) {
            log.info("Tile invalid gameId={} reason=out_of_bounds at={},{}", gameId, x, y);
            return false;
        }
        for (BuildingState building : world.buildings()) {
            if (building.coordinate().x() == x && building.coordinate().y() == y) {
                log.info(
                        "Tile invalid gameId={} reason=occupied at={},{} existingType={} owner={}",
                        gameId,
                        x,
                        y,
                        building.buildingType(),
                        building.ownerPlayerId()
                );
                return false;
            }
        }
        TerrainType terrainType = terrainTypeAt(world, x, y);
        boolean allowed = BuildingRules.canBePlacedOnTerrain(buildingType, terrainType);
        if (!allowed) {
            log.info(
                    "Tile invalid gameId={} reason=terrain_mismatch at={},{} type={} terrain={}",
                    gameId,
                    x,
                    y,
                    buildingType,
                    terrainType
            );
        }
        return allowed;
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

    private Integer parseCoordinate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BuildingType parseBuildingType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return BuildingType.valueOf(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
