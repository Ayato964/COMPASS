package org.codesfactory.ux.pianoroll;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PianoRollView extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener {

    // --- Constants ---
    public static final int MIN_PITCH = 0;
    public static final int MAX_PITCH = 127;
    public static final int KEY_WIDTH = 70;
    public static final int RULER_HEIGHT = 30;
    public static final int CONTROLLER_LANE_HEIGHT = 80; // Placeholder for velocity, etc.
    public static final Color DARK_BACKGROUND_COLOR = new Color(43, 43, 43);
    public static final Color GRID_LINE_COLOR_DARK = new Color(60, 60, 60);
    public static final Color GRID_LINE_COLOR_LIGHT = new Color(80, 80, 80);
    public static final Color WHITE_KEY_COLOR = Color.WHITE;
    public static final Color BLACK_KEY_COLOR = Color.BLACK;
    public static final Color NOTE_COLOR = new Color(200, 70, 70);
    public static final Color NOTE_BORDER_COLOR = new Color(150, 50, 50);
    public static final Color SELECTED_NOTE_COLOR = new Color(250, 120, 120);
    public static final Color SELECTED_NOTE_BORDER_COLOR = new Color(200, 90, 90);
    public static final Color PLAYBACK_HEAD_COLOR = Color.GREEN;
    public static final Color LOOP_RANGE_COLOR = new Color(0, 100, 200, 50); // Semi-transparent blue

    // --- Drawing Parameters ---
    private double pixelsPerTick = 0.05;
    private int noteHeight = 12;

    // --- MIDI Data & Timing ---
    private int ppqn = MidiHandler.DEFAULT_PPQN;
    private int beatsPerMeasure = 4;
    private int beatUnit = 4;
    private List<Note> notes = new ArrayList<>();
    private long totalTicks = (long) ppqn * beatsPerMeasure * 16; // Default 16 measures

    // --- Selection & Interaction ---
    private Note selectedNote = null;
    private Point dragStartPoint = null;
    private Note dragNoteOriginal = null; // Store original state of note being dragged/resized
//    private enum DragMode { NONE, MOVE, RESIZE_END }
    private enum DragMode { NONE, MOVE, RESIZE_END, PITCH_ONLY }
    private DragMode currentDragMode = DragMode.NONE;
    private int resizeHandleSensitivity = 5;

    // 長押し判定用
    private Timer longPressTimer;
    private final int LONG_PRESS_DELAY = 300; // 長押しと判定するまでの時間 (ミリ秒)
    private boolean isLongPress = false;

    // --- Playback & Loop ---
    private long playbackTick = 0;
    private boolean showLoopRange = false;
    private long loopStartTick = 0;
    private long loopEndTick = (long) ppqn * beatsPerMeasure * 4; // Default 4 measures loop

    // --- Other ---
    private PianoRoll parentFrame; // To update info label

    public PianoRollView(PianoRoll parent) {
        this.parentFrame = parent;
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        setBackground(DARK_BACKGROUND_COLOR);
        updatePreferredSize();
        // 長押し判定タイマーの初期化
        longPressTimer = new Timer(LONG_PRESS_DELAY, e -> {
            if (selectedNote != null && dragStartPoint != null) { // マウスボタンが押されたままで、ノートが選択されている場合
                isLongPress = true;
                currentDragMode = DragMode.PITCH_ONLY; // 長押しが確定したらPITCH_ONLYモードに
                // Optional: カーソル変更などの視覚的フィードバック
                // setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)); // 例: 上下移動カーソル
                System.out.println("Long press detected! Mode: PITCH_ONLY");
            }
        });
        longPressTimer.setRepeats(false);
    }
    // PianoRollView.java の中に以下を追加

    public boolean isLoopRangeVisible() {
        return this.showLoopRange;
    }

    public void loadNotes(List<Note> newNotes, int newPpqn, long newTotalTicks) {
        this.notes = new ArrayList<>(newNotes); // Create a mutable copy
        this.ppqn = newPpqn;
        this.beatsPerMeasure = 4; // Assuming 4/4 for now, could be read from MIDI
        this.beatUnit = 4;
        this.totalTicks = Math.max(newTotalTicks, (long)ppqn * beatsPerMeasure * 16); // Ensure minimum length
        this.selectedNote = null;
        updatePreferredSize();
        repaint();
        if (parentFrame != null) parentFrame.updateNoteInfo(null);
    }

    public List<Note> getAllNotes() {
        return new ArrayList<>(this.notes); // Return a copy
    }

    public int getPpqn() {
        return ppqn;
    }

    private void updatePreferredSize() {
        int preferredWidth = KEY_WIDTH + (int) (totalTicks * pixelsPerTick);
        int preferredHeight = RULER_HEIGHT + (MAX_PITCH - MIN_PITCH + 1) * noteHeight + CONTROLLER_LANE_HEIGHT;
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        revalidate();
    }

    // --- Coordinate Conversion ---
    private boolean isBlackKey(int midiNoteNumber) {
        int noteInOctave = midiNoteNumber % 12;
        return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 ||
                noteInOctave == 8 || noteInOctave == 10;
    }

    private int pitchToY(int midiNoteNumber) {
        return RULER_HEIGHT + (MAX_PITCH - midiNoteNumber) * noteHeight;
    }

    private int yToPitch(int y) {
        if (y < RULER_HEIGHT || y >= RULER_HEIGHT + (totalPitches() * noteHeight) ) return -1; // Outside note area
        return MAX_PITCH - ((y - RULER_HEIGHT) / noteHeight);
    }
    private int totalPitches() { return MAX_PITCH - MIN_PITCH + 1; }


    private int tickToX(long tick) {
        return KEY_WIDTH + (int) (tick * pixelsPerTick);
    }

    private long xToTick(int x) {
        if (x < KEY_WIDTH) return 0;
        return Math.max(0, (long) ((x - KEY_WIDTH) / pixelsPerTick));
    }

    private long snapToGrid(long tick, int snapDivision) { // snapDivision: 4 for quarter, 8 for eighth, etc.
        if (snapDivision <= 0) return tick;
        long ticksPerSnap = (long)ppqn * 4 / beatUnit / (snapDivision / 4); // Assuming beatUnit is quarter note
        return (Math.round((double)tick / ticksPerSnap)) * ticksPerSnap;
    }

    // --- Painting ---
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = getBounds();

        g2d.setColor(DARK_BACKGROUND_COLOR);
        g2d.fillRect(clip.x, clip.y, clip.width, clip.height);

        drawRuler(g2d, clip);
        drawPianoKeys(g2d, clip);
        drawGrid(g2d, clip);
        if (showLoopRange) drawLoopRange(g2d, clip);
        drawNotes(g2d, clip);
        drawControllerLane(g2d, clip); // Basic controller lane
        drawPlaybackHead(g2d, clip);
    }

    private void drawRuler(Graphics2D g2d, Rectangle clip) {
        g2d.setColor(Color.DARK_GRAY.brighter());
        g2d.fillRect(0, 0, getWidth(), RULER_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawLine(0, RULER_HEIGHT -1, getWidth(), RULER_HEIGHT -1);
        g2d.drawLine(KEY_WIDTH -1, 0, KEY_WIDTH-1, RULER_HEIGHT);

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        int ticksPerMeasure = ppqn * beatsPerMeasure;

        long startTick = Math.max(0, xToTick(clip.x - KEY_WIDTH));
        long endTick = xToTick(clip.x + clip.width - KEY_WIDTH) + ticksPerMeasure;
        endTick = Math.min(endTick, totalTicks);


        for (long currentTick = 0; currentTick <= endTick; currentTick += ppqn / 4) { // Check every 16th for measure/beat lines
            if (currentTick % ticksPerMeasure == 0) { // Measure line
                int x = tickToX(currentTick);
                if (x >= KEY_WIDTH && x >= clip.x && x <= clip.x + clip.width) {
                    g2d.setColor(Color.WHITE);
                    g2d.drawLine(x, RULER_HEIGHT - 10, x, RULER_HEIGHT);
                    g2d.drawString(String.valueOf(currentTick / ticksPerMeasure + 1), x + 2, RULER_HEIGHT - 12);
                }
            } else if (currentTick % ppqn == 0) { // Beat line (assuming ppqn is a quarter note)
                int x = tickToX(currentTick);
                if (x >= KEY_WIDTH && x >= clip.x && x <= clip.x + clip.width && pixelsPerTick * ppqn > 10) {
                    g2d.setColor(Color.LIGHT_GRAY);
                    g2d.drawLine(x, RULER_HEIGHT - 5, x, RULER_HEIGHT);
                }
            }
        }
    }

    private void drawPianoKeys(Graphics2D g2d, Rectangle clip) {
        g2d.setFont(new Font("Arial", Font.PLAIN, Math.max(8, noteHeight - 4)));
        int firstVisibleY = Math.max(RULER_HEIGHT, clip.y);
        int lastVisibleY = Math.min(RULER_HEIGHT + totalPitches() * noteHeight, clip.y + clip.height);

        for (int pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch++) {
            int y = pitchToY(pitch);
            if (y + noteHeight < firstVisibleY || y > lastVisibleY) continue;

            if (isBlackKey(pitch)) {
                g2d.setColor(BLACK_KEY_COLOR);
                g2d.fillRect(0, y, KEY_WIDTH * 2 / 3, noteHeight);
            } else {
                g2d.setColor(WHITE_KEY_COLOR);
                g2d.fillRect(0, y, KEY_WIDTH, noteHeight);
                g2d.setColor(Color.GRAY);
                g2d.drawRect(0, y, KEY_WIDTH, noteHeight);
                if (pitch % 12 == 0) { // C notes
                    g2d.setColor(Color.DARK_GRAY);
                    String pitchName = "C" + (pitch / 12 -1); // MIDI 0 = C-1
                    g2d.drawString(pitchName, 5, y + noteHeight - 3);
                }
            }
        }
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawLine(KEY_WIDTH -1, RULER_HEIGHT, KEY_WIDTH -1, getHeight() - CONTROLLER_LANE_HEIGHT);
    }

    private void drawGrid(Graphics2D g2d, Rectangle clip) {
        int gridTopY = RULER_HEIGHT;
        int gridBottomY = getHeight() - CONTROLLER_LANE_HEIGHT;

        // Vertical lines (time)
        int ticksPerBeat = ppqn * 4 / beatUnit;
        int ticksPerMeasure = ticksPerBeat * beatsPerMeasure;

        long startTick = Math.max(0, xToTick(Math.max(KEY_WIDTH, clip.x)));
        long endTick = xToTick(clip.x + clip.width) + ticksPerBeat;
        endTick = Math.min(endTick, totalTicks);


        for (long currentTick = 0; currentTick <= endTick; currentTick += ticksPerBeat / 4) { // 16th note grid
            int x = tickToX(currentTick);
            if (x < KEY_WIDTH || x < clip.x || x > clip.x + clip.width) continue;

            if (currentTick % ticksPerMeasure == 0) g2d.setColor(GRID_LINE_COLOR_LIGHT);
            else if (currentTick % ticksPerBeat == 0) g2d.setColor(GRID_LINE_COLOR_DARK);
            else if (pixelsPerTick * (ticksPerBeat / 4.0) > 3) g2d.setColor(GRID_LINE_COLOR_DARK.darker());
            else continue; // Too dense to draw
            g2d.drawLine(x, gridTopY, x, gridBottomY);
        }

        // Horizontal lines (pitch)
        int firstVisiblePitchY = Math.max(gridTopY, clip.y);
        int lastVisiblePitchY = Math.min(gridBottomY, clip.y + clip.height);

        for (int pitch = MIN_PITCH; pitch <= MAX_PITCH; pitch++) {
            int y = pitchToY(pitch);
            if (y + noteHeight < firstVisiblePitchY || y > lastVisiblePitchY) continue;

            if (isBlackKey(pitch)) g2d.setColor(GRID_LINE_COLOR_DARK.brighter());
            else g2d.setColor(GRID_LINE_COLOR_DARK);
            if (pitch % 12 == 0) g2d.setColor(GRID_LINE_COLOR_LIGHT); // C notes
            g2d.drawLine(KEY_WIDTH, y, clip.x + clip.width, y);
        }
        g2d.drawLine(KEY_WIDTH, gridBottomY-1, clip.x + clip.width, gridBottomY-1); // Bottom border of note area
    }

    private void drawNotes(Graphics2D g2d, Rectangle clip) {
        for (Note note : notes) {
            int x = tickToX(note.getStartTimeTicks());
            int y = pitchToY(note.getPitch());
            int width = (int) (note.getDurationTicks() * pixelsPerTick);
            int height = noteHeight -1; // -1 for border

            Rectangle noteRect = new Rectangle(x, y, Math.max(1, width), Math.max(1,height)); // Min width/height 1px
            if (clip.intersects(noteRect)) {
                if (note == selectedNote) {
                    g2d.setColor(SELECTED_NOTE_COLOR);
                    g2d.fillRect(x, y, width, height);
                    g2d.setColor(SELECTED_NOTE_BORDER_COLOR);
                    g2d.drawRect(x, y, width, height);
                } else {
                    g2d.setColor(NOTE_COLOR);
                    g2d.fillRect(x, y, width, height);
                    g2d.setColor(NOTE_BORDER_COLOR);
                    g2d.drawRect(x, y, width, height);
                }
            }
        }
    }

    private void drawControllerLane(Graphics2D g2d, Rectangle clip) {
        int laneTopY = getHeight() - CONTROLLER_LANE_HEIGHT;
        g2d.setColor(DARK_BACKGROUND_COLOR.darker());
        g2d.fillRect(0, laneTopY, getWidth(), CONTROLLER_LANE_HEIGHT);
        g2d.setColor(GRID_LINE_COLOR_DARK);
        g2d.drawLine(0, laneTopY, getWidth(), laneTopY); // Top border

        // Draw velocity for visible notes
        for (Note note : notes) {
            int x = tickToX(note.getStartTimeTicks());
            int noteWidth = (int) (note.getDurationTicks() * pixelsPerTick);
            Rectangle noteTimeSpanRect = new Rectangle(x, laneTopY, Math.max(1, noteWidth), CONTROLLER_LANE_HEIGHT);

            if (clip.intersects(noteTimeSpanRect)) { // Check if the note's time span is visible
                g2d.setColor(NOTE_COLOR.brighter());
                int velHeight = (int) ((note.getVelocity() / 127.0) * (CONTROLLER_LANE_HEIGHT - 10));
                int velY = laneTopY + (CONTROLLER_LANE_HEIGHT - 10 - velHeight) + 5;
                int velBarWidth = Math.max(2, (int)(pixelsPerTick * ppqn / 16) ); // approx 64th note width
                if (note == selectedNote) g2d.setColor(SELECTED_NOTE_COLOR.brighter());
                g2d.fillRect(x, velY, velBarWidth, velHeight);
            }
        }
        // Grid lines in controller lane
        int ticksPerBeat = ppqn * 4 / beatUnit;
        int ticksPerMeasure = ticksPerBeat * beatsPerMeasure;

        long startTick = Math.max(0, xToTick(Math.max(KEY_WIDTH, clip.x)));
        long endTick = xToTick(clip.x + clip.width) + ticksPerBeat; // Ensure last line is drawn
        endTick = Math.min(endTick, totalTicks);

        for (long currentTick = 0; currentTick <= endTick; currentTick += ticksPerBeat / 4) {
            int xPos = tickToX(currentTick);
            if (xPos < KEY_WIDTH || xPos < clip.x || xPos > clip.x + clip.width) continue;

            if (currentTick % ticksPerMeasure == 0) g2d.setColor(GRID_LINE_COLOR_LIGHT.darker());
            else if (currentTick % ticksPerBeat == 0) g2d.setColor(GRID_LINE_COLOR_DARK.darker());
            else continue;
            g2d.drawLine(xPos, laneTopY, xPos, getHeight());
        }
    }

    private void drawPlaybackHead(Graphics2D g2d, Rectangle clip) {
        int x = tickToX(playbackTick);
        if (x >= KEY_WIDTH && x >= clip.x && x <= clip.x + clip.width) {
            g2d.setColor(PLAYBACK_HEAD_COLOR);
            g2d.drawLine(x, RULER_HEIGHT, x, getHeight() - CONTROLLER_LANE_HEIGHT);
            // Draw in controller lane too
            g2d.drawLine(x, getHeight() - CONTROLLER_LANE_HEIGHT, x, getHeight());
        }
    }

    private void drawLoopRange(Graphics2D g2d, Rectangle clip) {
        int x1 = tickToX(loopStartTick);
        int x2 = tickToX(loopEndTick);
        if (x1 < x2) {
            Rectangle loopRect = new Rectangle(x1, RULER_HEIGHT, x2 - x1, getHeight() - RULER_HEIGHT - CONTROLLER_LANE_HEIGHT);
            if (clip.intersects(loopRect)) {
                g2d.setColor(LOOP_RANGE_COLOR);
                g2d.fillRect(loopRect.x, loopRect.y, loopRect.width, loopRect.height);
                g2d.setColor(LOOP_RANGE_COLOR.darker());
                g2d.drawRect(loopRect.x, loopRect.y, loopRect.width, loopRect.height);
            }
        }
    }


    public void setPlaybackTick(long tick) {
        this.playbackTick = tick;
        // Repaint only the region around the playback head for efficiency (optional)
        // int x = tickToX(tick);
        // repaint(x - 2, 0, 4, getHeight());
        // For simplicity, repaint all for now
    }

    public void setLoopRange(long start, long end) {
        this.loopStartTick = Math.max(0, start);
        this.loopEndTick = Math.max(this.loopStartTick, end);
        this.showLoopRange = true;
        repaint();
    }
    public void clearLoopRange() {
        this.showLoopRange = false;
        repaint();
    }
    public long getLoopStartTick() { return loopStartTick; }
    public long getLoopEndTick() { return loopEndTick; }


    // --- Mouse Events ---
    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1  && !isLongPress) {
            if (e.getX() < KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // Click on Piano Key
                int pitch = yToPitch(e.getY());
                if (pitch != -1) {
                    // Optional: Play note on key click (requires simple synth)
                    System.out.println("Key clicked: " + pitch);
                }
            } else if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // Click on Note Area
                Optional<Note> clickedNoteOpt = clickedNoteOpt = getNoteAt(e.getX(), e.getY());
                if (!clickedNoteOpt.isPresent()) { // Clicked on empty space, create note
                    int pitch = yToPitch(e.getY());
                    long startTime = snapToGrid(xToTick(e.getX()), 16); // Snap to 16th
                    long duration = ppqn; // Default 1 beat duration
                    if (pitch != -1) {
                        Note newNote = new Note(pitch, startTime, duration, 100, 0);
                        notes.add(newNote);
                        selectedNote = newNote;
                        if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                        if (startTime + duration > totalTicks) {
                            totalTicks = startTime + duration + (long)ppqn * 4; // Extend totalTicks if needed
                            updatePreferredSize();
                        }
                        repaint();
                    }
                }
                // Selection is handled in mousePressed for drag-ability
            } else if (e.getY() < RULER_HEIGHT && e.getX() >= KEY_WIDTH) { // Click on Ruler
                // Set loop points or playback start (example for loop)
                if (e.isShiftDown()) {
                    loopEndTick = snapToGrid(xToTick(e.getX()), 4);
                } else {
                    loopStartTick = snapToGrid(xToTick(e.getX()), 4);
                }
                if (loopEndTick < loopStartTick) { // Swap if end is before start
                    long temp = loopStartTick;
                    loopStartTick = loopEndTick;
                    loopEndTick = temp;
                }
                showLoopRange = true;
                if (parentFrame != null) parentFrame.updateLoopButtonText();
                repaint();
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            requestFocusInWindow(); // Important for key events if any
            if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // Note Area
                dragStartPoint = e.getPoint();
                isLongPress = false;
                longPressTimer.stop();
                Optional<Note> noteOpt = getNoteAt(e.getX(), e.getY());
                if (noteOpt.isPresent()) {
                    selectedNote = noteOpt.get();
                    if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                    // Check if resizing
                    int noteEndX = tickToX(selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks());
                    if (Math.abs(e.getX() - noteEndX) < resizeHandleSensitivity) {
                        currentDragMode = DragMode.RESIZE_END;
                    } else {
                        currentDragMode = DragMode.MOVE;
                    }
                    // Store original state for snapping relative to start, or for undo
                    dragNoteOriginal = new Note(selectedNote.getPitch(), selectedNote.getStartTimeTicks(),
                            selectedNote.getDurationTicks(), selectedNote.getVelocity(), selectedNote.getChannel());
                    // ★長押し判定タイマーを開始
                    // ただし、リサイズハンドル上では長押しで音高変更モードにしない方が自然かもしれない
                    if (currentDragMode != DragMode.RESIZE_END) { // リサイズ操作中は長押し音高変更を無効化
                        longPressTimer.start();
                    }
                } else {
                    selectedNote = null;
                    if (parentFrame != null) parentFrame.updateNoteInfo(null);
                    currentDragMode = DragMode.NONE; // Or implement marquee selection
                }
                repaint();
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (currentDragMode != DragMode.NONE && selectedNote != null) {
                // Final snap or adjustments
                if (currentDragMode == DragMode.MOVE) {
                    selectedNote.setStartTimeTicks(snapToGrid(selectedNote.getStartTimeTicks(), 16));
                    // pitch already discrete
                } else if (currentDragMode == DragMode.RESIZE_END) {
                    selectedNote.setDurationTicks(snapToGrid(selectedNote.getDurationTicks(), 16));
                    if (selectedNote.getDurationTicks() < ppqn / 16) { // Min duration
                        selectedNote.setDurationTicks(ppqn/16);
                    }
                }
                if (selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks() > totalTicks) {
                    totalTicks = selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks() + (long)ppqn * 4;
                    updatePreferredSize();
                }
                if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
            }
            currentDragMode = DragMode.NONE;
            dragStartPoint = null;
            dragNoteOriginal = null;
            repaint();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (currentDragMode == DragMode.NONE || selectedNote == null || dragStartPoint == null || dragNoteOriginal == null) {
            return;
        }

        int dx = e.getX() - dragStartPoint.x;
        int dy = e.getY() - dragStartPoint.y;

        if (currentDragMode == DragMode.MOVE) {
            long newStartTime = xToTick(tickToX(dragNoteOriginal.getStartTimeTicks()) + dx);
            int newPitch = yToPitch(pitchToY(dragNoteOriginal.getPitch()) + dy);

            // Clamp pitch
            if (newPitch < MIN_PITCH) newPitch = MIN_PITCH;
            if (newPitch > MAX_PITCH) newPitch = MAX_PITCH;

            if (newPitch != -1) selectedNote.setPitch(newPitch);
            selectedNote.setStartTimeTicks(Math.max(0, newStartTime)); // Ensure non-negative start time

        } else if (currentDragMode == DragMode.RESIZE_END) {
            long originalEndTime = dragNoteOriginal.getStartTimeTicks() + dragNoteOriginal.getDurationTicks();
            long newEndTime = xToTick(tickToX(originalEndTime) + dx);
            long newDuration = newEndTime - selectedNote.getStartTimeTicks(); // Start time doesn't change on resize end
            selectedNote.setDurationTicks(Math.max(ppqn / 16, newDuration)); // Min duration (e.g. 64th note)
        }
        if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
        repaint();
    }


    @Override
    public void mouseMoved(MouseEvent e) {
        if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT && selectedNote != null) {
            int noteEndX = tickToX(selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks());
            int noteY = pitchToY(selectedNote.getPitch());
            int noteEndY = noteY + noteHeight;

            // Change cursor for resize
            if (Math.abs(e.getX() - noteEndX) < resizeHandleSensitivity &&
                    e.getY() >= noteY && e.getY() <= noteEndY) {
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
            } else {
                setCursor(Cursor.getDefaultCursor());
            }
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {}
    @Override
    public void mouseExited(MouseEvent e) {}

    // --- Mouse Wheel for Zoom ---
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isAltDown()) { // Horizontal zoom (time)
            double oldPixelsPerTick = pixelsPerTick;
            if (e.getWheelRotation() < 0) { // Zoom in
                pixelsPerTick *= 1.25;
            } else { // Zoom out
                pixelsPerTick /= 1.25;
            }
            pixelsPerTick = Math.max(0.005, Math.min(pixelsPerTick, 2.0)); // Clamp zoom

            // Zoom relative to mouse pointer
            JViewport viewport = (JViewport) getParent();
            Point viewPos = viewport.getViewPosition();
            int mouseXInComponent = e.getX();
            double mouseTick = xToTick(mouseXInComponent);

            int newViewX = (int)(mouseTick * pixelsPerTick - (mouseXInComponent - KEY_WIDTH - viewPos.x) * (pixelsPerTick/oldPixelsPerTick)) - (KEY_WIDTH - viewPos.x);
            newViewX = KEY_WIDTH + (int)(mouseTick * pixelsPerTick) - (mouseXInComponent - KEY_WIDTH);


            updatePreferredSize();
            // Adjust scroll position to keep mouse pointer over same tick
            int newMouseX = tickToX((long)mouseTick);
            viewPos.x += (newMouseX - mouseXInComponent);
            viewport.setViewPosition(viewPos);

            repaint();

        } else if (e.isShiftDown()) { // Vertical zoom (pitch)
            int oldNoteHeight = noteHeight;
            if (e.getWheelRotation() < 0) { // Zoom in
                noteHeight += 1;
            } else { // Zoom out
                noteHeight -= 1;
            }
            noteHeight = Math.max(6, Math.min(noteHeight, 30)); // Clamp zoom

            JViewport viewport = (JViewport) getParent();
            Point viewPos = viewport.getViewPosition();
            int mouseYInComponent = e.getY();
            int mousePitch = yToPitch(mouseYInComponent);
            if (mousePitch == -1 && mouseYInComponent > RULER_HEIGHT) { // if in controller lane, zoom around center
                mousePitch = MAX_PITCH / 2;
            } else if (mousePitch == -1) { // if in ruler or key area
                mousePitch = yToPitch(RULER_HEIGHT + ((getHeight() - RULER_HEIGHT - CONTROLLER_LANE_HEIGHT)/2)); // center of visible keys
            }


            updatePreferredSize();
            // Adjust scroll position
            if (mousePitch != -1) {
                int newMouseY = pitchToY(mousePitch);
                viewPos.y += (newMouseY - mouseYInComponent);
                viewport.setViewPosition(viewPos);
            }
            repaint();
        } else {
            // Default scroll behavior if no modifier
            getParent().dispatchEvent(SwingUtilities.convertMouseEvent(this, e, getParent()));
        }
    }


    private Optional<Note> getNoteAt(int x, int y) {
        if (x < KEY_WIDTH || y < RULER_HEIGHT || y >= getHeight() - CONTROLLER_LANE_HEIGHT) return Optional.empty();

        long tick = xToTick(x);
        int pitch = yToPitch(y);
        if (pitch == -1) return Optional.empty();

        // Iterate in reverse to pick topmost note if overlapping
        for (int i = notes.size() - 1; i >= 0; i--) {
            Note note = notes.get(i);
            if (note.getPitch() == pitch &&
                    tick >= note.getStartTimeTicks() &&
                    tick < note.getStartTimeTicks() + note.getDurationTicks()) {
                return Optional.of(note);
            }
        }
        return Optional.empty();
    }

    // Zoom methods for buttons
    public void zoomInHorizontal() {
        pixelsPerTick *= 1.5;
        pixelsPerTick = Math.min(pixelsPerTick, 2.0);
        updatePreferredSize();
        repaint();
    }
    public void zoomOutHorizontal() {
        pixelsPerTick /= 1.5;
        pixelsPerTick = Math.max(0.005, pixelsPerTick);
        updatePreferredSize();
        repaint();
    }
    public void zoomInVertical() {
        noteHeight += 2;
        noteHeight = Math.min(noteHeight, 30);
        updatePreferredSize();
        repaint();
    }
    public void zoomOutVertical() {
        noteHeight -= 2;
        noteHeight = Math.max(6, noteHeight);
        updatePreferredSize();
        repaint();
    }
}

