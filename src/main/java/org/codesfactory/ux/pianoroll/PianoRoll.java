// PianoRoll.java (再修正版)

package org.codesfactory.ux.pianoroll;

import javax.sound.midi.InvalidMidiDataException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List; // ★★★ java.util.List をインポート ★★★
// import java.awt.List; // ← 不要、もしあれば削除

public class PianoRoll extends JFrame {

    // --- Fields ---
    private final PianoRollView pianoRollView; // View (final)
    private final PlaybackManager playbackManager; // Playback (final)
    private final JScrollPane scrollPane;
    private final JLabel infoLabel;
    private final JFileChooser fileChooser;
    private JButton playButton;
    private JButton stopButton;
    private JButton loopButton;
    private JMenuItem undoItem;
    private JMenuItem redoItem;
    private JMenuItem deleteAllItem;

    private File currentFile = null; // 現在開いている/保存したファイル

    // --- Constructor ---
    public PianoRoll(boolean isFullScreen, int width, int height) {
        setTitle("Java Piano Roll");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // Close operation handled in listener
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

        // Setup UI components
        scrollPane = new JScrollPane(pianoRollView);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        // Adjust scroll increments (consider doing this after the component is realized/sized)
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(30);

        // Create UI elements (Toolbar, StatusPanel, MenuBar)
        JToolBar toolBar = createToolbar();
        JPanel statusPanel = createStatusPanel();
        infoLabel = new JLabel("No note selected."); // Initialize infoLabel here
        statusPanel.add(infoLabel);
        JMenuBar menuBar = createMenuBar();
        // Assign menu items to fields for later access (enable/disable)
        undoItem = menuBar.getMenu(1).getItem(0); // Assuming Edit is the second menu (index 1), Undo is the first item (index 0)
        redoItem = menuBar.getMenu(1).getItem(1); // Redo is the second item
        deleteAllItem = menuBar.getMenu(1).getItem(3); // Delete All is the fourth item (after separator)

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
            // pack(); // Optional: Size based on component preferred sizes before setting explicit size
            setSize(width, height);
        }
        setLocationRelativeTo(null); // Center on screen

        // Set initial state for Undo/Redo menu items
        updateUndoRedoMenuItems(false, false); // Initially disabled
    }

    // --- UI Creation Methods ---

    private JToolBar createToolbar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        // Play Button
        playButton = new JButton("Play"); // Assign to field
        playButton.addActionListener(e -> {
            if (playbackManager.isPlaying()) {
                playbackManager.stop();
                // State update (button text) will be triggered by PlaybackManager calling updatePlayButtonState
            } else {
                pianoRollView.requestFocusInWindow();
                List<Note> notesForPlayback = pianoRollView.getAllNotes(); // Uses java.util.List
                System.out.println("Play Button: Loading " + notesForPlayback.size() + " notes for playback.");
                playbackManager.loadNotes(notesForPlayback, pianoRollView.getPpqn());

                if (pianoRollView.isLoopRangeVisible()) {
                    playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                } else {
                    playbackManager.clearLoop();
                }
                playbackManager.play();
                // State update (button text) called here for immediate feedback
                updatePlayButtonState(true);
            }
        });
        toolBar.add(playButton);

        // Stop Button
        stopButton = new JButton("Stop"); // Assign to field
        stopButton.addActionListener(e -> {
            playbackManager.stop();
            // State update (button text) will be triggered by PlaybackManager calling updatePlayButtonState
        });
        toolBar.add(stopButton);

        // Loop Button
        loopButton = new JButton("Loop Off"); // Assign to field
        loopButton.addActionListener(e -> {
            if (pianoRollView.isLoopRangeVisible()) {
                pianoRollView.clearLoopRange();
                playbackManager.clearLoop();
                // updateLoopButtonText(); // View change triggers repaint which should be sufficient? Or call explicitly.
            } else {
                pianoRollView.setLoopRange(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                playbackManager.setLoop(pianoRollView.getLoopStartTick(), pianoRollView.getLoopEndTick());
                // updateLoopButtonText();
            }
            updateLoopButtonText(); // Call after action regardless
        });
        toolBar.add(loopButton);

        // Zoom Buttons
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

    private JPanel createStatusPanel() {
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // infoLabel is initialized and added in the constructor after this panel is created
        return statusPanel;
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // --- File Menu ---
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
        exitItem.addActionListener(e -> handleWindowClosing()); // Call the same closing logic
        fileMenu.add(exitItem);
        menuBar.add(fileMenu);

        // --- Edit Menu ---
        JMenu editMenu = new JMenu("Edit");
        JMenuItem localUndoItem = new JMenuItem("Undo"); // Use local variable first
        localUndoItem.addActionListener(e -> {
            if (pianoRollView != null) {
                pianoRollView.getUndoManager().undo();
            }
        });
        localUndoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        localUndoItem.setEnabled(false);
        editMenu.add(localUndoItem);

        JMenuItem localRedoItem = new JMenuItem("Redo"); // Use local variable first
        localRedoItem.addActionListener(e -> {
            if (pianoRollView != null) {
                pianoRollView.getUndoManager().redo();
            }
        });
        String osName = System.getProperty("os.name", "").toLowerCase(); // Default to empty string if null
        if (osName.startsWith("mac") || osName.startsWith("darwin")) {
            localRedoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        } else {
            localRedoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        }
        localRedoItem.setEnabled(false);
        editMenu.add(localRedoItem);

        editMenu.addSeparator();
        JMenuItem localDeleteAllItem = new JMenuItem("Delete All Notes"); // Use local variable first
        localDeleteAllItem.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(
                    PianoRoll.this,
                    "Are you sure you want to delete all notes?",
                    "Confirm Delete All",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (response == JOptionPane.YES_OPTION && pianoRollView != null) {
                pianoRollView.deleteAllNotesAndRecordCommand();
            }
        });
        editMenu.add(localDeleteAllItem);
        menuBar.add(editMenu);

        return menuBar; // Return the created menu bar
    }

    // --- Public Methods Called by Other Classes (e.g., PianoRollView, UndoManager) ---

    public void updateUndoRedoMenuItems(boolean canUndo, boolean canRedo) {
        // Ensure menu items are initialized before updating
        if (undoItem == null || redoItem == null) {
            System.err.println("Error: Undo/Redo menu items not initialized yet.");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            undoItem.setEnabled(canUndo);
            redoItem.setEnabled(canRedo);
        });
    }

    public void updatePlayButtonState(boolean isPlaying) {
        SwingUtilities.invokeLater(() -> {
            if (playButton != null) {
                playButton.setText(isPlaying ? "Pause" : "Play");
            }
        });
    }

    public void updateLoopButtonText() {
        SwingUtilities.invokeLater(() -> {
            if (loopButton == null || pianoRollView == null) return;
            loopButton.setText(pianoRollView.isLoopRangeVisible() ? "Loop On" : "Loop Off");
        });
    }

    public void updateNoteInfo(Note note) {
        SwingUtilities.invokeLater(() -> {
            if (infoLabel == null || pianoRollView == null) return;
            int selectedCount = pianoRollView.getSelectedNotesCount();
            if (selectedCount > 1) {
                infoLabel.setText(selectedCount + " notes selected");
            } else if (selectedCount == 1 && note != null) {
                // Use a default PPQN if pianoRollView.getPpqn() might be 0 initially
                int ppqnForDisplay = Math.max(1, pianoRollView.getPpqn());
                infoLabel.setText(MessageFormat.format("Selected: Pitch {0} ({1}), Start {2} ({3} beat), Duration {4} ({5} beat), Vel {6}",
                        note.getPitch(), getPitchName(note.getPitch()),
                        note.getStartTimeTicks(), String.format("%.2f", (double) note.getStartTimeTicks() / ppqnForDisplay),
                        note.getDurationTicks(), String.format("%.2f", (double) note.getDurationTicks() / ppqnForDisplay),
                        note.getVelocity()));
            } else {
                infoLabel.setText("No note selected.");
            }
        });
    }


    // --- File Handling ---

    private void openMidiFile() {
        if (playbackManager.isPlaying()) playbackManager.stop(); // Stop playback before opening

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                MidiHandler.MidiData midiData = MidiHandler.loadMidiFile(file);
                pianoRollView.loadNotes(midiData.notes, midiData.ppqn, midiData.totalTicks); // Load notes into view (clears undo history)
                playbackManager.loadNotes(pianoRollView.getAllNotes(), pianoRollView.getPpqn()); // Load notes for playback
                currentFile = file;
                setTitle("Java Piano Roll - " + file.getName());
            } catch (InvalidMidiDataException | IOException | NullPointerException ex) { // Catch potential NPE from loading too
                JOptionPane.showMessageDialog(this, "Error opening MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
                currentFile = null;
                setTitle("Java Piano Roll");
                // Optionally clear the view if loading failed significantly
                // pianoRollView.loadNotes(new ArrayList<>(), MidiHandler.DEFAULT_PPQN, 0);
                // playbackManager.loadNotes(new ArrayList<>(), MidiHandler.DEFAULT_PPQN);
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

        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
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
                // Save using the current notes from the view
                MidiHandler.saveMidiFile(file, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
                currentFile = file;
                setTitle("Java Piano Roll - " + file.getName());
                JOptionPane.showMessageDialog(this, "MIDI file saved successfully as " + file.getName(), "Save Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (InvalidMidiDataException | IOException | NullPointerException ex) { // Catch potential NPE
                JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }
    }

    private void saveCurrentMidiFile() {
        if (currentFile == null) { // If file hasn't been saved yet, perform Save As
            saveMidiFileAs();
            return;
        }

        if (playbackManager.isPlaying()) playbackManager.stop();

        try {
            // Save to the known file location
            MidiHandler.saveMidiFile(currentFile, pianoRollView.getAllNotes(), pianoRollView.getPpqn());
            System.out.println("File saved to " + currentFile.getPath()); // Console feedback is less intrusive
            // Optional: Display status bar message briefly
            // infoLabel.setText("File saved.");
            // Timer timer = new Timer(2000, e -> updateNoteInfo(pianoRollView.selectedNote)); // Restore info after 2 sec
            // timer.setRepeats(false);
            // timer.start();

        } catch (InvalidMidiDataException | IOException | NullPointerException ex) { // Catch potential NPE
            JOptionPane.showMessageDialog(this, "Error saving MIDI file: " + ex.getMessage(), "File Error", JOptionPane.ERROR_MESSAGE);
            ex.printStackTrace();
        }
    }

    // --- Utility ---
    private static final String[] PITCH_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    public static String getPitchName(int midiNoteNumber) {
        if (midiNoteNumber < 0 || midiNoteNumber > 127) return "N/A";
        int octave = (midiNoteNumber / 12) - 1; // MIDI 0 = C-1
        return PITCH_NAMES[midiNoteNumber % 12] + octave;
    }

    // --- Window Closing Logic ---
    private void handleWindowClosing() {
        // TODO: Add check for unsaved changes and prompt user if necessary
        System.out.println("Window closing...");
        if (playbackManager != null) {
            playbackManager.close();
        }
        dispose(); // Close the window
        System.exit(0); // Terminate the application
    }

    // --- Main Method ---
    public static void main(String[] args) {
        // Set Look and Feel (optional)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Couldn't set system look and feel.");
        }

        SwingUtilities.invokeLater(() -> {
            PianoRoll frame = new PianoRoll(false, 1280, 720);
            frame.setVisible(true);
        });
    }
}