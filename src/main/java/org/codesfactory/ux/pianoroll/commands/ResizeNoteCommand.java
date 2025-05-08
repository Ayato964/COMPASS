package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;

public class ResizeNoteCommand implements Command {
    private PianoRollView view; // viewは repaint や選択状態の更新に使うかもしれない
    private Note noteToResize;
    private long oldDuration;
    private long newDuration;

    public ResizeNoteCommand(PianoRollView view, Note noteToResize, long oldDuration, long newDuration) {
        this.view = view;
        this.noteToResize = noteToResize;
        this.oldDuration = oldDuration;
        this.newDuration = newDuration;
    }

    @Override
    public void execute() {
        noteToResize.setDurationTicks(newDuration);
        if (view != null) {
            view.updateNoteInfoForFrame(noteToResize); // 親フレームの情報ラベル更新
        }
    }

    @Override
    public void undo() {
        noteToResize.setDurationTicks(oldDuration);
        if (view != null) {
            view.updateNoteInfoForFrame(noteToResize);
        }
    }

    @Override public String getDescription() { return "Resize Note"; } // (オプション)
}