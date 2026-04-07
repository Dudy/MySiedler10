package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.BuildingProductionRules;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.PlayerState;
import de.podolak.games.siedler.shared.model.ResourceType;
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
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

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
    private final JLabel buildingStorageLabel;
    private final JLabel buildingCountdownLabel;
    private final ButtonGroup buildingButtonGroup;
    private final JToggleButton roadModeToggle;
    private final JButton finishRoadButton;
    private final JButton cancelRoadButton;
    private final Timer uiClockTimer;
    private final int horizontalStep;
    private final int verticalStep;
    private volatile boolean centeredOnHeadquarters;
    private volatile TileCoordinate selectedBuildingCoordinate;

    public GameFrame(GameViewModel viewModel) {
        this.viewModel = viewModel;
        frame = new JFrame("MySiedler10 Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        resourcesLabel = new JLabel("Holz: -   Steine: -");
        resourcesLabel.setFont(resourcesLabel.getFont().deriveFont(Font.PLAIN, 12f));
        resourcesLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        resourcesLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        landPanel = new LandPanel(viewModel);
        scrollPane = new JScrollPane(landPanel);
        scrollPane.setWheelScrollingEnabled(false);

        selectedBuildingLabel = new JLabel("Kein Gebaeude gewaehlt");
        buildingCostLabel = new JLabel("Kosten: -");
        buildingYieldLabel = new JLabel("Ertrag: -");
        buildingStorageLabel = new JLabel("Lager: -");
        buildingCountdownLabel = new JLabel("Naechste Einheit: -");
        buildingButtonGroup = new ButtonGroup();
        roadModeToggle = new JToggleButton("Wegebau AUS");
        finishRoadButton = new JButton("Weg beenden");
        cancelRoadButton = new JButton("Weg Abbrechen");
        landPanel.setPlacementListener(this::onBuildingPlaced);
        landPanel.setBuildingSelectionListener(this::onExistingBuildingSelected);
        landPanel.setRoadBuildStateListener(this::onRoadBuildStateChanged);

        frame.add(resourcesLabel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(createRibbonPanel(), BorderLayout.SOUTH);
        MouseWheelListener zoomListener = createZoomListener();
        scrollPane.getViewport().addMouseWheelListener(zoomListener);
        landPanel.addMouseWheelListener(zoomListener);

        horizontalStep = LandPanel.horizontalStepPixels();
        verticalStep = LandPanel.verticalStepPixels();
        keyEventDispatcher = createKeyDispatcher();
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyEventDispatcher);
        uiClockTimer = new Timer(100, event -> {
            updateResourcesLabel();
            refreshSelectedBuildingDetails();
        });
        uiClockTimer.start();
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyEventDispatcher);
                uiClockTimer.stop();
            }
        });
        viewModel.addSnapshotListener(event -> SwingUtilities.invokeLater(() -> {
            centerOnHeadquartersIfNeeded();
            updateResourcesLabel();
            refreshSelectedBuildingDetails();
        }));

        frame.setSize(1150, 830);
        frame.setLocationRelativeTo(null);
    }

    public void showWindow() {
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            centerOnHeadquartersIfNeeded();
            updateResourcesLabel();
            refreshSelectedBuildingDetails();
        });
    }

    private JPanel createRibbonPanel() {
        JPanel ribbon = new JPanel(new BorderLayout());
        ribbon.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        roadModeToggle.addActionListener(event -> setRoadBuildMode(roadModeToggle.isSelected()));
        finishRoadButton.addActionListener(event -> landPanel.finishRoadBuild());
        cancelRoadButton.addActionListener(event -> landPanel.cancelRoadBuild());
        finishRoadButton.setEnabled(false);
        cancelRoadButton.setEnabled(false);
        left.add(roadModeToggle);
        left.add(finishRoadButton);
        left.add(cancelRoadButton);

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
        details.setPreferredSize(new Dimension(320, 130));
        details.setBorder(BorderFactory.createTitledBorder("Gebaeude"));
        details.add(selectedBuildingLabel);
        details.add(buildingCostLabel);
        details.add(buildingYieldLabel);
        details.add(buildingStorageLabel);
        details.add(buildingCountdownLabel);

        ribbon.add(left, BorderLayout.WEST);
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
        selectedBuildingCoordinate = null;
        if (buildingType != null && landPanel.isRoadBuildModeEnabled()) {
            setRoadBuildMode(false);
        }
        landPanel.setSelectedBuildingType(buildingType);

        if (buildingType == null) {
            selectedBuildingLabel.setText("Kein Gebaeude gewaehlt");
            buildingCostLabel.setText("Kosten: -");
            buildingYieldLabel.setText("Ertrag: -");
            buildingStorageLabel.setText("Lager: -");
            buildingCountdownLabel.setText("Naechste Einheit: -");
            return;
        }

        selectedBuildingLabel.setText("Auswahl: " + labelFor(buildingType));
        buildingCostLabel.setText("Kosten: Holz " + BuildingRules.woodCost(buildingType)
                + ", Stein " + BuildingRules.stoneCost(buildingType));
        buildingYieldLabel.setText("Ertrag: " + BuildingRules.yieldDescription(buildingType));
        buildingStorageLabel.setText("Lager: -");
        buildingCountdownLabel.setText("Naechste Einheit: -");
    }

    private void onExistingBuildingSelected(BuildingState buildingState) {
        selectedBuildingCoordinate = buildingState == null ? null : buildingState.coordinate();
        if (buildingState == null) {
            selectedBuildingLabel.setText("Kein Gebaeude gewaehlt");
            buildingCostLabel.setText("Kosten: -");
            buildingYieldLabel.setText("Ertrag: -");
            buildingStorageLabel.setText("Lager: -");
            buildingCountdownLabel.setText("Naechste Einheit: -");
            return;
        }
        BuildingState estimatedBuilding = viewModel.estimatedBuildingState(buildingState.coordinate());
        applyBuildingDetails(estimatedBuilding == null ? buildingState : estimatedBuilding);
    }

    private void onBuildingPlaced(boolean keepSelection) {
        log.info("Ribbon onBuildingPlaced keepSelection={}", keepSelection);
        if (keepSelection) {
            return;
        }
        buildingButtonGroup.clearSelection();
        selectBuildingType(null);
    }

    private void onRoadBuildStateChanged(boolean roadBuildModeEnabled, boolean hasDraftRoad, boolean canFinishRoad) {
        roadModeToggle.setSelected(roadBuildModeEnabled);
        roadModeToggle.setText(roadBuildModeEnabled ? "Wegebau EIN" : "Wegebau AUS");
        finishRoadButton.setEnabled(roadBuildModeEnabled && canFinishRoad);
        cancelRoadButton.setEnabled(roadBuildModeEnabled && hasDraftRoad);
    }

    private void setRoadBuildMode(boolean enabled) {
        log.info("Ribbon road build mode toggle requested enabled={}", enabled);
        roadModeToggle.setSelected(enabled);
        roadModeToggle.setText(enabled ? "Wegebau EIN" : "Wegebau AUS");
        if (enabled) {
            buildingButtonGroup.clearSelection();
            selectBuildingType(null);
        }
        landPanel.setRoadBuildModeEnabled(enabled);
        log.info("Ribbon road build mode toggle applied enabled={}", enabled);
    }

    private MouseWheelListener createZoomListener() {
        return event -> {
            double currentZoom = landPanel.zoomLevel();
            double factor = event.getWheelRotation() < 0 ? 1.12 : 1.0 / 1.12;
            double targetZoom = currentZoom * factor;
            double newZoom = landPanel.setZoomLevel(targetZoom);
            if (Math.abs(newZoom - currentZoom) < 0.0001) {
                return;
            }

            Point mouse = event.getPoint();
            Point panelPoint = SwingUtilities.convertPoint(event.getComponent(), mouse, landPanel);
            Point viewportPoint = SwingUtilities.convertPoint(event.getComponent(), mouse, scrollPane.getViewport());
            Point currentViewPosition = scrollPane.getViewport().getViewPosition();
            double worldX = panelPoint.x / currentZoom;
            double worldY = panelPoint.y / currentZoom;
            event.consume();
            applyZoomViewportPosition(
                    event.getComponent().getClass().getSimpleName(),
                    currentZoom,
                    newZoom,
                    mouse,
                    panelPoint,
                    viewportPoint,
                    currentViewPosition,
                    worldX,
                    worldY
            );
        };
    }

    private void applyZoomViewportPosition(
            String sourceName,
            double oldZoom,
            double newZoom,
            Point mouse,
            Point panelPoint,
            Point viewportPoint,
            Point viewPosBefore,
            double worldX,
            double worldY
    ) {
        int targetX = (int) Math.round(worldX * newZoom - viewportPoint.x);
        int targetY = (int) Math.round(worldY * newZoom - viewportPoint.y);

        Dimension extent = scrollPane.getViewport().getExtentSize();
        Dimension viewSize = landPanel.getPreferredSize();
        scrollPane.getViewport().setViewSize(viewSize);
        int maxX = Math.max(0, viewSize.width - extent.width);
        int maxY = Math.max(0, viewSize.height - extent.height);

        targetX = Math.max(0, Math.min(maxX, targetX));
        targetY = Math.max(0, Math.min(maxY, targetY));
        scrollPane.getViewport().setViewPosition(new Point(targetX, targetY));
        scrollPane.getViewport().repaint();
        logHeadquartersVisibility(newZoom);
        log.debug(
                "Zoom event source={} old={} new={} mouse={} panelPoint={} viewportPoint={} viewPosBefore={} viewPosAfter={},{} viewSize={} extent={}",
                sourceName,
                oldZoom,
                newZoom,
                mouse,
                panelPoint,
                viewportPoint,
                viewPosBefore,
                targetX,
                targetY,
                viewSize,
                extent
        );
    }

    private void logHeadquartersVisibility(double zoomLevel) {
        String localPlayerId = viewModel.localPlayerId();
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (localPlayerId == null || snapshot == null) {
            return;
        }
        Optional<TileCoordinate> headquarters = snapshot.world().buildings().stream()
                .filter(building -> building.buildingType() == BuildingType.HEADQUARTERS)
                .filter(building -> localPlayerId.equals(building.ownerPlayerId()))
                .map(BuildingState::coordinate)
                .findFirst();
        if (headquarters.isEmpty()) {
            return;
        }

        Point worldCenter = LandPanel.tileCenterPixels(headquarters.get().x(), headquarters.get().y());
        Point scaledCenter = new Point(
                (int) Math.round(worldCenter.x * zoomLevel),
                (int) Math.round(worldCenter.y * zoomLevel)
        );
        Point viewPosition = scrollPane.getViewport().getViewPosition();
        Dimension extent = scrollPane.getViewport().getExtentSize();
        Rectangle visibleRect = new Rectangle(viewPosition, extent);
        log.debug(
                "HQ visibility zoom={} worldCenter={} scaledCenter={} visibleRect={} inside={}",
                zoomLevel,
                worldCenter,
                scaledCenter,
                visibleRect,
                visibleRect.contains(scaledCenter)
        );
    }

    private void updateResourcesLabel() {
        GameStateSnapshot snapshot = viewModel.snapshot();
        String localPlayerId = viewModel.localPlayerId();
        long serverSecond = viewModel.estimatedServerSecond();
        if (snapshot == null || localPlayerId == null) {
            resourcesLabel.setText("Holz: -   Steine: -   Sekunde: -");
            return;
        }

        Optional<PlayerState> player = snapshot.players().stream()
                .filter(candidate -> localPlayerId.equals(candidate.playerId()))
                .findFirst();

        if (player.isEmpty()) {
            resourcesLabel.setText("Holz: -   Steine: -   Sekunde: " + formatServerSecond(serverSecond));
            return;
        }

        resourcesLabel.setText(
                "Holz: " + player.get().wood()
                        + "   Steine: " + player.get().stone()
                        + "   Sekunde: " + formatServerSecond(serverSecond)
        );
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

    private void refreshSelectedBuildingDetails() {
        TileCoordinate coordinate = selectedBuildingCoordinate;
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (coordinate == null || snapshot == null || snapshot.world() == null) {
            return;
        }

        for (BuildingState building : snapshot.world().buildings()) {
            if (building.coordinate().equals(coordinate)) {
                BuildingState estimatedBuilding = viewModel.estimatedBuildingState(coordinate);
                applyBuildingDetails(estimatedBuilding == null ? building : estimatedBuilding);
                return;
            }
        }

        selectedBuildingCoordinate = null;
        selectedBuildingLabel.setText("Kein Gebaeude gewaehlt");
        buildingCostLabel.setText("Kosten: -");
        buildingYieldLabel.setText("Ertrag: -");
        buildingStorageLabel.setText("Lager: -");
        buildingCountdownLabel.setText("Naechste Einheit: -");
    }

    private void applyBuildingDetails(BuildingState buildingState) {
        BuildingType buildingType = buildingState.buildingType();
        GameStateSnapshot snapshot = viewModel.snapshot();
        selectedBuildingLabel.setText("Ausgewaehlt: " + labelFor(buildingType)
                + " (" + buildingState.coordinate().x() + "," + buildingState.coordinate().y() + ")");
        buildingCostLabel.setText("Kosten: Holz " + BuildingRules.woodCost(buildingType)
                + ", Stein " + BuildingRules.stoneCost(buildingType));
        buildingYieldLabel.setText("Ertrag: " + BuildingRules.yieldDescription(buildingType));
        buildingStorageLabel.setText(storageLabelFor(buildingState));
        buildingCountdownLabel.setText(countdownLabelFor(snapshot, buildingState));
    }

    private String storageLabelFor(BuildingState buildingState) {
        List<String> storedResources = new ArrayList<>();
        for (ResourceType resourceType : ResourceType.values()) {
            int amount = buildingState.storedAmount(resourceType);
            if (amount > 0) {
                storedResources.add(labelFor(resourceType) + ": " + amount);
            }
        }
        if (storedResources.isEmpty()) {
            return "Lager: leer";
        }
        return "<html>Lager:<br/>" + String.join("<br/>", storedResources) + "</html>";
    }

    private String countdownLabelFor(GameStateSnapshot snapshot, BuildingState buildingState) {
        if (snapshot == null || snapshot.world() == null) {
            return "Naechste Einheit: -";
        }
        if (!BuildingProductionRules.producesResource(buildingState.buildingType())) {
            return "Naechste Einheit: -";
        }

        OptionalInt remainingTicks = BuildingProductionRules.remainingTicksUntilNextUnit(snapshot.world(), buildingState);
        if (remainingTicks.isEmpty()) {
            return "Naechste Einheit: keine Produktion";
        }

        int ticks = remainingTicks.getAsInt();
        long tickMillis = snapshot.config() == null ? 200L : snapshot.config().tickDurationMillis();
        long totalMillis = ticks * tickMillis;
        return "Naechste Einheit: " + ticks + " Ticks (" + formatDuration(totalMillis) + ")";
    }

    private String formatDuration(long totalMillis) {
        if (totalMillis < 1000) {
            return totalMillis + " ms";
        }

        long minutes = totalMillis / 60000;
        long seconds = (totalMillis % 60000) / 1000;
        long tenths = (totalMillis % 1000) / 100;
        if (minutes > 0) {
            return minutes + ":" + String.format("%02d", seconds) + " min";
        }
        return seconds + "." + tenths + " s";
    }

    private String labelFor(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> "Holzfaeller";
            case QUARRY -> "Steinbruch";
            case HEADQUARTERS -> "Hauptquartier";
            case ROAD_FLAG -> "Wegflagge";
        };
    }

    private String labelFor(ResourceType resourceType) {
        return switch (resourceType) {
            case WOOD -> "Holz";
            case STONE -> "Stein";
            case IRON_ORE -> "Eisenerz";
            case COAL -> "Kohle";
            case GOLD -> "Gold";
            case WATER -> "Wasser";
            case FOOD -> "Nahrung";
        };
    }

    private String formatServerSecond(long serverSecond) {
        return serverSecond < 0 ? "-" : Long.toString(serverSecond);
    }
}
