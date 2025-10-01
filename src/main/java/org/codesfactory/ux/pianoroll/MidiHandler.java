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

        for (Track track : sequence.getTracks()) {
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

    private static long findNoteOffDuration(Track track, int startIndex, int channel, int pitch, long noteOnTick) {
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
        Sequence sequence = new Sequence(Sequence.PPQ, ppqn);
        Track track = sequence.createTrack();

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

        for (Note note : notes) {
            try {
                ShortMessage noteOn = new ShortMessage();
                noteOn.setMessage(ShortMessage.NOTE_ON, note.getChannel(), note.getPitch(), note.getVelocity());
                track.add(new MidiEvent(noteOn, note.getStartTimeTicks()));

                ShortMessage noteOff = new ShortMessage();
                noteOff.setMessage(ShortMessage.NOTE_OFF, note.getChannel(), note.getPitch(), 0); // Velocity 0 for NOTE_OFF
                track.add(new MidiEvent(noteOff, note.getStartTimeTicks() + note.getDurationTicks()));
            } catch (InvalidMidiDataException e) {
                System.err.println("Error creating MIDI message for note: " + note);
                // Optionally re-throw or handle more gracefully
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

        for (Track track : sequence.getTracks()) {
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
}
