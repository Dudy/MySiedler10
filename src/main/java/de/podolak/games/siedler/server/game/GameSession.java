package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.simulation.WorldGenerator;
import de.podolak.games.siedler.server.simulation.WorldSimulator;
import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerColor;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class GameSession {
    private final String gameId;
    private final GameConfig config;
    private final Queue<PlayerCommand> pendingCommands;
    private final List<PlayerState> players;
    private final WorldSimulator simulator;
    private final WorldUpdatePublisher updatePublisher;

    private volatile int tick;
    private volatile String lastJoinedPlayerId;

    private GameSession(
            String gameId,
            GameConfig config,
            Queue<PlayerCommand> pendingCommands,
            List<PlayerState> players,
            WorldSimulator simulator,
            WorldUpdatePublisher updatePublisher
    ) {
        this.gameId = gameId;
        this.config = config;
        this.pendingCommands = pendingCommands;
        this.players = players;
        this.simulator = simulator;
        this.updatePublisher = updatePublisher;
    }

    public static GameSession create(String hostPlayerName, GameConfig config, WorldUpdatePublisher updatePublisher) {
        String gameId = UUID.randomUUID().toString();
        List<PlayerState> players = new ArrayList<>();
        String hostPlayerId = UUID.randomUUID().toString();
        players.add(new PlayerState(hostPlayerId, hostPlayerName, PlayerColor.RED, true));

        WorldSnapshot initialWorld = WorldGenerator.createInitialWorld(config);
        GameSession session = new GameSession(
                gameId,
                config,
                new ConcurrentLinkedQueue<>(),
                players,
                new WorldSimulator(initialWorld),
                updatePublisher
        );
        session.lastJoinedPlayerId = hostPlayerId;
        return session;
    }

    public synchronized GameSession addPlayer(String playerName) {
        if (players.size() >= config.maxPlayers()) {
            throw new IllegalStateException("Session is full");
        }

        String playerId = UUID.randomUUID().toString();
        PlayerColor color = PlayerColor.values()[players.size()];
        players.add(new PlayerState(playerId, playerName, color, true));
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
}
