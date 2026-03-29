package de.podolak.games.siedler.server.game;

import de.podolak.games.siedler.server.transport.WorldUpdatePublisher;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.MapDimensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameSessionTest {

    @Test
    void snapshotContainsServerSecondFromSupplier() {
        GameSession session = GameSession.create(
                "host",
                new GameConfig(4, 200, new MapDimensions(20, 20)),
                new NoopPublisher(),
                () -> 123L
        );

        assertEquals(123L, session.snapshot().serverSecond());
    }

    @Test
    void addPlayerPublishesSnapshotToSubscribers() {
        CountingPublisher publisher = new CountingPublisher();
        GameSession session = GameSession.create(
                "host",
                new GameConfig(4, 200, new MapDimensions(20, 20)),
                publisher,
                () -> 123L
        );

        session.addPlayer("guest");

        assertEquals(1, publisher.publishedMessages);
    }

    private static final class NoopPublisher implements WorldUpdatePublisher {
        @Override
        public void publish(de.podolak.games.siedler.shared.network.ws.TickUpdateMessage message) {
        }
    }

    private static final class CountingPublisher implements WorldUpdatePublisher {
        private int publishedMessages;

        @Override
        public void publish(de.podolak.games.siedler.shared.network.ws.TickUpdateMessage message) {
            publishedMessages += 1;
        }
    }
}
