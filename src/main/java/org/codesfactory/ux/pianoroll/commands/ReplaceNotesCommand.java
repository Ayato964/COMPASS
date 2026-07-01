package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.ArrayList;
import java.util.List;

public class ReplaceNotesCommand implements Command {
    private final PianoRollView view;
    private final List<Note> notesListRef;
    private final List<Note> deletedNotes;
    private final List<Note> addedNotes;

    public ReplaceNotesCommand(PianoRollView view, List<Note> notesList, List<Note> notesToDelete, List<Note> newNotes) {
        this.view = view;
        this.notesListRef = notesList;
        this.deletedNotes = new ArrayList<>(notesToDelete);
        this.addedNotes = new ArrayList<>(newNotes);
    }

    @Override
    public void execute() {
        notesListRef.removeAll(deletedNotes);
        notesListRef.addAll(addedNotes);

        if (view != null) {
            view.clearSelectionAfterCommand();
            view.updateNoteInfoForFrame(null);
        }
    }

    @Override
    public void undo() {
        notesListRef.removeAll(addedNotes);
        notesListRef.addAll(deletedNotes);

        if (view != null) {
            view.setSelectedNotesAfterCommand(new ArrayList<>(deletedNotes));
            if (!deletedNotes.isEmpty()) {
                view.updateNoteInfoForFrame(deletedNotes.get(0));
            } else {
                view.updateNoteInfoForFrame(null);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Replace " + deletedNotes.size() + " Notes with " + addedNotes.size() + " Notes";
    }
}
