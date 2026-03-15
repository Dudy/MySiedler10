package de.podolak.games.siedler.client;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "de.podolak.games.siedler.client")
public class ClientApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(ClientApplication.class)
                .headless(false)
                .web(WebApplicationType.NONE)
                .profiles("client")
                .run(args);
    }
}
