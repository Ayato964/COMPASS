package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;
import java.util.List;
import java.util.Objects; // Objects.requireNonNull を使う場合

public class AddNoteCommand implements Command {

    // --- Fields ---
    private final PianoRollView view;           // 操作対象のビュー (final)
    private final List<Note> notesListRef;      // Viewのnotesリストへの参照 (final)
    private final Note noteToAdd;               // 追加するノートオブジェクト (final)
    private boolean wasExecuted = false;       // executeが呼ばれたかどうかのフラグ（Redo用）

    // --- Constructor ---
    /**
     * 新しいノートを追加するコマンドを作成します。
     * このコマンドの execute() が呼ばれることで、実際にリストへの追加とUI更新が行われます。
     * @param view 操作対象の PianoRollView
     * @param notesList Viewが持つノートのリスト (直接参照)
     * @param noteToAdd 追加するノートオブジェクト
     */
    public AddNoteCommand(PianoRollView view, List<Note> notesList, Note noteToAdd) {
        // null チェック (防御的プログラミング)
        this.view = Objects.requireNonNull(view, "PianoRollView cannot be null");
        this.notesListRef = Objects.requireNonNull(notesList, "Notes list cannot be null");
        this.noteToAdd = Objects.requireNonNull(noteToAdd, "Note to add cannot be null");
        System.out.println("AddNoteCommand Constructor: notesListRef hash=" + System.identityHashCode(this.notesListRef) + " for note: " + noteToAdd);
    }

    // --- Command Implementation ---

    /**
     * コマンドを実行します（ノートをリストに追加し、UIを更新）。
     * Redo 操作でもこのメソッドが呼ばれます。
     */
    @Override
    public void execute() {
        System.out.println("AddNoteCommand execute() called for note: " + noteToAdd);
        System.out.println("  -> List size BEFORE add: " + notesListRef.size());

        // まだリストに含まれていない場合のみ追加（重複を防ぐ、equalsの実装に依存）
        // または、重複を許容する場合はこのif文は不要
        if (!notesListRef.contains(noteToAdd)) {
            boolean added = notesListRef.add(noteToAdd);
            System.out.println("  -> notesListRef.add(noteToAdd) returned: " + added);
        } else {
            System.out.println("  -> Note might already be in list (based on equals/hashCode or reference). Adding anyway for Redo.");
            // Redoの場合は既にリストから削除されているはずなので、containsはfalseになるはずだが、念のため。
            // もしRedo時に確実にリストに存在しない保証があるなら、containsチェックは不要。
            notesListRef.add(noteToAdd); // Redoのために追加は必要
        }

        System.out.println("  -> List size AFTER add: " + notesListRef.size());
        if (!notesListRef.isEmpty()) {
            System.out.println("  -> Last element in list AFTER add: " + notesListRef.getLast());
        }

        // UI状態を更新 (選択状態と情報ラベル)
        view.setSelectedNoteAfterCommand(noteToAdd); // 追加したノートを選択状態にする
        System.out.println("  -> Called view.setSelectedNoteAfterCommand()");
        wasExecuted = true; // 実行済みフラグ
        // repaint() は UndoManager が最後に行うのでここでは不要
    }

    /**
     * コマンドの操作を元に戻します（ノートをリストから削除し、UIを更新）。
     */
    @Override
    public void undo() {
        System.out.println("AddNoteCommand undo() called for note: " + noteToAdd);
        if (!wasExecuted) {
            System.out.println("  -> Command was not executed yet, nothing to undo.");
            return; // まだ実行されていないコマンドはundoしない
        }

        System.out.println("  -> List size BEFORE remove: " + notesListRef.size());
        boolean removed = notesListRef.remove(noteToAdd); // オブジェクトで削除
        System.out.println("  -> notesListRef.remove(noteToAdd) result: " + removed);
        if (!removed) {
            System.err.println("  WARNING: Note to remove was not found in the list during undo!");
        }
        System.out.println("  -> List size AFTER remove: " + notesListRef.size());

        // UI状態を更新 (選択解除と情報ラベル)
        view.clearSelectionAfterCommand(); // 選択をクリア
        System.out.println("  -> Called view.clearSelectionAfterCommand()");
        wasExecuted = false; // 未実行状態に戻す
        // repaint() は UndoManager が最後に行うのでここでは不要
    }

    /**
     * (オプション) このコマンドの説明を返します。Undo/Redoメニューなどに利用できます。
     * @return コマンドの説明文字列
     */
    @Override
    public String getDescription() {
        return "Add Note (Pitch: " + noteToAdd.getPitch() + ")";
    }
}