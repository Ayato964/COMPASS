// Command.java (新規ファイル)
package org.codesfactory.ux.pianoroll.commands; // パッケージを分けることを推奨

public interface Command {
    void execute();
    void undo();
    String getDescription(); // オプション
}