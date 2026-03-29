package de.podolak.games.siedler.shared.model;

import java.util.EnumMap;
import java.util.Map;

public record BuildingState(
        String buildingId,
        BuildingType buildingType,
        String ownerPlayerId,
        TileCoordinate coordinate,
        int constructionProgress,
        Map<ResourceType, Integer> storedResources,
        double productionProgressTicks
) {
    public BuildingState(
            String buildingId,
            BuildingType buildingType,
            String ownerPlayerId,
            TileCoordinate coordinate,
            int constructionProgress
    ) {
        this(buildingId, buildingType, ownerPlayerId, coordinate, constructionProgress, null, 0.0);
    }

    public BuildingState {
        storedResources = normalizeStoredResources(storedResources);
        productionProgressTicks = Math.max(0.0, productionProgressTicks);
    }

    public int storedAmount(ResourceType resourceType) {
        return storedResources.getOrDefault(resourceType, 0);
    }

    public BuildingState withStoredResources(Map<ResourceType, Integer> nextStoredResources) {
        return new BuildingState(
                buildingId,
                buildingType,
                ownerPlayerId,
                coordinate,
                constructionProgress,
                nextStoredResources,
                productionProgressTicks
        );
    }

    public BuildingState withProductionProgressTicks(double nextProductionProgressTicks) {
        return new BuildingState(
                buildingId,
                buildingType,
                ownerPlayerId,
                coordinate,
                constructionProgress,
                storedResources,
                nextProductionProgressTicks
        );
    }

    public BuildingState withProductionState(
            Map<ResourceType, Integer> nextStoredResources,
            double nextProductionProgressTicks
    ) {
        return new BuildingState(
                buildingId,
                buildingType,
                ownerPlayerId,
                coordinate,
                constructionProgress,
                nextStoredResources,
                nextProductionProgressTicks
        );
    }

    private static Map<ResourceType, Integer> normalizeStoredResources(Map<ResourceType, Integer> source) {
        EnumMap<ResourceType, Integer> normalized = new EnumMap<>(ResourceType.class);
        for (ResourceType resourceType : ResourceType.values()) {
            int amount = 0;
            if (source != null) {
                amount = Math.max(0, source.getOrDefault(resourceType, 0));
            }
            normalized.put(resourceType, amount);
        }
        return Map.copyOf(normalized);
    }
}
