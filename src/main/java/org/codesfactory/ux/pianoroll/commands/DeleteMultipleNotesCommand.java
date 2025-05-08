package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.ArrayList;
import java.util.List;

public class DeleteMultipleNotesCommand implements Command {
    final private PianoRollView view;
    final private List<Note> notesListRef; // PianoRollViewのnotesリストへの参照
    final private List<Note> deletedNotes; // 削除されたノートのコピーを保持

    public DeleteMultipleNotesCommand(PianoRollView view, List<Note> notesList, List<Note> notesToDelete) {
        this.view = view;
        this.notesListRef = notesList;
        // ★重要: 削除するノートのディープコピーを作成して保持する
        // (もしNoteクラスがミュータブルで、他で変更される可能性がある場合)
        // 今回はNoteオブジェクトの参照をそのまま保持するが、
        // Noteが他の場所で変更されない、または復元時に新しいインスタンスを作るならOK
        this.deletedNotes = new ArrayList<>(notesToDelete); // 渡されたリストのコピーを保持
    }

    @Override
    public void execute() {
        // コマンド実行時には既にUI上で選択され、削除対象として渡されている
        // PianoRollView側で notes.removeAll(selectedNotesList) を実行した後にこのコマンドが作られる場合、
        // executeでは何もしないか、最終的な状態確認のみ。
        // ここでは、コマンドが削除処理自体も担う設計とする。
        notesListRef.removeAll(deletedNotes);

        if (view != null) {
            view.clearSelectionAfterCommand(); // 選択をクリア
            view.updateNoteInfoForFrame(null);
        }
    }

    @Override
    public void undo() {
        // 削除されたノートをリストに再度追加する
        // 単純に追加すると順序が最後になる。元の順序を保持するには工夫が必要。
        notesListRef.addAll(deletedNotes);

        if (view != null) {
            // (オプション) Undo後にこれらのノートを再度選択状態にする
            view.setSelectedNotesAfterCommand(new ArrayList<>(deletedNotes));
            if (!deletedNotes.isEmpty()) {
                view.updateNoteInfoForFrame(deletedNotes.getFirst()); // 代表して最初のノート情報を表示

            } else {
                view.updateNoteInfoForFrame(null);
            }
        }
    }

    @Override public String getDescription() { return "Delete " + deletedNotes.size() + " Notes"; }
}