package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

public final class LandPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(LandPanel.class);

    private static final int HEX_SIZE = 18;
    private static final int INFLUENCE_RADIUS = 3;
    private static final double HEX_WIDTH = Math.sqrt(3) * HEX_SIZE;
    private static final double HEX_HEIGHT = 2.0 * HEX_SIZE;
    private static final double HORIZONTAL_SPACING = HEX_WIDTH;
    private static final double VERTICAL_SPACING = HEX_HEIGHT * 0.75;

    private final GameViewModel viewModel;
    private final Cursor defaultCursor;
    private final Cursor buildCursor;
    private volatile BuildingType selectedBuildingType;
    private volatile TileCoordinate hoveredTile;
    private volatile TileCoordinate selectedBuildingTile;
    private volatile PlacementListener placementListener;
    private volatile BuildingSelectionListener buildingSelectionListener;

    public LandPanel(GameViewModel viewModel) {
        this.viewModel = viewModel;
        this.defaultCursor = getCursor();
        this.buildCursor = createBuildCursor();
        setBackground(new Color(35, 44, 29));
        updatePreferredSize(viewModel.snapshot());
        viewModel.addSnapshotListener(event -> SwingUtilities.invokeLater(() -> {
            updatePreferredSize((GameStateSnapshot) event.getNewValue());
            validateHoveredTile();
            validateSelectedBuildingTile();
            repaint();
        }));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHoveredTile(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (hoveredTile != null) {
                    hoveredTile = null;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                TileCoordinate clickedTile = tileAt(event.getPoint());
                if (clickedTile == null) {
                    return;
                }
                if (selectedBuildingType == null) {
                    selectExistingBuilding(clickedTile);
                    return;
                }
                BuildingType placingType = selectedBuildingType;
                int clickedX = clickedTile.x();
                int clickedY = clickedTile.y();
                hoveredTile = clickedTile;
                log.info(
                        "UI place click type={} at={},{} shiftDown={}",
                        placingType,
                        clickedX,
                        clickedY,
                        event.isShiftDown()
                );
                if (!canPlaceSelectedBuildingAt(clickedTile, true)) {
                    log.info(
                            "UI place click rejected by local panel validation type={} at={},{}",
                            placingType,
                            clickedX,
                            clickedY
                    );
                    return;
                }
                boolean placed = viewModel.placeBuilding(placingType, clickedTile);
                if (placed && placementListener != null) {
                    placementListener.onBuildingPlaced(event.isShiftDown());
                    log.info("UI place click succeeded type={} at={},{}", placingType, clickedX, clickedY);
                } else if (!placed) {
                    log.info("UI place click rejected by view model type={} at={},{}", placingType, clickedX, clickedY);
                }
            }
        };
        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);

        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return;
        }

        Graphics2D graphics2D = (Graphics2D) graphics.create();
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        WorldSnapshot world = snapshot.world();
        drawVisibleTiles(graphics2D, world);
        drawInfluenceArea(graphics2D, world);
        drawHoveredTile(graphics2D, world);
        drawBuildings(graphics2D, world);

        graphics2D.dispose();
    }

    public void setSelectedBuildingType(BuildingType buildingType) {
        selectedBuildingType = buildingType;
        log.info("UI building selection changed to {}", buildingType);
        updateCursor();
        if (buildingType == null) {
            hoveredTile = null;
        } else {
            selectedBuildingTile = null;
            notifyBuildingSelection(null);
        }
        repaint();
    }

    public void setPlacementListener(PlacementListener placementListener) {
        this.placementListener = placementListener;
        log.debug("UI placement listener updated hasListener={}", placementListener != null);
    }

    public void setBuildingSelectionListener(BuildingSelectionListener buildingSelectionListener) {
        this.buildingSelectionListener = buildingSelectionListener;
        log.debug("UI building selection listener updated hasListener={}", buildingSelectionListener != null);
    }

    private void updateCursor() {
        if (selectedBuildingType == null) {
            setCursor(defaultCursor);
            return;
        }
        setCursor(buildCursor);
    }

    private void drawBuildings(Graphics2D graphics2D, WorldSnapshot world) {
        for (BuildingState building : world.buildings()) {
            String marker = markerFor(building.buildingType());
            if (marker == null) {
                continue;
            }

            Point center = tileCenterPixels(building.coordinate().x(), building.coordinate().y());
            drawMarkerWithOutline(graphics2D, marker, center, fontSizeFor(building.buildingType()));
        }
    }

    private void drawHoveredTile(Graphics2D graphics2D, WorldSnapshot world) {
        if (selectedBuildingType == null || hoveredTile == null) {
            return;
        }
        if (hoveredTile.x() < 0 || hoveredTile.y() < 0
                || hoveredTile.x() >= world.dimensions().width()
                || hoveredTile.y() >= world.dimensions().height()) {
            return;
        }

        Polygon hex = createHex(hoveredTile.x(), hoveredTile.y());
        boolean canPlaceHere = canPlaceSelectedBuildingAt(hoveredTile, false);
        Color glowColor = canPlaceHere ? new Color(118, 255, 140, 95) : new Color(255, 145, 0, 90);
        Color borderColor = canPlaceHere ? new Color(170, 255, 170, 235) : new Color(255, 184, 77, 230);

        graphics2D.setColor(glowColor);
        graphics2D.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics2D.drawPolygon(hex);

        graphics2D.setColor(borderColor);
        graphics2D.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics2D.drawPolygon(hex);
    }

    private void drawInfluenceArea(Graphics2D graphics2D, WorldSnapshot world) {
        TileCoordinate center = influenceCenter(world);
        if (center == null) {
            return;
        }

        int minX = Math.max(0, center.x() - INFLUENCE_RADIUS);
        int maxX = Math.min(world.dimensions().width() - 1, center.x() + INFLUENCE_RADIUS);
        int minY = Math.max(0, center.y() - INFLUENCE_RADIUS);
        int maxY = Math.min(world.dimensions().height() - 1, center.y() + INFLUENCE_RADIUS);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TileCoordinate candidate = new TileCoordinate(x, y);
                int distance = hexDistance(center, candidate);
                if (distance > INFLUENCE_RADIUS) {
                    continue;
                }

                Polygon hex = createHex(x, y);
                graphics2D.setColor(new Color(255, 255, 255, 40));
                graphics2D.fillPolygon(hex);

                if (distance == INFLUENCE_RADIUS) {
                    graphics2D.setColor(new Color(0, 0, 0, 170));
                    graphics2D.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    graphics2D.drawPolygon(hex);
                }
            }
        }
    }

    private void drawVisibleTiles(Graphics2D graphics2D, WorldSnapshot world) {
        Rectangle clip = graphics2D.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }

        int width = world.dimensions().width();
        int height = world.dimensions().height();
        List<TerrainTile> tiles = world.terrainTiles();
        boolean indexedGrid = tiles.size() == width * height;

        int minY = clamp((int) Math.floor((clip.y - HEX_SIZE) / VERTICAL_SPACING) - 2, 0, height - 1);
        int maxY = clamp((int) Math.ceil((clip.y + clip.height + HEX_SIZE) / VERTICAL_SPACING) + 2, 0, height - 1);

        for (int y = minY; y <= maxY; y++) {
            double rowOffset = (y % 2) * (HEX_WIDTH / 2.0);
            int minX = clamp((int) Math.floor((clip.x - rowOffset - HEX_WIDTH) / HORIZONTAL_SPACING) - 2, 0, width - 1);
            int maxX = clamp((int) Math.ceil((clip.x + clip.width - rowOffset + HEX_WIDTH) / HORIZONTAL_SPACING) + 2, 0, width - 1);

            for (int x = minX; x <= maxX; x++) {
                TerrainType terrainType = terrainTypeAt(tiles, indexedGrid, width, x, y);
                Polygon hex = createHex(x, y);
                graphics2D.setColor(colorFor(terrainType));
                graphics2D.fillPolygon(hex);
                graphics2D.setColor(new Color(0, 0, 0, 35));
                graphics2D.drawPolygon(hex);
            }
        }
    }

    private TerrainType terrainTypeAt(List<TerrainTile> tiles, boolean indexedGrid, int width, int x, int y) {
        if (indexedGrid) {
            return tiles.get(y * width + x).terrainType();
        }

        for (TerrainTile tile : tiles) {
            if (tile.coordinate().x() == x && tile.coordinate().y() == y) {
                return tile.terrainType();
            }
        }
        return TerrainType.GRASSLAND;
    }

    private Polygon createHex(int x, int y) {
        int centerX = (int) Math.round(HEX_WIDTH / 2.0 + x * HORIZONTAL_SPACING + (y % 2) * (HEX_WIDTH / 2.0));
        int centerY = (int) Math.round(HEX_SIZE + y * VERTICAL_SPACING);

        int[] xs = new int[6];
        int[] ys = new int[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            xs[i] = (int) Math.round(centerX + HEX_SIZE * Math.cos(angle));
            ys[i] = (int) Math.round(centerY + HEX_SIZE * Math.sin(angle));
        }

        return new Polygon(xs, ys, 6);
    }

    private void updateHoveredTile(Point point) {
        if (selectedBuildingType == null) {
            if (hoveredTile != null) {
                hoveredTile = null;
                repaint();
            }
            return;
        }

        TileCoordinate nextHovered = tileAt(point);
        if ((nextHovered == null && hoveredTile == null)
                || (nextHovered != null && nextHovered.equals(hoveredTile))) {
            return;
        }
        hoveredTile = nextHovered;
        repaint();
    }

    private TileCoordinate tileAt(Point point) {
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return null;
        }

        int width = snapshot.world().dimensions().width();
        int height = snapshot.world().dimensions().height();
        if (width <= 0 || height <= 0) {
            return null;
        }

        int approxY = clamp((int) Math.round((point.y - HEX_SIZE) / VERTICAL_SPACING), 0, height - 1);
        for (int y = Math.max(0, approxY - 2); y <= Math.min(height - 1, approxY + 2); y++) {
            double rowOffset = (y % 2) * (HEX_WIDTH / 2.0);
            int approxX = clamp((int) Math.round((point.x - (HEX_WIDTH / 2.0) - rowOffset) / HORIZONTAL_SPACING), 0, width - 1);
            for (int x = Math.max(0, approxX - 2); x <= Math.min(width - 1, approxX + 2); x++) {
                if (createHex(x, y).contains(point)) {
                    return new TileCoordinate(x, y);
                }
            }
        }
        return null;
    }

    private void validateHoveredTile() {
        if (hoveredTile == null) {
            return;
        }

        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            hoveredTile = null;
            return;
        }

        int width = snapshot.world().dimensions().width();
        int height = snapshot.world().dimensions().height();
        if (hoveredTile.x() < 0 || hoveredTile.y() < 0 || hoveredTile.x() >= width || hoveredTile.y() >= height) {
            hoveredTile = null;
        }
    }

    private void validateSelectedBuildingTile() {
        if (selectedBuildingTile == null || selectedBuildingType != null) {
            return;
        }
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null || buildingAt(snapshot.world(), selectedBuildingTile) == null) {
            selectedBuildingTile = null;
            notifyBuildingSelection(null);
        }
    }

    private boolean canPlaceSelectedBuildingAt(TileCoordinate coordinate, boolean logValidation) {
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null || selectedBuildingType == null) {
            if (logValidation) {
                log.info(
                        "Panel validation failed reason=missing_context hasSnapshot={} selectedType={}",
                        snapshot != null,
                        selectedBuildingType
                );
            }
            return false;
        }
        int x = coordinate.x();
        int y = coordinate.y();
        if (x < 0 || y < 0
                || x >= snapshot.world().dimensions().width()
                || y >= snapshot.world().dimensions().height()) {
            if (logValidation) {
                log.info("Panel validation failed reason=out_of_bounds at={},{}", x, y);
            }
            return false;
        }
        for (BuildingState building : snapshot.world().buildings()) {
            if (building.coordinate().x() == x && building.coordinate().y() == y) {
                if (logValidation) {
                    log.info(
                            "Panel validation failed reason=occupied at={},{} existingType={} owner={}",
                            x,
                            y,
                            building.buildingType(),
                            building.ownerPlayerId()
                    );
                }
                return false;
            }
        }
        TerrainType terrainType = terrainTypeAt(
                snapshot.world().terrainTiles(),
                snapshot.world().terrainTiles().size() == snapshot.world().dimensions().width() * snapshot.world().dimensions().height(),
                snapshot.world().dimensions().width(),
                x,
                y
        );
        boolean allowed = BuildingRules.canBePlacedOnTerrain(selectedBuildingType, terrainType);
        if (!allowed && logValidation) {
            log.info(
                    "Panel validation failed reason=terrain_mismatch type={} terrain={} at={},{}",
                    selectedBuildingType,
                    terrainType,
                    x,
                    y
            );
        }
        return allowed;
    }

    private void selectExistingBuilding(TileCoordinate coordinate) {
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return;
        }
        BuildingState building = buildingAt(snapshot.world(), coordinate);
        if (building == null) {
            if (selectedBuildingTile != null) {
                log.info("UI existing building deselected");
            }
            selectedBuildingTile = null;
            notifyBuildingSelection(null);
            repaint();
            return;
        }

        selectedBuildingTile = building.coordinate();
        log.info(
                "UI existing building selected type={} at={},{} owner={}",
                building.buildingType(),
                building.coordinate().x(),
                building.coordinate().y(),
                building.ownerPlayerId()
        );
        notifyBuildingSelection(building);
        repaint();
    }

    private BuildingState buildingAt(WorldSnapshot world, TileCoordinate coordinate) {
        for (BuildingState building : world.buildings()) {
            if (building.coordinate().x() == coordinate.x() && building.coordinate().y() == coordinate.y()) {
                return building;
            }
        }
        return null;
    }

    private TileCoordinate influenceCenter(WorldSnapshot world) {
        if (selectedBuildingType != null) {
            return hoveredTile;
        }
        if (selectedBuildingTile == null) {
            return null;
        }
        BuildingState building = buildingAt(world, selectedBuildingTile);
        if (building == null) {
            selectedBuildingTile = null;
            notifyBuildingSelection(null);
            return null;
        }
        return building.coordinate();
    }

    private int hexDistance(TileCoordinate a, TileCoordinate b) {
        CubeCoordinate cubeA = toCube(a);
        CubeCoordinate cubeB = toCube(b);
        return Math.max(
                Math.abs(cubeA.x() - cubeB.x()),
                Math.max(Math.abs(cubeA.y() - cubeB.y()), Math.abs(cubeA.z() - cubeB.z()))
        );
    }

    private CubeCoordinate toCube(TileCoordinate coordinate) {
        int q = coordinate.x() - ((coordinate.y() - (coordinate.y() & 1)) / 2);
        int z = coordinate.y();
        int x = q;
        int y = -x - z;
        return new CubeCoordinate(x, y, z);
    }

    private void notifyBuildingSelection(BuildingState buildingState) {
        if (buildingSelectionListener != null) {
            buildingSelectionListener.onBuildingSelected(buildingState);
        }
    }

    private void updatePreferredSize(GameStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        WorldSnapshot world = snapshot.world();

        int width = (int) Math.ceil(HEX_WIDTH * world.dimensions().width() + HEX_WIDTH / 2.0);
        int height = (int) Math.ceil(HEX_SIZE + VERTICAL_SPACING * Math.max(0, world.dimensions().height() - 1) + HEX_SIZE);

        setPreferredSize(new Dimension(width, height));
        revalidate();
    }

    private Color colorFor(TerrainType terrainType) {
        return switch (terrainType) {
            case GRASSLAND -> new Color(108, 153, 74);
            case FOREST -> new Color(52, 102, 55);
            case MOUNTAIN -> new Color(132, 132, 132);
            case WATER -> new Color(66, 120, 191);
            case SAND -> new Color(217, 197, 128);
        };
    }

    private int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    public static int horizontalStepPixels() {
        return (int) Math.round(HORIZONTAL_SPACING);
    }

    public static int verticalStepPixels() {
        return (int) Math.round(VERTICAL_SPACING);
    }

    public static Point tileCenterPixels(int x, int y) {
        int centerX = (int) Math.round(HEX_WIDTH / 2.0 + x * HORIZONTAL_SPACING + (y % 2) * (HEX_WIDTH / 2.0));
        int centerY = (int) Math.round(HEX_SIZE + y * VERTICAL_SPACING);
        return new Point(centerX, centerY);
    }

    private String markerFor(BuildingType buildingType) {
        return switch (buildingType) {
            case HEADQUARTERS -> "X";
            case LUMBERJACK -> "H";
            case QUARRY -> "S";
            case ROAD_FLAG -> null;
        };
    }

    private int fontSizeFor(BuildingType buildingType) {
        if (buildingType == BuildingType.HEADQUARTERS) {
            return HEX_SIZE + 8;
        }
        return HEX_SIZE + 2;
    }

    private void drawMarkerWithOutline(Graphics2D graphics2D, String marker, Point center, int fontSize) {
        Font oldFont = graphics2D.getFont();
        graphics2D.setFont(oldFont.deriveFont(Font.BOLD, fontSize));
        FontMetrics metrics = graphics2D.getFontMetrics();

        int x = center.x - (metrics.stringWidth(marker) / 2);
        int y = center.y + ((metrics.getAscent() - metrics.getDescent()) / 2);

        graphics2D.setColor(Color.BLACK);
        graphics2D.drawString(marker, x - 1, y);
        graphics2D.drawString(marker, x + 1, y);
        graphics2D.drawString(marker, x, y - 1);
        graphics2D.drawString(marker, x, y + 1);

        graphics2D.setColor(Color.WHITE);
        graphics2D.drawString(marker, x, y);
        graphics2D.setFont(oldFont);
    }

    private Cursor createBuildCursor() {
        BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(255, 145, 0, 220));
        graphics.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int centerX = 16;
        int centerY = 16;
        int size = 8;
        int[] xs = new int[6];
        int[] ys = new int[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            xs[i] = (int) Math.round(centerX + size * Math.cos(angle));
            ys[i] = (int) Math.round(centerY + size * Math.sin(angle));
        }
        graphics.drawPolygon(xs, ys, 6);
        graphics.dispose();
        return Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(centerX, centerY), "hex-build-cursor");
    }

    public interface PlacementListener {
        void onBuildingPlaced(boolean keepSelection);
    }

    public interface BuildingSelectionListener {
        void onBuildingSelected(BuildingState buildingState);
    }

    private record CubeCoordinate(int x, int y, int z) {
    }
}
