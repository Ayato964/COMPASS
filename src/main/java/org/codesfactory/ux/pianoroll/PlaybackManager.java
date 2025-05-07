package org.codesfactory.ux.pianoroll;

import javax.sound.midi.*;
import java.util.List;
import java.util.function.Consumer;

public class PlaybackManager {
    private Sequencer sequencer;
    private Sequence sequence;
    private PianoRollView pianoRollView; // To update playback head

    public PlaybackManager(PianoRollView view) {
        this.pianoRollView = view;
        try {
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            // Add a MetaEventListener to know when playback stops or loops
            sequencer.addMetaEventListener(meta -> {
                if (meta.getType() == 47) { // End of track meta event
                    sequencer.setTickPosition(0);
                    if (pianoRollView != null) {
                        pianoRollView.setPlaybackTick(0);
                        pianoRollView.repaint();
                    }
                    // If not looping, could call stop explicitly here
                    // sequencer.stop();
                }
            });

        } catch (MidiUnavailableException e) {
            e.printStackTrace();
            sequencer = null; // Indicate failure
        }
    }

    public void loadNotes(List<Note> notes, int ppqn) {
        if (sequencer == null) return;
        if (sequencer.isRunning()) {
            sequencer.stop();
        }
        try {
            sequence = new Sequence(Sequence.PPQ, ppqn);
            Track track = sequence.createTrack();

            for (Note note : notes) {
                ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, note.getChannel(), note.getPitch(), note.getVelocity());
                track.add(new MidiEvent(noteOn, note.getStartTimeTicks()));
                ShortMessage noteOff = new ShortMessage(ShortMessage.NOTE_OFF, note.getChannel(), note.getPitch(), 0);
                track.add(new MidiEvent(noteOff, note.getStartTimeTicks() + note.getDurationTicks()));
            }
            sequencer.setSequence(sequence);
        } catch (InvalidMidiDataException e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (sequencer != null && sequence != null) {
            sequencer.start();
            startPlaybackHeadUpdater();
        }
    }

    public void stop() {
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
            sequencer.setTickPosition(0); // Reset to beginning
            if (pianoRollView != null) {
                pianoRollView.setPlaybackTick(0);
                pianoRollView.repaint();
            }
        }
    }

    public boolean isPlaying() {
        return sequencer != null && sequencer.isRunning();
    }

    public void setLoop(long startTick, long endTick) {
        if (sequencer != null) {
            sequencer.setLoopStartPoint(startTick);
            sequencer.setLoopEndPoint(endTick);
            sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
        }
    }

    public void clearLoop() {
        if (sequencer != null) {
            sequencer.setLoopCount(0); // Turn off looping
        }
    }


    private void startPlaybackHeadUpdater() {
        // Thread to update the playback head in PianoRollView
        Thread updaterThread = new Thread(() -> {
            while (sequencer != null && sequencer.isRunning()) {
                if (pianoRollView != null) {
                    pianoRollView.setPlaybackTick(sequencer.getTickPosition());
                    pianoRollView.repaint(); // This should be SwingUtilities.invokeLater if view is complex
                }
                try {
                    Thread.sleep(30); // Update rate (e.g., ~30 FPS)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            // Ensure head is at 0 when stopped if not looping to end
            if (pianoRollView != null && sequencer != null && !sequencer.isRunning()) {
                pianoRollView.setPlaybackTick(sequencer.getTickPosition()); // last position
                pianoRollView.repaint();
            }
        });
        updaterThread.setDaemon(true);
        updaterThread.start();
    }

    public void close() {
        if (sequencer != null && sequencer.isOpen()) {
            sequencer.close();
        }
    }
}