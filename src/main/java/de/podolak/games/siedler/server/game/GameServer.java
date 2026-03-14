package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.simulation.SimulationTicker;
import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.model.GameConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GameServer {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final WorldUpdatePublisher updatePublisher;

    public GameServer(WorldUpdatePublisher updatePublisher) {
        this.updatePublisher = updatePublisher;
    }

    public void start() {
        System.out.println("Server ready for dedicated REST and WebSocket transport.");
    }

    public GameSession createSession(String hostPlayerName, GameConfig config) {
        GameSession session = GameSession.create(hostPlayerName, config, updatePublisher);
        sessions.put(session.gameId(), session);
        new SimulationTicker(session).start();
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
