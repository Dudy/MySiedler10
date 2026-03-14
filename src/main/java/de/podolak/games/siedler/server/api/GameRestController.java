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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public final class GameRestController {
    private final GameServer gameServer;

    public GameRestController(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    @PostMapping
    public CreateGameResponse createGame(@RequestBody CreateGameRequest request) {
        GameConfig config = request.config() == null ? GameConfig.defaultConfig() : request.config();
        GameSession session = gameServer.createSession(request.hostPlayerName(), config);
        return new CreateGameResponse(session.gameId(), session.hostPlayerId(), session.snapshot());
    }

    @PostMapping("/{gameId}/players")
    public JoinGameResponse joinGame(@PathVariable String gameId, @RequestBody JoinGameRequest request) {
        GameSession session = gameServer.joinSession(gameId, request.playerName());
        return new JoinGameResponse(session.gameId(), session.lastJoinedPlayerId(), session.snapshot());
    }

    @GetMapping("/{gameId}")
    public LobbySnapshotResponse getSnapshot(@PathVariable String gameId) {
        return new LobbySnapshotResponse(gameServer.requireSession(gameId).snapshot());
    }

    @PostMapping("/{gameId}/commands")
    public void submitCommand(@PathVariable String gameId, @RequestBody SubmitCommandRequest request) {
        PlayerCommand command = request.command();
        gameServer.requireSession(gameId).enqueue(command);
    }
}
