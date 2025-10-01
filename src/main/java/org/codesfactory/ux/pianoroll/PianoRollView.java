// PianoRollView.java (全体 - 2025/05/08 時点の修正統合版)

package org.codesfactory.ux.pianoroll;

import org.codesfactory.ux.pianoroll.commands.*; // コマンド関連をまとめてインポート

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PianoRollView extends JPanel implements MouseListener, MouseMotionListener, MouseWheelListener, KeyListener {

    // --- Constants ---
    public static final int MIN_PITCH = 0;
    public static final int MAX_PITCH = 127;
    public static final int KEY_WIDTH = 70;
    public static final int RULER_HEIGHT = 30;
    public static final int CONTROLLER_LANE_HEIGHT = 80;
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
    public static final Color LOOP_MARKER_COLOR = new Color(0, 80, 180);
    private static final Color OUTLINE_COLOR = new Color(60, 150, 255); // 外形線の色 (青系)
    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(1.5f); // 外形線の太さ
    private final int LONG_PRESS_DELAY = 300; // 長押し判定時間 (ms)
    private final int resizeHandleSensitivity = 5; // リサイズハンドルの感度 (pixels)

    // --- Drawing Parameters ---
    private double pixelsPerTick = 0.05;
    private int noteHeight = 12;

    // --- MIDI Data & Timing ---
    private int ppqn = MidiHandler.DEFAULT_PPQN;
    private int beatsPerMeasure = 4;
    private int beatUnit = 4;
    private final List<Note> notes = new ArrayList<>(); // ★ finalにして再代入を防ぐ
    private long totalTicks = (long) ppqn * beatsPerMeasure * 256;

    // --- Selection & Interaction State ---
    private Note selectedNote = null; // 現在主に選択されているノート（単一選択、または複数選択の代表）
    private final List<Note> selectedNotesList = new ArrayList<>(); // 複数選択されたノートのリスト
    private Point dragStartPoint = null;
    private Note dragNoteOriginal = null; // 単一ノートの移動/リサイズ開始時の状態
    // TODO: 複数ノート移動/リサイズ時の Undo/Redo 対応 (dragNoteOriginal の扱いを要検討)

    private enum DragMode {NONE, MOVE, RESIZE_END} // PITCH_ONLY は長押し用
    private DragMode currentDragMode = DragMode.NONE;

    private boolean isDrawingOutline = false; // Shift + Drag で外形描画中フラグ
    private final List<Point> outlinePathPoints = new ArrayList<>(); // 外形描画の軌跡

    private boolean isMarqueeSelecting = false; // 範囲(マーキー)選択中フラグ
    private Rectangle marqueeRect = null;
    private Point marqueeStartPoint = null;

    final private Timer longPressTimer; // 長押し判定用タイマー
    private boolean isLongPress = false; // 長押し判定フラグ

    // --- Playback & Loop ---
    private long playbackTick = 0;
    private boolean showLoopRange = false;
    private long loopStartTick = 0;
    private long loopEndTick = (long) ppqn * beatsPerMeasure * 4;

    // --- Other ---
    private final PianoRoll parentFrame; // 親フレームへの参照 (final)
    private final UndoManager undoManager; // Undo/Redo マネージャ (final)

    // --- Constructor ---
    public PianoRollView(PianoRoll parent) {
        this.parentFrame = parent;
        this.undoManager = new UndoManager(this);
        System.out.println("PianoRollView Constructor: Initial notes hash=" + System.identityHashCode(this.notes));

        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        addKeyListener(this);
        setFocusable(true);
        setBackground(DARK_BACKGROUND_COLOR);
        updatePreferredSize();

        // 長押し判定タイマー初期化
        longPressTimer = new Timer(LONG_PRESS_DELAY, _ -> {
            // タイマーが発火した = 長押しが確定した
            isLongPress = true;
            System.out.println("Long press timer fired! Mode: PITCH_ONLY");
            // 長押し時の処理 (例: PITCH_ONLYモードに移行)
            // currentDragMode = DragMode.PITCH_ONLY; // mousePressed側で設定した方が良いかも
            // setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        });
        longPressTimer.setRepeats(false);
    }

    // --- Public Methods for Interaction & Data ---

    public UndoManager getUndoManager() {
        return this.undoManager;
    }

    // ★★★ playbackTick のゲッターを追加 ★★★
    public long getPlaybackTick() {
        return playbackTick;
    }

    public PianoRoll getParentFrame() {
        return this.parentFrame;
    }

//    public double getPixelsPerTickSafe() {
//        return Math.max(0.001, pixelsPerTick);
//    }

    public void loadNotes(List<Note> newNotes, int newPpqn, long newTotalTicks) {
        System.out.println("loadNotes called. Current notes hash before clear=" + System.identityHashCode(this.notes));
        this.notes.clear(); // 既存リストの内容をクリア
        if (newNotes != null) {
            this.notes.addAll(newNotes); // 新しいノートを追加
        }
        System.out.println("loadNotes: Notes reloaded. notes hash=" + System.identityHashCode(this.notes) + ", size=" + this.notes.size());

        this.ppqn = newPpqn;
        // TODO: Read time signature from MIDI file if available
        this.beatsPerMeasure = 4;
        this.beatUnit = 4;
        this.totalTicks = Math.max(newTotalTicks, (long)this.ppqn * beatsPerMeasure * 16);
        clearSelectionAfterCommand(); // 選択状態をクリア
        updatePreferredSize();
        if (this.undoManager != null) {
            this.undoManager.clearStacks();
        }
        if (parentFrame != null) {
            parentFrame.updateUndoRedoMenuItems(false, false);
            parentFrame.updateNoteInfo(null);
        }
        repaint();
    }

    public List<Note> getAllNotes() {
        System.out.println("getAllNotes called. notes hash=" + System.identityHashCode(this.notes) + ", size=" + this.notes.size());
        return new ArrayList<>(this.notes); // 防御的コピーを返す
    }

    public int getPpqn() {
        return ppqn;
    }

    public int getBeatsPerMeasure() {
        return beatsPerMeasure;
    }

    public void selectNotesInRange(long startTick, long endTick) {
        List<Note> newlySelected = new ArrayList<>();
        for (Note note : notes) {
            long noteStart = note.getStartTimeTicks();
            // Select if the note starts within the range
            if (noteStart >= startTick && noteStart < endTick) {
                newlySelected.add(note);
            }
        }
        System.out.println("Selected " + newlySelected.size() + " notes in range [" + startTick + ", " + endTick + ").");
        setSelectedNotesAfterCommand(newlySelected);
        repaint(); // Need to repaint to show selection
    }

    // --- Helper methods for command execution ---

    public void setSelectedNoteAfterCommand(Note note) {
        this.selectedNote = note;
        this.selectedNotesList.clear();
        if (note != null) {
            this.selectedNotesList.add(note);
        }
        if (parentFrame != null) {
            parentFrame.updateNoteInfo(note);
        }
        // repaint(); // UndoManagerが最後に呼ぶ
    }

    public void clearSelectionAfterCommand() {
        this.selectedNote = null;
        this.selectedNotesList.clear();
        if (parentFrame != null) {
            parentFrame.updateNoteInfo(null);
        }
        // repaint();
    }

    public void setSelectedNotesAfterCommand(List<Note> notesToSelect) {
        this.selectedNotesList.clear();
        if (notesToSelect != null && !notesToSelect.isEmpty()) {
            this.selectedNotesList.addAll(notesToSelect);
            this.selectedNote = notesToSelect.getFirst(); // 代表として最初のノート
            if (parentFrame != null) {
                parentFrame.updateNoteInfo(this.selectedNote); // 代表情報を表示
            }
        } else {
            this.selectedNote = null;
            if (parentFrame != null) {
                parentFrame.updateNoteInfo(null);
            }
        }
        // repaint();
    }

    public void updateNoteInfoForFrame(Note note) {
        if (parentFrame != null) {
            parentFrame.updateNoteInfo(note);
        }
    }

    // PianoRollView.java

    public void setPlaybackTick(long tick) {
        long oldTick = this.playbackTick;
        if (oldTick != tick) {
            this.playbackTick = tick;

            // --- 限定的な再描画 ---
//            int RULER_AND_NOTES_BOTTOM = getHeight() - CONTROLLER_LANE_HEIGHT; // ルーラーとノートエリアの下端
            int x_new = tickToX(tick);
            int x_old = tickToX(oldTick);
            int repaintX = Math.min(x_new, x_old) - 2; // 少し余裕を持たせる
            int repaintWidth = Math.abs(x_new - x_old) + 4; // 幅も余裕を持たせる

            // 再描画が必要な範囲を計算 (再生ヘッドが動くのはルーラーとノートエリア、コントローラーレーン)
            // 1. ルーラー部分の再描画リクエスト (古いヘッドと新しいヘッドの範囲)
            // repaint(repaintX, 0, repaintWidth, RULER_HEIGHT);
            // 2. ノートエリア部分の再描画リクエスト
            // repaint(repaintX, RULER_HEIGHT, repaintWidth, RULER_AND_NOTES_BOTTOM - RULER_HEIGHT);
            // 3. コントローラーレーン部分の再描画リクエスト
            // repaint(repaintX, RULER_AND_NOTES_BOTTOM, repaintWidth, CONTROLLER_LANE_HEIGHT);

            // ↑ 個別に repaint を呼ぶと効率が悪い場合があるため、
            //   古いヘッドと新しいヘッドを含む縦長の矩形全体を repaint する方がシンプルで確実な場合が多い
            repaint(repaintX, 0, repaintWidth, getHeight()); // ★ 縦長の矩形全体を再描画

            // --- ここまで ---

            // 全体再描画の場合 (比較用)
            // repaint();
        }
    }


//    public long getCurrentPlaybackTick() {
//        return this.playbackTick;
//    }

    // ★★★ 親フレームの再生ボタン状態更新メソッドを呼び出すヘルパー ★★★
    public void updateParentPlayButtonState(boolean isPlaying) {
        if (parentFrame != null) {
            parentFrame.updatePlayButtonState(isPlaying);
        }
    }

    public int getSelectedNotesCount() {
        // 複数選択リストが空でなく、中身があればそのサイズを返す
        if (selectedNotesList != null && !selectedNotesList.isEmpty()) {
            return selectedNotesList.size();
        }
        // 複数選択リストが空でも、単一選択があれば1を返す
        if (selectedNote != null) {
            return 1;
        }
        // どちらも選択されていなければ0
        return 0;
    }

//    public List<Note> getSelectedNotesListCopy() {
//        return new ArrayList<>(this.selectedNotesList);
//    }

    public void deleteAllNotesAndRecordCommand() { // Undo可能な全削除を目指すメソッド
        if (!notes.isEmpty()) {
            // TODO: DeleteMultipleNotesCommand を使ってUndo可能にする
            // 現在は単純削除 + Undo履歴クリア
            System.out.println("Executing Delete All Notes...");
            List<Note> notesToDelete = new ArrayList<>(this.notes); // 削除するリストのコピー
            if (!notesToDelete.isEmpty()) {
                DeleteMultipleNotesCommand command = new DeleteMultipleNotesCommand(this, this.notes, notesToDelete);
                undoManager.executeCommand(command); // コマンド実行
            }

            // --- 以前の簡易実装 (Undo不可) ---
            // deleteAllNotes();
            // undoManager.clearStacks();
            // if (parentFrame != null) parentFrame.updateUndoRedoMenuItems(false, false);
            // --- ここまで ---
        }
    }

    // --- Loop Range Methods ---
    public boolean isLoopRangeVisible() {
        return this.showLoopRange;
    }
    public void setLoopRange(long p1, long p2) {
        this.loopStartTick = Math.max(0, Math.min(p1, p2));
        this.loopEndTick = Math.max(0, Math.max(p1, p2));
        // Ensure start is strictly less than end for a valid loop
        if (this.loopStartTick >= this.loopEndTick) {
            this.loopStartTick = Math.max(0, this.loopEndTick - (ppqn > 0 ? ppqn / 4 : 120)); // Ensure a minimal loop length
        }
        this.showLoopRange = true;
        if (parentFrame != null) {
            parentFrame.updateLoopButtonText();
        }
        repaint();
    }
    public void clearLoopRange() {
        this.showLoopRange = false;
        if (parentFrame != null) {
            parentFrame.updateLoopButtonText();
        }
        repaint();
    }
    public long getLoopStartTick() { return loopStartTick; }
    public long getLoopEndTick() { return loopEndTick; }

    // --- Zoom Methods ---
    public void zoomInHorizontal() { /* ... 実装済み ... */
        pixelsPerTick *= 1.5;
        pixelsPerTick = Math.min(pixelsPerTick, 2.0);
        updatePreferredSize();
        repaint();
    }
    public void zoomOutHorizontal() { /* ... 実装済み ... */
        pixelsPerTick /= 1.5;
        pixelsPerTick = Math.max(0.005, pixelsPerTick);
        updatePreferredSize();
        repaint();
    }
    public void zoomInVertical() { /* ... 実装済み ... */
        noteHeight += 2;
        noteHeight = Math.min(noteHeight, 30);
        updatePreferredSize();
        repaint();
    }
    public void zoomOutVertical() { /* ... 実装済み ... */
        noteHeight -= 2;
        noteHeight = Math.max(6, noteHeight);
        updatePreferredSize();
        repaint();
    }

    // --- Private Helper Methods ---
    private void updatePreferredSize() {
        int preferredWidth = KEY_WIDTH + (int) (totalTicks * pixelsPerTick);
        int preferredHeight = RULER_HEIGHT + totalPitches() * noteHeight + CONTROLLER_LANE_HEIGHT;
        setPreferredSize(new Dimension(preferredWidth, preferredHeight));
        revalidate(); // Tell scroll pane to update
    }

    private boolean isBlackKey(int midiNoteNumber) {
        int noteInOctave = midiNoteNumber % 12;
        return noteInOctave == 1 || noteInOctave == 3 || noteInOctave == 6 ||
                noteInOctave == 8 || noteInOctave == 10;
    }

    private int pitchToY(int midiNoteNumber) {
        return RULER_HEIGHT + (MAX_PITCH - midiNoteNumber) * noteHeight;
    }

    private int yToPitch(int y) {
        if (y < RULER_HEIGHT || y >= RULER_HEIGHT + totalPitches() * noteHeight) return -1;
        return MAX_PITCH - ((y - RULER_HEIGHT) / noteHeight);
    }

    private int totalPitches() {
        return MAX_PITCH - MIN_PITCH + 1;
    }

    private int tickToX(long tick) {
        return KEY_WIDTH + (int) (tick * pixelsPerTick);
    }

    private long xToTick(int x) {
        if (x < KEY_WIDTH) return 0;
        return Math.max(0, (long) ((x - KEY_WIDTH) / pixelsPerTick));
    }

    private long snapToGrid(long tick, int snapDivision) {
        if (snapDivision <= 0 || ppqn <= 0 || beatUnit <= 0) return tick; // Avoid division by zero
        // Calculate ticks per snap unit (e.g., 16th note)
        long ticksPerBeat = (long)ppqn * 4 / beatUnit;
        long ticksPerSnap = ticksPerBeat / (snapDivision / 4); // Assumes snapDivision is based on quarter notes (4=quarter, 8=eighth, 16=sixteenth)
        if (ticksPerSnap <= 0) return tick; // Avoid division by zero if snap is too fine
        return (Math.round((double) tick / ticksPerSnap)) * ticksPerSnap;
    }

    private Optional<Note> getNoteAt(int x, int y) {
        // ... (ログ強化版 or 通常版 - 前回提示の通り) ...
        if (x < KEY_WIDTH || y < RULER_HEIGHT || y >= getHeight() - CONTROLLER_LANE_HEIGHT) return Optional.empty();
        long targetTick = xToTick(x);
        int targetPitch = yToPitch(y);
        if (targetPitch == -1) return Optional.empty();

        for (int i = notes.size() - 1; i >= 0; i--) {
            Note note = notes.get(i);
            if (note.getPitch() == targetPitch &&
                    targetTick >= note.getStartTimeTicks() &&
                    targetTick < note.getStartTimeTicks() + note.getDurationTicks()) {
                return Optional.of(note);
            }
        }
        return Optional.empty();
    }

    private void selectNotesInMarquee() {
        List<Note> newlySelected = new ArrayList<>();
        if (marqueeRect == null) return;

        for (Note note : notes) {
            int noteX = tickToX(note.getStartTimeTicks());
            int noteY = pitchToY(note.getPitch());
            int noteWidth = (int) (note.getDurationTicks() * pixelsPerTick);
            int noteActualHeight = this.noteHeight;
            Rectangle noteBounds = new Rectangle(noteX, noteY, Math.max(1,noteWidth), Math.max(1,noteActualHeight));
            if (marqueeRect.intersects(noteBounds)) {
                newlySelected.add(note);
            }
        }
        System.out.println("Marquee selected " + newlySelected.size() + " notes.");
        setSelectedNotesAfterCommand(newlySelected); // 選択状態を更新
        // repaint(); // setSelectedNotesAfterCommand内、またはmouseReleasedの最後に呼ばれる
    }

    // --- Painting Methods --- (drawRuler, drawPianoKeys, drawGrid, drawNotes, etc.) are assumed to be defined as before

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Rectangle clip = g.getClipBounds();
        if (clip == null) clip = getBounds();

        g2d.setColor(DARK_BACKGROUND_COLOR);
        g2d.fillRect(clip.x, clip.y, clip.width, clip.height);

        drawGrid(g2d, clip);
        drawRuler(g2d, clip);
        drawPianoKeys(g2d, clip);
        if (isLoopRangeVisible()) drawLoopRange(g2d, clip);
        drawNotes(g2d, clip); // Handles selection highlighting
        drawControllerLane(g2d, clip);
        drawPlaybackHead(g2d, clip);

        if (isMarqueeSelecting && marqueeRect != null) {
            g2d.setColor(new Color(0, 100, 255, 50));
            g2d.fill(marqueeRect);
            g2d.setColor(new Color(0, 100, 255));
            g2d.draw(marqueeRect);
        }

        if (isDrawingOutline && outlinePathPoints.size() > 1) {
            g2d.setColor(OUTLINE_COLOR);
            Stroke originalStroke = g2d.getStroke();
            g2d.setStroke(OUTLINE_STROKE);
            Path2D path = new Path2D.Float();
            Point firstPoint = outlinePathPoints.getFirst();
            path.moveTo(firstPoint.getX(), firstPoint.getY());
            for (int i = 1; i < outlinePathPoints.size(); i++) {
                Point p = outlinePathPoints.get(i);
                path.lineTo(p.getX(), p.getY());
            }
            g2d.draw(path);
            g2d.setStroke(originalStroke);
        }
    }

    private void drawRuler(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
        g2d.setColor(Color.DARK_GRAY.brighter());
        g2d.fillRect(0, 0, getWidth(), RULER_HEIGHT);
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawLine(0, RULER_HEIGHT - 1, getWidth(), RULER_HEIGHT - 1); // 下境界線
        g2d.drawLine(KEY_WIDTH - 1, 0, KEY_WIDTH - 1, RULER_HEIGHT);    // 左鍵盤エリアとの境界線

        g2d.setFont(new Font("Arial", Font.PLAIN, 10));
        int ticksPerMeasure = ppqn * beatsPerMeasure;

        long startTickRuler = Math.max(0, xToTick(clip.x - KEY_WIDTH)); // ルーラー描画開始Tick
        long endTickRuler = xToTick(clip.x + clip.width - KEY_WIDTH) + ticksPerMeasure; // ルーラー描画終了Tick
        endTickRuler = Math.min(endTickRuler, totalTicks);

        // 小節線と拍線の描画
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
            }
        }

        // ループ範囲フラグ（マーカー）の描画
        if (showLoopRange) {
            int startMarkerX = tickToX(loopStartTick);
            int endMarkerX = tickToX(loopEndTick);
            g2d.setColor(LOOP_MARKER_COLOR); // マーカーの色

            if (startMarkerX >= KEY_WIDTH && startMarkerX >= clip.x && startMarkerX <= clip.x + clip.width) {
                Polygon startTriangle = new Polygon();
                startTriangle.addPoint(startMarkerX, 0);
                startTriangle.addPoint(startMarkerX - 4, 6);
                startTriangle.addPoint(startMarkerX + 4, 6);
                g2d.fillPolygon(startTriangle);
            }

            if (endMarkerX >= KEY_WIDTH && endMarkerX >= clip.x && endMarkerX <= clip.x + clip.width) {
                Polygon endTriangle = new Polygon();
                endTriangle.addPoint(endMarkerX, 0);
                endTriangle.addPoint(endMarkerX - 4, 6);
                endTriangle.addPoint(endMarkerX + 4, 6);
                g2d.fillPolygon(endTriangle);
            }

            if (startMarkerX < endMarkerX) {
                int rectX = Math.max(KEY_WIDTH, startMarkerX);
                int rectWidth = endMarkerX - rectX;
                if (rectX + rectWidth > KEY_WIDTH) {
                    Rectangle loopRulerRect = new Rectangle(rectX, 0, rectWidth, RULER_HEIGHT -1);
                    Rectangle clippedLoopRulerRect = loopRulerRect.intersection(clip);
                    if (!clippedLoopRulerRect.isEmpty()) {
                        g2d.setColor(new Color(LOOP_RANGE_COLOR.getRed(), LOOP_RANGE_COLOR.getGreen(), LOOP_RANGE_COLOR.getBlue(), 30));
                        g2d.fillRect(clippedLoopRulerRect.x, clippedLoopRulerRect.y, clippedLoopRulerRect.width, clippedLoopRulerRect.height);
                    }
                }
            }
        }
    }
    private void drawPianoKeys(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
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
    private void drawGrid(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
        int gridTopY = RULER_HEIGHT;
        int gridBottomY = getHeight() - CONTROLLER_LANE_HEIGHT;

        // Vertical lines (time)
        int ticksPerBeat = ppqn * 4 / beatUnit;
        int ticksPerMeasure = ticksPerBeat * beatsPerMeasure;
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
    private void drawNotes(Graphics2D g2d, Rectangle clip) { /* ... 実装済み (選択ハイライト対応) ... */
        for (Note note : notes) {
            int x = tickToX(note.getStartTimeTicks());
            int y = pitchToY(note.getPitch());
            int width = (int) (note.getDurationTicks() * pixelsPerTick);
            int height = this.noteHeight - 1;

            Rectangle noteRect = new Rectangle(x, y, Math.max(1, width), Math.max(1, height));
            if (clip.intersects(noteRect)) {
                boolean isSelected = false;
                if (!selectedNotesList.isEmpty()) {
                    if (selectedNotesList.contains(note)) {
                        isSelected = true;
                    }
                } else if (note == selectedNote) { // selectedNotesListが空の場合のみ単一選択を評価
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
    private void drawControllerLane(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
        int laneTopY = getHeight() - CONTROLLER_LANE_HEIGHT;
        g2d.setColor(DARK_BACKGROUND_COLOR.darker());
        g2d.fillRect(0, laneTopY, getWidth(), CONTROLLER_LANE_HEIGHT);
        g2d.setColor(GRID_LINE_COLOR_DARK);
        g2d.drawLine(0, laneTopY, getWidth(), laneTopY);

        for (Note note : notes) {
            int x = tickToX(note.getStartTimeTicks());
            int noteWidth = (int) (note.getDurationTicks() * pixelsPerTick);
            Rectangle noteTimeSpanRect = new Rectangle(x, laneTopY, Math.max(1, noteWidth), CONTROLLER_LANE_HEIGHT);

            if (clip.intersects(noteTimeSpanRect)) {
                g2d.setColor(NOTE_COLOR.brighter());
                int velHeight = (int) ((note.getVelocity() / 127.0) * (CONTROLLER_LANE_HEIGHT - 10));
                int velY = laneTopY + (CONTROLLER_LANE_HEIGHT - 10 - velHeight) + 5;
                int velBarWidth = Math.max(2, (int)(pixelsPerTick * ppqn / 16));
                boolean isSelected = selectedNotesList.contains(note) || (selectedNotesList.isEmpty() && note == selectedNote);
                if (isSelected) g2d.setColor(SELECTED_NOTE_COLOR.brighter());
                g2d.fillRect(x, velY, velBarWidth, velHeight);
            }
        }

        int ticksPerBeat = ppqn * 4 / beatUnit;
        int ticksPerMeasure = ticksPerBeat * beatsPerMeasure;
        long endTick = xToTick(clip.x + clip.width) + ticksPerBeat;
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
    private void drawPlaybackHead(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
        int x = tickToX(playbackTick);
        if (x >= KEY_WIDTH && x >= clip.x && x <= clip.x + clip.width) {
            g2d.setColor(PLAYBACK_HEAD_COLOR);
            g2d.drawLine(x, RULER_HEIGHT, x, getHeight()); // ルーラーから下端まで引く
        }
    }
    private void drawLoopRange(Graphics2D g2d, Rectangle clip) { /* ... 実装済み ... */
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

    // --- Event Listeners --- (mouseClicked, mousePressed, mouseReleased, mouseDragged, mouseWheelMoved, keyPressed, etc.)

    @Override
    public void mouseClicked(MouseEvent e) {
        // デバッグ出力
        System.out.println(
                String.format("mouseClicked: Button=%d, Shift=%b, Ctrl=%b, Alt=%b, LongPress=%b, DrawingPencil=%b, X=%d, Y=%d",
                        e.getButton(), e.isShiftDown(), e.isControlDown(), e.isAltDown(),
                        isLongPress, isDrawingOutline, e.getX(), e.getY()) // isDrawingWithPencil -> isDrawingOutline
        );

        if (isDrawingOutline) { // 外形描画モード中はクリック無視
            System.out.println("mouseClicked: In outline drawing mode, ignoring click.");
            return;
        }

        // 長押し中はクリック処理をスキップ (意図しないノート作成を防ぐ)
        if (isLongPress) {
            System.out.println("mouseClicked: Long press active, ignoring click.");
            return;
        }

        if (e.getButton() == MouseEvent.BUTTON1) {

            if (e.getX() < KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                // ピアノ鍵盤クリック
                int pitch = yToPitch(e.getY());
                if (pitch != -1) {
                    System.out.println("Piano key clicked (Audition): " + pitch);
                    // TODO: Audition 機能実装
                }
            } else if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                // ノートエリアクリック
                Optional<Note> clickedNoteOpt = getNoteAt(e.getX(), e.getY());

                if (!clickedNoteOpt.isPresent()) {
                    // 空白部分クリック -> 新規ノート作成
                    System.out.println("mouseClicked: Empty space in note area clicked. Attempting to create note.");
                    int pitch = yToPitch(e.getY());
                    long startTime = snapToGrid(xToTick(e.getX()), 16);
                    long duration = this.ppqn; // 1拍の長さ

                    System.out.println(String.format("  Create params: pitch=%d, startTime=%d, duration=%d (ppqn=%d)",
                            pitch, startTime, duration, this.ppqn));

                    if (pitch != -1) {
                        Note newNote = new Note(pitch, startTime, duration, 100, 0);
                        System.out.println("mouseClicked: Creating AddNoteCommand with notes hash=" + System.identityHashCode(this.notes));
                        AddNoteCommand addCmd = new AddNoteCommand(this, this.notes, newNote);
                        undoManager.executeCommand(addCmd); // コマンド経由で追加・選択・情報更新
                        System.out.println("  AddNoteCommand executed for new note.");

                        if (startTime + duration > totalTicks) {
                            totalTicks = startTime + duration + (long) this.ppqn * 4;
                            updatePreferredSize();
                            System.out.println("  Total ticks updated to: " + totalTicks);
                        }
                    } else {
                        System.out.println("  Note creation skipped: Invalid pitch.");
                    }
                } else {
                    // 既存ノートクリック
                    System.out.println("Existing note clicked. Selection primarily handled by mousePressed. Note: " + clickedNoteOpt.get());
                    // ダブルクリックなどの処理をここに追加可能
                }
            } else if (e.getY() < RULER_HEIGHT && e.getX() >= KEY_WIDTH) {
                // --- ルーラーエリアをクリックした場合 ---
                long clickedTick = snapToGrid(xToTick(e.getX()), 4); // クリック位置を拍にスナップ

                if (e.isAltDown()) {
                    // Alt + Click for loop range setting
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        setLoopRange(clickedTick, loopEndTick);
                        System.out.println("Loop start set by Alt+LeftClick: " + loopStartTick);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        setLoopRange(loopStartTick, clickedTick);
                        System.out.println("Loop end set by Alt+RightClick: " + loopEndTick);
                    }
                    if (parentFrame != null) parentFrame.updateLoopButtonText();
                    return; // Consume event
                }

                System.out.println("Ruler area clicked.");

                if (e.isShiftDown()) {
                    // Shift + クリック：再生開始位置 (playbackTick) を変更
                    setPlaybackTick(clickedTick);
                    System.out.println(String.format("  Playback position set to: %d by Shift+Click on ruler.", clickedTick));

                } else if (e.isControlDown() || e.isMetaDown()) { // Ctrl/Cmd + クリック：ループ終了位置 (loopEndTick) を設定
                    setLoopRange(loopStartTick, clickedTick);
                    System.out.println(String.format("  Loop end set to: %d by Ctrl/Cmd+Click on ruler.", loopEndTick));
                    if (parentFrame != null) parentFrame.updateLoopButtonText();

                } else {
                    // No modifier click: Set playback position
                    if (parentFrame != null) {
                        parentFrame.setPlaybackTickPosition(clickedTick);
                    }
                    System.out.println(String.format("  Playback position set to: %d by Click on ruler.", clickedTick));
                }
            }
        }
    }


    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            requestFocusInWindow();
            dragStartPoint = e.getPoint();
            isLongPress = false; // Press時にリセット
            longPressTimer.stop(); // 既存タイマー停止

            if (e.isShiftDown() && e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                // Shift + クリック -> 外形描画開始
                isDrawingOutline = true;
                currentDragMode = DragMode.NONE;
                clearSelectionAfterCommand();
                outlinePathPoints.clear();
                outlinePathPoints.add(e.getPoint());
                repaint();
            } else if (e.getX() >= KEY_WIDTH && e.getY() < RULER_HEIGHT) {
                // --- Ruler Area Click Logic ---
                long clickedTick = snapToGrid(xToTick(e.getX()), 4);

                if (e.isControlDown()) { // Use Ctrl key as requested
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        // Ctrl + Left Click: Set loop start
                        setLoopRange(clickedTick, loopEndTick);
                        System.out.println("Loop start set by Ctrl+LeftClick: " + loopStartTick);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // Ctrl + Right Click: Set loop end
                        setLoopRange(loopStartTick, clickedTick);
                        System.out.println("Loop end set by Ctrl+RightClick: " + loopEndTick);
                    }
                    if (parentFrame != null) parentFrame.updateLoopButtonText();

                } else if (!e.isShiftDown() && !e.isAltDown() && !e.isMetaDown()) {
                    // No modifier click: Set playback position
                    if (parentFrame != null) {
                        parentFrame.setPlaybackTickPosition(clickedTick);
                    }
                    System.out.println(String.format("  Playback position set to: %d by Click on ruler.", clickedTick));
                }
                // Consume the event to prevent other interactions
                e.consume();

            } else if (e.getX() >= KEY_WIDTH && e.getY() > RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                // 通常のノートエリアプレス
                isDrawingOutline = false;
                Optional<Note> noteOpt = getNoteAt(e.getX(), e.getY());
                if (noteOpt.isPresent()) {
                    // 既存ノート上でのプレス -> 移動 or リサイズ or 複数選択操作
                    selectedNote = noteOpt.get(); // クリックされたノートを記憶
                    boolean isSelected = selectedNotesList.contains(selectedNote);

                    if (!e.isShiftDown() && !e.isControlDown()) { // 修飾キーなし -> 単一選択 & ドラッグ準備
                        if (!isSelected) { // まだ選択されていなければ選択
                            setSelectedNoteAfterCommand(selectedNote);
                        }
                        // 移動かリサイズか判定
                        int noteEndX = tickToX(selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks());
                        if (Math.abs(e.getX() - noteEndX) < resizeHandleSensitivity) {
                            currentDragMode = DragMode.RESIZE_END;
                        } else {
                            currentDragMode = DragMode.MOVE;
                        }
                        // 元の状態を保存 (単一選択のみ)
                        dragNoteOriginal = new Note(selectedNote.getPitch(), selectedNote.getStartTimeTicks(), selectedNote.getDurationTicks(), selectedNote.getVelocity(), selectedNote.getChannel());
                        longPressTimer.restart(); // 長押し判定開始
                    } else if (e.isShiftDown()) { // Shift + クリック -> 複数選択に追加/削除 (トグル)
                        currentDragMode = DragMode.NONE; // ドラッグは開始しない
                        Note clickedNote = noteOpt.get(); // クリックされたノートを取得
                        if (selectedNotesList.contains(clickedNote)) {
                            // --- 既に選択されているノートをShift+クリック -> 選択解除 ---
                            selectedNotesList.remove(clickedNote);
                            System.out.println("  Removed from multi-selection: " + clickedNote);

                            // ★★★ 削除したノートが代表選択(selectedNote)だったかチェック ★★★
                            if (clickedNote == this.selectedNote) {
                                // 代表選択だったノートが削除されたので、代表選択を更新する
                                this.selectedNote = selectedNotesList.isEmpty() ? null : selectedNotesList.getFirst(); // リストが空ならnull、そうでなければ最初の要素を代表に
                                System.out.println("  -> Representative selection updated to: " + this.selectedNote);
                            }
                            // ★★★ ここまで修正 ★★★

                        } else {
                            // --- 選択されていないノートをShift+クリック -> 選択追加 ---
                            selectedNotesList.add(clickedNote);
                            this.selectedNote = clickedNote; // 新しく追加したものを代表選択にする（または既存の代表を維持するなど、仕様による）
                            System.out.println("  Added to multi-selection: " + clickedNote);
                        }
                        updateNoteInfoForFrame(this.selectedNote); // 代表情報を更新
                        repaint();
                    } else { // Ctrl/Cmd + クリック (将来の拡張用、今は何もしないか単一選択)a
                        currentDragMode = DragMode.NONE;
                        setSelectedNoteAfterCommand(selectedNote); // とりあえず単一選択
                        repaint();
                    }

                } else { // 空白部分でのプレス -> マーキー選択開始
                    clearSelectionAfterCommand();
                    currentDragMode = DragMode.NONE;
                    isMarqueeSelecting = true;
                    marqueeStartPoint = e.getPoint();
                    marqueeRect = new Rectangle(marqueeStartPoint);
                    repaint();
                }
            } else { // 鍵盤やルーラー
                isDrawingOutline = false;
                currentDragMode = DragMode.NONE;
                clearSelectionAfterCommand();
                repaint();
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        longPressTimer.stop(); // ボタンが離されたら長押しタイマー停止
        isLongPress = false;   // 長押しフラグもリセット

        // --- マーキー選択モードの終了処理 ---
        if (isMarqueeSelecting) {
            System.out.println("mouseReleased: Marquee selection finished.");
            isMarqueeSelecting = false;
            if (marqueeRect != null && marqueeRect.width > 5 && marqueeRect.height > 5) {
                selectNotesInMarquee(); // 範囲内のノートを選択
            } else {
                clearSelectionAfterCommand(); // 無効な矩形なら選択解除
                System.out.println("  Marquee rectangle too small or invalid, selection cleared.");
            }
            marqueeRect = null;
            repaint(); // マーキー矩形消去と選択ハイライト更新

            // モードとドラッグ情報をリセットして終了
            currentDragMode = DragMode.NONE;
            dragStartPoint = null;
            dragNoteOriginal = null;
            return;
        }

        // --- 左ボタンのリリースの場合のみ ---
        if (e.getButton() == MouseEvent.BUTTON1) {

            if (isDrawingOutline) {
                // --- 外形描画モードの終了処理 ---
                System.out.println("mouseReleased: Outline drawing finished.");
                isDrawingOutline = false;
                outlinePathPoints.clear();
                repaint(); // 軌跡を消去

            } else if (currentDragMode == DragMode.MOVE) { // 移動モード完了
                System.out.println("mouseReleased: Note MOVE finished.");
                if (!selectedNotesList.isEmpty()) {
                    // TODO: 複数ノート移動のUndo対応 (MoveMultipleNotesCommandなど)
                    if (selectedNotesList.size() == 1 && selectedNote != null && dragNoteOriginal != null) {
                        // 単一ノート移動のUndoコマンド登録
                        long finalSnappedStartTime = snapToGrid(selectedNote.getStartTimeTicks(), 16);
                        int finalPitch = selectedNote.getPitch();

                        if (dragNoteOriginal.getStartTimeTicks() != finalSnappedStartTime || dragNoteOriginal.getPitch() != finalPitch) {
                            System.out.println("  Registering MoveNoteCommand.");
                            MoveNoteCommand moveCmd = new MoveNoteCommand(this, selectedNote, dragNoteOriginal.getStartTimeTicks(), dragNoteOriginal.getPitch(), finalSnappedStartTime, finalPitch);
                            undoManager.executeCommand(moveCmd); // コマンド実行 & 登録
                        } else {
                            // 位置が変わらなかった場合、元の状態に戻す
                            selectedNote.setStartTimeTicks(dragNoteOriginal.getStartTimeTicks());
                            selectedNote.setPitch(dragNoteOriginal.getPitch());
                            System.out.println("  Note position did not change, no MoveNoteCommand registered.");
                            repaint(); // 元の位置に戻すための再描画
                        }
                        // totalTicks 更新チェック (単一の場合)
                        checkAndUpdateTotalTicks(selectedNote);

                    } else if (selectedNotesList.size() > 1) {
                        System.out.println("  Multiple notes move finished - Undo/Redo not fully implemented yet.");
                        // TODO: 複数ノート移動のUndoコマンド登録処理
                        // TODO: 複数ノート移動後の totalTicks 更新チェック
                        checkAndUpdateTotalTicksForMultipleNotes(); // ヘルパーメソッドを呼ぶ
                        repaint(); // ドラッグ中の表示を確定
                    }
                    // 情報ラベル更新 (選択状態に応じて)
                    updateNoteInfoForFrame(selectedNote); // 複数選択時はupdateNoteInfo内で分岐される

                } else {
                    System.out.println("  Move mode finished but no notes were selected?"); // 念のため
                }

            } else if (currentDragMode == DragMode.RESIZE_END) { // リサイズモード完了
                System.out.println("mouseReleased: Note RESIZE finished.");
                // リサイズは通常単一ノートのみ対象とする
                if (selectedNote != null && dragNoteOriginal != null && selectedNotesList.size() == 1) {
                    long finalSnappedDuration = snapToGrid(selectedNote.getDurationTicks(), 16);
                    if (finalSnappedDuration < ppqn / 16) finalSnappedDuration = ppqn / 16; // 最小長さ

                    if (dragNoteOriginal.getDurationTicks() != finalSnappedDuration) {
                        System.out.println("  Registering ResizeNoteCommand.");
                        ResizeNoteCommand resizeCmd = new ResizeNoteCommand(this, selectedNote, dragNoteOriginal.getDurationTicks(), finalSnappedDuration);
                        undoManager.executeCommand(resizeCmd); // コマンド実行 & 登録
                    } else {
                        selectedNote.setDurationTicks(dragNoteOriginal.getDurationTicks()); // 元の長さに戻す
                        System.out.println("  Note duration did not change, no ResizeNoteCommand registered.");
                        repaint(); // 元の長さに戻すための再描画
                    }
                    // totalTicks 更新チェック (単一の場合)
                    checkAndUpdateTotalTicks(selectedNote);
                    // 情報ラベル更新
                    updateNoteInfoForFrame(selectedNote);

                } else {
                    System.out.println("  Resize mode finished but not single selection or original data missing?");
                    // 複数選択時のリサイズは通常サポートしないか、特別な処理が必要
                    repaint(); // ドラッグ中の表示を確定
                }

            } else {
                // 通常のクリックリリース（ノート作成や選択）や、他のモード（PITCH_ONLYなど）からのリリース
                // mouseClicked や mousePressed で主な処理は行われているため、
                // ここで特別な処理は不要な場合が多い。
                System.out.println("mouseReleased: No specific drag/draw mode active or other mode.");
            }

            // --- 全ての左ボタンリリースに共通の後処理 ---
            currentDragMode = DragMode.NONE;
            dragStartPoint = null;
            dragNoteOriginal = null;
            // isDrawingOutline, isMarqueeSelecting もリセット済みのはず
        }
        // 右ボタンなどのリリースはここでは処理しない
    }

    // --- totalTicks 更新用ヘルパーメソッド (新規追加) ---

    /**
     * 指定された単一ノートの終了時間に基づいて totalTicks を必要に応じて更新します。
     * @param note チェック対象のノート
     */
    private void checkAndUpdateTotalTicks(Note note) {
        if (note == null) return;
        long noteEndTime = note.getStartTimeTicks() + note.getDurationTicks();
        if (noteEndTime > totalTicks) {
            totalTicks = noteEndTime + (long) ppqn * 4; // 少し余裕を持たせる
            updatePreferredSize();
            System.out.println("  Total ticks updated to: " + totalTicks + " due to single note edit.");
        }
    }

    /**
     * 複数選択されているノートに基づいて totalTicks を必要に応じて更新します。
     */
    private void checkAndUpdateTotalTicksForMultipleNotes() {
        if (selectedNotesList.isEmpty()) return;
        long maxEndTime = 0;
        for (Note note : selectedNotesList) {
            maxEndTime = Math.max(maxEndTime, note.getStartTimeTicks() + note.getDurationTicks());
        }
        if (maxEndTime > totalTicks) {
            totalTicks = maxEndTime + (long) ppqn * 4; // 少し余裕を持たせる
            updatePreferredSize();
            System.out.println("  Total ticks updated to: " + totalTicks + " due to multiple notes edit.");
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (isDrawingOutline) {
            // 外形描画モード
            if (e.getX() >= KEY_WIDTH && e.getY() >= RULER_HEIGHT && e.getY() < getHeight() - CONTROLLER_LANE_HEIGHT) {
                outlinePathPoints.add(e.getPoint());
            }
            repaint();
        } else if (isMarqueeSelecting) {
            // マーキー選択モード
            if (marqueeStartPoint != null) {
                marqueeRect.setBounds(
                        Math.min(marqueeStartPoint.x, e.getX()), Math.min(marqueeStartPoint.y, e.getY()),
                        Math.abs(e.getX() - marqueeStartPoint.x), Math.abs(e.getY() - marqueeStartPoint.y));
                repaint();
            }
        } else if (currentDragMode == DragMode.MOVE && !selectedNotesList.isEmpty()) {
            // ノート移動モード (単一選択のみ対応中)
            if (selectedNote != null && dragNoteOriginal != null && selectedNotesList.size() == 1) {
                int dx = e.getX() - dragStartPoint.x;
                int dy = e.getY() - dragStartPoint.y;
                long newStartTime = xToTick(tickToX(dragNoteOriginal.getStartTimeTicks()) + dx);
                int newPitch = yToPitch(pitchToY(dragNoteOriginal.getPitch()) + dy);
                if (newPitch < MIN_PITCH) newPitch = MIN_PITCH;
                if (newPitch > MAX_PITCH) newPitch = MAX_PITCH;
                newStartTime = Math.max(0, newStartTime);
                if (newPitch != -1) selectedNote.setPitch(newPitch);
                selectedNote.setStartTimeTicks(newStartTime);
                if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
                repaint();
            } // TODO: 複数ノート移動
        } else if (currentDragMode == DragMode.RESIZE_END && selectedNote != null && dragNoteOriginal != null && selectedNotesList.size() == 1) {
            // ノートリサイズモード (単一選択のみ)
            int dx = e.getX() - dragStartPoint.x;
            long originalEndTime = dragNoteOriginal.getStartTimeTicks() + dragNoteOriginal.getDurationTicks();
            long newEndTime = xToTick(tickToX(originalEndTime) + dx);
            long newDuration = newEndTime - selectedNote.getStartTimeTicks();
            newDuration = Math.max(ppqn / 16, newDuration);

            System.out.println(String.format("mouseDragged(RESIZE): newDuration=%d", newDuration)); // ★デバッグログ追加
            selectedNote.setDurationTicks(newDuration);
            System.out.println(String.format("  -> selectedNote duration now: %d", selectedNote.getDurationTicks())); // ★デバッグログ追加
            if (parentFrame != null) parentFrame.updateNoteInfo(selectedNote);
            repaint();
        }
        // 長押し判定中にドラッグされたらタイマーキャンセル
        if (longPressTimer.isRunning()) {
            longPressTimer.stop();
            isLongPress = false; // ドラッグ開始したら長押しではない
            System.out.println("Drag started, long press cancelled.");
        }
    }

    // PianoRollView.java の mouseWheelMoved メソッド

    // PianoRollView.java の mouseWheelMoved メソッド

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (e.isControlDown() && !e.isShiftDown()) { // Horizontal zoom
            if (e.getWheelRotation() < 0) zoomInHorizontal(); else zoomOutHorizontal();
            e.consume();
        } else if (e.isControlDown() && e.isShiftDown()) { // Vertical zoom
            if (e.getWheelRotation() < 0) zoomInVertical(); else zoomOutVertical();
            e.consume();
        } else { // Default scroll behavior
            JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (scrollPane != null) {
                scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scrollPane));
            }
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {} // 必要なら実装

    @Override
    public void mouseExited(MouseEvent e) {
        if (longPressTimer.isRunning()) {
            longPressTimer.stop();
            isLongPress = false;
            System.out.println("Mouse exited, long press cancelled.");
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        boolean onResizeHandle = false;
        if (selectedNote != null && selectedNotesList.size() == 1) {
            int noteEndX = tickToX(selectedNote.getStartTimeTicks() + selectedNote.getDurationTicks());
            int noteY = pitchToY(selectedNote.getPitch());
            if (Math.abs(e.getX() - noteEndX) < resizeHandleSensitivity && e.getY() >= noteY && e.getY() < noteY + noteHeight) {
                onResizeHandle = true;
            }
        }
        setCursor(onResizeHandle ? Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR) : Cursor.getDefaultCursor());
    }

    @Override
    public void keyTyped(KeyEvent e) {} // 通常は keyPressed を使う

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE) {
            if (parentFrame != null) {
                parentFrame.togglePlayback();
            }
            e.consume(); // Prevent the space key from triggering other actions
            return;
        }

        if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            System.out.println("Delete/Backspace key pressed.");
            if (!selectedNotesList.isEmpty()) {
                System.out.println("  Attempting to delete multiple notes: " + selectedNotesList.size());
                DeleteMultipleNotesCommand deleteCmd = new DeleteMultipleNotesCommand(this, this.notes, new ArrayList<>(selectedNotesList));
                undoManager.executeCommand(deleteCmd);
            } else if (selectedNote != null) {
                System.out.println("  Attempting to delete single note: " + selectedNote);
                DeleteNoteCommand deleteCmd = new DeleteNoteCommand(this, this.notes, selectedNote);
                undoManager.executeCommand(deleteCmd);
            } else {
                System.out.println("  No notes selected to delete.");
            }
        }
        // TODO: Add other keyboard shortcuts (arrow keys for moving notes, etc.)
    }

    @Override
    public void keyReleased(KeyEvent e) {} // 通常は keyPressed を使う

} // End of PianoRollView class