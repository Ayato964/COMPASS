// PianoRoll.java (Corrected version 2)

package org.codesfactory.ux.pianoroll;

import com.formdev.flatlaf.FlatDarkLaf;
import org.codesfactory.api.GenerateMeta;
import org.codesfactory.api.ModelInfo;
import org.codesfactory.api.MozartAPIClient;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PianoRoll extends JFrame {

    // --- Fields ---
    private final PianoRollView pianoRollView;
    private final PlaybackManager playbackManager;
    private final JScrollPane scrollPane;
    private final JLabel infoLabel;
    private final JFileChooser fileChooser;
    private JButton playButton;
    private JButton stopButton;
    private JButton loopButton;
    private JMenuItem undoItem;
    private JMenuItem redoItem;
    private JMenuItem deleteAllItem;
    private JTextField loopStartField;
    private JTextField loopEndField;

    // --- MozartAPI Fields ---
    private final MozartAPIClient mozartAPIClient;
    private JComboBox<ModelInfo> modelComboBox;
    private JButton generateButton;

    private File currentFile = null;

    // --- Constructor ---
    public PianoRoll(boolean isFullScreen, int width, int height) {
        setTitle("COMPASS");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                handleWindowClosing();
            }
        });

        setLayout(new BorderLayout());

        // Initialize core components
        pianoRollView = new PianoRollView(this);
        playbackManager = new PlaybackManager(pianoRollView);
        mozartAPIClient = new MozartAPIClient();

        // Setup UI components
        scrollPane = new JScrollPane(pianoRollView);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(30);

        // Create UI elements
        JToolBar toolBar = createToolbar();
        JPanel statusPanel = createStatusPanel();
        infoLabel = new JLabel("No note selected.");
        statusPanel.add(infoLabel);
        JMenuBar menuBar = createMenuBar();
        undoItem = menuBar.getMenu(1).getItem(0);
        redoItem = menuBar.getMenu(1).getItem(1);
        deleteAllItem = menuBar.getMenu(1).getItem(3);

        add(toolBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
        setJMenuBar(menuBar);

        // Initialize File Chooser
        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("MIDI Files (*.mid, *.midi)", "mid", "midi"));

        // Set window size and location
        if (isFullScreen) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            setSize(width, height);
        }
        setLocationRelativeTo(null);

        // Final setup
        updateUndoRedoMenuItems(false, false);
        updateLoopButtonText(); // Initialize loop fields
        loadModels();
    }

    // --- UI Creation Methods ---

    private JToolBar createToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        toolBar.setLayout(new BoxLayout(toolBar, BoxLayout.X_AXIS));

        Font iconFont = new Font("Segoe UI Emoji", Font.PLAIN, 18);

        // --- Left Group ---
        loopButton = new JButton("🔁");
        loopButton.setFont(iconFont);
        loopButton.setToolTipText("Toggle Loop");
        loopButton.setFocusPainted(false);
        loopButton.addActionListener(e -> {
            if (pianoRollView.isLoopRangeVisible()) {
                pianoRollView.clearLoopRange();
                playbackManager.clearLoop();
            } else {
                pianoRollView.setLoopRange(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
            }
            updateLoopButtonText();
        });
        toolBar.add(loopButton);
        toolBar.add(Box.createHorizontalStrut(15));

        JButton zoomInHBtn = new JButton("+");
        zoomInHBtn.setToolTipText("Horizontal Zoom In (Ctrl+Wheel)");
        zoomInHBtn.setFocusPainted(false);
        zoomInHBtn.addActionListener(e -> pianoRollView.zoomInHorizontal());
        toolBar.add(zoomInHBtn);

        JButton zoomOutHBtn = new JButton("-");
        zoomOutHBtn.setToolTipText("Horizontal Zoom Out (Ctrl+Wheel)");
        zoomOutHBtn.setFocusPainted(false);
        zoomOutHBtn.addActionListener(e -> pianoRollView.zoomOutHorizontal());
        toolBar.add(zoomOutHBtn);

        toolBar.add(Box.createHorizontalStrut(10));

        JButton zoomInVBtn = new JButton("+");
        zoomInVBtn.setToolTipText("Vertical Zoom In (Ctrl+Shift+Wheel)");
        zoomInVBtn.setFocusPainted(false);
        zoomInVBtn.addActionListener(e -> pianoRollView.zoomInVertical());
        toolBar.add(zoomInVBtn);

        JButton zoomOutVBtn = new JButton("-");
        zoomOutVBtn.setToolTipText("Vertical Zoom Out (Ctrl+Shift+Wheel)");
        zoomOutVBtn.setFocusPainted(false);
        zoomOutVBtn.addActionListener(e -> pianoRollView.zoomOutVertical());
        toolBar.add(zoomOutVBtn);

        // --- Center Group ---
        toolBar.add(Box.createHorizontalGlue());

        generateButton = new JButton("✨");
        generateButton.setFont(iconFont);
        generateButton.setToolTipText("Generate Music with AI");
        generateButton.setFocusPainted(false);
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> generateMusic());
        toolBar.add(generateButton);
        toolBar.add(Box.createHorizontalStrut(5));

        playButton = new JButton("▶");
        playButton.setFont(iconFont);
        playButton.setToolTipText("Play / Pause (Space)");
        playButton.setFocusPainted(false);
        playButton.addActionListener(e -> togglePlayback());
        toolBar.add(playButton);
        toolBar.add(Box.createHorizontalStrut(5));

        stopButton = new JButton("■");
        stopButton.setFont(iconFont);
        stopButton.setToolTipText("Stop and Reset");
        stopButton.setFocusPainted(false);
        stopButton.addActionListener(e -> playbackManager.stop());
        toolBar.add(stopButton);

        toolBar.add(Box.createHorizontalStrut(15));
        toolBar.add(new JLabel("Loop Range:"));
        toolBar.add(Box.createHorizontalStrut(5));

        loopStartField = new JTextField(8);
        loopStartField.setToolTipText("Loop Start Tick (Press Enter to apply)");
        loopStartField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopStartField);

        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(new JLabel("-"));
        toolBar.add(Box.createHorizontalStrut(5));

        loopEndField = new JTextField(8);
        loopEndField.setToolTipText("Loop End Tick (Press Enter to apply)");
        loopEndField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopEndField);

        toolBar.add(Box.createHorizontalStrut(5));
        JButton setLoopButton = new JButton("Set");
        setLoopButton.setToolTipText("Apply new loop range");
        setLoopButton.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(setLoopButton);

        // --- Right Group ---
        toolBar.add(Box.createHorizontalGlue());

        toolBar.add(new JLabel("AI Model: "));
        modelComboBox = new JComboBox<>();
        modelComboBox.setEnabled(false);
        modelComboBox.setMaximumSize(new Dimension(200, 30));
        toolBar.add(modelComboBox);

        return toolBar;
    }

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        return statusPanel;
    }

    private JMenuBar createMenuBar() {
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
        exitItem.addActionListener(e -> handleWindowClosing());
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = new JMenu("Edit");
        JMenuItem localUndoItem = new JMenuItem("Undo");
        localUndoItem.addActionListener(e -> {
            if (pianoRollView != null) pianoRollView.getUndoManager().undo();
        });
        localUndoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        localUndoItem.setEnabled(false);
        editMenu.add(localUndoItem);

        JMenuItem localRedoItem = new JMenuItem("Redo");
        localRedoItem.addActionListener(e -> {
            if (pianoRollView != null) pianoRollView.getUndoManager().redo();
        });
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.startsWith("mac") || osName.startsWith("darwin")) {
            localRedoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        } else {
            localRedoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        localRedoItem.setEnabled(false);
        editMenu.add(localRedoItem);

        editMenu.addSeparator();
        JMenuItem localDeleteAllItem = new JMenuItem("Delete All Notes");
        localDeleteAllItem.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(PianoRoll.this, "Are you sure you want to delete all notes?", "Confirm Delete All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && pianoRollView != null) {
                pianoRollView.deleteAllNotesAndRecordCommand();
            }
        });
        editMenu.add(localDeleteAllItem);
        menuBar.add(editMenu);

        return menuBar;
    }

    // --- Playback Control ---

    public void togglePlayback() {
        if (playbackManager.isPlaying()) {
            playbackManager.pause();
        } else {
            List<Note> notesForPlayback = pianoRollView.getAllNotes();
            if (notesForPlayback.isEmpty()) {
                infoLabel.setText("Add some notes to play.");
                return;
            }

            // Load notes only if the sequence is not already set
            if (playbackManager.getSequence() == null) { // A way to check if notes are loaded
                playbackManager.loadNotes(notesForPlayback, pianoRollView.getPpqn());
            }

            if (pianoRollView.isLoopRangeVisible()) {
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
            } else {
                playbackManager.clearLoop();
            }
            playbackManager.play();
        }
    }

    public void setPlaybackTickPosition(long tick) {
        if (playbackManager != null) {
            playbackManager.setTickPosition(tick);
        }
    }

    // --- Public Methods Called by Other Classes ---

    public void updateUndoRedoMenuItems(boolean canUndo, boolean canRedo) {
        if (undoItem == null || redoItem == null) return;
        SwingUtilities.invokeLater(() -> {
            undoItem.setEnabled(canUndo);
            redoItem.setEnabled(canRedo);
        });
    }

    public void updatePlayButtonState(boolean isPlaying) {
        SwingUtilities.invokeLater(() -> {
            if (playButton != null) playButton.setText(isPlaying ? "❚❚" : "▶");
        });
    }

    private void updateLoopRangeFromFields() {
        try {
            long startTick = Long.parseLong(loopStartField.getText());
            long endTick = Long.parseLong(loopEndField.getText());

            pianoRollView.setLoopRange(startTick, endTick);

            if (pianoRollView.isLoopRangeVisible()) {
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
            }
            updateLoopButtonText(); // Sync UI
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid tick value. Please enter numbers only.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void updateLoopButtonText() {
        SwingUtilities.invokeLater(() -> {
            if (loopButton != null && pianoRollView != null) {
                loopButton.setSelected(pianoRollView.isLoopRangeVisible());
            }
            if (loopStartField != null && loopEndField != null && pianoRollView != null) {
                loopStartField.setText(String.valueOf(pianoRollView.getLoopStartTick()));
                loopEndField.setText(String.valueOf(pianoRollView.getLoopEndTick()));
            }
        });
    }

    public void updateNoteInfo(Note note) {
        SwingUtilities.invokeLater(() -> {
            if (infoLabel == null || pianoRollView == null) return;
            int selectedCount = pianoRollView.getSelectedNotesCount();
            if (selectedCount > 1) {
                infoLabel.setText(selectedCount + " notes selected");
            } else if (selectedCount == 1 && note != null) {
                int ppqn = Math.max(1, pianoRollView.getPpqn());
                infoLabel.setText(MessageFormat.format("Pitch {0} ({1}), Start {2} ({3}), Duration {4} ({5}), Vel {6}",
                        note.getPitch(), getPitchName(note.getPitch()),
                        note.getStartTimeTicks(), String.format("%.2f", (double) note.getStartTimeTicks() / ppqn),
                        note.getDurationTicks(), String.format("%.2f", (double) note.getDurationTicks() / ppqn),
                        note.getVelocity()));
            } else {
                infoLabel.setText("No note selected.");
            }
        });
    }

    // --- File Handling ---

    private void openMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                MidiHandler.MidiData midiData = MidiHandler.loadMidiFile(file);
                pianoRollView.loadNotes(midiData.notes, midiData.ppqn, midiData.totalTicks);
                playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                currentFile = file;
                setTitle("COMPASS - " + file.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error opening MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                currentFile = null;
                setTitle("COMPASS");
            }
        }
    }

    private void saveMidiFileAs() {
        if (playbackManager.isPlaying()) playbackManager.stop();
        if (currentFile != null) {
            fileChooser.setCurrentDirectory(currentFile.getParentFile());
            fileChooser.setSelectedFile(new File(currentFile.getName()));
        } else {
            fileChooser.setSelectedFile(new File("Untitled.mid"));
        }
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".mid") && !file.getName().toLowerCase().endsWith(".midi")) {
                file = new File(file.getParentFile(), file.getName() + ".mid");
            }
            if (file.exists()) {
                int response = JOptionPane.showConfirmDialog(this, "File '" + file.getName() + "' already exists. Replace?", "Confirm Save As", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) return;
            }
            try {
                MidiHandler.saveMidiFile(file, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                currentFile = file;
                setTitle("COMPASS - " + file.getName());
                JOptionPane.showMessageDialog(this, "MIDI file saved as " + file.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void saveCurrentMidiFile() {
        if (currentFile == null) {
            saveMidiFileAs();
            return;
        }
        if (playbackManager.isPlaying()) playbackManager.stop();
        try {
            MidiHandler.saveMidiFile(currentFile, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
            System.out.println("File saved to " + currentFile.getPath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // --- MozartAPI Methods ---

    private void loadModels() {
        infoLabel.setText("Loading AI models...");
        SwingWorker<List<ModelInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ModelInfo> doInBackground() throws Exception {
                return mozartAPIClient.getModelInfo();
            }

            @Override
            protected void done() {
                try {
                    List<ModelInfo> models = get();
                    modelComboBox.removeAllItems();
                    if (models.isEmpty()) {
                        infoLabel.setText("No AI models found.");
                        modelComboBox.addItem(new ModelInfo() {
                            @Override
                            public String toString() { return "No models available"; }
                        });
                        modelComboBox.setEnabled(false);
                        generateButton.setEnabled(false);
                    } else {
                        for (ModelInfo model : models) {
                            modelComboBox.addItem(model);
                        }
                        infoLabel.setText("AI Models loaded.");
                        modelComboBox.setEnabled(true);
                        generateButton.setEnabled(true);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                    infoLabel.setText("Error loading AI models.");
                    JOptionPane.showMessageDialog(PianoRoll.this, "Could not connect to MozartAPI: " + e.getMessage(), "API Error", JOptionPane.ERROR_MESSAGE);
                    modelComboBox.setEnabled(false);
                    generateButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    private void generateMusic() {
        ModelInfo selectedModel = (ModelInfo) modelComboBox.getSelectedItem();
        if (selectedModel == null || selectedModel.getModelName() == null) {
            JOptionPane.showMessageDialog(this, "Please select an AI model first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        generateButton.setEnabled(false);
        infoLabel.setText("Generating music with " + selectedModel.getModelName() + "...");

        SwingWorker<MidiHandler.MidiData, Void> worker = new SwingWorker<>() {
            @Override
            protected MidiHandler.MidiData doInBackground() throws Exception {
                File tempMidiFile = File.createTempFile("compass-generate-", ".mid");
                try {
                    MidiHandler.saveMidiFile(tempMidiFile, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                    String modelType = selectedModel.getModelName();
                    int tempo = (int) playbackManager.getTempo();
                    GenerateMeta meta = new GenerateMeta(modelType, Collections.singletonList(56), tempo, "generate");
                    byte[] generatedMidiBytes = mozartAPIClient.generate(tempMidiFile, meta);
                    return MidiHandler.loadMidiFromBytes(generatedMidiBytes);
                } finally {
                    tempMidiFile.delete();
                }
            }

            @Override
            protected void done() {
                try {
                    MidiHandler.MidiData generatedData = get();
                    pianoRollView.loadNotes(generatedData.notes, generatedData.ppqn, generatedData.totalTicks);
                    infoLabel.setText("Music generation complete.");
                } catch (Exception e) {
                    e.printStackTrace();
                    infoLabel.setText("Error during music generation.");
                    JOptionPane.showMessageDialog(PianoRoll.this, "Failed to generate music: " + e.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                }
                generateButton.setEnabled(true);
            }
        };
        worker.execute();
    }

    // --- Utility ---
    private static final String[] PITCH_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    public static String getPitchName(int midiNoteNumber) {
        if (midiNoteNumber < 0 || midiNoteNumber > 127) return "N/A";
        int octave = (midiNoteNumber / 12) - 1;
        return PITCH_NAMES[midiNoteNumber % 12] + octave;
    }

    // --- Window Closing Logic ---
    private void handleWindowClosing() {
        System.out.println("Window closing...");
        if (playbackManager != null) {
            playbackManager.close();
        }
        dispose();
        System.exit(0);
    }

    // --- Main Method ---
    public static void main(String[] args) {
        FlatDarkLaf.setup(); // Apply modern dark theme

        SwingUtilities.invokeLater(() -> {
            PianoRoll frame = new PianoRoll(false, 1280, 720);
            frame.setVisible(true);
        });
    }
}