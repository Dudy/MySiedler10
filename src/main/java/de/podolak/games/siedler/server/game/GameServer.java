package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.model.GameConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public final class GameServer {
    private static final Logger log = LoggerFactory.getLogger(GameServer.class);

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final WorldUpdatePublisher updatePublisher;
    private final TaskScheduler taskScheduler;

    public GameServer(WorldUpdatePublisher updatePublisher, TaskScheduler taskScheduler) {
        this.updatePublisher = updatePublisher;
        this.taskScheduler = taskScheduler;
    }

    public void start() {
        log.info("Server ready for dedicated REST and WebSocket transport.");
    }

    public GameSession createSession(String hostPlayerName, GameConfig config) {
        GameSession session = GameSession.create(hostPlayerName, config, updatePublisher);
        sessions.put(session.gameId(), session);
        log.info(
                "Created session gameId={} host={} map={}x{} tickMs={} maxPlayers={}",
                session.gameId(),
                hostPlayerName,
                config.mapDimensions().width(),
                config.mapDimensions().height(),
                config.tickDurationMillis(),
                config.maxPlayers()
        );
        taskScheduler.scheduleAtFixedRate(session::advanceOneTick, Duration.ofMillis(config.tickDurationMillis()));
        log.info("Scheduled tick loop for gameId={}", session.gameId());
        return session;
    }

    public GameSession joinSession(String gameId, String playerName) {
        log.info("Join request gameId={} player={}", gameId, playerName);
        GameSession session = requireSession(gameId).addPlayer(playerName);
        log.info("Player joined gameId={} player={} playersNow={}", gameId, playerName, session.snapshot().players().size());
        return session;
    }

    public GameSession requireSession(String gameId) {
        GameSession session = sessions.get(gameId);
        if (session == null) {
            log.warn("Unknown gameId requested: {}", gameId);
            throw new IllegalArgumentException("Unknown game id: " + gameId);
        }
        return session;
    }
}
