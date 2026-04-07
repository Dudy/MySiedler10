package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.shared.model.MapDimensions;
import de.podolak.games.siedler.shared.model.RoadGeometry;
import de.podolak.games.siedler.shared.model.RoadVertexCoordinate;
import de.podolak.games.siedler.shared.model.TileCoordinate;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LandPanelTest {

    @Test
    void nearestRoadVertexFindsVertexAtExactHexCorner() {
        RoadVertexCoordinate vertex = RoadGeometry.verticesForTile(new TileCoordinate(4, 4)).getFirst();
        Point point = LandPanel.roadVertexPixels(vertex);

        RoadVertexCoordinate nearest = LandPanel.nearestRoadVertex(new MapDimensions(10, 10), point);

        assertEquals(vertex, nearest);
    }

    @Test
    void nearestRoadVertexFindsVertexNearHexCorner() {
        RoadVertexCoordinate vertex = RoadGeometry.verticesForTile(new TileCoordinate(4, 4)).getFirst();
        Point point = LandPanel.roadVertexPixels(vertex);
        Point nearbyPoint = new Point(point.x + 8, point.y + 6);

        RoadVertexCoordinate nearest = LandPanel.nearestRoadVertex(new MapDimensions(10, 10), nearbyPoint);

        assertEquals(vertex, nearest);
    }

    @Test
    void nearestRoadVertexReturnsNullOutsideSelectionRadius() {
        RoadVertexCoordinate nearest = LandPanel.nearestRoadVertex(new MapDimensions(10, 10), new Point(-100, -100));

        assertNull(nearest);
    }
}
