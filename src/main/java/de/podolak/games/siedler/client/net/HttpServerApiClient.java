package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.network.rest.CreateGameRequest;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameRequest;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import de.podolak.games.siedler.shared.network.rest.SubmitCommandRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class HttpServerApiClient implements ServerApiClient {
    private final RestClient restClient;
    private final SiedlerClientProperties properties;

    public HttpServerApiClient(RestClient restClient, SiedlerClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CreateGameResponse createGame() {
        return restClient.post()
                .uri("/api/games")
                .body(new CreateGameRequest(properties.getPlayerName(), null))
                .retrieve()
                .body(CreateGameResponse.class);
    }

    @Override
    public JoinGameResponse joinGame(String gameId) {
        return restClient.post()
                .uri("/api/games/{gameId}/players", gameId)
                .body(new JoinGameRequest(properties.getPlayerName()))
                .retrieve()
                .body(JoinGameResponse.class);
    }

    @Override
    public void submit(PlayerCommand command) {
        restClient.post()
                .uri("/api/games/{gameId}/commands", command.gameId())
                .body(new SubmitCommandRequest(command))
                .retrieve()
                .toBodilessEntity();
    }
}
