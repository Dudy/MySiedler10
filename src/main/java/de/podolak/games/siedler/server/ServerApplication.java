package de.podolak.games.siedler.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "de.podolak.games.siedler.server")
public class ServerApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ServerApplication.class);
        application.setAdditionalProfiles("server");
        application.run(args);
    }
}
