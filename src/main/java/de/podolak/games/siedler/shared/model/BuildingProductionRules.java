package de.podolak.games.siedler.shared.model;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

public final class BuildingProductionRules {
    public static final int INFLUENCE_RADIUS = 3;
    private static final double PRODUCTION_EPSILON = 1.0e-9;

    private BuildingProductionRules() {
    }

    public static ResourceType producedResource(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> ResourceType.WOOD;
            case QUARRY -> ResourceType.STONE;
            default -> null;
        };
    }

    public static TerrainType sourceTerrain(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> TerrainType.FOREST;
            case QUARRY -> TerrainType.MOUNTAIN;
            default -> null;
        };
    }

    public static int baseProductionTicks(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK, QUARRY -> 50;
            default -> 0;
        };
    }

    public static int productionCoverageTileCount(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK, QUARRY -> 36;
            default -> 0;
        };
    }

    public static boolean producesResource(BuildingType buildingType) {
        return producedResource(buildingType) != null
                && sourceTerrain(buildingType) != null
                && baseProductionTicks(buildingType) > 0
                && productionCoverageTileCount(buildingType) > 0;
    }

    public static int coveredSourceTiles(WorldSnapshot world, BuildingState building) {
        if (world == null || building == null || !producesResource(building.buildingType())) {
            return 0;
        }

        TileCoordinate center = building.coordinate();
        TerrainType terrainType = sourceTerrain(building.buildingType());
        int radius = INFLUENCE_RADIUS;
        int matchingTiles = 0;
        int minX = Math.max(0, center.x() - radius);
        int maxX = Math.min(world.dimensions().width() - 1, center.x() + radius);
        int minY = Math.max(0, center.y() - radius);
        int maxY = Math.min(world.dimensions().height() - 1, center.y() + radius);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                TileCoordinate candidate = new TileCoordinate(x, y);
                if (candidate.equals(center) || hexDistance(center, candidate) > radius) {
                    continue;
                }
                if (terrainTypeAt(world, x, y) == terrainType) {
                    matchingTiles += 1;
                }
            }
        }
        return matchingTiles;
    }

    public static double progressPerTick(WorldSnapshot world, BuildingState building) {
        if (world == null || building == null || !producesResource(building.buildingType())) {
            return 0.0;
        }
        int coverageTileCount = productionCoverageTileCount(building.buildingType());
        if (coverageTileCount <= 0) {
            return 0.0;
        }
        return (double) coveredSourceTiles(world, building) / coverageTileCount;
    }

    public static OptionalInt remainingTicksUntilNextUnit(WorldSnapshot world, BuildingState building) {
        if (world == null || building == null || !producesResource(building.buildingType())) {
            return OptionalInt.empty();
        }

        double progressPerTick = progressPerTick(world, building);
        if (progressPerTick <= 0.0) {
            return OptionalInt.empty();
        }

        double remainingProgress = Math.max(0.0, baseProductionTicks(building.buildingType()) - building.productionProgressTicks());
        int remainingTicks = (int) Math.ceil(remainingProgress / progressPerTick);
        return OptionalInt.of(Math.max(0, remainingTicks));
    }

    public static BuildingState advanceBuilding(WorldSnapshot world, BuildingState building, int ticks) {
        if (world == null || building == null || ticks <= 0) {
            return building;
        }
        if (building.constructionProgress() < 100 || !producesResource(building.buildingType())) {
            return building;
        }

        double progressPerTick = progressPerTick(world, building);
        if (progressPerTick <= 0.0) {
            return building;
        }

        int baseProductionTicks = baseProductionTicks(building.buildingType());
        double totalProgress = building.productionProgressTicks() + (progressPerTick * ticks);
        int producedUnits = (int) Math.floor((totalProgress + PRODUCTION_EPSILON) / baseProductionTicks);
        if (producedUnits <= 0) {
            return building.withProductionProgressTicks(totalProgress);
        }

        ResourceType producedResource = producedResource(building.buildingType());
        Map<ResourceType, Integer> updatedStoredResources = new EnumMap<>(ResourceType.class);
        updatedStoredResources.putAll(building.storedResources());
        updatedStoredResources.put(
                producedResource,
                building.storedAmount(producedResource) + producedUnits
        );
        return building.withProductionState(
                updatedStoredResources,
                Math.max(0.0, totalProgress - (producedUnits * baseProductionTicks))
        );
    }

    private static TerrainType terrainTypeAt(WorldSnapshot world, int x, int y) {
        int index = y * world.dimensions().width() + x;
        if (index >= 0 && index < world.terrainTiles().size()) {
            TerrainTile tile = world.terrainTiles().get(index);
            if (tile.coordinate().x() == x && tile.coordinate().y() == y) {
                return tile.terrainType();
            }
        }
        for (TerrainTile tile : world.terrainTiles()) {
            if (tile.coordinate().x() == x && tile.coordinate().y() == y) {
                return tile.terrainType();
            }
        }
        return TerrainType.GRASSLAND;
    }

    private static int hexDistance(TileCoordinate a, TileCoordinate b) {
        CubeCoordinate cubeA = toCube(a);
        CubeCoordinate cubeB = toCube(b);
        return Math.max(
                Math.abs(cubeA.x() - cubeB.x()),
                Math.max(Math.abs(cubeA.y() - cubeB.y()), Math.abs(cubeA.z() - cubeB.z()))
        );
    }

    private static CubeCoordinate toCube(TileCoordinate coordinate) {
        int q = coordinate.x() - ((coordinate.y() - (coordinate.y() & 1)) / 2);
        int z = coordinate.y();
        int x = q;
        int y = -x - z;
        return new CubeCoordinate(x, y, z);
    }

    private record CubeCoordinate(int x, int y, int z) {
    }
}
