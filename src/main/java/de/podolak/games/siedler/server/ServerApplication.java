package de.podolak.games.siedler.server;

import de.podolak.games.siedler.server.api.GameRestApi;
import de.podolak.games.siedler.server.api.InMemoryGameRestApi;
import de.podolak.games.siedler.server.game.GameServer;
import de.podolak.games.siedler.server.transport.LoggingWebSocketPublisher;

public final class ServerApplication {
    private ServerApplication() {
    }

    public static void main(String[] args) {
        GameServer gameServer = new GameServer(new LoggingWebSocketPublisher());
        GameRestApi api = new InMemoryGameRestApi(gameServer);
        System.out.println("Siedler server architecture bootstrap started.");
        System.out.println("REST API implementation: " + api.getClass().getSimpleName());
        gameServer.start();
    }
}
