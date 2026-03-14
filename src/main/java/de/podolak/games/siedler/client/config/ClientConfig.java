package de.podolak.games.siedler.client.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;

@Configuration
@EnableConfigurationProperties(SiedlerClientProperties.class)
public class ClientConfig {
    @Bean
    RestClient restClient(SiedlerClientProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getServerBaseUrl())
                .build();
    }

    @Bean
    WebSocketHttpHeaders webSocketHttpHeaders() {
        return new WebSocketHttpHeaders();
    }

    @Bean
    WebSocketStompClient webSocketStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }
}
