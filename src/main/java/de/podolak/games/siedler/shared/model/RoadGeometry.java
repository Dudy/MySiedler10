package de.podolak.games.siedler.shared.model;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

public final class RoadGeometry {
    private static final int[][] TILE_VERTEX_OFFSETS = {
            {2, 1},
            {2, 3},
            {1, 4},
            {0, 3},
            {0, 1},
            {1, 0}
    };

    private RoadGeometry() {
    }

    public static List<RoadVertexCoordinate> verticesForTile(TileCoordinate tileCoordinate) {
        int baseX = tileCoordinate.x() * 2 + (tileCoordinate.y() & 1);
        int baseY = tileCoordinate.y() * 3;
        List<RoadVertexCoordinate> vertices = new ArrayList<>(TILE_VERTEX_OFFSETS.length);
        for (int[] offset : TILE_VERTEX_OFFSETS) {
            vertices.add(new RoadVertexCoordinate(baseX + offset[0], baseY + offset[1]));
        }
        return List.copyOf(vertices);
    }

    public static Set<RoadEdge> adjacentEdges(MapDimensions dimensions, RoadVertexCoordinate vertex) {
        if (dimensions == null || vertex == null) {
            return Set.of();
        }
        Set<RoadEdge> edges = new LinkedHashSet<>();
        for (TileCoordinate tile : candidateTiles(dimensions, vertex)) {
            List<RoadVertexCoordinate> tileVertices = verticesForTile(tile);
            for (int i = 0; i < tileVertices.size(); i++) {
                RoadEdge edge = new RoadEdge(tileVertices.get(i), tileVertices.get((i + 1) % tileVertices.size()));
                if (edge.contains(vertex)) {
                    edges.add(edge);
                }
            }
        }
        return Set.copyOf(edges);
    }

    public static List<RoadEdge> edgesForPath(List<RoadVertexCoordinate> path) {
        if (path == null || path.size() < 2) {
            return List.of();
        }
        List<RoadEdge> edges = new ArrayList<>(path.size() - 1);
        for (int i = 1; i < path.size(); i++) {
            RoadVertexCoordinate previous = path.get(i - 1);
            RoadVertexCoordinate current = path.get(i);
            if (previous == null || current == null) {
                return List.of();
            }
            edges.add(new RoadEdge(previous, current));
        }
        return List.copyOf(edges);
    }

    public static boolean isValidVertex(MapDimensions dimensions, RoadVertexCoordinate vertex) {
        return !candidateTiles(dimensions, vertex).isEmpty();
    }

    public static boolean isValidEdge(MapDimensions dimensions, RoadEdge edge) {
        return edge != null && adjacentEdges(dimensions, edge.start()).contains(edge);
    }

    private static Set<TileCoordinate> candidateTiles(MapDimensions dimensions, RoadVertexCoordinate vertex) {
        if (dimensions == null || vertex == null) {
            return Set.of();
        }
        int approxX = Math.floorDiv(vertex.x(), 2);
        int approxY = Math.floorDiv(vertex.y(), 3);
        Set<TileCoordinate> tiles = new LinkedHashSet<>();
        for (int y = Math.max(0, approxY - 1); y <= Math.min(dimensions.height() - 1, approxY + 1); y++) {
            for (int x = Math.max(0, approxX - 1); x <= Math.min(dimensions.width() - 1, approxX + 1); x++) {
                TileCoordinate tile = new TileCoordinate(x, y);
                if (verticesForTile(tile).contains(vertex)) {
                    tiles.add(tile);
                }
            }
        }
        return Set.copyOf(tiles);
    }
}
