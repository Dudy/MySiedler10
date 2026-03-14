package de.podolak.games.siedler.client;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.client.net.GameUpdateSubscriber;
import de.podolak.games.siedler.client.net.ServerApiClient;
import de.podolak.games.siedler.client.ui.GameFrame;
import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;

@Component
public final class ClientBootstrap implements ApplicationRunner {
    private final ServerApiClient serverApiClient;
    private final GameUpdateSubscriber gameUpdateSubscriber;
    private final GameViewModel gameViewModel;
    private final SiedlerClientProperties properties;

    public ClientBootstrap(
            ServerApiClient serverApiClient,
            GameUpdateSubscriber gameUpdateSubscriber,
            GameViewModel gameViewModel,
            SiedlerClientProperties properties
    ) {
        this.serverApiClient = serverApiClient;
        this.gameUpdateSubscriber = gameUpdateSubscriber;
        this.gameViewModel = gameViewModel;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        SessionBootstrap bootstrap = bootstrapSession();
        gameViewModel.setLocalPlayerId(bootstrap.playerId());
        gameViewModel.updateSnapshot(bootstrap.snapshot());
        SwingUtilities.invokeLater(() -> new GameFrame(gameViewModel).showWindow());
        gameUpdateSubscriber.subscribe(bootstrap.gameId(), message -> gameViewModel.updateSnapshot(message.snapshot()));
    }

    private SessionBootstrap bootstrapSession() {
        if (properties.getGameId() == null || properties.getGameId().isBlank()) {
            CreateGameResponse response = serverApiClient.createGame();
            return new SessionBootstrap(response.gameId(), response.playerId(), response.snapshot());
        }

        JoinGameResponse response = serverApiClient.joinGame(properties.getGameId());
        return new SessionBootstrap(response.gameId(), response.playerId(), response.snapshot());
    }

    private record SessionBootstrap(
            String gameId,
            String playerId,
            de.podolak.games.siedler.shared.model.GameStateSnapshot snapshot
    ) {
    }
}
