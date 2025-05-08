// UndoManager.java
package org.codesfactory.ux.pianoroll.commands;
import org.codesfactory.ux.pianoroll.PianoRollView; // ★★★ この行を追加 ★★★
import java.util.Stack;

public class UndoManager {
    private Stack<Command> undoStack = new Stack<>();
    private Stack<Command> redoStack = new Stack<>();
    private PianoRollView view; // repaintなどのためにViewへの参照を持つ

    public UndoManager(PianoRollView view) {
        this.view = view;
    }

    public void executeCommand(Command command) {
        // command.execute(); // 設計による
        undoStack.push(command);
        redoStack.clear();
        updateUndoRedoStates();
        if (view != null) view.repaint();
    }

    public void undo() {
        if (!undoStack.isEmpty()) {
            Command command = undoStack.pop();
            command.undo();
            redoStack.push(command);
            updateUndoRedoStates();
            view.repaint();
        }
    }

    public void redo() {
        if (!redoStack.isEmpty()) {
            Command command = redoStack.pop();
            command.execute(); // または command.redo()
            undoStack.push(command);
            updateUndoRedoStates();
            view.repaint();
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    // ★★★ このメソッドを追加 ★★★
    public void clearStacks() {
        undoStack.clear();
        redoStack.clear();
        // updateUndoRedoStates(); // クリア後なので、メニューは無効になるはず。呼び出しは任意。
        // PianoRoll側でクリア後に updateUndoRedoMenuItems(false, false) を呼んでいるので、
        // ここで再度呼ぶ必要はないかもしれません。
    }
    // ★★★ ここまで ★★★

    private void updateUndoRedoStates() {
        if (view != null && view.getParentFrame() != null) { // nullチェックを追加
            view.getParentFrame().updateUndoRedoMenuItems(canUndo(), canRedo());
        } else if (view != null) {
            // viewはあるがparentFrameがない場合(テスト時など)はエラーを出さないようにする
            // もしくは、リスナーパターンでコールバックを登録する方がより疎結合になる
            System.err.println("UndoManager: PianoRollView's parent frame is null, cannot update menu items.");
        }
    }
}