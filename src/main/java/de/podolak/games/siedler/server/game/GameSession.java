package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.simulation.WorldGenerator;
import de.podolak.games.siedler.server.simulation.WorldSimulator;
import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerColor;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public final class GameSession {
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
        players.add(new PlayerState(hostPlayerId, hostPlayerName, PlayerColor.RED, true));

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
        players.add(new PlayerState(playerId, playerName, color, true));
        spawnHeadquarters(playerId);
        lastJoinedPlayerId = playerId;
        return this;
    }

    public void enqueue(PlayerCommand command) {
        pendingCommands.add(command);
    }

    public synchronized void advanceOneTick() {
        tick += 1;
        simulator.applyPendingCommands(List.copyOf(pendingCommands));
        pendingCommands.clear();
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
    }
}
