package de.podolak.games.siedler.client;

import de.podolak.games.siedler.client.net.LocalLoopbackServerApiClient;
import de.podolak.games.siedler.client.ui.GameFrame;
import de.podolak.games.siedler.client.viewmodel.GameViewModel;

import javax.swing.SwingUtilities;

public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        GameViewModel viewModel = new GameViewModel(new LocalLoopbackServerApiClient());
        SwingUtilities.invokeLater(() -> new GameFrame(viewModel).showWindow());
    }
}
