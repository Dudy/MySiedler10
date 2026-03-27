package de.podolak.games.siedler.shared.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RoadRules {
    private RoadRules() {
    }

    public static boolean isValidRoadPath(
            MapDimensions dimensions,
            List<RoadState> existingRoads,
            List<RoadVertexCoordinate> path
    ) {
        if (dimensions == null || path == null || path.size() < 2) {
            return false;
        }

        Set<RoadEdge> occupiedEdges = occupiedEdges(existingRoads);
        Set<RoadEdge> seenEdges = new HashSet<>();
        for (RoadEdge edge : RoadGeometry.edgesForPath(path)) {
            if (!RoadGeometry.isValidEdge(dimensions, edge)) {
                return false;
            }
            if (!seenEdges.add(edge)) {
                return false;
            }
            if (occupiedEdges.contains(edge)) {
                return false;
            }
        }
        return true;
    }

    public static Set<RoadEdge> occupiedEdges(List<RoadState> roads) {
        Set<RoadEdge> occupiedEdges = new HashSet<>();
        if (roads == null) {
            return occupiedEdges;
        }
        for (RoadState road : roads) {
            occupiedEdges.addAll(RoadGeometry.edgesForPath(road.path()));
        }
        return occupiedEdges;
    }
}
