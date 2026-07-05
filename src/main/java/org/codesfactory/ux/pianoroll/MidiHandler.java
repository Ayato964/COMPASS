package org.codesfactory.ux.pianoroll;

import javax.sound.midi.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MidiHandler {

    public static final int DEFAULT_PPQN = 480;

    public static class MidiData {
        public List<Note> notes;
        public int ppqn;
        public long totalTicks;
        public float tempo; // Added tempo in BPM

        public MidiData(List<Note> notes, int ppqn, long totalTicks, float tempo) {
            this.notes = notes;
            this.ppqn = ppqn;
            this.totalTicks = totalTicks;
            this.tempo = tempo;
        }
    }

    public static MidiData loadMidiFile(File file) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(file);
        List<Note> notes = new ArrayList<>();
        int ppqn = sequence.getResolution();
        if (ppqn == 0) ppqn = DEFAULT_PPQN; // Fallback if resolution is timecode based

        long maxTick = 0;
        float tempo = 120.0f; // Default tempo
        boolean tempoFound = false;

        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                long tick = event.getTick();
                if (tick > maxTick) maxTick = tick;

                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) { // Note ON with velocity > 0
                        int pitch = sm.getData1();
                        int velocity = sm.getData2();
                        int channel = sm.getChannel();
                        // Find corresponding NOTE_OFF
                        long durationTicks = findNoteOffDuration(track, i + 1, channel, pitch, tick);
                        if (durationTicks > 0) {
                            notes.add(new Note(pitch, tick, durationTicks, velocity, channel));
                            if (tick + durationTicks > maxTick) maxTick = tick + durationTicks;
                        }
                    }
                } else if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    if (mm.getType() == 0x51 && !tempoFound) { // Tempo event
                        byte[] data = mm.getData();
                        if (data.length == 3) {
                            int mspqn = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                            tempo = 60000000.0f / mspqn;
                            tempoFound = true; // Only use the first tempo event found
                        }
                    }
                }
            }
        }
        // Ensure totalTicks covers at least a few measures if no notes or very short sequence
        if (maxTick < (long)ppqn * 4 * 4) { // at least 4 measures
            maxTick = (long)ppqn * 4 * 8; // Default to 8 measures if content is short
        }


        return new MidiData(notes, ppqn, maxTick, tempo);
    }

    private static long findNoteOffDuration(javax.sound.midi.Track track, int startIndex, int channel, int pitch, long noteOnTick) {
        for (int i = startIndex; i < track.size(); i++) {
            MidiEvent event = track.get(i);
            MidiMessage message = event.getMessage();
            if (message instanceof ShortMessage) {
                ShortMessage sm = (ShortMessage) message;
                if (sm.getChannel() == channel && sm.getData1() == pitch) {
                    if (sm.getCommand() == ShortMessage.NOTE_OFF || (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() == 0)) {
                        return event.getTick() - noteOnTick;
                    }
                }
            }
        }
        return 0; // Should not happen in a well-formed MIDI file
    }

    public static void saveMidiFile(File file, List<Note> notes, int ppqn, float tempo) throws InvalidMidiDataException, IOException {
        saveMidiFile(file, notes, ppqn, tempo, "PIANO");
    }

    public static void saveMidiFile(File file, List<Note> notes, int ppqn, float tempo, String instrument) throws InvalidMidiDataException, IOException {
        Sequence sequence = new Sequence(Sequence.PPQ, ppqn);
        javax.sound.midi.Track track = sequence.createTrack();

        // Add tempo event at the beginning of the track
        try {
            MetaMessage tempoMessage = new MetaMessage();
            int mspqn = (int)(60000000 / tempo);
            byte[] data = new byte[3];
            data[0] = (byte)((mspqn >> 16) & 0xff);
            data[1] = (byte)((mspqn >> 8) & 0xff);
            data[2] = (byte)(mspqn & 0xff);
            tempoMessage.setMessage(0x51, data, data.length);
            track.add(new MidiEvent(tempoMessage, 0));
        } catch (InvalidMidiDataException e) {
            System.err.println("Error creating tempo event: " + e.getMessage());
        }

        // Add Program Change event based on target instrument to align with API validation
        int program = 0; // Default to Piano
        if ("SAX".equalsIgnoreCase(instrument)) {
            program = 65; // Soprano Sax
        }
        
        try {
            ShortMessage pc = new ShortMessage();
            pc.setMessage(ShortMessage.PROGRAM_CHANGE, 0, program, 0); // Channel 0, Program
            track.add(new MidiEvent(pc, 0));
        } catch (InvalidMidiDataException e) {
            System.err.println("Error creating program change event: " + e.getMessage());
        }

        for (Note note : notes) {
            try {
                ShortMessage noteOn = new ShortMessage();
                // Ensure everything is on Channel 0 to match Program Change channel
                noteOn.setMessage(ShortMessage.NOTE_ON, 0, note.getPitch(), note.getVelocity());
                track.add(new MidiEvent(noteOn, note.getStartTimeTicks()));

                ShortMessage noteOff = new ShortMessage();
                noteOff.setMessage(ShortMessage.NOTE_OFF, 0, note.getPitch(), 0); // Velocity 0 for NOTE_OFF
                track.add(new MidiEvent(noteOff, note.getStartTimeTicks() + note.getDurationTicks()));
            } catch (InvalidMidiDataException e) {
                System.err.println("Error creating MIDI message for note: " + note);
            }
        }
        MidiSystem.write(sequence, MidiSystem.getMidiFileTypes(sequence)[0], file);
    }

    public static void saveConditionsMidiFile(File file, List<Note> notes, int ppqn, float tempo) throws InvalidMidiDataException, IOException {
        Sequence sequence = new Sequence(Sequence.PPQ, ppqn);
        javax.sound.midi.Track track = sequence.createTrack();

        try {
            MetaMessage tempoMessage = new MetaMessage();
            int mspqn = (int)(60000000 / tempo);
            byte[] data = new byte[3];
            data[0] = (byte)((mspqn >> 16) & 0xff);
            data[1] = (byte)((mspqn >> 8) & 0xff);
            data[2] = (byte)(mspqn & 0xff);
            tempoMessage.setMessage(0x51, data, data.length);
            track.add(new MidiEvent(tempoMessage, 0));
        } catch (InvalidMidiDataException e) {
            System.err.println("Error creating tempo event: " + e.getMessage());
        }

        try {
            ShortMessage pc0 = new ShortMessage();
            pc0.setMessage(ShortMessage.PROGRAM_CHANGE, 0, 0, 0);
            track.add(new MidiEvent(pc0, 0));
            
            ShortMessage pc1 = new ShortMessage();
            pc1.setMessage(ShortMessage.PROGRAM_CHANGE, 1, 64, 0);
            track.add(new MidiEvent(pc1, 0));
        } catch (InvalidMidiDataException e) {
            System.err.println("Error creating program change event: " + e.getMessage());
        }

        for (Note note : notes) {
            try {
                int ch = note.getChannel();
                if (ch != 0 && ch != 1) {
                    ch = 0;
                }
                
                ShortMessage noteOn = new ShortMessage();
                noteOn.setMessage(ShortMessage.NOTE_ON, ch, note.getPitch(), note.getVelocity());
                track.add(new MidiEvent(noteOn, note.getStartTimeTicks()));

                ShortMessage noteOff = new ShortMessage();
                noteOff.setMessage(ShortMessage.NOTE_OFF, ch, note.getPitch(), 0);
                track.add(new MidiEvent(noteOff, note.getStartTimeTicks() + note.getDurationTicks()));
            } catch (InvalidMidiDataException e) {
                System.err.println("Error creating MIDI message for note: " + note);
            }
        }
        MidiSystem.write(sequence, MidiSystem.getMidiFileTypes(sequence)[0], file);
    }

    public static MidiData loadMidiFromBytes(byte[] midiBytes) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(midiBytes));
        List<Note> notes = new ArrayList<>();
        int ppqn = sequence.getResolution();
        if (ppqn == 0) ppqn = DEFAULT_PPQN;

        long maxTick = 0;
        float tempo = 120.0f; // Default tempo
        boolean tempoFound = false;

        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                long tick = event.getTick();
                if (tick > maxTick) maxTick = tick;

                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        int pitch = sm.getData1();
                        int velocity = sm.getData2();
                        int channel = sm.getChannel();
                        long durationTicks = findNoteOffDuration(track, i + 1, channel, pitch, tick);
                        if (durationTicks > 0) {
                            notes.add(new Note(pitch, tick, durationTicks, velocity, channel));
                            if (tick + durationTicks > maxTick) maxTick = tick + durationTicks;
                        }
                    }
                } else if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    if (mm.getType() == 0x51 && !tempoFound) { // Tempo event
                        byte[] data = mm.getData();
                        if (data.length == 3) {
                            int mspqn = ((data[0] & 0xff) << 16) | ((data[1] & 0xff) << 8) | (data[2] & 0xff);
                            tempo = 60000000.0f / mspqn;
                            tempoFound = true; // Only use the first tempo event found
                        }
                    }
                }
            }
        }

        if (maxTick < (long)ppqn * 4 * 4) {
            maxTick = (long)ppqn * 4 * 8;
        }

        return new MidiData(notes, ppqn, maxTick, tempo);
    }

    public static class MidiTrackInfo {
        public int trackIndex;
        public String name;
        public int program = 0;
        public List<Note> notes = new ArrayList<>();
        
        public String getInstrumentName() {
            return getGMInstrumentName(program);
        }

        @Override
        public String toString() {
            return name + " (" + getInstrumentName() + ") [" + notes.size() + " notes]";
        }
    }

    public static String getGMInstrumentName(int program) {
        if (program >= 0 && program <= 7) return "Piano";
        if (program >= 8 && program <= 15) return "Chromatic Percussion";
        if (program >= 16 && program <= 23) return "Organ";
        if (program >= 24 && program <= 31) return "Guitar";
        if (program >= 32 && program <= 39) return "Bass";
        if (program >= 40 && program <= 47) return "Strings";
        if (program >= 48 && program <= 55) return "Ensemble";
        if (program >= 56 && program <= 63) return "Brass";
        if (program >= 64 && program <= 71) return "Reed (Sax/Oboe)";
        if (program >= 72 && program <= 79) return "Pipe (Flute)";
        if (program >= 80 && program <= 87) return "Synth Lead";
        if (program >= 88 && program <= 95) return "Synth Pad";
        if (program >= 96 && program <= 103) return "Synth Effects";
        if (program >= 104 && program <= 111) return "Ethnic";
        if (program >= 112 && program <= 119) return "Percussive";
        if (program >= 120 && program <= 127) return "Sound Effects";
        return "Unknown";
    }

    public static List<MidiTrackInfo> loadMidiTracks(File file) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(file);
        List<MidiTrackInfo> trackList = new ArrayList<>();
        
        javax.sound.midi.Track[] tracks = sequence.getTracks();
        for (int t = 0; t < tracks.length; t++) {
            javax.sound.midi.Track track = tracks[t];
            MidiTrackInfo info = new MidiTrackInfo();
            info.trackIndex = t;
            info.name = "Track " + (t + 1);
            
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) message;
                    if (mm.getType() == 0x03) {
                        byte[] data = mm.getData();
                        info.name = new String(data).trim();
                    }
                } else if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    if (sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                        info.program = sm.getData1();
                    }
                }
            }
            
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                long tick = event.getTick();
                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        int pitch = sm.getData1();
                        int velocity = sm.getData2();
                        int channel = sm.getChannel();
                        long durationTicks = findNoteOffDuration(track, i + 1, channel, pitch, tick);
                        if (durationTicks > 0) {
                            info.notes.add(new Note(pitch, tick, durationTicks, velocity, channel));
                        }
                    }
                }
            }
            
            if (!info.notes.isEmpty()) {
                trackList.add(info);
            }
        }
        return trackList;
    }
}
