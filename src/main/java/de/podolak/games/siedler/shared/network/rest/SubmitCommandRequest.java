package de.podolak.games.siedler.shared.network.rest;

import de.podolak.games.siedler.shared.command.PlayerCommand;

public record SubmitCommandRequest(PlayerCommand command) {
}
