package org.codesfactory.ux.pianoroll;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
// import文に以下を追加
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener; // KeyListener をインポート
// クラス宣言を修正
public class PianoRollView extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener { // KeyListener を実装
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

    // --- タブ上部のメモリを明示するフラグ ---
    public static final Color LOOP_MARKER_COLOR = new Color(0,0,0); // ループ範囲の背景色より少し濃い色
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

    private boolean isDrawingWithPencil = false; // Shift + Drag でノートを描画するモード
    private Note currentPencilDrawingNote = null; // 現在ペンシルで連続描画中のノート
    // --- Other ---
    private PianoRoll parentFrame; // To update info labe

    //--delete-marker--// l
    private boolean isMarqueeSelecting = false;
    private Rectangle marqueeRect = null;
    private Point marqueeStartPoint = null;
    // ★重要: 複数選択に対応する場合、selectedNote を List<Note> に変更する必要がある
    private List<Note> selectedNotesList = new ArrayList<>(); // 仮: 複数選択されたノートを保持

    // --- ★ KeyListener の実装 ---
    @Override
    public void keyTyped(KeyEvent e) {
        // 通常は使用しない
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            boolean notesWereDeleted = false;
            if (!selectedNotesList.isEmpty()) { // ★複数選択リストを優先
                // (オプション) Undoのために削除するノートのリストを保存
                // List<Note> deletedNotesBackup = new ArrayList<>(selectedNotesList);
                notes.removeAll(selectedNotesList);
                selectedNotesList.clear();
                notesWereDeleted = true;
                System.out.println("Deleted multiple selected notes.");
            } else if (selectedNote != null) { // 単一選択の場合
                // (オプション) Undoのために削除するノートを保存
                // Note deletedNoteBackup = selectedNote;
                notes.remove(selectedNote);
                selectedNote = null;
                notesWereDeleted = true;
                System.out.println("Deleted single selected note.");
            }

            if (notesWereDeleted) {
                if (parentFrame != null) {
                    parentFrame.updateNoteInfo(null);
                }
                repaint();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // 通常は使用しない
    }
    // --- ★ここまで追加 ---

    public PianoRollView(PianoRoll parent) {
        this.parentFrame = parent;
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this); // ★ KeyListener を登録
        setFocusable(true);   // ★ キーイベントを受け取るためにフォーカス可能にする
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

        if (isMarqueeSelecting && marqueeRect != null) {
            g2d.setColor(new Color(0, 100, 255, 50)); // 半透明の青
            g2d.fill(marqueeRect);
            g2d.setColor(new Color(0, 100, 255)); // 枠線
            g2d.draw(marqueeRect);
        }
    }

    // --- マーキー範囲内のノートを選択するメソッド (新規) ---
    private void selectNotesInMarquee() {
        selectedNotesList.clear(); // まずクリア
        selectedNote = null; // 単一選択もクリア

        if (marqueeRect == null) return;

        for (Note note : notes) {
            int noteX = tickToX(note.getStartTimeTicks());
            int noteY = pitchToY(note.getPitch());
            int noteWidth = (int) (note.getDurationTicks() * pixelsPerTick);
            int noteActualHeight = this.noteHeight; // Note: noteHeightは1音の高さなので、実際の描画高はこれ

            Rectangle noteBounds = new Rectangle(noteX, noteY, Math.max(1,noteWidth), Math.max(1,noteActualHeight));

            // marqueeRect と noteBounds が交差するかどうかで判定
            if (marqueeRect.intersects(noteBounds)) {
                selectedNotesList.add(note);
            }
        }
        System.out.println("Marquee selected " + selectedNotesList.size() + " notes."); // デバッグ用
        // ★重要: 複数選択されたノートの描画方法 (ハイライトなど) を drawNotes で対応する必要がある
        // ★重要: 情報ラベルの更新も複数選択に対応する必要がある
        if (parentFrame != null && !selectedNotesList.isEmpty()) {
            // 複数の場合は代表的な情報を表示するか、「Multiple notes selected」などと表示
            parentFrame.updateNoteInfo(selectedNotesList.get(0)); // とりあえず最初のノート情報
        } else if (parentFrame != null) {
            parentFrame.updateNoteInfo(null);
        }
        repaint();
    }

    private void drawRuler(Graphics2D g2d, Rectangle clip) {
        g2d.setColor(Color.DARK_GRAY.brighter());
        g2d.fillRect(0, 0, getWidth(), RULER_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawLine(0, RULER_HEIGHT -1, getWidth(), RULER_HEIGHT -1);
        g2d.drawLine(KEY_WIDTH -1, 0, KEY_WIDTH-1, RULER_HEIGHT);

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        int ticksPerMeasure = ppqn * beatsPerMeasure;

        long startTickRuler = Math.max(0, xToTick(clip.x - KEY_WIDTH));
        long endTickRuler = xToTick(clip.x + clip.width - KEY_WIDTH) + ticksPerMeasure; // ルーラー描画終了Tick
        endTickRuler = Math.min(endTickRuler, totalTicks);
        // 小節線と拍線の描画 (既存のロジック)
        for (long currentTick = 0; currentTick <= endTickRuler; currentTick += ppqn / 4) {
            if (currentTick < startTickRuler && currentTick + ppqn / 4 < startTickRuler) continue; // 描画範囲より前ならスキップ

            int x = tickToX(currentTick);
            if (x < KEY_WIDTH) continue; // 鍵盤エリアより左は描画しない

            if (x >= clip.x && x <= clip.x + clip.width) { // クリップ範囲内のみ描画
                if (currentTick % ticksPerMeasure == 0) { // Measure line
                    g2d.setColor(Color.WHITE);
                    g2d.drawLine(x, RULER_HEIGHT - 10, x, RULER_HEIGHT);
                    g2d.drawString(String.valueOf(currentTick / ticksPerMeasure + 1), x + 2, RULER_HEIGHT - 12);
                } else if (currentTick % ppqn == 0) { // Beat line (assuming ppqn is a quarter note)
                    if (pixelsPerTick * ppqn > 10) { // ある程度スペースがある場合のみ拍線を描画
                        g2d.setColor(Color.LIGHT_GRAY);
                        g2d.drawLine(x, RULER_HEIGHT - 5, x, RULER_HEIGHT);
                    }
                }
                // (オプション) さらに細かいグリッド線 (16分音符など) をルーラーに描画する場合
                // else if (pixelsPerTick * (ppqn / 4.0) > 5) {
                //     g2d.setColor(Color.GRAY);
                //     g2d.drawLine(x, RULER_HEIGHT - 3, x, RULER_HEIGHT);
                // }
            }
        }

        // --- ★ループ範囲フラグ（マーカー）の描画 ---
        if (showLoopRange) {
            int startMarkerX = tickToX(loopStartTick);
            int endMarkerX = tickToX(loopEndTick);
            g2d.setColor(LOOP_MARKER_COLOR); // マーカーの色

            // 開始マーカー (下向き三角形)
            if (startMarkerX >= KEY_WIDTH && startMarkerX >= clip.x && startMarkerX <= clip.x + clip.width) {
                Polygon startTriangle = new Polygon();
                startTriangle.addPoint(startMarkerX, 0); // 上の頂点
                startTriangle.addPoint(startMarkerX - 4, 15); // 左下の頂点
                startTriangle.addPoint(startMarkerX + 4, 15); // 右下の頂点
                g2d.fillPolygon(startTriangle);
                // マーカーの下に縦線を引いても良い
                // g2d.drawLine(startMarkerX, 6, startMarkerX, RULER_HEIGHT -1);
            }

            // 終了マーカー (下向き三角形)
            if (endMarkerX >= KEY_WIDTH && endMarkerX >= clip.x && endMarkerX <= clip.x + clip.width) {
                Polygon endTriangle = new Polygon();
                endTriangle.addPoint(endMarkerX, 0);   // 上の頂点
                endTriangle.addPoint(endMarkerX - 4, 15); // 左下の頂点
                endTriangle.addPoint(endMarkerX + 4, 15); // 右下の頂点
                g2d.fillPolygon(endTriangle);
                // マーカーの下に縦線を引いても良い
                // g2d.drawLine(endMarkerX, 6, endMarkerX, RULER_HEIGHT -1);
            }

            // ループ範囲を示すルーラー上の背景色 (オプション、ノートエリアの背景とは別に)
            if (startMarkerX < endMarkerX) {
                int rectX = Math.max(KEY_WIDTH, startMarkerX); // 鍵盤エリアより左にはみ出ないように
                int rectWidth = endMarkerX - rectX;
                if (rectX + rectWidth > KEY_WIDTH) { // 幅が0以上で、鍵盤エリア内にある場合
                    // クリップ範囲との交差部分のみ描画
                    Rectangle loopRulerRect = new Rectangle(rectX, 0, rectWidth, RULER_HEIGHT -1);
                    Rectangle clippedLoopRulerRect = loopRulerRect.intersection(clip);
                    if (!clippedLoopRulerRect.isEmpty()) {
                        g2d.setColor(new Color(LOOP_RANGE_COLOR.getRed(), LOOP_RANGE_COLOR.getGreen(), LOOP_RANGE_COLOR.getBlue(), 30)); // 少し薄い色で
                        g2d.fillRect(clippedLoopRulerRect.x, clippedLoopRulerRect.y, clippedLoopRulerRect.width, clippedLoopRulerRect.height);
                    }
                }
            }
        }
        // --- ★ここまで追加 ---
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
                boolean isSelected = false;
                if (!selectedNotesList.isEmpty()) { // ★複数選択リストが空でない場合
                    if (selectedNotesList.contains(note)) {
                        isSelected = true;
                    }
                } else if (note == selectedNote) { // ★単一選択の場合 (複数選択リストが空の場合のみ評価)
                    isSelected = true;
                }

                if (isSelected) {
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
        if (isDrawingWithPencil) { // ペンシル描画中はクリックイベントを無視
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON1  && !isLongPress) {
            if (e.getX() < KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // Click on Piano Key
                int pitch = yToPitch(e.getY());
                if (pitch != -1) {
                    // Optional: Play note on key click (requires simple synth)
                    System.out.println("Key clicked: " + pitch);
                }
            } else if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // Click on Note Area
                if (!e.isShiftDown()) {
                    Optional<Note> clickedNoteOpt = getNoteAt(e.getX(), e.getY());
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

    // --- mouseReleased メソッドの修正 ---
    @Override
    public void mouseReleased(MouseEvent e) {
        if (isMarqueeSelecting) {
            isMarqueeSelecting = false;
            if (marqueeRect != null && marqueeRect.width > 0 && marqueeRect.height > 0) {
                selectNotesInMarquee(); // マーキー範囲内のノートを選択するメソッド呼び出し
            }
            marqueeRect = null; // 選択矩形をクリア
            repaint();
        }
        if (e.getButton() == MouseEvent.BUTTON1) {
            if (isDrawingWithPencil) {
                isDrawingWithPencil = false;
                if (currentPencilDrawingNote != null) {
                    selectedNote = currentPencilDrawingNote; // 最後に描画/延長したノートを選択
                    if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                }
                currentPencilDrawingNote = null; // リセット
                repaint(); // 選択状態のハイライトのため
                // 必要ならここで最後に描画されたノートを選択状態にするなど
            } else if (currentDragMode != DragMode.NONE && selectedNote != null) {
                if (currentDragMode == DragMode.MOVE) {
                    selectedNote.setStartTimeTicks(snapToGrid(selectedNote.getStartTimeTicks(), 16));
                } else if (currentDragMode == DragMode.RESIZE_END) {
                    selectedNote.setDurationTicks(snapToGrid(selectedNote.getDurationTicks(), 16));
                    if (selectedNote.getDurationTicks() < ppqn / 16) {
                        selectedNote.setDurationTicks(ppqn / 16);
                    }
                }
                if (selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks() > totalTicks) {
                    totalTicks = selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks() + (long) ppqn * 4;
                    updatePreferredSize();
                }
                if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                repaint();
            }
            currentDragMode = DragMode.NONE;
            dragStartPoint = null;
            dragNoteOriginal = null;
//            repaint();
        }
    }



    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            requestFocusInWindow();
            dragStartPoint = e.getPoint(); // 常にドラッグ開始点を記録

            if (e.isShiftDown() && e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                // Shiftキーが押されていればペンシル描画モード
                isDrawingWithPencil = true;
                currentDragMode = DragMode.NONE; // 他のドラッグモードは無効化
                selectedNote = null; // ペンシル中はノート選択を解除
                if (parentFrame != null) parentFrame.updateNoteInfo(null);
                currentPencilDrawingNote = null; // ★描画開始時にリセット
                // 最初のノートを描画
//                drawPencilNote(e.getX(), e.getY());
                // 最初のノートの「核」を作成 (まだリストには追加しないか、仮追加してドラッグで更新)
                startOrContinuePencilNote(e.getX(), e.getY());
                repaint();

            } else if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) { // 通常のノートエリア操作
                isDrawingWithPencil = false; // ペンシル描画モードではない
                Optional<Note> noteOpt = getNoteAt(e.getX(), e.getY());
                if (noteOpt.isPresent()) {
                    selectedNote = noteOpt.get();
                    if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                    int noteEndX = tickToX(selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks());
                    if (Math.abs(e.getX() - noteEndX) < resizeHandleSensitivity) {
                        currentDragMode = DragMode.RESIZE_END;
                    } else {
                        currentDragMode = DragMode.MOVE;
                    }
                    dragNoteOriginal = new Note(selectedNote.getPitch(), selectedNote.getStartTimeTicks(),
                            selectedNote.getDurationTicks(), selectedNote.getVelocity(), selectedNote.getChannel());
                } else {
                    selectedNote = null;
                    if (parentFrame != null) parentFrame.updateNoteInfo(null);
                    currentDragMode = DragMode.NONE;
                }
                repaint();
            } else {
                // 鍵盤エリアやルーラーエリアのクリックはここでは処理しない (mouseClickedで処理)
                isDrawingWithPencil = false;
                currentDragMode = DragMode.NONE;
            }
        }

        if (!isDrawingWithPencil && currentDragMode == DragMode.NONE && selectedNote == null &&
                e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
            // ノートがない場所でクリックされた場合、マーキー選択を開始
            isMarqueeSelecting = true;
            marqueeStartPoint = e.getPoint();
            marqueeRect = new Rectangle(marqueeStartPoint);
            selectedNotesList.clear(); // 以前の複数選択をクリア (単一選択のselectedNoteもnullに)
            selectedNote = null;
            if (parentFrame != null) parentFrame.updateNoteInfo(null);
            repaint();
        }
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

    // --- mouseDragged メソッドの修正 ---
    @Override
    public void mouseDragged(MouseEvent e) {
        if (isMarqueeSelecting) {
            marqueeRect.setBounds(
                    Math.min(marqueeStartPoint.x, e.getX()),
                    Math.min(marqueeStartPoint.y, e.getY()),
                    Math.abs(e.getX() - marqueeStartPoint.x),
                    Math.abs(e.getY() - marqueeStartPoint.y)
            );
            // (オプション) marqueeRectと交差するノートをリアルタイムでハイライトするならここで処理
            repaint();
        }else if (isDrawingWithPencil) {
            // ペンシル描画モードの場合
            if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                startOrContinuePencilNote(e.getX(), e.getY()); // ★ロジックを共通化
                repaint();
            }
        } else if (currentDragMode != DragMode.NONE && selectedNote != null && dragStartPoint != null && dragNoteOriginal != null) {
            // 通常のノート移動・リサイズモード
            int dx = e.getX() - dragStartPoint.x;
            int dy = e.getY() - dragStartPoint.y;

            if (currentDragMode == DragMode.MOVE) {
                long newStartTime = xToTick(tickToX(dragNoteOriginal.getStartTimeTicks()) + dx);
                int newPitch = yToPitch(pitchToY(dragNoteOriginal.getPitch()) + dy);
                if (newPitch < MIN_PITCH) newPitch = MIN_PITCH;
                if (newPitch > MAX_PITCH) newPitch = MAX_PITCH;
                if (newPitch != -1) selectedNote.setPitch(newPitch);
                selectedNote.setStartTimeTicks(Math.max(0, newStartTime));
            } else if (currentDragMode == DragMode.RESIZE_END) {
                long originalEndTime = dragNoteOriginal.getStartTimeTicks() + dragNoteOriginal.getDurationTicks();
                long newEndTime = xToTick(tickToX(originalEndTime) + dx);
                long newDuration = newEndTime - selectedNote.getStartTimeTicks();
                selectedNote.setDurationTicks(Math.max(ppqn / 16, newDuration));
            }
            if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
            repaint();
        }
    }

    // --- ★startOrContinuePencilNote メソッド (旧 drawPencilNote を大幅に変更) ---
    private void startOrContinuePencilNote(int mouseX, int mouseY) {
        int pitch = yToPitch(mouseY);
        long currentTick = xToTick(mouseX); // マウスカーソル下のTick
        // フリーハンド描画なので、グリッドに細かくスナップさせる (例: 32分音符)
        // これが実質的なノートの最小単位の長さになる
        long snapUnitTicks = ppqn / 8; // 32分音符 (ppqnが480なら60ticks) 好みで調整
        long snappedCurrentTick = (currentTick / snapUnitTicks) * snapUnitTicks;

        if (pitch == -1) { // 描画範囲外
            // もし描画中ノートがあれば、それを確定（リストに追加済みなら何もしない）
            currentPencilDrawingNote = null; // 連続描画を中断
            return;
        }

        if (currentPencilDrawingNote == null) {
            // --- 新しい連続描画の開始 ---
            // 既に同じ位置にノートがないか軽くチェック (オプション)
            for (Note n : notes) {
                if (n.getPitch() == pitch && n.getStartTimeTicks() <= snappedCurrentTick &&
                        snappedCurrentTick < n.getStartTimeTicks() + n.getDurationTicks()) {
                    // マウス位置に既存ノートがあれば、新規描画はしない（既存ノートを選択する挙動にしても良い）
                    // 今回はペンシルツールなので、上書き的に新しいノートを開始する
                    // return; // 何もせず抜ける場合
                }
            }

            currentPencilDrawingNote = new Note(pitch, snappedCurrentTick, snapUnitTicks, 100, 0);
            notes.add(currentPencilDrawingNote); // 新しいノートをリストに追加
            if (currentPencilDrawingNote.getStartTimeTicks() + currentPencilDrawingNote.getDurationTicks() > totalTicks) {
                totalTicks = currentPencilDrawingNote.getStartTimeTicks() + currentPencilDrawingNote.getDurationTicks() + (long) ppqn * 4;
                updatePreferredSize();
            }
        } else {
            // --- 既存の連続描画を継続または変更 ---
            if (currentPencilDrawingNote.getPitch() == pitch &&
                    snappedCurrentTick >= currentPencilDrawingNote.getStartTimeTicks()) {
                // 音高が同じで、時間が進んでいる (または同じ開始ティックだが延長の可能性)
                long newDuration = snappedCurrentTick - currentPencilDrawingNote.getStartTimeTicks() + snapUnitTicks;
                if (newDuration > 0) {
                    currentPencilDrawingNote.setDurationTicks(newDuration);
                    if (currentPencilDrawingNote.getStartTimeTicks() + currentPencilDrawingNote.getDurationTicks() > totalTicks) {
                        totalTicks = currentPencilDrawingNote.getStartTimeTicks() + currentPencilDrawingNote.getDurationTicks() + (long) ppqn * 4;
                        updatePreferredSize();
                    }
                } else {
                    // 時間が戻った場合などは、新しいノートとして開始 (前のノートは確定)
                    currentPencilDrawingNote = null; // 前のノートを終了
                    startOrContinuePencilNote(mouseX, mouseY); // 再帰的に新しいノートとして開始
                    return;
                }
            } else {
                // 音高が変わった、または時間が不連続 (前に戻ったなど)
                // 現在の currentPencilDrawingNote は確定（リストには既に追加されている）
                currentPencilDrawingNote = null; // 前のノートの連続描画を終了
                startOrContinuePencilNote(mouseX, mouseY); // 新しい位置で再度描画開始を試みる
                return;
            }
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

