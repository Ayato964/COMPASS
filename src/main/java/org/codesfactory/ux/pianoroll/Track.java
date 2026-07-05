package org.codesfactory.ux.pianoroll;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Track implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private String name;
    private String instrument = "PIANO";
    private java.awt.Color color = new java.awt.Color(78, 59, 120);
    private final List<Note> notes = new ArrayList<>();
    private final List<MidiRegion> regions = new ArrayList<>();
    private boolean isMuted = false;
    private boolean isSoloed = false;

    public Track(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInstrument() {
        return instrument;
    }

    public void setInstrument(String instrument) {
        this.instrument = instrument;
    }

    public boolean isMonophonic() {
        return "SAX".equalsIgnoreCase(instrument) || "VIOLIN".equalsIgnoreCase(instrument) || "BASS".equalsIgnoreCase(instrument);
    }

    public List<Note> getNotes() {
        return notes;
    }

    public List<MidiRegion> getRegions() {
        return regions;
    }

    public void addRegion(MidiRegion region) {
        regions.add(region);
    }

    public void removeRegion(MidiRegion region) {
        regions.remove(region);
    }

    public java.awt.Color getColor() {
        return color;
    }

    public void setColor(java.awt.Color color) {
        this.color = color;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        this.isMuted = muted;
    }

    public boolean isSoloed() {
        return isSoloed;
    }

    public void setSoloed(boolean soloed) {
        this.isSoloed = soloed;
    }

    @Override
    public String toString() {
        return "Track{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", instrument='" + instrument + '\'' +
                ", isMonophonic=" + isMonophonic() +
                ", notesCount=" + notes.size() +
                ", regionsCount=" + regions.size() +
                '}';
    }
}
