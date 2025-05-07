// PianoRoll.java の先頭部分

package org.codesfactory.ux.pianoroll;

import javax.sound.midi.InvalidMidiDataException; // <--- これを追加
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.awt.event.ActionEvent;     // ActionEvent のために追加
import java.awt.event.KeyEvent;      // KeyEvent のために追加
import javax.swing.KeyStroke;        // KeyStroke のために追加
import java.awt.Toolkit;             // Toolkit のために追加 (プラットフォーム判定用)

// ... 以下クラス定義

public class PianoRoll extends JFrame {
    private PianoRollView pianoRollView;
    private JScrollPane scrollPane;
    private PlaybackManager playbackManager;

    private JLabel infoLabel;
    private JFileChooser fileChooser;
    private JButton playButton, stopButton, loopButton;

    private File currentFile = null;  // ★現在開いている/保存したファイルのパスを保持

    public PianoRoll(boolean isFullScreen, int width, int height) {
        setTitle("Java Piano Roll");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Handle close with playback manager
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

        pianoRollView = new PianoRollView(this);
        playbackManager = new PlaybackManager(pianoRollView);

        scrollPane = new JScrollPane(pianoRollView);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        // Adjust scroll speed (optional, might need tuning)
        scrollPane.getVerticalScrollBar().setUnitIncrement(pianoRollView.getHeight() / 20); // Use current noteHeight
        scrollPane.getHorizontalScrollBar().setUnitIncrement( (int)(pianoRollView.getPpqn() * 0.05 / 4)); // pixelsPerTick


        add(createToolbar(), BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(createStatusPanel(), BorderLayout.SOUTH);

        setupMenuBar();

        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files (*.mid, *.midi)", "mid", "midi"));


        if (isFullScreen) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            setSize(width, height);
        }
        setLocationRelativeTo(null); // Center on screen
    }

    private JToolBar createToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        playButton = new JButton("Play");
        playButton.addActionListener(e -> {
            if (playbackManager.isPlaying()) {
                playbackManager.stop(); // Acts as pause if pressed again
                playButton.setText("Play");
            } else {
                pianoRollView.requestFocusInWindow(); // Ensure view has focus for any key events
                playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                if (pianoRollView.isLoopRangeVisible()){
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
                // Default loop range or prompt user
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
        if (pianoRollView.isLoopRangeVisible()) {
            loopButton.setText("Loop On");
        } else {
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
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open MIDI...");
        openItem.addActionListener(e -> openMidiFile());
        fileMenu.add(openItem);

        JMenuItem saveItem = new JMenuItem("Save MIDI As...");
        saveItem.addActionListener(e -> saveMidiFile());
        fileMenu.add(saveItem);

        fileMenu.addSeparator();
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> dispatchEvent(new java.awt.event.WindowEvent(this, java.awt.event.WindowEvent.WINDOW_CLOSING)));
        fileMenu.add(exitItem);

        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void openMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        playButton.setText("Play");

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                MidiHandler.MidiData midiData = MidiHandler.loadMidiFile(file);
                pianoRollView.loadNotes(midiData.notes, midiData.ppqn, midiData.totalTicks);
                playbackManager.loadNotes(midiData.notes, midiData.ppqn); // Also load into playback
                setTitle("Java Piano Roll - " + file.getName());
            } catch (InvalidMidiDataException | IOException ex) {
                JOptionPane.showMessageDialog(this, "Error opening MIDI file: " + ex.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void saveMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        playButton.setText("Play");

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            // Ensure .mid extension
            if (!file.getName().toLowerCase().endsWith(".mid") && !file.getName().toLowerCase().endsWith(".midi")) {
                file = new File(file.getParentFile(), file.getName() + ".mid");
            }
            try {
                MidiHandler.saveMidiFile(file, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                setTitle("Java Piano Roll - " + file.getName());
                JOptionPane.showMessageDialog(this, "MIDI file saved successfully!", "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (InvalidMidiDataException | IOException ex) {
                JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    public void updateNoteInfo(Note note) {
        if (note != null) {
            infoLabel.setText(MessageFormat.format(
                    "Selected: Pitch {0} ({1}), Start {2} ({3} beat), Duration {4} ({5} beat), Vel {6}",
                    note.getPitch(), getPitchName(note.getPitch()),
                    note.getStartTimeTicks(), String.format("%.2f", (double)note.getStartTimeTicks() / pianoRollView.getPpqn()),
                    note.getDurationTicks(), String.format("%.2f", (double)note.getDurationTicks() / pianoRollView.getPpqn()),
                    note.getVelocity()
            ));
        } else {
            infoLabel.setText("No note selected. Click to create or select.");
        }
    }

    private static final String[] PITCH_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    public static String getPitchName(int midiNoteNumber) {
        if (midiNoteNumber < 0 || midiNoteNumber > 127) return "N/A";
        int octave = (midiNoteNumber / 12) - 1; // MIDI note 0 is C-1
        return PITCH_NAMES[midiNoteNumber % 12] + octave;
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PianoRoll frame = new PianoRoll(false, 1280, 720);
            // PianoRoll frame = new PianoRoll(true, 1280, 720); // Fullscreen
            frame.setVisible(true);
        });
    }
}