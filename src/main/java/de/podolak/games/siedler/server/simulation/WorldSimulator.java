package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingProductionRules;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.RoadState;
import de.podolak.games.siedler.shared.model.WorldSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class WorldSimulator {
    private static final Logger log = LoggerFactory.getLogger(WorldSimulator.class);

    private volatile WorldSnapshot currentWorld;

    public WorldSimulator(WorldSnapshot initialWorld) {
        this.currentWorld = initialWorld;
    }

    public void applyPendingCommands(List<PlayerCommand> commands) {
        if (!commands.isEmpty()) {
            log.debug("WorldSimulator received {} pending commands", commands.size());
        }
    }

    public boolean advanceTick(int tick) {
        log.debug("WorldSimulator advancing to tick={} buildings={}", tick, currentWorld.buildings().size());
        AdvanceBuildingsResult result = advanceBuildings(currentWorld);
        currentWorld = new WorldSnapshot(
                tick,
                currentWorld.dimensions(),
                currentWorld.terrainTiles(),
                currentWorld.resourceNodes(),
                result.buildings(),
                currentWorld.roads()
        );
        return result.producedResources();
    }

    public synchronized void addBuilding(BuildingState buildingState) {
        log.info(
                "WorldSimulator addBuilding id={} type={} owner={} at={},{}",
                buildingState.buildingId(),
                buildingState.buildingType(),
                buildingState.ownerPlayerId(),
                buildingState.coordinate().x(),
                buildingState.coordinate().y()
        );
        List<BuildingState> buildings = new ArrayList<>(currentWorld.buildings());
        buildings.add(buildingState);
        currentWorld = new WorldSnapshot(
                currentWorld.tick(),
                currentWorld.dimensions(),
                currentWorld.terrainTiles(),
                currentWorld.resourceNodes(),
                List.copyOf(buildings),
                currentWorld.roads()
        );
    }

    public synchronized void addRoad(RoadState roadState) {
        log.info(
                "WorldSimulator addRoad id={} owner={} vertices={}",
                roadState.roadId(),
                roadState.ownerPlayerId(),
                roadState.path().size()
        );
        List<RoadState> roads = new ArrayList<>(currentWorld.roads());
        roads.add(roadState);
        currentWorld = new WorldSnapshot(
                currentWorld.tick(),
                currentWorld.dimensions(),
                currentWorld.terrainTiles(),
                currentWorld.resourceNodes(),
                currentWorld.buildings(),
                List.copyOf(roads)
        );
    }

    public WorldSnapshot snapshot() {
        return currentWorld;
    }

    private AdvanceBuildingsResult advanceBuildings(WorldSnapshot world) {
        if (world.buildings().isEmpty()) {
            return new AdvanceBuildingsResult(world.buildings(), false);
        }

        boolean changed = false;
        boolean producedResources = false;
        List<BuildingState> buildings = new ArrayList<>(world.buildings().size());
        for (BuildingState building : world.buildings()) {
            BuildingState updatedBuilding = advanceBuilding(world, building);
            buildings.add(updatedBuilding);
            if (!updatedBuilding.equals(building)) {
                changed = true;
            }
            if (!updatedBuilding.storedResources().equals(building.storedResources())) {
                producedResources = true;
            }
        }
        return new AdvanceBuildingsResult(changed ? List.copyOf(buildings) : world.buildings(), producedResources);
    }

    private BuildingState advanceBuilding(WorldSnapshot world, BuildingState building) {
        if (building.constructionProgress() < 100) {
            return building;
        }
        if (!BuildingProductionRules.producesResource(building.buildingType())) {
            return building;
        }
        return BuildingProductionRules.advanceBuilding(world, building, 1);
    }

    private record AdvanceBuildingsResult(List<BuildingState> buildings, boolean producedResources) {
    }
}
