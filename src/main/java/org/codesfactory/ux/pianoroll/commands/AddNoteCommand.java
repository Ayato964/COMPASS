package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.List;

public class AddNoteCommand implements Command {
    private PianoRollView view;
    private List<Note> notesListRef; // PianoRollViewのnotesリストへの参照
    private Note noteToAdd;

    public AddNoteCommand(PianoRollView view, List<Note> notesList, Note noteToAdd) {
        this.view = view;
        this.notesListRef = notesList; // 直接参照を保持
        this.noteToAdd = noteToAdd;
    }

    @Override
    public void execute() {
        if (!notesListRef.contains(noteToAdd)) { // 重複追加を防ぐ (必須ではない)
            notesListRef.add(noteToAdd);
        }
        if (view != null) {
            view.setSelectedNoteAfterCommand(noteToAdd); // コマンド実行後に選択状態にするためのヘルパーメソッドをViewに作ることを推奨
        }
    }

    @Override
    public void undo() {
        notesListRef.remove(noteToAdd);
        if (view != null) {
            view.clearSelectionAfterCommand(); // 選択解除のためのヘルパーメソッド
        }
    }


    @Override public String getDescription() { return "Add Note"; } // (オプション)
}