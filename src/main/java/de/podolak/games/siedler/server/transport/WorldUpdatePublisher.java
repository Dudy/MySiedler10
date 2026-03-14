package de.podolak.games.siedler.server.transport;

import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;

public interface WorldUpdatePublisher {
    void publish(TickUpdateMessage message);
}
