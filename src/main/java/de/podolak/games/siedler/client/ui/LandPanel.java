package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.BuildingType;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.WorldSnapshot;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.List;

public final class LandPanel extends JPanel {
    private static final int HEX_SIZE = 18;
    private static final double HEX_WIDTH = Math.sqrt(3) * HEX_SIZE;
    private static final double HEX_HEIGHT = 2.0 * HEX_SIZE;
    private static final double HORIZONTAL_SPACING = HEX_WIDTH;
    private static final double VERTICAL_SPACING = HEX_HEIGHT * 0.75;

    private final GameViewModel viewModel;

    public LandPanel(GameViewModel viewModel) {
        this.viewModel = viewModel;
        setBackground(new Color(35, 44, 29));
        updatePreferredSize(viewModel.snapshot());
        viewModel.addSnapshotListener(event -> SwingUtilities.invokeLater(() -> {
            updatePreferredSize((GameStateSnapshot) event.getNewValue());
            repaint();
        }));
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
        drawBuildings(graphics2D, world);

        graphics2D.dispose();
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
}
