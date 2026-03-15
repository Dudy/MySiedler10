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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public final class GameRestController {
    private static final Logger log = LoggerFactory.getLogger(GameRestController.class);

    private final GameServer gameServer;

    public GameRestController(GameServer gameServer) {
        this.gameServer = gameServer;
    }

    @PostMapping
    public CreateGameResponse createGame(@RequestBody CreateGameRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing create game request body");
        }
        GameConfig config = request.config() == null ? GameConfig.defaultConfig() : request.config();
        String hostPlayerName = request.hostPlayerName() == null || request.hostPlayerName().isBlank()
                ? "Player"
                : request.hostPlayerName();
        log.info(
                "REST createGame host={} map={}x{} tickMs={} maxPlayers={}",
                hostPlayerName,
                config.mapDimensions().width(),
                config.mapDimensions().height(),
                config.tickDurationMillis(),
                config.maxPlayers()
        );
        GameSession session = gameServer.createSession(hostPlayerName, config);
        log.info("REST createGame success gameId={} hostPlayerId={}", session.gameId(), session.hostPlayerId());
        return new CreateGameResponse(session.gameId(), session.hostPlayerId(), session.snapshot());
    }

    @PostMapping("/{gameId}/players")
    public JoinGameResponse joinGame(@PathVariable String gameId, @RequestBody JoinGameRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing join game request body");
        }
        String playerName = request.playerName() == null || request.playerName().isBlank()
                ? "Player"
                : request.playerName();
        log.info("REST joinGame gameId={} playerName={}", gameId, playerName);
        GameSession session = gameServer.joinSession(gameId, playerName);
        log.info("REST joinGame success gameId={} playerId={}", session.gameId(), session.lastJoinedPlayerId());
        return new JoinGameResponse(session.gameId(), session.lastJoinedPlayerId(), session.snapshot());
    }

    @GetMapping("/{gameId}")
    public LobbySnapshotResponse getSnapshot(@PathVariable String gameId) {
        log.debug("REST getSnapshot gameId={}", gameId);
        return new LobbySnapshotResponse(gameServer.requireSession(gameId).snapshot());
    }

    @PostMapping("/{gameId}/commands")
    public void submitCommand(@PathVariable String gameId, @RequestBody SubmitCommandRequest request) {
        if (request == null || request.command() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing command in request body");
        }
        PlayerCommand command = request.command();
        log.info(
                "REST submitCommand gameId={} commandId={} playerId={} type={} payload={}",
                gameId,
                command.commandId(),
                command.playerId(),
                command.commandType(),
                command.payload()
        );
        gameServer.requireSession(gameId).enqueue(command);
    }
}
