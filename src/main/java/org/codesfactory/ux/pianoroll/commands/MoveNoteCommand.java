// MoveNoteCommand.java (ノート移動の例)
package org.codesfactory.ux.pianoroll.commands;

import org.codesfactory.ux.pianoroll.Note;
import org.codesfactory.ux.pianoroll.PianoRollView;

public class MoveNoteCommand implements Command {
    private PianoRollView view;
    private Note noteToMove;
    private long oldStartTime, newStartTime;
    private int oldPitch, newPitch;

    public MoveNoteCommand(PianoRollView view, Note noteToMove, long oldStartTime, int oldPitch, long newStartTime, int newPitch) {
        this.view = view;
        this.noteToMove = noteToMove;
        this.oldStartTime = oldStartTime;
        this.oldPitch = oldPitch;
        this.newStartTime = newStartTime; // 移動後の値はexecuteで適用済みと考える
        this.newPitch = newPitch;
    }

    @Override
    public void execute() {
        // 実行時は既にノートが移動済みのはず
        noteToMove.setStartTimeTicks(newStartTime);
        noteToMove.setPitch(newPitch);
        // view.repaint();
    }

    @Override
    public void undo() {
        noteToMove.setStartTimeTicks(oldStartTime);
        noteToMove.setPitch(oldPitch);
        // view.repaint();
    }

    // ★★★ このメソッドを追加 ★★★
    @Override
    public String getDescription() {
        // このコマンドの説明を返す。例えば以下のように。
        // 実際のピッチ名や時間表記を使うとより分かりやすい。
        return "Move Note (Pitch: " + oldPitch + " -> " + newPitch +
                ", Start: " + oldStartTime + " -> " + newStartTime + ")";
    }
    // ★★★ ここまで ★★★
}
