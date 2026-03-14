package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;

public interface ServerApiClient {
    GameStateSnapshot bootstrapPreviewWorld();

    void submit(PlayerCommand command);
}
