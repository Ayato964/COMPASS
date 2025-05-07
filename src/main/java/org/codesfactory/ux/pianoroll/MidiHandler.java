package org.codesfactory.ux.pianoroll;

import javax.sound.midi.*;
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
        // TODO: Add time signature and tempo events if needed for advanced display

        public MidiData(List<Note> notes, int ppqn, long totalTicks) {
            this.notes = notes;
            this.ppqn = ppqn;
            this.totalTicks = totalTicks;
        }
    }

    public static MidiData loadMidiFile(File file) throws InvalidMidiDataException, IOException {
        Sequence sequence = MidiSystem.getSequence(file);
        List<Note> notes = new ArrayList<>();
        int ppqn = sequence.getResolution();
        if (ppqn == 0) ppqn = DEFAULT_PPQN; // Fallback if resolution is timecode based

        long maxTick = 0;

        // For simplicity, we'll read notes from the first track that has note events
        // A more robust solution would handle multiple tracks or allow track selection
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                MidiMessage message = event.getMessage();
                if (message instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) message;
                    long tick = event.getTick();
                    if (tick > maxTick) maxTick = tick;

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
                }
            }
            // If we found notes in this track, break (simple single track handling)
            // if (!notes.isEmpty()) break;
        }
        // Ensure totalTicks covers at least a few measures if no notes or very short sequence
        if (maxTick < (long)ppqn * 4 * 4) { // at least 4 measures
            maxTick = (long)ppqn * 4 * 8; // Default to 8 measures if content is short
        }


        return new MidiData(notes, ppqn, maxTick);
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

    public static void saveMidiFile(File file, List<Note> notes, int ppqn) throws InvalidMidiDataException, IOException {
        Sequence sequence = new Sequence(Sequence.PPQ, ppqn);
        Track track = sequence.createTrack();

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
}