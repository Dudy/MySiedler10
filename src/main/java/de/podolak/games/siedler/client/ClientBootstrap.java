package de.podolak.games.siedler.client;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.client.net.GameUpdateSubscriber;
import de.podolak.games.siedler.client.net.ServerApiClient;
import de.podolak.games.siedler.client.ui.GameFrame;
import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.swing.SwingUtilities;

@Component
public final class ClientBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ClientBootstrap.class);

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
        log.info("Client bootstrap started playerName={} configuredGameId={}", properties.getPlayerName(), properties.getGameId());
        SessionBootstrap bootstrap = bootstrapSession();
        log.info("Client bootstrap session ready gameId={} playerId={}", bootstrap.gameId(), bootstrap.playerId());
        gameViewModel.setLocalPlayerId(bootstrap.playerId());
        gameViewModel.updateSnapshot(bootstrap.snapshot());
        SwingUtilities.invokeLater(() -> new GameFrame(gameViewModel).showWindow());
        gameUpdateSubscriber.subscribe(bootstrap.gameId(), message -> gameViewModel.updateSnapshot(message.snapshot()));
        log.info("Client bootstrap complete, subscribed to tick updates gameId={}", bootstrap.gameId());
    }

    private SessionBootstrap bootstrapSession() {
        if (properties.getGameId() == null || properties.getGameId().isBlank()) {
            log.info("No gameId configured, creating new session");
            CreateGameResponse response = serverApiClient.createGame();
            if (response == null || response.snapshot() == null || response.gameId() == null || response.playerId() == null) {
                throw new IllegalStateException("Server returned incomplete create-game response");
            }
            return new SessionBootstrap(response.gameId(), response.playerId(), response.snapshot());
        }

        log.info("Joining existing session gameId={}", properties.getGameId());
        JoinGameResponse response = serverApiClient.joinGame(properties.getGameId());
        if (response == null || response.snapshot() == null || response.gameId() == null || response.playerId() == null) {
            throw new IllegalStateException("Server returned incomplete join-game response");
        }
        return new SessionBootstrap(response.gameId(), response.playerId(), response.snapshot());
    }

    private record SessionBootstrap(
            String gameId,
            String playerId,
            de.podolak.games.siedler.shared.model.GameStateSnapshot snapshot
    ) {
    }
}
