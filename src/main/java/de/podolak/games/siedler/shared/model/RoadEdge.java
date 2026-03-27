package de.podolak.games.siedler.shared.model;

import java.util.Objects;

public record RoadEdge(RoadVertexCoordinate start, RoadVertexCoordinate end) {
    public RoadEdge {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (compare(start, end) > 0) {
            RoadVertexCoordinate swap = start;
            start = end;
            end = swap;
        }
    }

    public boolean contains(RoadVertexCoordinate vertex) {
        return start.equals(vertex) || end.equals(vertex);
    }

    public RoadVertexCoordinate other(RoadVertexCoordinate vertex) {
        if (start.equals(vertex)) {
            return end;
        }
        if (end.equals(vertex)) {
            return start;
        }
        return null;
    }

    private static int compare(RoadVertexCoordinate left, RoadVertexCoordinate right) {
        int xComparison = Integer.compare(left.x(), right.x());
        if (xComparison != 0) {
            return xComparison;
        }
        return Integer.compare(left.y(), right.y());
    }
}
