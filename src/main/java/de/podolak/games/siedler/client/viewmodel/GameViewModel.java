package de.podolak.games.siedler.client.viewmodel;

import de.podolak.games.siedler.shared.model.GameStateSnapshot;
import org.springframework.stereotype.Component;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

@Component
public final class GameViewModel {
    private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
    private volatile GameStateSnapshot snapshot;

    public GameStateSnapshot snapshot() {
        return snapshot;
    }

    public void updateSnapshot(GameStateSnapshot snapshot) {
        GameStateSnapshot oldSnapshot = this.snapshot;
        this.snapshot = snapshot;
        propertyChangeSupport.firePropertyChange("snapshot", oldSnapshot, snapshot);
    }

    public void addSnapshotListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener("snapshot", listener);
    }
}
