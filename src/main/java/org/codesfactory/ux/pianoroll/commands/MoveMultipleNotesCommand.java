package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.ArrayList;
import java.util.List;

public class MoveMultipleNotesCommand implements Command {
    private final PianoRollView view;
    private final List<Note> notes;
    private final List<Long> originalStartTicks;
    private final List<Integer> originalPitches;
    private final List<Long> finalStartTicks;
    private final List<Integer> finalPitches;

    public MoveMultipleNotesCommand(PianoRollView view, List<Note> notes, 
                                     List<Long> origStartTicks, List<Integer> origPitches,
                                     List<Long> finStartTicks, List<Integer> finPitches) {
        this.view = view;
        this.notes = new ArrayList<>(notes);
        this.originalStartTicks = new ArrayList<>(origStartTicks);
        this.originalPitches = new ArrayList<>(origPitches);
        this.finalStartTicks = new ArrayList<>(finStartTicks);
        this.finalPitches = new ArrayList<>(finPitches);
    }

    @Override
    public void execute() {
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            note.setStartTimeTicks(finalStartTicks.get(i));
            note.setPitch(finalPitches.get(i));
        }
        if (view != null) {
            view.repaint();
        }
    }

    @Override
    public void undo() {
        for (int i = 0; i < notes.size(); i++) {
            Note note = notes.get(i);
            note.setStartTimeTicks(originalStartTicks.get(i));
            note.setPitch(originalPitches.get(i));
        }
        if (view != null) {
            view.repaint();
        }
    }

    @Override
    public String getDescription() {
        return "Move " + notes.size() + " Notes";
    }
}
