package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Component
public final class GameUpdateSubscriber {
    private static final Logger log = LoggerFactory.getLogger(GameUpdateSubscriber.class);

    private final WebSocketStompClient stompClient;
    private final WebSocketHttpHeaders webSocketHeaders;
    private final SiedlerClientProperties properties;

    public GameUpdateSubscriber(
            WebSocketStompClient stompClient,
            WebSocketHttpHeaders webSocketHeaders,
            SiedlerClientProperties properties
    ) {
        this.stompClient = stompClient;
        this.webSocketHeaders = webSocketHeaders;
        this.properties = properties;
    }

    public void subscribe(String gameId, Consumer<TickUpdateMessage> consumer) {
        log.info("WS subscribe start gameId={} url={}", gameId, properties.getWebSocketUrl());
        StompHeaders connectHeaders = new StompHeaders();
        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                properties.getWebSocketUrl(),
                webSocketHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );

        sessionFuture.thenAccept(session -> {
            String destination = "/topic/games/" + gameId + "/ticks";
            log.info("WS connected gameId={} sessionId={} destination={}", gameId, session.getSessionId(), destination);
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return TickUpdateMessage.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    if (!(payload instanceof TickUpdateMessage message)) {
                        log.warn("WS received unexpected payload type={} for gameId={}", payload == null ? null : payload.getClass().getName(), gameId);
                        return;
                    }
                    if (message.snapshot() == null) {
                        log.warn("WS received tick update without snapshot gameId={} tick={}", message.gameId(), message.tick());
                        return;
                    }
                    log.debug("WS tick update gameId={} tick={}", message.gameId(), message.tick());
                    consumer.accept(message);
                }
            });
            log.info("WS subscribed gameId={} destination={}", gameId, destination);
        }).exceptionally(error -> {
            log.error("WS subscribe failed gameId={}", gameId, error);
            return null;
        });
    }
}
