package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.network.rest.CreateGameRequest;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameRequest;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import de.podolak.games.siedler.shared.network.rest.SubmitCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class HttpServerApiClient implements ServerApiClient {
    private static final Logger log = LoggerFactory.getLogger(HttpServerApiClient.class);

    private final RestClient restClient;
    private final SiedlerClientProperties properties;

    public HttpServerApiClient(RestClient restClient, SiedlerClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CreateGameResponse createGame() {
        log.info("HTTP createGame playerName={}", properties.getPlayerName());
        CreateGameResponse response = restClient.post()
                .uri("/api/games")
                .body(new CreateGameRequest(properties.getPlayerName(), null))
                .retrieve()
                .body(CreateGameResponse.class);
        if (response != null) {
            log.info("HTTP createGame success gameId={} playerId={}", response.gameId(), response.playerId());
        } else {
            log.warn("HTTP createGame returned null response");
        }
        return response;
    }

    @Override
    public JoinGameResponse joinGame(String gameId) {
        log.info("HTTP joinGame gameId={} playerName={}", gameId, properties.getPlayerName());
        JoinGameResponse response = restClient.post()
                .uri("/api/games/{gameId}/players", gameId)
                .body(new JoinGameRequest(properties.getPlayerName()))
                .retrieve()
                .body(JoinGameResponse.class);
        if (response != null) {
            log.info("HTTP joinGame success gameId={} playerId={}", response.gameId(), response.playerId());
        } else {
            log.warn("HTTP joinGame returned null response gameId={}", gameId);
        }
        return response;
    }

    @Override
    public void submit(PlayerCommand command) {
        log.info(
                "HTTP submitCommand gameId={} commandId={} playerId={} type={} payload={}",
                command.gameId(),
                command.commandId(),
                command.playerId(),
                command.commandType(),
                command.payload()
        );
        restClient.post()
                .uri("/api/games/{gameId}/commands", command.gameId())
                .body(new SubmitCommandRequest(command))
                .retrieve()
                .toBodilessEntity();
        log.info("HTTP submitCommand success commandId={}", command.commandId());
    }
}
