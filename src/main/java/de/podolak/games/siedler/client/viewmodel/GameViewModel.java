package de.podolak.games.siedler.client.viewmodel;

import de.podolak.games.siedler.client.net.ServerApiClient;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;

public final class GameViewModel {
    private final ServerApiClient serverApiClient;
    private volatile GameStateSnapshot snapshot;

    public GameViewModel(ServerApiClient serverApiClient) {
        this.serverApiClient = serverApiClient;
        this.snapshot = serverApiClient.bootstrapPreviewWorld();
    }

    public GameStateSnapshot snapshot() {
        return snapshot;
    }
}
