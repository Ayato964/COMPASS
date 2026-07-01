package org.codesfactory.ux.pianoroll;

import java.io.Serializable;
import java.util.UUID;

public class MidiRegion implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private long startTick;
    private long endTick;

    public MidiRegion(long startTick, long endTick) {
        this.id = UUID.randomUUID().toString();
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public String getId() {
        return id;
    }

    public long getStartTick() {
        return startTick;
    }

    public void setStartTick(long startTick) {
        this.startTick = startTick;
    }

    public long getEndTick() {
        return endTick;
    }

    public void setEndTick(long endTick) {
        this.endTick = endTick;
    }

    public long getLengthTicks() {
        return endTick - startTick;
    }

    @Override
    public String toString() {
        return "MidiRegion{" +
                "id='" + id + '\'' +
                ", startTick=" + startTick +
                ", endTick=" + endTick +
                '}';
    }
}
