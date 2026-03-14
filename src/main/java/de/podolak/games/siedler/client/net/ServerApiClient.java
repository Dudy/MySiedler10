package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;

public interface ServerApiClient {
    CreateGameResponse createGame();

    JoinGameResponse joinGame(String gameId);

    void submit(PlayerCommand command);
}
