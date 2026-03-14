package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.client.viewmodel.GameViewModel;
import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import de.podolak.games.siedler.shared.model.TerrainTile;
import de.podolak.games.siedler.shared.model.TerrainType;
import de.podolak.games.siedler.shared.model.WorldSnapshot;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

public final class LandPanel extends JPanel {
    private static final int TILE_SIZE = 36;

    private final GameViewModel viewModel;

    public LandPanel(GameViewModel viewModel) {
        this.viewModel = viewModel;
        setBackground(new Color(35, 44, 29));
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
        WorldSnapshot world = snapshot.world();
        for (TerrainTile tile : world.terrainTiles()) {
            graphics.setColor(colorFor(tile.terrainType()));
            int x = tile.coordinate().x() * TILE_SIZE;
            int y = tile.coordinate().y() * TILE_SIZE;
            graphics.fillRect(x, y, TILE_SIZE, TILE_SIZE);
            graphics.setColor(new Color(0, 0, 0, 35));
            graphics.drawRect(x, y, TILE_SIZE, TILE_SIZE);
        }
    }

    private void updatePreferredSize(GameStateSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        WorldSnapshot world = snapshot.world();
        setPreferredSize(new Dimension(world.dimensions().width() * TILE_SIZE, world.dimensions().height() * TILE_SIZE));
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
}
