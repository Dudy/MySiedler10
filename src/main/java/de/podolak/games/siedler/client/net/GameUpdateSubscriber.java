package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.client.config.SiedlerClientProperties;
import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;
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
        StompHeaders connectHeaders = new StompHeaders();
        CompletableFuture<StompSession> sessionFuture = stompClient.connectAsync(
                properties.getWebSocketUrl(),
                webSocketHeaders,
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        );

        sessionFuture.thenAccept(session -> session.subscribe("/topic/games/" + gameId + "/ticks", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return TickUpdateMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                consumer.accept((TickUpdateMessage) payload);
            }
        }));
    }
}
