package de.podolak.games.siedler.client.net;

import de.podolak.games.siedler.server.simulation.WorldGenerator;
import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.GameConfig;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerColor;
import de.podolak.games.siedler.shared.model.PlayerState;

import java.time.Instant;
import java.util.List;

public final class LocalLoopbackServerApiClient implements ServerApiClient {
    @Override
    public GameStateSnapshot bootstrapPreviewWorld() {
        GameConfig config = GameConfig.defaultConfig();
        return new GameStateSnapshot(
                "preview-game",
                Instant.now(),
                config,
                List.of(new PlayerState("local-player", "Player 1", PlayerColor.RED, true)),
                WorldGenerator.createInitialWorld(config)
        );
    }

    @Override
    public void submit(PlayerCommand command) {
        System.out.println("REST submit placeholder: " + command.commandType());
    }
}
