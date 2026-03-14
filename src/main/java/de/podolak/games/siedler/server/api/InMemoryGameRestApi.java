package de.podolak.games.siedler.server.api;

import de.podolak.games.siedler.server.game.GameServer;
import de.podolak.games.siedler.server.game.GameSession;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.network.rest.CreateGameRequest;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameRequest;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import de.podolak.games.siedler.shared.network.rest.LobbySnapshotResponse;
import de.podolak.games.siedler.shared.network.rest.SubmitCommandRequest;

public final class InMemoryGameRestApi implements GameRestApi {
    private final GameServer gameServer;

    public InMemoryGameRestApi(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    @Override
    public CreateGameResponse createGame(CreateGameRequest request) {
        GameConfig config = request.config() == null ? GameConfig.defaultConfig() : request.config();
        GameSession session = gameServer.createSession(request.hostPlayerName(), config);
        return new CreateGameResponse(session.gameId(), session.hostPlayerId(), session.snapshot());
    }

    @Override
    public JoinGameResponse joinGame(String gameId, JoinGameRequest request) {
        GameSession session = gameServer.joinSession(gameId, request.playerName());
        return new JoinGameResponse(session.gameId(), session.lastJoinedPlayerId(), session.snapshot());
    }

    @Override
    public LobbySnapshotResponse getSnapshot(String gameId) {
        return new LobbySnapshotResponse(gameServer.requireSession(gameId).snapshot());
    }

    @Override
    public void submitCommand(String gameId, SubmitCommandRequest request) {
        PlayerCommand command = request.command();
        gameServer.requireSession(gameId).enqueue(command);
    }
}
