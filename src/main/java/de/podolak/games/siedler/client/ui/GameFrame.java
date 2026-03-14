package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;

import javax.swing.JFrame;
import java.awt.BorderLayout;

public final class GameFrame {
    private final JFrame frame;

    public GameFrame(GameViewModel viewModel) {
        frame = new JFrame("MySiedler10 Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(new LandPanel(viewModel), BorderLayout.CENTER);
        frame.setSize(1100, 780);
        frame.setLocationRelativeTo(null);
    }

    public void showWindow() {
        frame.setVisible(true);
    }
}
