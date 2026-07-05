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
import java.awt.event.ActionListener;
import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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

    // 音楽生成パラメータの共有保存用スタティック変数（直近の設定を保持）
    private static double lastPValue = 0.95;
    private static double lastTempValue = 1.0;
    private static boolean lastSendKey = false;
    private static String lastSelectedKey = "C";
    private static boolean lastSendGenre = false;
    private static List<String> lastSelectedGenres = new ArrayList<>(Collections.singletonList("jazz"));
    private static boolean lastSendDensity = false;
    private static int lastDensityValue = 4;
    private static boolean lastSendThinking = true;
    private static boolean lastUseInstComp = false;
    private static String lastSelectedTargetTrackName = ""; // 直近で考慮した他トラック名

    private File currentFile = null;

    // --- Multi-track Connection Fields ---
    private Track linkedTrack;
    private MidiRegion linkedRegion;
    private List<Track> allTracks;
    private ArrangementFrame parentFrame;
    private Runnable onCloseCallback;

    // --- Constructor for Multi-track Sub-window ---
    public PianoRoll(Track track, MidiRegion region, List<Track> allTracks, ArrangementFrame parentFrame, Runnable onCloseCallback) {
        this.linkedTrack = track;
        this.linkedRegion = region;
        this.allTracks = allTracks;
        this.parentFrame = parentFrame;
        this.onCloseCallback = onCloseCallback;

        setTitle("Piano Roll - " + track.getName());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Close sub-window only
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (onCloseCallback != null) {
                    onCloseCallback.run();
                }
                if (playbackManager != null) {
                    playbackManager.close();
                }
                dispose();
            }
        });

        setLayout(new BorderLayout());

        // Initialize core components
        pianoRollView = new PianoRollView(this);
        playbackManager = new PlaybackManager(pianoRollView);
        mozartAPIClient = new MozartAPIClient();

        // Bind shared notes list from parent track
        pianoRollView.setNotesList(track.getNotes());

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

        // Window size and location
        setSize(1100, 700);
        setLocationRelativeTo(null);

        // Calculate loop measures based on region start/end ticks
        int ppqn = pianoRollView.getPpqn();
        int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
        int ticksPerMeasure = ppqn * beatsPerMeasure;
        int startMeasure = (int) (region.getStartTick() / ticksPerMeasure) + 1;
        int endMeasure = (int) (region.getEndTick() / ticksPerMeasure);

        loopStartField.setText(String.valueOf(startMeasure));
        loopEndField.setText(String.valueOf(endMeasure));

        // Enforce loop range & position
        pianoRollView.setLoopRange(region.getStartTick(), region.getEndTick());
        pianoRollView.setVisibleTickRange(region.getStartTick(), region.getEndTick());
        playbackManager.setLoop(region.getStartTick(), region.getEndTick());
        playbackManager.setTickPosition(region.getStartTick());

        // Final setup
        updateUndoRedoMenuItems(false, false);
        updateLoopButtonText();
        updateTempoField();
        loadModels();

        // Ctrl+G: 直接生成（ダイアログ非表示）
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK), "generateDirect"
        );
        getRootPane().getActionMap().put("generateDirect", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (generateButton.isEnabled()) {
                    generateMusic(false);
                }
            }
        });

        // Ctrl+Shift+G: 設定ダイアログを開いて生成
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_G, java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.SHIFT_DOWN_MASK), "generateWithSettings"
        );
        getRootPane().getActionMap().put("generateWithSettings", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (generateButton.isEnabled()) {
                    generateMusic(true);
                }
            }
        });
    }

    // --- Constructor ---
    public PianoRoll(boolean isFullScreen, int width, int height) {
        this.linkedTrack = new Track("Track 1");
        this.allTracks = new ArrayList<>();
        this.allTracks.add(linkedTrack);

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

        // --- Left Group (Playback Controls & Zoom) ---
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

        toolBar.add(Box.createHorizontalStrut(15));

        toolBar.add(new JLabel("Zoom:"));
        toolBar.add(Box.createHorizontalStrut(3));
        JButton zoomInHBtn = new JButton("↔+");
        zoomInHBtn.setToolTipText("Horizontal Zoom In (Ctrl+Wheel)");
        zoomInHBtn.setFocusPainted(false);
        zoomInHBtn.addActionListener(e -> pianoRollView.zoomInHorizontal());
        toolBar.add(zoomInHBtn);
        JButton zoomOutHBtn = new JButton("↔-");
        zoomOutHBtn.setToolTipText("Horizontal Zoom Out (Ctrl+Wheel)");
        zoomOutHBtn.setFocusPainted(false);
        zoomOutHBtn.addActionListener(e -> pianoRollView.zoomOutHorizontal());
        toolBar.add(zoomOutHBtn);
        toolBar.add(Box.createHorizontalStrut(3));
        JButton zoomInVBtn = new JButton("↕+");
        zoomInVBtn.setToolTipText("Vertical Zoom In (Ctrl+Shift+Wheel)");
        zoomInVBtn.setFocusPainted(false);
        zoomInVBtn.addActionListener(e -> pianoRollView.zoomInVertical());
        toolBar.add(zoomInVBtn);
        JButton zoomOutVBtn = new JButton("↕-");
        zoomOutVBtn.setToolTipText("Vertical Zoom Out (Ctrl+Shift+Wheel)");
        zoomOutVBtn.setFocusPainted(false);
        zoomOutVBtn.addActionListener(e -> pianoRollView.zoomOutVertical());
        toolBar.add(zoomOutVBtn);

        // Center alignment Glue
        toolBar.add(Box.createHorizontalGlue());

        // --- Center Group (AI Model Selection - Large) ---
        toolBar.add(new JLabel("AI Model:"));
        toolBar.add(Box.createHorizontalStrut(5));
        modelComboBox = new JComboBox<>();
        modelComboBox.setEnabled(false);
        modelComboBox.setMinimumSize(new Dimension(150, 26));
        modelComboBox.setPreferredSize(new Dimension(220, 26));
        modelComboBox.setMaximumSize(new Dimension(280, 26));
        toolBar.add(modelComboBox);
        toolBar.add(Box.createHorizontalStrut(5));

        generateButton = new JButton("✨ Generate");
        generateButton.setToolTipText("Generate Music with AI");
        generateButton.setFocusPainted(false);
        generateButton.setEnabled(false);
        generateButton.addActionListener(e -> generateMusic(true));
        toolBar.add(generateButton);

        // Right alignment Glue
        toolBar.add(Box.createHorizontalGlue());

        // --- Right Group (Quantize, Time Sig, Range) ---
        toolBar.add(new JLabel("Quantize:"));
        toolBar.add(Box.createHorizontalStrut(3));
        String[] quantizeOptions = {"1/1", "1/2", "1/4", "1/8", "1/16", "1/32", "1/64"};
        JComboBox<String> quantizeComboBox = new JComboBox<>(quantizeOptions);
        quantizeComboBox.setSelectedItem("1/16");
        quantizeComboBox.setMaximumSize(new Dimension(70, 24));
        toolBar.add(quantizeComboBox);

        JCheckBox tripletCheckBox = new JCheckBox("3連");
        toolBar.add(tripletCheckBox);

        ActionListener quantizeListener = e -> {
            updateQuantize(quantizeComboBox.getSelectedItem().toString(), tripletCheckBox.isSelected());
        };
        quantizeComboBox.addActionListener(quantizeListener);
        tripletCheckBox.addActionListener(quantizeListener);
        toolBar.add(Box.createHorizontalStrut(10));

        toolBar.add(new JLabel("Sig:"));
        toolBar.add(Box.createHorizontalStrut(3));
        String[] timeSigOptions = {"4/4", "3/4"};
        JComboBox<String> timeSigComboBox = new JComboBox<>(timeSigOptions);
        timeSigComboBox.setSelectedItem("4/4");
        timeSigComboBox.setMaximumSize(new Dimension(60, 24));
        timeSigComboBox.addActionListener(e -> {
            updateTimeSignature(timeSigComboBox.getSelectedItem().toString());
        });
        toolBar.add(timeSigComboBox);
        toolBar.add(Box.createHorizontalStrut(15));

        toolBar.add(new JLabel("Range:"));
        toolBar.add(Box.createHorizontalStrut(3));
        loopStartField = new JTextField(2);
        loopStartField.setMaximumSize(new Dimension(30, 24));
        loopStartField.setToolTipText("Start Measure (Press Enter to set loop)");
        loopStartField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopStartField);

        toolBar.add(new JLabel("-"));
        loopEndField = new JTextField(2);
        loopEndField.setMaximumSize(new Dimension(30, 24));
        loopEndField.setToolTipText("End Measure (Press Enter to set loop)");
        loopEndField.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(loopEndField);
        toolBar.add(Box.createHorizontalStrut(3));

        JButton setLoopButton = new JButton("Set");
        setLoopButton.setToolTipText("Apply new loop range based on measures");
        setLoopButton.addActionListener(e -> updateLoopRangeFromFields());
        toolBar.add(setLoopButton);

        JButton selectButton = new JButton("Sel");
        selectButton.setToolTipText("Select notes in the specified measure range");
        selectButton.addActionListener(e -> selectNotesByMeasureRange());
        toolBar.add(selectButton);

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
            long savedTick = 0;
            if (playbackManager.getSequencer() != null) {
                savedTick = playbackManager.getSequencer().getTickPosition();
            }

            List<Note> notesForPlayback = pianoRollView.getAllNotes();
            if (notesForPlayback.isEmpty()) {
                infoLabel.setText("Add some notes to play.");
                return;
            }

            // Always load the latest notes before playing
            playbackManager.loadNotes(notesForPlayback, pianoRollView.getPpqn());

            if (pianoRollView.isLoopRangeVisible()) {
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
            } else {
                playbackManager.clearLoop();
            }
            playbackManager.setTickPosition(savedTick);
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

    private void updateQuantize(String divisionStr, boolean isTriplet) {
        if (pianoRollView != null) {
            int division = 16;
            switch (divisionStr) {
                case "1/1": division = 1; break;
                case "1/2": division = 2; break;
                case "1/4": division = 4; break;
                case "1/8": division = 8; break;
                case "1/16": division = 16; break;
                case "1/32": division = 32; break;
                case "1/64": division = 64; break;
            }
            pianoRollView.setQuantize(division, isTriplet);
        }
    }

    private void updateTimeSignature(String sigStr) {
        if (pianoRollView != null) {
            int beats = 4;
            if ("3/4".equals(sigStr)) {
                beats = 3;
            }
            pianoRollView.setBeatsPerMeasure(beats);
        }
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
                List<MidiHandler.MidiTrackInfo> trackList = MidiHandler.loadMidiTracks(file);
                if (trackList.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "No notes found in the MIDI file.", "File Warning", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                
                MidiHandler.MidiTrackInfo selectedTrackInfo = null;
                if (trackList.size() == 1) {
                    selectedTrackInfo = trackList.get(0);
                } else {
                    MidiHandler.MidiTrackInfo[] choices = trackList.toArray(new MidiHandler.MidiTrackInfo[0]);
                    MidiHandler.MidiTrackInfo choice = (MidiHandler.MidiTrackInfo) JOptionPane.showInputDialog(
                        this,
                        "Select a track to load into the Piano Roll:",
                        "Multiple Tracks Detected",
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        choices,
                        choices[0]
                    );
                    if (choice == null) return; // User cancelled
                    selectedTrackInfo = choice;
                }

                MidiHandler.MidiData midiData = MidiHandler.loadMidiFile(file);
                pianoRollView.loadNotes(selectedTrackInfo.notes, midiData.ppqn, midiData.totalTicks);
                playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                playbackManager.setTempo(midiData.tempo);
                updateTempoField();
                currentFile = file;
                setTitle("COMPASS - " + file.getName() + " [" + selectedTrackInfo.name + "]");
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
                        modelComboBox.addItem(new ModelInfo("MAINTENANCE", "メンテナンス中"));
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
                    infoLabel.setText("Error loading AI models. Offline or maintenance.");
                    modelComboBox.removeAllItems();
                    modelComboBox.addItem(new ModelInfo("MAINTENANCE", "メンテナンス中"));
                    modelComboBox.setEnabled(false);
                    generateButton.setEnabled(false);
                }
            }
        };
        worker.execute();
    }

    public void generateMusic(boolean showDialog) {
        ModelInfo selectedModel = (ModelInfo) modelComboBox.getSelectedItem();
        if (selectedModel == null || selectedModel.getModelName() == null || selectedModel.getModelName().equals("MAINTENANCE")) {
            JOptionPane.showMessageDialog(this, "有効なAIモデルを選択してください。", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // --- Get Target Measure Range and Ticks from Toolbar ---
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
            
            startTick = (long)(startMeasure - 1) * ticksPerMeasure;
            endTick = (long)endMeasure * ticksPerMeasure;

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "ツールバーの小節範囲に数値を入力してください。", "入力エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- Past / Future Context Extraction (Max 8 Measures) ---
        int ppqn = pianoRollView.getPpqn();
        int beatsPerMeasure = pianoRollView.getBeatsPerMeasure();
        int ticksPerMeasure = ppqn * beatsPerMeasure;
        long maxContextTicks = (long) ticksPerMeasure * 8; // 8小節

        List<Note> allNotes = pianoRollView.getAllNotes();

        // 過去コンテキスト
        long pastStart = Math.max(0, startTick - maxContextTicks);
        long pastEnd = startTick;
        List<Note> pastNotes = allNotes.stream()
                .filter(n -> n.getStartTimeTicks() >= pastStart && n.getStartTimeTicks() < pastEnd)
                .collect(Collectors.toList());

        // 未来コンテキスト
        long futureStart = endTick;
        long futureEnd = endTick + maxContextTicks;
        List<Note> futureNotes = allNotes.stream()
                .filter(n -> n.getStartTimeTicks() >= futureStart && n.getStartTimeTicks() < futureEnd)
                .collect(Collectors.toList());

        boolean hasPast = !pastNotes.isEmpty();
        boolean hasFuture = !futureNotes.isEmpty();
        boolean hasContext = hasPast || hasFuture;

        // 他トラックの条件コンテキストの候補 (同じ時間範囲にノートが存在するトラック)
        List<Track> candidateTracks = new ArrayList<>();
        if (allTracks != null) {
            for (Track t : allTracks) {
                if (t != linkedTrack) {
                    boolean hasNotesInRange = t.getNotes().stream()
                            .anyMatch(n -> n.getStartTimeTicks() >= startTick && n.getStartTimeTicks() < endTick);
                    if (hasNotesInRange) {
                        candidateTracks.add(t);
                    }
                }
            }
        }
        boolean hasConditions = !candidateTracks.isEmpty();

        // 実際の生成に使うパラメータ変数群（デフォルトは直近の保存値）
        double pValue = lastPValue;
        double tempValue = lastTempValue;
        boolean sendKey = !hasContext || lastSendKey;
        String selectedKey = lastSelectedKey;
        boolean sendGenre = !hasContext || lastSendGenre;
        List<String> selectedGenres = new ArrayList<>(lastSelectedGenres);
        boolean sendDensity = !hasContext || lastSendDensity;
        int densityValue = lastDensityValue;
        boolean sendThinking = hasContext && lastSendThinking;
        boolean useInstComp = hasConditions && lastUseInstComp;
        Track targetTrackForConditions = null;

        if (showDialog) {
            // --- Parameters Form UI ---
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            // p Slider Setup (Diversity)
            JPanel pPanel = new JPanel(new BorderLayout(5, 0));
            pPanel.setOpaque(false);
            JSlider pSlider = new JSlider(0, 100, (int)(pValue * 100));
            JLabel pValLabel = new JLabel(String.format("%.2f", pValue));
            pValLabel.setPreferredSize(new Dimension(30, 20));
            pSlider.addChangeListener(e -> pValLabel.setText(String.format("%.2f", pSlider.getValue() / 100.0)));
            pPanel.add(pSlider, BorderLayout.CENTER);
            pPanel.add(pValLabel, BorderLayout.EAST);

            gbc.gridx = 0; gbc.gridy = 0;
            panel.add(new JLabel("Variety (Diversity):"), gbc);
            gbc.gridx = 1;
            panel.add(pPanel, gbc);

            // Temperature (Creativity) Slider Setup
            JPanel tempPanel = new JPanel(new BorderLayout(5, 0));
            tempPanel.setOpaque(false);
            JSlider tempSlider = new JSlider(1, 20, (int)(tempValue * 10));
            JLabel tempValLabel = new JLabel(String.format("%.1f", tempValue));
            tempValLabel.setPreferredSize(new Dimension(30, 20));
            tempSlider.addChangeListener(e -> tempValLabel.setText(String.format("%.1f", tempSlider.getValue() / 10.0)));
            tempPanel.add(tempSlider, BorderLayout.CENTER);
            tempPanel.add(tempValLabel, BorderLayout.EAST);

            gbc.gridx = 0; gbc.gridy = 1;
            panel.add(new JLabel("Creativity (Temperature):"), gbc);
            gbc.gridx = 1;
            panel.add(tempPanel, gbc);

            // Key
            JCheckBox keyCheckBox = new JCheckBox("Specify Key:");
            keyCheckBox.setSelected(lastSendKey);
            JComboBox<String> keyComboBox = new JComboBox<>(new String[]{
                    "C", "Cm", "C#", "C#m", "D", "Dm", "D#", "D#m", "E", "Em", "F", "Fm",
                    "F#", "F#m", "G", "Gm", "G#", "G#m", "A", "Am", "A#", "A#m", "B", "Bm"
            });
            keyComboBox.setSelectedItem(selectedKey);
            gbc.gridy = 2;
            if (hasContext) {
                gbc.gridx = 0; panel.add(keyCheckBox, gbc);
                gbc.gridx = 1; panel.add(keyComboBox, gbc);
                keyComboBox.setEnabled(lastSendKey);
                keyCheckBox.addActionListener(e -> keyComboBox.setEnabled(keyCheckBox.isSelected()));
            } else {
                gbc.gridx = 0; panel.add(new JLabel("Key:"), gbc);
                gbc.gridx = 1; panel.add(keyComboBox, gbc);
            }

            // Genre AutoComplete TextFields
            JCheckBox genreCheckBox = new JCheckBox("Specify Genre(s):");
            genreCheckBox.setSelected(lastSendGenre);
            String[] genres = {
                    "80s", "90s", "alternative", "ambient", "blues", "celtic", "chillout",
                    "classical", "country", "dance", "drumnbass", "easylistening", "electronic",
                    "electropop", "experimental", "folk", "funk", "hiphop", "house", "indie",
                    "instrumentalpop", "instrumentalrock", "jazz", "jazzfusion", "latin", "lounge",
                    "metal", "newage", "orchestral", "pop", "popfolk", "poprock", "punkrock",
                    "reggae", "rock", "soundtrack", "swing", "symphonic", "synthpop", "techno",
                    "trance", "world"
            };
            JTextField genreField1 = new JTextField(8);
            JTextField genreField2 = new JTextField(8);
            if (selectedGenres.size() > 0) genreField1.setText(selectedGenres.get(0));
            if (selectedGenres.size() > 1) genreField2.setText(selectedGenres.get(1));
            setupAutoComplete(genreField1, genres);
            setupAutoComplete(genreField2, genres);

            JPanel genreInputPanel = new JPanel(new GridLayout(1, 2, 5, 0));
            genreInputPanel.setOpaque(false);
            genreInputPanel.add(genreField1);
            genreInputPanel.add(genreField2);

            gbc.gridy = 3;
            if (hasContext) {
                gbc.gridx = 0; panel.add(genreCheckBox, gbc);
                gbc.gridx = 1; panel.add(genreInputPanel, gbc);
                genreField1.setEnabled(lastSendGenre);
                genreField2.setEnabled(lastSendGenre);
                genreCheckBox.addActionListener(e -> {
                    boolean selected = genreCheckBox.isSelected();
                    genreField1.setEnabled(selected);
                    genreField2.setEnabled(selected);
                });
            } else {
                gbc.gridx = 0; panel.add(new JLabel("Genre(s):"), gbc);
                gbc.gridx = 1; panel.add(genreInputPanel, gbc);
            }

            // Note Density Slider Setup
            JPanel densityPanel = new JPanel(new BorderLayout(5, 0));
            densityPanel.setOpaque(false);
            JSlider densitySlider = new JSlider(1, 10, densityValue);
            JLabel densityValLabel = new JLabel(String.valueOf(densityValue));
            densityValLabel.setPreferredSize(new Dimension(30, 20));
            densitySlider.addChangeListener(e -> densityValLabel.setText(String.valueOf(densitySlider.getValue())));
            densityPanel.add(densitySlider, BorderLayout.CENTER);
            densityPanel.add(densityValLabel, BorderLayout.EAST);

            // Note Density
            JCheckBox densityCheckBox = new JCheckBox("Specify Note Density:");
            densityCheckBox.setSelected(lastSendDensity);
            gbc.gridy = 4;
            if (hasContext) {
                gbc.gridx = 0; panel.add(densityCheckBox, gbc);
                gbc.gridx = 1; panel.add(densityPanel, gbc);
                densitySlider.setEnabled(lastSendDensity);
                densityValLabel.setEnabled(lastSendDensity);
                densityCheckBox.addActionListener(e -> {
                    boolean enabled = densityCheckBox.isSelected();
                    densitySlider.setEnabled(enabled);
                    densityValLabel.setEnabled(enabled);
                });
            } else {
                gbc.gridx = 0; panel.add(new JLabel("Note Density (1-10):"), gbc);
                gbc.gridx = 1; panel.add(densityPanel, gbc);
            }

            // Thinking (CoT)
            JCheckBox thinkingCheckBox = new JCheckBox("Enable CoT (Thinking)");
            thinkingCheckBox.setSelected(lastSendThinking);
            gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 2;
            if (hasContext) {
                panel.add(thinkingCheckBox, gbc);
            }

            // inst_comp
            JCheckBox instCompCheckBox = new JCheckBox("Consider other tracks (inst_comp)");
            instCompCheckBox.setSelected(lastUseInstComp);
            gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2;

            JPanel tracksPanel = new JPanel();
            tracksPanel.setLayout(new BoxLayout(tracksPanel, BoxLayout.Y_AXIS));
            tracksPanel.setOpaque(false);
            tracksPanel.setBorder(BorderFactory.createEmptyBorder(5, 25, 5, 5));

            ButtonGroup group = new ButtonGroup();
            List<JRadioButton> trackRadioButtons = new ArrayList<>();
            for (Track t : candidateTracks) {
                JRadioButton tRb = new JRadioButton(t.getName() + " (" + t.getInstrument() + ")");
                boolean shouldSelect = false;
                if (!lastSelectedTargetTrackName.isEmpty()) {
                    shouldSelect = t.getName().equals(lastSelectedTargetTrackName);
                }
                if (!shouldSelect && trackRadioButtons.isEmpty()) {
                    shouldSelect = true;
                }
                tRb.setSelected(shouldSelect);
                tRb.setEnabled(lastUseInstComp);
                tRb.setOpaque(false);
                group.add(tRb);
                trackRadioButtons.add(tRb);
                tracksPanel.add(tRb);
            }

            instCompCheckBox.addActionListener(e -> {
                boolean enabled = instCompCheckBox.isSelected();
                for (JRadioButton tRb : trackRadioButtons) {
                    tRb.setEnabled(enabled);
                }
            });

            if (hasConditions) {
                panel.add(instCompCheckBox, gbc);
                gbc.gridy = 7;
                panel.add(tracksPanel, gbc);
            }

            int optionResult = JOptionPane.showConfirmDialog(this, panel, "Generation Parameters", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (optionResult != JOptionPane.OK_OPTION) {
                return; // User cancelled
            }

            // UIから値を取得
            pValue = pSlider.getValue() / 100.0;
            tempValue = tempSlider.getValue() / 10.0;
            sendKey = !hasContext || keyCheckBox.isSelected();
            selectedKey = (String) keyComboBox.getSelectedItem();
            sendGenre = !hasContext || genreCheckBox.isSelected();
            
            selectedGenres.clear();
            if (sendGenre) {
                String g1 = genreField1.getText().trim().toLowerCase();
                String g2 = genreField2.getText().trim().toLowerCase();
                if (!g1.isEmpty()) selectedGenres.add(g1);
                if (!g2.isEmpty()) selectedGenres.add(g2);
            }
            
            sendDensity = !hasContext || densityCheckBox.isSelected();
            densityValue = densitySlider.getValue();
            sendThinking = hasContext && thinkingCheckBox.isSelected();
            useInstComp = hasConditions && instCompCheckBox.isSelected();

            if (useInstComp) {
                for (int i = 0; i < candidateTracks.size(); i++) {
                    if (trackRadioButtons.get(i).isSelected()) {
                        targetTrackForConditions = candidateTracks.get(i);
                        break;
                    }
                }
            }

            // 保存
            lastPValue = pValue;
            lastTempValue = tempValue;
            lastSendKey = sendKey;
            lastSelectedKey = selectedKey;
            lastSendGenre = sendGenre;
            lastSelectedGenres = new ArrayList<>(selectedGenres);
            lastSendDensity = sendDensity;
            lastDensityValue = densityValue;
            lastSendThinking = sendThinking;
            lastUseInstComp = useInstComp;
            if (targetTrackForConditions != null) {
                lastSelectedTargetTrackName = targetTrackForConditions.getName();
            } else {
                lastSelectedTargetTrackName = "";
            }
        } else {
            // ダイアログを表示しない場合は保存された last*** から復元
            if (useInstComp) {
                for (Track t : candidateTracks) {
                    if (t.getName().equals(lastSelectedTargetTrackName)) {
                        targetTrackForConditions = t;
                        break;
                    }
                }
                if (targetTrackForConditions == null && !candidateTracks.isEmpty()) {
                    targetTrackForConditions = candidateTracks.get(0);
                    lastSelectedTargetTrackName = targetTrackForConditions.getName();
                }
            }
        }

        // conditionsNotes の最終抽出
        List<Note> conditionsNotes = new ArrayList<>();
        if (useInstComp && targetTrackForConditions != null) {
            int mappedChannel = targetTrackForConditions.isMonophonic() ? 1 : 0;
            List<Note> tNotes = targetTrackForConditions.getNotes().stream()
                    .filter(n -> n.getStartTimeTicks() >= startTick && n.getStartTimeTicks() < endTick)
                    .map(n -> new Note(n.getPitch(), n.getStartTimeTicks(), n.getDurationTicks(), n.getVelocity(), mappedChannel))
                    .collect(Collectors.toList());
            conditionsNotes.addAll(tNotes);
        }



        final double finalPValue = pValue;
        final double finalTempValue = tempValue;
        final boolean finalSendKey = sendKey;
        final String finalSelectedKey = selectedKey;
        final boolean finalSendGenre = sendGenre;
        final List<String> finalSelectedGenres = selectedGenres;
        final boolean finalSendDensity = sendDensity;
        final int finalDensityValue = densityValue;
        final boolean finalSendThinking = sendThinking;

        // --- Set up variables for GenerationWorker ---
        generateButton.setEnabled(false);
        infoLabel.setText("Generating music with " + selectedModel.getModelName() + "...");
        System.out.println("generateMusic: Starting GenerationWorker...");

        class GenerationWorker extends SwingWorker<MidiHandler.MidiData, Void> {
            private final ModelInfo model;
            private final long workerStartTick;
            private final long workerEndTick;
            private final List<Note> wPastNotes;
            private final List<Note> wFutureNotes;
            private final List<Note> wConditionsNotes;
            private final long wPastStart;
            private final long wFutureStart;
            private final boolean useInstComp;

            public GenerationWorker(ModelInfo model, long startTick, long endTick,
                                    List<Note> pastNotes, List<Note> futureNotes, List<Note> conditionsNotes,
                                    long pastStart, long futureStart, boolean useInstComp) {
                this.model = model;
                this.workerStartTick = startTick;
                this.workerEndTick = endTick;
                this.wPastNotes = pastNotes;
                this.wFutureNotes = futureNotes;
                this.wConditionsNotes = conditionsNotes;
                this.wPastStart = pastStart;
                this.wFutureStart = futureStart;
                this.useInstComp = useInstComp;
            }

            @Override
            protected MidiHandler.MidiData doInBackground() throws Exception {
                System.out.println("GenerationWorker: doInBackground started.");
                File tempPastMidiFile = null;
                File tempFutureMidiFile = null;
                File tempConditionsMidiFile = null;
                File tempMetaFile = File.createTempFile("compass-meta-", ".json");

                try {
                    System.out.println("GenerationWorker: Temporary files created.");

                    String targetInst = linkedTrack.isMonophonic() ? "SAX" : "PIANO";

                    // 過去コンテキスト保存
                    if (!wPastNotes.isEmpty()) {
                        tempPastMidiFile = File.createTempFile("compass-past-", ".mid");
                        List<Note> shiftedPast = new ArrayList<>();
                        for (Note n : wPastNotes) {
                            shiftedPast.add(new Note(n.getPitch(), n.getStartTimeTicks() - wPastStart, n.getDurationTicks(), n.getVelocity(), n.getChannel()));
                        }
                        MidiHandler.saveMidiFile(tempPastMidiFile, shiftedPast, pianoRollView.getPpqn(), 120.0f, targetInst);
                        System.out.println("GenerationWorker: Saved past context with " + wPastNotes.size() + " notes.");
                    }

                    // 未来コンテキスト保存
                    if (!wFutureNotes.isEmpty()) {
                        tempFutureMidiFile = File.createTempFile("compass-future-", ".mid");
                        List<Note> shiftedFuture = new ArrayList<>();
                        for (Note n : wFutureNotes) {
                            shiftedFuture.add(new Note(n.getPitch(), n.getStartTimeTicks() - wFutureStart, n.getDurationTicks(), n.getVelocity(), n.getChannel()));
                        }
                        MidiHandler.saveMidiFile(tempFutureMidiFile, shiftedFuture, pianoRollView.getPpqn(), 120.0f, targetInst);
                        System.out.println("GenerationWorker: Saved future context with " + wFutureNotes.size() + " notes.");
                    }

                    // 条件コンテキスト保存
                    if (useInstComp && !wConditionsNotes.isEmpty()) {
                        tempConditionsMidiFile = File.createTempFile("compass-conditions-", ".mid");
                        List<Note> shiftedConditions = new ArrayList<>();
                        for (Note n : wConditionsNotes) {
                            shiftedConditions.add(new Note(n.getPitch(), n.getStartTimeTicks() - workerStartTick, n.getDurationTicks(), n.getVelocity(), n.getChannel()));
                        }
                        MidiHandler.saveConditionsMidiFile(tempConditionsMidiFile, shiftedConditions, pianoRollView.getPpqn(), 120.0f);
                        System.out.println("GenerationWorker: Saved conditions context with " + wConditionsNotes.size() + " notes.");
                    }

                    // meta_jsonの構築 (テンポはAPIとの整合性のために120BPM固定)
                    String modelType = model.getModelName();
                    int tempoVal = 120;

                    String taskStr = "Meta2MIDI";
                    if (useInstComp) {
                        taskStr = "inst_comp";
                    } else if (tempPastMidiFile != null && tempFutureMidiFile != null) {
                        taskStr = "infill";
                    }

                    GenerateMeta meta = new GenerateMeta(modelType, Collections.singletonList(targetInst), tempoVal, taskStr);
                    meta.setP(finalPValue);
                    meta.setTemperature(finalTempValue);

                    if (finalSendKey) {
                        meta.setKey(finalSelectedKey);
                    }
                    if (finalSendGenre) {
                        meta.setGenre(finalSelectedGenres);
                    }
                    if (finalSendDensity) {
                        Map<String, Integer> densities = new HashMap<>();
                        densities.put(targetInst, finalDensityValue);
                        meta.setGenNoteDense(densities);
                    }
                    if (finalSendThinking) {
                        meta.setThinking(true);
                    }

                    // 生成小節数を設定
                    int totalTargetTicks = (int) (workerEndTick - workerStartTick);
                    int measures = totalTargetTicks / ticksPerMeasure;
                    if (measures < 1) measures = 1;
                    if (measures > 8) measures = 8;
                    meta.setGenfieldMeasure(measures);

                    try (FileWriter writer = new FileWriter(tempMetaFile)) {
                        new Gson().toJson(meta, writer);
                    }
                    System.out.println("GenerationWorker: Meta JSON configuration prepared.");

                    System.out.println("GenerationWorker: Calling API client...");
                    byte[] responseBytes = mozartAPIClient.generate(tempPastMidiFile, tempFutureMidiFile, tempConditionsMidiFile, tempMetaFile);
                    System.out.println("GenerationWorker: API client response received.");

                    byte[] midiBytes;
                    if (responseBytes.length > 2 && responseBytes[0] == 0x50 && responseBytes[1] == 0x4B) {
                        System.out.println("GenerationWorker: ZIP archive detected. Decompressing...");
                        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(responseBytes))) {
                            ZipEntry zipEntry = zis.getNextEntry();
                            midiBytes = null;
                            while (zipEntry != null) {
                                if (!zipEntry.isDirectory() && (zipEntry.getName().toLowerCase().endsWith(".mid") || zipEntry.getName().toLowerCase().endsWith(".midi"))) {
                                    midiBytes = zis.readAllBytes();
                                    System.out.println("GenerationWorker: Found generated midi file inside ZIP: " + zipEntry.getName());
                                    break;
                                }
                                zipEntry = zis.getNextEntry();
                            }
                            if (midiBytes == null) {
                                throw new IOException("No MIDI file found inside generated ZIP.");
                            }
                        }
                    } else {
                        System.out.println("GenerationWorker: Loading raw MIDI response.");
                        midiBytes = responseBytes;
                    }

                    return MidiHandler.loadMidiFromBytes(midiBytes);

                } finally {
                    System.out.println("GenerationWorker: Cleaning up temp files...");
                    if (tempPastMidiFile != null) tempPastMidiFile.delete();
                    if (tempFutureMidiFile != null) tempFutureMidiFile.delete();
                    if (tempConditionsMidiFile != null) tempConditionsMidiFile.delete();
                    if (tempMetaFile != null) tempMetaFile.delete();
                }
            }

            @Override
            protected void done() {
                try {
                    MidiHandler.MidiData generatedData = get();
                    System.out.println("GenerationWorker: done() successfully retrieved MIDI data.");
                    
                    // 選択範囲に限定してノートをマージ (PPQNスケーリング対応)
                    pianoRollView.replaceNotesInRange(this.workerStartTick, this.workerEndTick, generatedData.notes, generatedData.ppqn);
                    
                    // Force playback reload
                    playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                    updateTempoField();
                    infoLabel.setText("Music generation complete.");
                } catch (Exception e) {
                    System.err.println("GenerationWorker done() error: " + e.getMessage());
                    e.printStackTrace();
                    infoLabel.setText("Error during music generation.");
                    JOptionPane.showMessageDialog(PianoRoll.this, "Failed to generate music: " + e.getMessage(), "Generation Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    generateButton.setEnabled(true);
                }
            }
        }

        new GenerationWorker(selectedModel, startTick, endTick, pastNotes, futureNotes, conditionsNotes, pastStart, futureStart, useInstComp).execute();
    }

    // --- Utility ---
    private static final String[] PITCH_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    public static String getPitchName(int midiNoteNumber) {
        if (midiNoteNumber < 0 || midiNoteNumber > 127) return "N/A";
        int octave = (midiNoteNumber / 12) - 1;
        return PITCH_NAMES[midiNoteNumber % 12] + octave;
    }

    public PlaybackManager getPlaybackManager() {
        return this.playbackManager;
    }

    public void updatePlaybackHeadOnly(long tick) {
        if (pianoRollView != null) {
            SwingUtilities.invokeLater(() -> {
                pianoRollView.setPlaybackTick(tick);
                autoScrollToPlayHead(tick);
            });
        }
    }

    public void updateTempoAndSync(double bpm) {
        if (playbackManager != null) {
            playbackManager.setTempo((float) bpm);
        }
    }

    private void autoScrollToPlayHead(long tick) {
        if (pianoRollView == null || scrollPane == null) return;
        int px = pianoRollView.tickToX(tick);
        JViewport viewport = scrollPane.getViewport();
        Point viewPos = viewport.getViewPosition();
        int viewWidth = viewport.getWidth();
        
        if (px >= viewPos.x + viewWidth || px < viewPos.x) {
            int newViewX = Math.max(0, px - (int)(viewWidth * 0.1));
            int maxViewX = pianoRollView.getWidth() - viewWidth;
            newViewX = Math.max(0, Math.min(newViewX, maxViewX));
            viewPos.x = newViewX;
            viewport.setViewPosition(viewPos);
        }
    }

    private void setupAutoComplete(JTextField textField, String[] suggestions) {
        JPopupMenu popup = new JPopupMenu();
        JList<String> list = new JList<>();
        popup.add(new JScrollPane(list));
        popup.setFocusable(false);

        textField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (popup.isVisible()) {
                    int selected = list.getSelectedIndex();
                    int size = list.getModel().getSize();
                    if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                        if (size > 0) {
                            list.setSelectedIndex((selected + 1) % size);
                            list.ensureIndexIsVisible(list.getSelectedIndex());
                        }
                        e.consume();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                        if (size > 0) {
                            list.setSelectedIndex((selected - 1 + size) % size);
                            list.ensureIndexIsVisible(list.getSelectedIndex());
                        }
                        e.consume();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_TAB) {
                        if (size > 0 && selected != -1) {
                            textField.setText(list.getSelectedValue());
                            popup.setVisible(false);
                        }
                        e.consume();
                    } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                        if (size > 0 && selected != -1) {
                            textField.setText(list.getSelectedValue());
                            popup.setVisible(false);
                        }
                        e.consume();
                    }
                }
            }
        });

        textField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void update() {
                SwingUtilities.invokeLater(() -> {
                    String text = textField.getText().trim().toLowerCase();
                    if (text.isEmpty()) {
                        popup.setVisible(false);
                        return;
                    }
                    List<String> filtered = new ArrayList<>();
                    for (String s : suggestions) {
                        if (s.toLowerCase().contains(text)) {
                            filtered.add(s);
                        }
                    }
                    if (filtered.isEmpty()) {
                        popup.setVisible(false);
                    } else {
                        list.setListData(filtered.toArray(new String[0]));
                        list.setSelectedIndex(0);
                        
                        if (!popup.isVisible() && textField.isShowing()) {
                            popup.show(textField, 0, textField.getHeight());
                            textField.requestFocusInWindow();
                        }
                        popup.pack();
                    }
                });
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { update(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { update(); }
        });
        
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String val = list.getSelectedValue();
                    if (val != null) {
                        textField.setText(val);
                        popup.setVisible(false);
                    }
                }
            }
        });
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
