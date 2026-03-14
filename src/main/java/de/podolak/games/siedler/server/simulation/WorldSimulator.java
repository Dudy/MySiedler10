package de.podolak.games.siedler.server.simulation;

import de.podolak.games.siedler.shared.command.PlayerCommand;
import de.podolak.games.siedler.shared.model.BuildingState;
import de.podolak.games.siedler.shared.model.WorldSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class WorldSimulator {
    private volatile WorldSnapshot currentWorld;

    public WorldSimulator(WorldSnapshot initialWorld) {
        this.currentWorld = initialWorld;
    }

    public void applyPendingCommands(List<PlayerCommand> commands) {
        if (!commands.isEmpty()) {
            System.out.println("Processing commands: " + commands.size());
        }
    }

    public void advanceTick(int tick) {
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

    public WorldSnapshot snapshot() {
        return currentWorld;
    }
}
