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
    private boolean isMonophonic = false;
    private java.awt.Color color = new java.awt.Color(78, 59, 120);
    private final List<Note> notes = new ArrayList<>();
    private final List<MidiRegion> regions = new ArrayList<>();

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
        return isMonophonic;
    }

    public void setMonophonic(boolean monophonic) {
        isMonophonic = monophonic;
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

    @Override
    public String toString() {
        return "Track{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", instrument='" + instrument + '\'' +
                ", isMonophonic=" + isMonophonic +
                ", notesCount=" + notes.size() +
                ", regionsCount=" + regions.size() +
                '}';
    }
}
