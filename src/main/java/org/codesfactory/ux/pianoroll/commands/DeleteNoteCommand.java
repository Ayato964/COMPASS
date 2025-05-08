// DeleteNoteCommand.java (単一ノート削除の例)
package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.List;

public class DeleteNoteCommand implements Command {
    private PianoRollView view;
    private List<Note> notesList;
    private Note noteToDelete;
    // private int originalIndex; // (オプション) 元の位置に復元する場合

    public DeleteNoteCommand(PianoRollView view, List<Note> notesList, Note noteToDelete) {
        this.view = view;
        this.notesList = notesList;
        this.noteToDelete = noteToDelete;
        // this.originalIndex = notesList.indexOf(noteToDelete); // 削除前にインデックスを保存
    }

    @Override
    public void execute() {
        // 実行時は既にノートが削除されている前提
        // もしコマンド作成時にまだ削除されていなければ、ここで行う
        if (notesList.contains(noteToDelete)) {
            notesList.remove(noteToDelete);
        }
        // view.setSelectedNote(null);
        // view.repaint();
    }

    @Override
    public void undo() {
        notesList.add(noteToDelete); // (オプション) originalIndexに挿入するなら add(originalIndex, noteToDelete)
        //view.setSelectedNote(noteToDelete); // (オプション)
        // view.repaint();
    }
    @Override
    public String getDescription() {
        // このコマンドの説明を返す
        return "Delete Note (Pitch: " + noteToDelete.getPitch() +
                ", Start: " + noteToDelete.getStartTimeTicks() + ")";
    }
}