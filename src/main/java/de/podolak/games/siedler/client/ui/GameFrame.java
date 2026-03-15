package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;

public final class GameFrame {
    private static final Logger log = LoggerFactory.getLogger(GameFrame.class);

    private final JFrame frame;
    private final LandPanel landPanel;
    private final JScrollPane scrollPane;
    private final KeyEventDispatcher keyEventDispatcher;
    private final GameViewModel viewModel;
    private final JLabel resourcesLabel;
    private final JLabel selectedBuildingLabel;
    private final JLabel buildingCostLabel;
    private final JLabel buildingYieldLabel;
    private final ButtonGroup buildingButtonGroup;
    private final int horizontalStep;
    private final int verticalStep;
    private volatile boolean centeredOnHeadquarters;

    public GameFrame(GameViewModel viewModel) {
        this.viewModel = viewModel;
        frame = new JFrame("MySiedler10 Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        resourcesLabel = new JLabel("Holz: -   Steine: -");
        resourcesLabel.setFont(resourcesLabel.getFont().deriveFont(Font.PLAIN, 12f));
        resourcesLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        landPanel = new LandPanel(viewModel);
        scrollPane = new JScrollPane(landPanel);

        selectedBuildingLabel = new JLabel("Kein Gebaeude gewaehlt");
        buildingCostLabel = new JLabel("Kosten: -");
        buildingYieldLabel = new JLabel("Ertrag: -");
        buildingButtonGroup = new ButtonGroup();
        landPanel.setPlacementListener(this::onBuildingPlaced);
        landPanel.setBuildingSelectionListener(this::onExistingBuildingSelected);

        frame.add(resourcesLabel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(createRibbonPanel(), BorderLayout.SOUTH);

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
        viewModel.addSnapshotListener(event -> SwingUtilities.invokeLater(() -> {
            centerOnHeadquartersIfNeeded();
            updateResourcesLabel();
        }));

        frame.setSize(1150, 830);
        frame.setLocationRelativeTo(null);
    }

    public void showWindow() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            centerOnHeadquartersIfNeeded();
            updateResourcesLabel();
        });
    }

    private JPanel createRibbonPanel() {
        JPanel ribbon = new JPanel(new BorderLayout());
        ribbon.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        JPanel middle = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        JRadioButton lumberjackButton = createBuildingButton("Holzfaeller", BuildingType.LUMBERJACK);
        JRadioButton quarryButton = createBuildingButton("Steinbruch", BuildingType.QUARRY);
        buildingButtonGroup.add(lumberjackButton);
        buildingButtonGroup.add(quarryButton);
        middle.add(lumberjackButton);
        middle.add(quarryButton);

        JButton cancelButton = new JButton("Auswahl aufheben");
        cancelButton.addActionListener(event -> {
            buildingButtonGroup.clearSelection();
            selectBuildingType(null);
        });
        middle.add(cancelButton);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setPreferredSize(new Dimension(250, 90));
        details.setBorder(BorderFactory.createTitledBorder("Gebaeude"));
        details.add(selectedBuildingLabel);
        details.add(buildingCostLabel);
        details.add(buildingYieldLabel);

        ribbon.add(middle, BorderLayout.CENTER);
        ribbon.add(details, BorderLayout.EAST);
        return ribbon;
    }

    private JRadioButton createBuildingButton(String label, BuildingType buildingType) {
        JRadioButton button = new JRadioButton(label);
        button.addActionListener(event -> selectBuildingType(buildingType));
        return button;
    }

    private void selectBuildingType(BuildingType buildingType) {
        log.info("Ribbon select buildingType={}", buildingType);
        landPanel.setSelectedBuildingType(buildingType);

        if (buildingType == null) {
            selectedBuildingLabel.setText("Kein Gebaeude gewaehlt");
            buildingCostLabel.setText("Kosten: -");
            buildingYieldLabel.setText("Ertrag: -");
            return;
        }

        selectedBuildingLabel.setText("Auswahl: " + labelFor(buildingType));
        buildingCostLabel.setText("Kosten: Holz " + BuildingRules.woodCost(buildingType)
                + ", Stein " + BuildingRules.stoneCost(buildingType));
        buildingYieldLabel.setText("Ertrag: " + BuildingRules.yieldDescription(buildingType));
    }

    private void onExistingBuildingSelected(BuildingState buildingState) {
        if (buildingState == null) {
            selectedBuildingLabel.setText("Kein Gebaeude gewaehlt");
            buildingCostLabel.setText("Kosten: -");
            buildingYieldLabel.setText("Ertrag: -");
            return;
        }
        BuildingType buildingType = buildingState.buildingType();
        selectedBuildingLabel.setText("Ausgewaehlt: " + labelFor(buildingType)
                + " (" + buildingState.coordinate().x() + "," + buildingState.coordinate().y() + ")");
        buildingCostLabel.setText("Kosten: Holz " + BuildingRules.woodCost(buildingType)
                + ", Stein " + BuildingRules.stoneCost(buildingType));
        buildingYieldLabel.setText("Ertrag: " + BuildingRules.yieldDescription(buildingType));
    }

    private void onBuildingPlaced(boolean keepSelection) {
        log.info("Ribbon onBuildingPlaced keepSelection={}", keepSelection);
        if (keepSelection) {
            return;
        }
        buildingButtonGroup.clearSelection();
        selectBuildingType(null);
    }

    private void updateResourcesLabel() {
        GameStateSnapshot snapshot = viewModel.snapshot();
        String localPlayerId = viewModel.localPlayerId();
        if (snapshot == null || localPlayerId == null) {
            resourcesLabel.setText("Holz: -   Steine: -");
            return;
        }

        Optional<PlayerState> player = snapshot.players().stream()
                .filter(candidate -> localPlayerId.equals(candidate.playerId()))
                .findFirst();

        if (player.isEmpty()) {
            resourcesLabel.setText("Holz: -   Steine: -");
            return;
        }

        resourcesLabel.setText("Holz: " + player.get().wood() + "   Steine: " + player.get().stone());
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

    private String labelFor(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> "Holzfaeller";
            case QUARRY -> "Steinbruch";
            case HEADQUARTERS -> "Hauptquartier";
            case ROAD_FLAG -> "Wegflagge";
        };
    }
}
