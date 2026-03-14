package de.podolak.games.siedler.shared.command;

import java.time.Instant;
import java.util.Map;

public record PlayerCommand(
        String commandId,
        String gameId,
        String playerId,
        CommandType commandType,
        Map<String, String> payload,
        Instant clientTime
) {
}
