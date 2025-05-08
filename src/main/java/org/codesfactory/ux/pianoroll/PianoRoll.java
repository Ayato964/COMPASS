// PianoRoll.java

package org.codesfactory.ux.pianoroll;

// commands パッケージのインポート (前回提示されたUndoManagerなどがある前提)
//import org.codesfactory.ux.pianoroll.commands.UndoManager;

import javax.sound.midi.InvalidMidiDataException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent; // Redoショートカットのため
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
// import java.awt.event.ActionEvent; // JMenuItemのリスナーで暗黙的に使われるので明示的でなくてもOK

public class PianoRoll extends JFrame {
    private PianoRollView pianoRollView;
    private JScrollPane scrollPane;
    private PlaybackManager playbackManager;

    private JLabel infoLabel;
    private JFileChooser fileChooser;
    private JButton playButton, stopButton, loopButton;

    // private UndoManager undoManager; // PianoRollViewが持つ形に変更も検討したが、Frameが持つ方が自然か
    private JMenuItem undoItem;
    private JMenuItem redoItem;

    private File currentFile = null;

    public PianoRoll(boolean isFullScreen, int width, int height) {
        setTitle("Java Piano Roll");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (playbackManager != null) {
                    playbackManager.stop();
                    playbackManager.close();
                }
                System.exit(0);
            }
        });

        setLayout(new BorderLayout());

        // PianoRollView のインスタンス化は一度だけ
        // UndoManager は PianoRollView が持つように変更（Viewの操作と密結合するため）
        pianoRollView = new PianoRollView(this); // PianoRollView が自身の UndoManager を持つ
        playbackManager = new PlaybackManager(pianoRollView);

        scrollPane = new JScrollPane(pianoRollView);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        if (pianoRollView.getHeight() > 0 && pianoRollView.getPpqn() > 0) { // 初期化後に値がセットされることを考慮
            scrollPane.getVerticalScrollBar().setUnitIncrement(Math.max(1, pianoRollView.getHeight() / 20));
            scrollPane.getHorizontalScrollBar().setUnitIncrement(Math.max(1, (int) (pianoRollView.getPpqn() * pianoRollView.getPixelsPerTickSafe() / 4)));
        }


        add(createToolbar(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);

        setupMenuBar(); // メニューバー設定 (Undo/Redoアイテムもここで初期化)

        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files (*.mid, *.midi)", "mid", "midi"));

        if (isFullScreen) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            setSize(width, height);
        }
        setLocationRelativeTo(null);

        // 初期状態でUndo/Redoメニューを更新 (PianoRollViewのUndoManagerを参照)
        updateUndoRedoMenuItems(pianoRollView.getUndoManager().canUndo(), pianoRollView.getUndoManager().canRedo());
    }

    private JToolBar createToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        playButton = new JButton("Play");
        playButton.addActionListener(e -> {
            if (playbackManager.isPlaying()) {
                playbackManager.stop();
                playButton.setText("Play");
            } else {
                pianoRollView.requestFocusInWindow();
                playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                if (pianoRollView.isLoopRangeVisible()) {
                    playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                } else {
                    playbackManager.clearLoop();
                }
                playbackManager.play();
                playButton.setText("Pause");
            }
        });
        toolBar.add(playButton);

        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> {
            playbackManager.stop();
            playButton.setText("Play");
        });
        toolBar.add(stopButton);

        loopButton = new JButton("Loop Off");
        loopButton.addActionListener(e -> {
            if (pianoRollView.isLoopRangeVisible()) {
                pianoRollView.clearLoopRange();
                playbackManager.clearLoop();
                loopButton.setText("Loop Off");
            } else {
                pianoRollView.setLoopRange(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                loopButton.setText("Loop On");
            }
        });
        toolBar.add(loopButton);

        toolBar.addSeparator();
        JButton zoomInHBtn = new JButton("H Zoom +");
        zoomInHBtn.addActionListener(e -> pianoRollView.zoomInHorizontal());
        toolBar.add(zoomInHBtn);
        JButton zoomOutHBtn = new JButton("H Zoom -");
        zoomOutHBtn.addActionListener(e -> pianoRollView.zoomOutHorizontal());
        toolBar.add(zoomOutHBtn);

        toolBar.addSeparator();
        JButton zoomInVBtn = new JButton("V Zoom +");
        zoomInVBtn.addActionListener(e -> pianoRollView.zoomInVertical());
        toolBar.add(zoomInVBtn);
        JButton zoomOutVBtn = new JButton("V Zoom -");
        zoomOutVBtn.addActionListener(e -> pianoRollView.zoomOutVertical());
        toolBar.add(zoomOutVBtn);

        return toolBar;
    }

    public void updateLoopButtonText() {
        if (pianoRollView != null && pianoRollView.isLoopRangeVisible()) { // nullチェック追加
            loopButton.setText("Loop On");
        } else if (loopButton != null) { // nullチェック追加
            loopButton.setText("Loop Off");
        }
    }

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoLabel = new JLabel("No note selected.");
        statusPanel.add(infoLabel);
        return statusPanel;
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // File Menu
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open MIDI...");
        openItem.addActionListener(e -> openMidiFile());
        openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save MIDI");
        saveItem.addActionListener(e -> saveCurrentMidiFile());
        saveItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        fileMenu.add(saveItem);

        JMenuItem saveAsItem = new JMenuItem("Save MIDI As...");
        saveAsItem.addActionListener(e -> saveMidiFileAs());
        fileMenu.add(saveAsItem);

        fileMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        undoItem = new JMenuItem("Undo");
        undoItem.addActionListener(e -> {
            if (pianoRollView != null) {
                pianoRollView.getUndoManager().undo(); // PianoRollViewが持つUndoManagerを呼ぶ
            }
        });
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        editMenu.add(undoItem);

        redoItem = new JMenuItem("Redo");
        redoItem.addActionListener(e -> {
            if (pianoRollView != null) {
                pianoRollView.getUndoManager().redo(); // PianoRollViewが持つUndoManagerを呼ぶ
            }
        });
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("mac") || osName.startsWith("darwin")) {
            redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK)); // Cmd+Shift+Z
        } else {
            redoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx())); // Ctrl+Y
        }
        editMenu.add(redoItem);

        // (オプション) Delete All Notes を Editメニューに追加
        editMenu.addSeparator();
        JMenuItem deleteAllItem = new JMenuItem("Delete All Notes");
        deleteAllItem.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(
                    PianoRoll.this,
                    "Are you sure you want to delete all notes?",
                    "Confirm Delete All",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION && pianoRollView != null) {
                pianoRollView.deleteAllNotesAndRecordCommand(); // コマンドとして記録するメソッドをViewに作る
            }
        });
        editMenu.add(deleteAllItem);


        menuBar.add(editMenu);
        setJMenuBar(menuBar);
    }

    // PianoRollViewのUndoManagerの状態に基づいてメニューアイテムを更新
    public void updateUndoRedoMenuItems(boolean canUndo, boolean canRedo) {
        if (undoItem != null) {
            undoItem.setEnabled(canUndo);
        }
        if (redoItem != null) {
            redoItem.setEnabled(canRedo);
        }
    }

    private void openMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        if (playButton != null) playButton.setText("Play"); // nullチェック

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                MidiHandler.MidiData midiData = MidiHandler.loadMidiFile(file);
                pianoRollView.loadNotes(midiData.notes, midiData.ppqn, midiData.totalTicks);
                playbackManager.loadNotes(midiData.notes, midiData.ppqn);
                setTitle("Java Piano Roll - " + file.getName());
                currentFile = file;
                pianoRollView.getUndoManager().clearStacks(); // 新しいファイルを開いたらUndo/Redo履歴をクリア
                updateUndoRedoMenuItems(false, false); // メニューも更新
            } catch (InvalidMidiDataException | IOException ex) {
                JOptionPane.showMessageDialog(this, "Error opening MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                currentFile = null;
            }
        }
    }

    private void saveMidiFileAs() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        if (playButton != null) playButton.setText("Play");

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".mid") && !file.getName().toLowerCase().endsWith(".midi")) {
                file = new File(file.getParentFile(), file.getName() + ".mid");
            }
            if (file.exists()) {
                int response = JOptionPane.showConfirmDialog(this, "File \"" + file.getName() + "\" already exists. Do you want to replace it?", "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            try {
                MidiHandler.saveMidiFile(file, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                setTitle("Java Piano Roll - " + file.getName());
                currentFile = file;
                JOptionPane.showMessageDialog(this, "MIDI file saved successfully as " + file.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (InvalidMidiDataException | IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void saveCurrentMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        if (playButton != null) playButton.setText("Play");

        if (currentFile != null) {
            try {
                MidiHandler.saveMidiFile(currentFile, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                JOptionPane.showMessageDialog(this, "MIDI file saved successfully to " + currentFile.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (InvalidMidiDataException | IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        } else {
            saveMidiFileAs();
        }
    }

    public void updateNoteInfo(Note note) {
        if (pianoRollView == null) return; // 初期化前は実行しない
        if (note != null) {
            infoLabel.setText(MessageFormat.format("Selected: Pitch {0} ({1}), Start {2} ({3} beat), Duration {4} ({5} beat), Vel {6}", note.getPitch(), getPitchName(note.getPitch()), note.getStartTimeTicks(), String.format("%.2f", (double) note.getStartTimeTicks() / pianoRollView.getPpqn()), note.getDurationTicks(), String.format("%.2f", (double) note.getDurationTicks() / pianoRollView.getPpqn()), note.getVelocity()));
        } else {
            infoLabel.setText("No note selected. Click to create or select.");
        }
    }

    private static final String[] PITCH_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    public static String getPitchName(int midiNoteNumber) {
        if (midiNoteNumber < 0 || midiNoteNumber > 127) return "N/A";
        int octave = (midiNoteNumber / 12) - 1;
        return PITCH_NAMES[midiNoteNumber % 12] + octave;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PianoRoll frame = new PianoRoll(false, 1280, 720);
            frame.setVisible(true);
        });
    }
}