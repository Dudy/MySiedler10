package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.BuildingRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.RoadEdge;
import de.podolak.games.siedler.shared.model.RoadGeometry;
import de.podolak.games.siedler.shared.model.RoadRules;
import de.podolak.games.siedler.shared.model.RoadState;
import de.podolak.games.siedler.shared.model.RoadVertexCoordinate;
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
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class LandPanel extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(LandPanel.class);

    private static final int HEX_SIZE = 18;
    private static final int INFLUENCE_RADIUS = 3;
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 5.0;
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
    private volatile RoadBuildStateListener roadBuildStateListener;
    private volatile boolean roadBuildModeEnabled;
    private volatile RoadVertexCoordinate activeRoadVertex;
    private volatile RoadEdge hoveredRoadEdge;
    private volatile List<RoadVertexCoordinate> draftRoadPath = List.of();
    private volatile Point lastMousePoint;
    private volatile long lastPaintDiagnosticsNanos;
    private volatile double zoomLevel = MIN_ZOOM;

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
            validateRoadDraftState();
            repaint();
        }));

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                lastMousePoint = event.getPoint();
                if (roadBuildModeEnabled) {
                    updateHoveredRoadEdge(event.getPoint());
                    return;
                }
                updateHoveredTile(event.getPoint());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                lastMousePoint = null;
                boolean needsRepaint = false;
                if (hoveredTile != null) {
                    hoveredTile = null;
                    needsRepaint = true;
                }
                if (hoveredRoadEdge != null) {
                    hoveredRoadEdge = null;
                    needsRepaint = true;
                }
                if (needsRepaint) {
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                if (roadBuildModeEnabled) {
                    handleRoadClick(event);
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
        Rectangle viewClip = graphics2D.getClipBounds();
        Rectangle worldClip = toWorldClip(viewClip);
        graphics2D.scale(zoomLevel, zoomLevel);
        graphics2D.setClip(worldClip);

        WorldSnapshot world = snapshot.world();
        logPaintDiagnosticsIfNeeded(world, worldClip);
        drawVisibleTiles(graphics2D, world, worldClip);
        drawInfluenceArea(graphics2D, world);
        drawHoveredTile(graphics2D, world);
        drawRoads(graphics2D, world);
        drawDraftRoad(graphics2D);
        drawActiveRoadVertex(graphics2D);
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

    public void setRoadBuildStateListener(RoadBuildStateListener roadBuildStateListener) {
        this.roadBuildStateListener = roadBuildStateListener;
        notifyRoadBuildStateChanged();
    }

    public void setRoadBuildModeEnabled(boolean enabled) {
        if (roadBuildModeEnabled == enabled) {
            return;
        }
        roadBuildModeEnabled = enabled;
        if (!enabled) {
            clearRoadDraftState();
        } else {
            hoveredTile = null;
            selectedBuildingTile = null;
            notifyBuildingSelection(null);
        }
        updateCursor();
        notifyRoadBuildStateChanged();
        repaint();
    }

    public boolean isRoadBuildModeEnabled() {
        return roadBuildModeEnabled;
    }

    public boolean hasDraftRoad() {
        return !draftRoadPath.isEmpty();
    }

    public boolean canFinishRoad() {
        return draftRoadPath.size() >= 2;
    }

    public void finishRoadBuild() {
        if (draftRoadPath.size() < 2) {
            return;
        }
        boolean built = viewModel.buildRoad(draftRoadPath);
        if (built) {
            clearRoadDraftState();
            notifyRoadBuildStateChanged();
            repaint();
        }
    }

    public void cancelRoadBuild() {
        if (draftRoadPath.isEmpty() && activeRoadVertex == null && hoveredRoadEdge == null) {
            return;
        }
        clearRoadDraftState();
        notifyRoadBuildStateChanged();
        repaint();
    }

    public double zoomLevel() {
        return zoomLevel;
    }

    public double setZoomLevel(double requestedZoomLevel) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requestedZoomLevel));
        if (Math.abs(clamped - zoomLevel) < 0.0001) {
            return zoomLevel;
        }
        zoomLevel = clamped;
        updatePreferredSize(viewModel.snapshot());
        repaint(0, 0, getWidth(), getHeight());
        log.debug("Zoom changed to {}", zoomLevel);
        return zoomLevel;
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
        if (selectedBuildingType == null || roadBuildModeEnabled) {
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

    private void drawRoads(Graphics2D graphics2D, WorldSnapshot world) {
        for (RoadState road : world.roads()) {
            drawRoadPath(graphics2D, road.path(), new Color(124, 88, 46), new Color(71, 47, 23), 7f, 3.5f);
        }
    }

    private void drawDraftRoad(Graphics2D graphics2D) {
        if (draftRoadPath.size() >= 2) {
            drawRoadPath(graphics2D, draftRoadPath, new Color(103, 183, 255), new Color(30, 92, 152), 6.5f, 3f);
        }
        if (hoveredRoadEdge != null) {
            drawRoadPath(
                    graphics2D,
                    List.of(hoveredRoadEdge.start(), hoveredRoadEdge.end()),
                    new Color(171, 225, 255),
                    new Color(72, 148, 201),
                    6.5f,
                    3f
            );
        }
    }

    private void drawActiveRoadVertex(Graphics2D graphics2D) {
        if (activeRoadVertex == null) {
            return;
        }
        Point center = roadVertexPixels(activeRoadVertex);
        graphics2D.setColor(new Color(29, 73, 222, 235));
        graphics2D.fillOval(center.x - 4, center.y - 4, 8, 8);
        graphics2D.setColor(new Color(210, 230, 255, 230));
        graphics2D.setStroke(new BasicStroke(1.6f));
        graphics2D.drawOval(center.x - 5, center.y - 5, 10, 10);
    }

    private void drawRoadPath(
            Graphics2D graphics2D,
            List<RoadVertexCoordinate> path,
            Color lineColor,
            Color outlineColor,
            float lineWidth,
            float outlineWidth
    ) {
        if (path == null || path.size() < 2) {
            return;
        }
        for (int i = 1; i < path.size(); i++) {
            Point start = roadVertexPixels(path.get(i - 1));
            Point end = roadVertexPixels(path.get(i));
            graphics2D.setColor(outlineColor);
            graphics2D.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics2D.drawLine(start.x, start.y, end.x, end.y);
            graphics2D.setColor(lineColor);
            graphics2D.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics2D.drawLine(start.x, start.y, end.x, end.y);
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

    private void drawVisibleTiles(Graphics2D graphics2D, WorldSnapshot world, Rectangle clip) {
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

    private Rectangle toWorldClip(Rectangle viewClip) {
        if (viewClip == null) {
            return new Rectangle(0, 0, getWidth(), getHeight());
        }
        if (zoomLevel <= 0.0) {
            return new Rectangle(viewClip);
        }
        return new Rectangle(
                (int) Math.floor(viewClip.x / zoomLevel),
                (int) Math.floor(viewClip.y / zoomLevel),
                (int) Math.ceil(viewClip.width / zoomLevel),
                (int) Math.ceil(viewClip.height / zoomLevel)
        );
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

    private void handleRoadClick(MouseEvent event) {
        lastMousePoint = event.getPoint();
        if (event.isShiftDown()) {
            undoLastRoadSegment();
            return;
        }
        if (activeRoadVertex == null) {
            RoadVertexCoordinate selectedVertex = roadVertexAt(event.getPoint());
            if (selectedVertex == null) {
                return;
            }
            activeRoadVertex = selectedVertex;
            draftRoadPath = List.of(selectedVertex);
            hoveredRoadEdge = null;
            updateHoveredRoadEdge(event.getPoint());
            notifyRoadBuildStateChanged();
            repaint();
            return;
        }
        if (hoveredRoadEdge == null) {
            updateHoveredRoadEdge(event.getPoint());
            if (hoveredRoadEdge == null) {
                return;
            }
        }

        RoadVertexCoordinate nextVertex = hoveredRoadEdge.other(activeRoadVertex);
        if (nextVertex == null) {
            return;
        }
        List<RoadVertexCoordinate> nextPath = new ArrayList<>(draftRoadPath);
        nextPath.add(nextVertex);
        draftRoadPath = List.copyOf(nextPath);
        activeRoadVertex = nextVertex;
        updateHoveredRoadEdge(event.getPoint());
        notifyRoadBuildStateChanged();
        repaint();
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

    private void updateHoveredRoadEdge(Point point) {
        if (!roadBuildModeEnabled || activeRoadVertex == null) {
            if (hoveredRoadEdge != null) {
                hoveredRoadEdge = null;
                repaint();
            }
            return;
        }
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return;
        }
        Point worldPoint = toWorldPoint(point);
        RoadEdge nextHovered = nearestRoadEdge(snapshot.world(), activeRoadVertex, worldPoint);
        if ((nextHovered == null && hoveredRoadEdge == null)
                || (nextHovered != null && nextHovered.equals(hoveredRoadEdge))) {
            return;
        }
        hoveredRoadEdge = nextHovered;
        repaint();
    }

    private TileCoordinate tileAt(Point point) {
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return null;
        }
        if (zoomLevel <= 0.0) {
            return null;
        }
        int baseX = (int) Math.round(point.x / zoomLevel);
        int baseY = (int) Math.round(point.y / zoomLevel);

        int width = snapshot.world().dimensions().width();
        int height = snapshot.world().dimensions().height();
        if (width <= 0 || height <= 0) {
            return null;
        }

        int approxY = clamp((int) Math.round((baseY - HEX_SIZE) / VERTICAL_SPACING), 0, height - 1);
        for (int y = Math.max(0, approxY - 2); y <= Math.min(height - 1, approxY + 2); y++) {
            double rowOffset = (y % 2) * (HEX_WIDTH / 2.0);
            int approxX = clamp((int) Math.round((baseX - (HEX_WIDTH / 2.0) - rowOffset) / HORIZONTAL_SPACING), 0, width - 1);
            for (int x = Math.max(0, approxX - 2); x <= Math.min(width - 1, approxX + 2); x++) {
                if (createHex(x, y).contains(baseX, baseY)) {
                    return new TileCoordinate(x, y);
                }
            }
        }
        return null;
    }

    private RoadVertexCoordinate roadVertexAt(Point point) {
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            return null;
        }
        TileCoordinate tile = tileAt(point);
        if (tile == null) {
            return null;
        }
        Point worldPoint = toWorldPoint(point);
        RoadVertexCoordinate nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (RoadVertexCoordinate candidate : RoadGeometry.verticesForTile(tile)) {
            Point candidatePoint = roadVertexPixels(candidate);
            double distanceSquared = candidatePoint.distanceSq(worldPoint);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }
        double selectionRadius = Math.pow(HEX_SIZE * 0.85, 2);
        return bestDistanceSquared <= selectionRadius ? nearest : null;
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

    private void validateRoadDraftState() {
        if (!roadBuildModeEnabled) {
            return;
        }
        GameStateSnapshot snapshot = viewModel.snapshot();
        if (snapshot == null) {
            clearRoadDraftState();
            notifyRoadBuildStateChanged();
            return;
        }
        if (draftRoadPath.size() >= 2
                && !RoadRules.isValidRoadPath(snapshot.world().dimensions(), snapshot.world().roads(), draftRoadPath)) {
            clearRoadDraftState();
            notifyRoadBuildStateChanged();
            return;
        }
        if (activeRoadVertex != null
                && !RoadGeometry.isValidVertex(snapshot.world().dimensions(), activeRoadVertex)) {
            clearRoadDraftState();
            notifyRoadBuildStateChanged();
            return;
        }
        if (activeRoadVertex != null && lastMousePoint != null) {
            updateHoveredRoadEdge(lastMousePoint);
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

    private RoadEdge nearestRoadEdge(WorldSnapshot world, RoadVertexCoordinate startVertex, Point worldPoint) {
        Set<RoadEdge> occupiedEdges = RoadRules.occupiedEdges(world.roads());
        Set<RoadEdge> draftEdges = Set.copyOf(RoadGeometry.edgesForPath(draftRoadPath));
        RoadEdge nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (RoadEdge edge : RoadGeometry.adjacentEdges(world.dimensions(), startVertex)) {
            if (occupiedEdges.contains(edge) || draftEdges.contains(edge)) {
                continue;
            }
            Point start = roadVertexPixels(edge.start());
            Point end = roadVertexPixels(edge.end());
            double distance = Line2D.ptSegDist(start.x, start.y, end.x, end.y, worldPoint.x, worldPoint.y);
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = edge;
            }
        }
        return nearest;
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

    private void undoLastRoadSegment() {
        if (draftRoadPath.size() <= 1) {
            return;
        }
        List<RoadVertexCoordinate> nextPath = new ArrayList<>(draftRoadPath.subList(0, draftRoadPath.size() - 1));
        draftRoadPath = List.copyOf(nextPath);
        activeRoadVertex = draftRoadPath.getLast();
        if (lastMousePoint != null) {
            updateHoveredRoadEdge(lastMousePoint);
        } else {
            hoveredRoadEdge = null;
        }
        notifyRoadBuildStateChanged();
        repaint();
    }

    private void clearRoadDraftState() {
        draftRoadPath = List.of();
        activeRoadVertex = null;
        hoveredRoadEdge = null;
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

        double baseWidth = HEX_WIDTH * world.dimensions().width() + HEX_WIDTH / 2.0;
        double baseHeight = HEX_SIZE + VERTICAL_SPACING * Math.max(0, world.dimensions().height() - 1) + HEX_SIZE;
        int width = (int) Math.ceil(baseWidth * zoomLevel);
        int height = (int) Math.ceil(baseHeight * zoomLevel);

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

    public static Point roadVertexPixels(RoadVertexCoordinate coordinate) {
        int x = (int) Math.round(coordinate.x() * (HEX_WIDTH / 2.0));
        int y = (int) Math.round(coordinate.y() * (HEX_SIZE / 2.0));
        return new Point(x, y);
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

    private void logPaintDiagnosticsIfNeeded(WorldSnapshot world, Rectangle worldClip) {
        long now = System.nanoTime();
        if (now - lastPaintDiagnosticsNanos < 2_000_000_000L) {
            return;
        }
        int visibleBuildings = 0;
        for (BuildingState building : world.buildings()) {
            Point center = tileCenterPixels(building.coordinate().x(), building.coordinate().y());
            if (worldClip.contains(center)) {
                visibleBuildings++;
            }
        }
        if (world.buildings().isEmpty() || visibleBuildings > 0) {
            return;
        }
        lastPaintDiagnosticsNanos = now;
        log.warn(
                "Paint diagnostics visibleBuildings=0 totalBuildings={} clip={} zoom={} preferredSize={} lastMousePoint={} roadMode={}",
                world.buildings().size(),
                worldClip,
                zoomLevel,
                getPreferredSize(),
                lastMousePoint,
                roadBuildModeEnabled
        );
    }

    private Point toWorldPoint(Point point) {
        return new Point(
                (int) Math.round(point.x / zoomLevel),
                (int) Math.round(point.y / zoomLevel)
        );
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

    public interface RoadBuildStateListener {
        void onRoadBuildStateChanged(boolean roadBuildModeEnabled, boolean hasDraftRoad, boolean canFinishRoad);
    }

    private void notifyRoadBuildStateChanged() {
        if (roadBuildStateListener != null) {
            roadBuildStateListener.onRoadBuildStateChanged(roadBuildModeEnabled, hasDraftRoad(), canFinishRoad());
        }
    }

    private record CubeCoordinate(int x, int y, int z) {
    }
}
