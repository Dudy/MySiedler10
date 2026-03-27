package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.shared.command.PlayerCommand;
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

    public void advanceTick(int tick) {
        log.debug("WorldSimulator advancing to tick={} buildings={}", tick, currentWorld.buildings().size());
        currentWorld = new WorldSnapshot(
                tick,
                currentWorld.dimensions(),
                currentWorld.terrainTiles(),
                currentWorld.resourceNodes(),
                currentWorld.buildings(),
                currentWorld.roads()
        );
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
}
