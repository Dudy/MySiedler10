package de.podolak.games.siedler.server.api;

import de.podolak.games.siedler.shared.network.rest.CreateGameRequest;
import de.podolak.games.siedler.shared.network.rest.CreateGameResponse;
import de.podolak.games.siedler.shared.network.rest.JoinGameRequest;
import de.podolak.games.siedler.shared.network.rest.JoinGameResponse;
import de.podolak.games.siedler.shared.network.rest.LobbySnapshotResponse;
import de.podolak.games.siedler.shared.network.rest.SubmitCommandRequest;

public interface GameRestApi {
    CreateGameResponse createGame(CreateGameRequest request);

    JoinGameResponse joinGame(String gameId, JoinGameRequest request);

    LobbySnapshotResponse getSnapshot(String gameId);

    void submitCommand(String gameId, SubmitCommandRequest request);
}
