package de.podolak.games.siedler.server.transport;

import de.podolak.games.siedler.shared.network.ws.TickUpdateMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public final class SpringWebSocketPublisher implements WorldUpdatePublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public SpringWebSocketPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(TickUpdateMessage message) {
        messagingTemplate.convertAndSend("/topic/games/" + message.gameId() + "/ticks", message);
    }
}
