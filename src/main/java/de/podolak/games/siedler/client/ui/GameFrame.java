package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.TileCoordinate;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

public final class GameFrame {
    private final JFrame frame;
    private final LandPanel landPanel;
    private final JScrollPane scrollPane;
    private final KeyEventDispatcher keyEventDispatcher;
    private final GameViewModel viewModel;
    private final int horizontalStep;
    private final int verticalStep;
    private volatile boolean centeredOnHeadquarters;

    public GameFrame(GameViewModel viewModel) {
        this.viewModel = viewModel;
        frame = new JFrame("MySiedler10 Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        landPanel = new LandPanel(viewModel);
        scrollPane = new JScrollPane(landPanel);
        frame.add(scrollPane, BorderLayout.CENTER);
        horizontalStep = LandPanel.horizontalStepPixels();
        verticalStep = LandPanel.verticalStepPixels();
        keyEventDispatcher = createKeyDispatcher();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
            }
        });
        viewModel.addSnapshotListener(event -> SwingUtilities.invokeLater(() -> centerOnHeadquartersIfNeeded()));
        frame.setSize(1100, 780);
        frame.setLocationRelativeTo(null);
    }

    public void showWindow() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(this::centerOnHeadquartersIfNeeded);
    }

    private KeyEventDispatcher createKeyDispatcher() {
        return event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (activeWindow != frame) {
                return false;
            }

            return switch (event.getKeyCode()) {
                case KeyEvent.VK_W, KeyEvent.VK_UP -> {
                    moveViewportBy(0, -verticalStep);
                    yield true;
                }
                case KeyEvent.VK_A, KeyEvent.VK_LEFT -> {
                    moveViewportBy(-horizontalStep, 0);
                    yield true;
                }
                case KeyEvent.VK_S, KeyEvent.VK_DOWN -> {
                    moveViewportBy(0, verticalStep);
                    yield true;
                }
                case KeyEvent.VK_D, KeyEvent.VK_RIGHT -> {
                    moveViewportBy(horizontalStep, 0);
                    yield true;
                }
                default -> false;
            };
        };
    }

    private void moveViewportBy(int dx, int dy) {
        Point current = scrollPane.getViewport().getViewPosition();
        Dimension extent = scrollPane.getViewport().getExtentSize();
        Dimension viewSize = scrollPane.getViewport().getViewSize();

        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);

        int targetX = Math.max(0, Math.min(maxX, current.x + dx));
        int targetY = Math.max(0, Math.min(maxY, current.y + dy));
        scrollPane.getViewport().setViewPosition(new Point(targetX, targetY));
    }

    private void centerOnHeadquartersIfNeeded() {
        if (centeredOnHeadquarters) {
            return;
        }

        String localPlayerId = viewModel.localPlayerId();
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (localPlayerId == null || snapshot == null) {
            return;
        }

        Optional<TileCoordinate> coordinate = snapshot.world().buildings().stream()
                .filter(building -> building.buildingType() == BuildingType.HEADQUARTERS)
                .filter(building -> localPlayerId.equals(building.ownerPlayerId()))
                .map(BuildingState::coordinate)
                .findFirst();
        if (coordinate.isEmpty()) {
            return;
        }

        centerViewportOn(coordinate.get());
        centeredOnHeadquarters = true;
    }

    private void centerViewportOn(TileCoordinate tileCoordinate) {
        Point center = LandPanel.tileCenterPixels(tileCoordinate.x(), tileCoordinate.y());
        Dimension extent = scrollPane.getViewport().getExtentSize();
        Dimension viewSize = landPanel.getPreferredSize();

        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);
        int targetX = Math.max(0, Math.min(maxX, center.x - (extent.width / 2)));
        int targetY = Math.max(0, Math.min(maxY, center.y - (extent.height / 2)));
        scrollPane.getViewport().setViewPosition(new Point(targetX, targetY));
    }
}
