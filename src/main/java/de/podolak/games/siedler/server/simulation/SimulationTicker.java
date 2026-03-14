package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.server.game.GameSession;

public final class SimulationTicker {
    private final GameSession session;

    public SimulationTicker(GameSession session) {
        this.session = session;
    }

    public void start() {
        Thread tickerThread = Thread.ofVirtual().name("game-ticker-" + session.gameId()).start(() -> {
            while (true) {
                session.advanceOneTick();
                try {
                    Thread.sleep(session.config().tickDurationMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        tickerThread.setUncaughtExceptionHandler((thread, throwable) ->
                System.err.println("Ticker failed for " + thread.getName() + ": " + throwable.getMessage()));
    }
}
