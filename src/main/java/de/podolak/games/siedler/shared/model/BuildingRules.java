package de.podolak.games.siedler.shared.model;

public final class BuildingRules {
    private BuildingRules() {
    }

    public static boolean canBePlacedByPlayer(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK, QUARRY -> true;
            default -> false;
        };
    }

    public static boolean canBePlacedOnTerrain(BuildingType buildingType, TerrainType terrainType) {
        return switch (buildingType) {
            case LUMBERJACK -> terrainType == TerrainType.GRASSLAND;
            case QUARRY -> terrainType == TerrainType.MOUNTAIN;
            default -> false;
        };
    }

    public static int woodCost(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> 3;
            case QUARRY -> 2;
            default -> 0;
        };
    }

    public static int stoneCost(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> 1;
            case QUARRY -> 3;
            default -> 0;
        };
    }

    public static String yieldDescription(BuildingType buildingType) {
        return switch (buildingType) {
            case LUMBERJACK -> "Produziert Holz je nach Waldanteil";
            case QUARRY -> "Produziert Stein je nach Berganteil";
            default -> "-";
        };
    }
}
