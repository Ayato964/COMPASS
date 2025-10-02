// PianoRoll.java (with tempo controls)

package org.codesfactory.ux.pianoroll;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import org.codesfactory.api.GenerateMeta;
import org.codesfactory.api.ModelInfo;
import org.codesfactory.api.MozartAPIClient;
import org.codesfactory.ux.pianoroll.commands.ReplaceNotesCommand;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
    private JTextField tempoField; // Added

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
        updateTempoField(); // Initialize tempo field
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

        toolBar.add(Box.createHorizontalStrut(10));
        toolBar.add(new JLabel("Tempo:"));
        toolBar.add(Box.createHorizontalStrut(5));
        tempoField = new JTextField(5);
        tempoField.setMaximumSize(new Dimension(60, 24));
        tempoField.setToolTipText("Set Tempo (BPM) and press Enter");
        tempoField.addActionListener(e -> updateTempoFromField());
        toolBar.add(tempoField);
        toolBar.add(Box.createHorizontalStrut(15));

        toolBar.add(new JLabel("Range (Measures):"));
        toolBar.add(Box.createHorizontalStrut(5));

        loopStartField = new JTextField(5);
        loopStartField.setToolTipText("Start Measure (Press Enter to set loop)");
        loopStartField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopStartField);

        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(new JLabel("-"));
        toolBar.add(Box.createHorizontalStrut(5));

        loopEndField = new JTextField(5);
        loopEndField.setToolTipText("End Measure (Press Enter to set loop)");
        loopEndField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopEndField);

        toolBar.add(Box.createHorizontalStrut(5));
        JButton setLoopButton = new JButton("Set Loop");
        setLoopButton.setToolTipText("Apply new loop range based on measures");
        setLoopButton.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(setLoopButton);

        toolBar.add(Box.createHorizontalStrut(5));
        JButton selectButton = new JButton("Select");
        selectButton.setToolTipText("Select notes in the specified measure range");
        selectButton.addActionListener(e -> selectNotesByMeasureRange());
        toolBar.add(selectButton);

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

    private void selectNotesByMeasureRange() {
        try {
            int startMeasure = Integer.parseInt(loopStartField.getText());
            int endMeasure = Integer.parseInt(loopEndField.getText());

            if (startMeasure <= 0 || endMeasure <= 0 || startMeasure > endMeasure) {
                JOptionPane.showMessageDialog(this, "Invalid measure range.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int ppqn = pianoRollView.getPpqn();
            int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
            int ticksPerMeasure = ppqn * beatsPerMeasure;

            long startTick = (long)(startMeasure - 1) * ticksPerMeasure;
            long endTick = (long)endMeasure * ticksPerMeasure;

            pianoRollView.selectNotesInRange(startTick, endTick);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid measure value. Please enter numbers only.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateLoopRangeFromFields() {
        try {
            int startMeasure = Integer.parseInt(loopStartField.getText());
            int endMeasure = Integer.parseInt(loopEndField.getText());

            if (startMeasure <= 0 || endMeasure <= 0 || startMeasure > endMeasure) {
                JOptionPane.showMessageDialog(this, "Invalid measure range.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int ppqn = pianoRollView.getPpqn();
            int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
            int ticksPerMeasure = ppqn * beatsPerMeasure;

            long startTick = (long)(startMeasure - 1) * ticksPerMeasure;
            long endTick = (long)endMeasure * ticksPerMeasure;

            pianoRollView.setLoopRange(startTick, endTick);

            if (pianoRollView.isLoopRangeVisible()) {
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
            }
            updateLoopButtonText(); // Sync UI
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid measure value. Please enter numbers only.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void updateLoopButtonText() {
        SwingUtilities.invokeLater(() -> {
            if (loopButton != null && pianoRollView != null) {
                loopButton.setSelected(pianoRollView.isLoopRangeVisible());
            }
            if (loopStartField != null && loopEndField != null && pianoRollView != null) {
                // Convert ticks back to measures
                int ppqn = pianoRollView.getPpqn();
                int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
                if (ppqn > 0 && beatsPerMeasure > 0) {
                    int ticksPerMeasure = ppqn * beatsPerMeasure;
                    long startTick = pianoRollView.getLoopStartTick();
                    long endTick = pianoRollView.getLoopEndTick();

                    int startMeasure = (int)(startTick / ticksPerMeasure) + 1;
                    long lastTickInRange = (endTick > 0) ? endTick - 1 : 0;
                    int endMeasure = (int)(lastTickInRange / ticksPerMeasure) + 1;

                    loopStartField.setText(String.valueOf(startMeasure));
                    loopEndField.setText(String.valueOf(endMeasure));

                } else {
                    // Fallback to ticks if timing info is not ready
                    loopStartField.setText(String.valueOf(pianoRollView.getLoopStartTick()));
                    loopEndField.setText(String.valueOf(pianoRollView.getLoopEndTick()));
                }
            }
        });
    }

    private void updateTempoFromField() {
        try {
            float tempo = Float.parseFloat(tempoField.getText());
            if (tempo > 0) {
                playbackManager.setTempo(tempo);
            } else {
                JOptionPane.showMessageDialog(this, "Tempo must be a positive number.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid tempo value. Please enter a number.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
        // Update the field to show the actual current tempo, in case the input was invalid or adjusted
        updateTempoField();
    }

    private void updateTempoField() {
        SwingUtilities.invokeLater(() -> {
            if (tempoField != null) {
                tempoField.setText(String.format("%.1f", playbackManager.getTempo()));
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
                playbackManager.setTempo(midiData.tempo);
                updateTempoField();
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
                MidiHandler.saveMidiFile(file, pianoRollView.getAllNotes(), pianoRollView.getPpqn(), playbackManager.getTempo());
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
            MidiHandler.saveMidiFile(currentFile, pianoRollView.getAllNotes(), pianoRollView.getPpqn(), playbackManager.getTempo());
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

        // --- Get Measure Range and Ticks from Toolbar ---
        int startMeasure;
        int endMeasure;
        long startTick;
        long endTick;
        try {
            startMeasure = Integer.parseInt(loopStartField.getText());
            endMeasure = Integer.parseInt(loopEndField.getText());

            if (startMeasure <= 0 || endMeasure <= 0 || startMeasure > endMeasure) {
                JOptionPane.showMessageDialog(this, "ツールバーの小節範囲が正しくありません。", "範囲エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }

            int ppqn = pianoRollView.getPpqn();
            int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
            int ticksPerMeasure = ppqn * beatsPerMeasure;
            // Calculate the original start tick based on the user's input
            long originalStartTick = (long)(startMeasure - 1) * ticksPerMeasure;
            // Adjust the start tick to be one measure earlier, ensuring it's not negative
            startTick = Math.max(0, originalStartTick - ticksPerMeasure);
            endTick = (long)endMeasure * ticksPerMeasure;

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ツールバーの小節範囲に数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // --- End of Get Range ---


        // --- Parameter Input Dialog ---
        JPanel panel = new JPanel(new GridLayout(0, 2, 5, 5));
        JSpinner pSpinner = new JSpinner(new SpinnerNumberModel(0.95, 0.0, 1.0, 0.01));
        JSpinner tempSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 2.0, 0.1));

        panel.add(new JLabel("p (Nucleus Sampling):"));
        panel.add(pSpinner);
        panel.add(new JLabel("Temperature:"));
        panel.add(tempSpinner);

        int result = JOptionPane.showConfirmDialog(this, panel, "Generation Parameters", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return; // User cancelled
        }

        double pValue = (Double) pSpinner.getValue();
        double tempValue = (Double) tempSpinner.getValue();
        // --- End of Dialog ---

        generateButton.setEnabled(false);
        infoLabel.setText("Generating music with " + selectedModel.getModelName() + "...");
        System.out.println("generateMusic: Starting GenerationWorker...");

        class GenerationWorker extends SwingWorker<MidiHandler.MidiData, Void> {
            private final ModelInfo model;
            private final long workerStartTick;
            private final long workerEndTick;

            public GenerationWorker(ModelInfo model, long startTick, long endTick) {
                this.model = model;
                this.workerStartTick = startTick;
                this.workerEndTick = endTick;
            }

            @Override
            protected MidiHandler.MidiData doInBackground() throws Exception {
                System.out.println("GenerationWorker: doInBackground started.");
                File tempMidiFile = File.createTempFile("compass-generate-", ".mid");
                File tempMetaFile = File.createTempFile("compass-meta-", ".json");
                try {
                    System.out.println("GenerationWorker: Temporary files created.");

                    // --- Filter notes based on measure range (using worker's ticks) ---
                    List<Note> allNotes = pianoRollView.getAllNotes();
                    List<Note> notesInRange = allNotes.stream()
                            .filter(n -> n.getStartTimeTicks() >= this.workerStartTick && n.getStartTimeTicks() < this.workerEndTick)
                            .collect(Collectors.toList());

                    System.out.println("GenerationWorker: Found " + notesInRange.size() + " notes in measure range.");

                    // Save only the notes in the specified range
                    MidiHandler.saveMidiFile(tempMidiFile, notesInRange, pianoRollView.getPpqn(), playbackManager.getTempo());
                    System.out.println("GenerationWorker: Temp MIDI file saved with notes in range.");


                    String modelType = model.getModelName();
                    int tempo = (int) playbackManager.getTempo();

                    GenerateMeta meta = new GenerateMeta(modelType, Collections.singletonList(0), tempo, "MELODY_GEM");
                    meta.setP(pValue);
                    meta.setTemperature(tempValue);

                    try (FileWriter writer = new FileWriter(tempMetaFile)) {
                        new Gson().toJson(meta, writer);
                    }
                    System.out.println("GenerationWorker: Temp meta JSON file saved.");

                    System.out.println("GenerationWorker: Calling API client...");
                    byte[] responseBytes = mozartAPIClient.generate(tempMidiFile, tempMetaFile);
                    System.out.println("GenerationWorker: API client returned.");

                    byte[] midiBytes;

                    // Check if the response is a ZIP file (PK header)
                    if (responseBytes.length > 2 && responseBytes[0] == 0x50 && responseBytes[1] == 0x4B) {
                        System.out.println("GenerationWorker: ZIP file detected. Unzipping...");
                        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBytes))) {
                            ZipEntry zipEntry = zis.getNextEntry();
                            midiBytes = null;
                            while (zipEntry != null) {
                                if (!zipEntry.isDirectory() && (zipEntry.getName().toLowerCase().endsWith(".mid") || zipEntry.getName().toLowerCase().endsWith(".midi"))) {
                                    midiBytes = zis.readAllBytes();
                                    System.out.println("GenerationWorker: Found MIDI file in ZIP: " + zipEntry.getName());
                                    break;
                                }
                                zipEntry = zis.getNextEntry();
                            }
                            if (midiBytes == null) {
                                throw new IOException("No MIDI file found in the generated ZIP archive.");
                            }
                        }
                    } else {
                        System.out.println("GenerationWorker: Assuming raw MIDI data.");
                        midiBytes = responseBytes;
                    }
                    return MidiHandler.loadMidiFromBytes(midiBytes);
                } finally {
                    System.out.println("GenerationWorker: Deleting temporary files.");
                    tempMidiFile.delete();
                    tempMetaFile.delete();
                }
            }

            @Override
            protected void done() {
                try {
                    MidiHandler.MidiData generatedData = get();
                    System.out.println("GenerationWorker: done() method finished successfully.");

                    // Call the method in PianoRollView to handle the note replacement
                    pianoRollView.replaceNotesFrom(this.workerStartTick, generatedData.notes);

                    // IMPORTANT: Force the playback manager to reload the updated notes
                    playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());

                    // Update tempo and UI
                    // playbackManager.setTempo(generatedData.tempo); // Don't update tempo from generated file, as it might be incorrect.
                    updateTempoField();
                    infoLabel.setText("Music generation complete.");
                    System.out.println("GenerationWorker: Note replacement and UI updates are complete.");

                } catch (Exception e) {
                    System.err.println("GenerationWorker: Error in done() method: " + e.getMessage());
                    e.printStackTrace();
                    infoLabel.setText("Error during music generation.");
                    JOptionPane.showMessageDialog(PianoRoll.this, "Failed to generate music: " + e.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateButton.setEnabled(true);
                }
            }
        }

        new GenerationWorker(selectedModel, startTick, endTick).execute();
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
