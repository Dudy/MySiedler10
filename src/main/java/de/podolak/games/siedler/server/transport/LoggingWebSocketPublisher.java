package de.podolak.games.siedler.server.transport;

import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;

public final class LoggingWebSocketPublisher implements WorldUpdatePublisher {
    @Override
    public void publish(TickUpdateMessage message) {
        System.out.println("WebSocket broadcast tick=" + message.tick() + " gameId=" + message.gameId());
    }
}
