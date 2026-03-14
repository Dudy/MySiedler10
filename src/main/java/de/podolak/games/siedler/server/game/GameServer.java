package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.model.GameConfig;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class GameServer {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final WorldUpdatePublisher updatePublisher;
    private final TaskScheduler taskScheduler;

    public GameServer(WorldUpdatePublisher updatePublisher, TaskScheduler taskScheduler) {
        this.updatePublisher = updatePublisher;
        this.taskScheduler = taskScheduler;
    }

    public void start() {
        System.out.println("Server ready for dedicated REST and WebSocket transport.");
    }

    public GameSession createSession(String hostPlayerName, GameConfig config) {
        GameSession session = GameSession.create(hostPlayerName, config, updatePublisher);
        sessions.put(session.gameId(), session);
        taskScheduler.scheduleAtFixedRate(session::advanceOneTick, Duration.ofMillis(config.tickDurationMillis()));
        return session;
    }

    public GameSession joinSession(String gameId, String playerName) {
        return requireSession(gameId).addPlayer(playerName);
    }

    public GameSession requireSession(String gameId) {
        GameSession session = sessions.get(gameId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown game id: " + gameId);
        }
        return session;
    }
}
