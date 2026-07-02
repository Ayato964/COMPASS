package org.codesfactory.ux.pianoroll;

import com.formdev.flatlaf.FlatDarkLaf;
import com.google.gson.Gson;
import org.codesfactory.api.GenerateMeta;
import org.codesfactory.api.MozartAPIClient;
import java.io.File;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.sound.midi.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

public class ArrangementFrame extends JFrame {
    
    private final List<Track> tracks = new ArrayList<>();
    private final PlaybackManager playbackManager; // メインシーケンサ用
    private final List<PianoRoll> activePianoRolls = new ArrayList<>();
    
    private final JPanel trackHeaderPanel;
    private final TimelinePanel timelinePanel;
    private final JScrollPane scrollPane;
    
    private final JTextField bpmField;
    private final JComboBox<String> quantizeComboBox;
    
    private final int trackHeight = 80;
    private final int rulerHeight = 30;
    private final int ppqn = 480;
    private final int beatsPerMeasure = 4;
    private final int ticksPerMeasure = ppqn * beatsPerMeasure;
    
    private double zoomX = 0.15; // 横方向のズーム率 (1 tick あたりのピクセル数)
    
    private Track selectedTrack = null;
    private final java.util.Set<Track> selectedTracks = new java.util.HashSet<>();
    private MidiRegion selectedRegion = null;
    private final java.util.Set<MidiRegion> selectedRegions = new java.util.HashSet<>();
    
    public ArrangementFrame() {
        setTitle("COMPASS - Arrangement View");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 720);
        setLocationRelativeTo(null);
        
        // デフォルトトラックを1つ追加
        Track defaultTrack = new Track("Track 1");
        tracks.add(defaultTrack);
        selectedTrack = defaultTrack;
        selectedTracks.add(defaultTrack);
        
        // PlaybackManagerの初期化
        playbackManager = new PlaybackManager(null); // Timeline用の再生ヘッド同期は別で行う
        
        // ツールバー
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JButton playButton = new JButton("▶ Play");
        JButton pauseButton = new JButton("⏸ Pause");
        JButton stopButton = new JButton("■ Stop");
        
        JButton addTrackButton = new JButton("＋ Add Track");
        JButton deleteTrackButton = new JButton("✖ Delete Track");
        
        bpmField = new JTextField("120.0", 4);
        quantizeComboBox = new JComboBox<>(new String[]{"1 Measure", "1/2", "1/4", "1/8", "1/16"});
        
        toolBar.add(playButton);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(pauseButton);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(stopButton);
        toolBar.addSeparator();
        toolBar.add(new JLabel(" BPM: "));
        toolBar.add(bpmField);
        toolBar.addSeparator();
        toolBar.add(addTrackButton);
        toolBar.add(Box.createHorizontalStrut(5));
        toolBar.add(deleteTrackButton);
        toolBar.addSeparator();
        toolBar.add(new JLabel(" Grid Snap: "));
        toolBar.add(quantizeComboBox);
        toolBar.addSeparator();
        toolBar.add(new JLabel(" Zoom: "));
        JButton zoomInBtn = new JButton("↔+");
        JButton zoomOutBtn = new JButton("↔-");
        zoomInBtn.setFocusPainted(false);
        zoomOutBtn.setFocusPainted(false);
        zoomInBtn.addActionListener(e -> zoomIn(null));
        zoomOutBtn.addActionListener(e -> zoomOut(null));
        toolBar.add(zoomInBtn);
        toolBar.add(Box.createHorizontalStrut(2));
        toolBar.add(zoomOutBtn);
        
        add(toolBar, BorderLayout.NORTH);
        
        // トラックヘッダーパネル (縦並び)
        trackHeaderPanel = new JPanel();
        trackHeaderPanel.setLayout(new BoxLayout(trackHeaderPanel, BoxLayout.Y_AXIS));
        
        // タイムラインパネル
        timelinePanel = new TimelinePanel();
        
        // スクロールペイン
        scrollPane = new JScrollPane(timelinePanel);
        scrollPane.setRowHeaderView(trackHeaderPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(30);
        
        add(scrollPane, BorderLayout.CENTER);
        
        // リフレッシュ
        rebuildTrackHeaders();
        
        // イベントハンドラ
        playButton.addActionListener(e -> startPlayback());
        pauseButton.addActionListener(e -> pausePlayback());
        stopButton.addActionListener(e -> stopPlayback());
        
        addTrackButton.addActionListener(e -> {
            Track newTrack = new Track("Track " + (tracks.size() + 1));
            tracks.add(newTrack);
            selectedTrack = newTrack;
            selectedTracks.clear();
            selectedTracks.add(newTrack);
            rebuildTrackHeaders();
            timelinePanel.recalculateSize();
            scrollPane.revalidate();
            scrollPane.repaint();
        });
        
        deleteTrackButton.addActionListener(e -> {
            if (tracks.size() > selectedTracks.size()) {
                tracks.removeAll(selectedTracks);
                selectedTracks.clear();
                selectedTrack = tracks.get(tracks.size() - 1);
                selectedTracks.add(selectedTrack);
                rebuildTrackHeaders();
                timelinePanel.recalculateSize();
                scrollPane.revalidate();
                scrollPane.repaint();
            } else {
                JOptionPane.showMessageDialog(ArrangementFrame.this, "Cannot delete all tracks.", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        bpmField.addActionListener(e -> updateTempo());
        bpmField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                updateTempo();
            }
        });
        
        // 再生位置の定期更新タイマー (再生ヘッドのアニメーション用)
        Timer repaintTimer = new Timer(30, e -> {
            if (playbackManager.getSequencer() != null && playbackManager.getSequencer().isRunning()) {
                long currentTick = playbackManager.getSequencer().getTickPosition();
                timelinePanel.repaint();
                for (PianoRoll pr : activePianoRolls) {
                    pr.updatePlaybackHeadOnly(currentTick);
                }
            }
        });
        repaintTimer.start();
        
        // グローバルキーショートカットの設定 (Space: 再生/一時停止, Delete/Backspace: 選択リージョン削除, Up/Down: トラック切替)
        JComponent mainContent = (JComponent) getContentPane();
        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "togglePlayback"
        );
        mainContent.getActionMap().put("togglePlayback", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (playbackManager.getSequencer() != null && playbackManager.getSequencer().isRunning()) {
                    pausePlayback();
                } else {
                    startPlayback();
                }
            }
        });

        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelected"
        );
        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "deleteSelected"
        );
        mainContent.getActionMap().put("deleteSelected", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedRegion();
            }
        });

        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "selectPrevTrack"
        );
        mainContent.getActionMap().put("selectPrevTrack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectPreviousTrack();
            }
        });

        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "selectNextTrack"
        );
        mainContent.getActionMap().put("selectNextTrack", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectNextTrack();
            }
        });

        mainContent.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK), "selectAllTracks"
        );
        mainContent.getActionMap().put("selectAllTracks", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                selectAllTracks();
            }
        });

        // ウィンドウクローズ時にMIDI解放
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                playbackManager.close();
            }
        });

        // 起動時にデフォルトでテキストボックスが入力待機にならないよう、フォーカスをメインタイムラインに移動
        SwingUtilities.invokeLater(() -> timelinePanel.requestFocusInWindow());
    }
    
    private JPopupMenu createTrackPopupMenu(Track track) {
        JPopupMenu trackPopupMenu = new JPopupMenu();
        JMenuItem deleteTrackItem = new JMenuItem("Delete Track");
        deleteTrackItem.addActionListener(ev -> {
            if (tracks.size() > selectedTracks.size()) {
                tracks.removeAll(selectedTracks);
                selectedTracks.clear();
                selectedTrack = tracks.get(tracks.size() - 1);
                selectedTracks.add(selectedTrack);
                rebuildTrackHeaders();
                timelinePanel.recalculateSize();
                scrollPane.revalidate();
                scrollPane.repaint();
            } else {
                JOptionPane.showMessageDialog(ArrangementFrame.this, "Cannot delete all tracks.", "Warning", JOptionPane.WARNING_MESSAGE);
            }
        });
        trackPopupMenu.add(deleteTrackItem);

        // トラックの色変更メニューを追加
        JMenu colorSubMenu = new JMenu("Change Track Color");
        java.util.Map<String, java.awt.Color> colorMap = new java.util.LinkedHashMap<>();
        colorMap.put("Purple (Default)", new java.awt.Color(78, 59, 120));
        colorMap.put("Red", new java.awt.Color(168, 50, 50));
        colorMap.put("Blue", new java.awt.Color(50, 94, 168));
        colorMap.put("Green", new java.awt.Color(50, 168, 82));
        colorMap.put("Yellow", new java.awt.Color(168, 160, 50));
        colorMap.put("Orange", new java.awt.Color(168, 101, 50));

        for (java.util.Map.Entry<String, java.awt.Color> entry : colorMap.entrySet()) {
            JMenuItem colorItem = new JMenuItem(entry.getKey());
            colorItem.addActionListener(ev -> {
                track.setColor(entry.getValue());
                rebuildTrackHeaders();
                timelinePanel.repaint();
            });
            colorSubMenu.add(colorItem);
        }
        trackPopupMenu.add(colorSubMenu);

        // トラックの結合メニューを追加 (MIDIリージョン同士を結合)
        JMenuItem mergeRegionsItem = new JMenuItem("Merge Selected Regions (Blocks)");
        mergeRegionsItem.setEnabled(selectedRegions.size() > 1);
        mergeRegionsItem.addActionListener(ev -> {
            mergeSelectedRegions();
        });
        trackPopupMenu.add(mergeRegionsItem);
        
        return trackPopupMenu;
    }

    private void rebuildTrackHeaders() {
        trackHeaderPanel.removeAll();
        
        // Remove old listeners to avoid memory leaks/multiple triggers
        for (MouseListener ml : trackHeaderPanel.getMouseListeners()) {
            trackHeaderPanel.removeMouseListener(ml);
        }

        // ルーラーに対応するスペースを上部に追加
        JPanel dummySpacer = new JPanel();
        dummySpacer.setPreferredSize(new Dimension(220, rulerHeight));
        dummySpacer.setMinimumSize(new Dimension(220, rulerHeight));
        dummySpacer.setMaximumSize(new Dimension(220, rulerHeight));
        dummySpacer.setOpaque(false);
        trackHeaderPanel.add(dummySpacer);
        
        for (Track track : tracks) {
            JPanel header = new JPanel();
            header.setLayout(new GridBagLayout());
            header.setPreferredSize(new Dimension(220, trackHeight));
            header.setMinimumSize(new Dimension(220, trackHeight));
            header.setMaximumSize(new Dimension(220, trackHeight));
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Color.DARK_GRAY));
            
            if (selectedTracks.contains(track)) {
                header.setBackground(new Color(70, 75, 95));
            } else {
                header.setBackground(new Color(43, 43, 43));
            }
            
            // トラック名
            JTextField nameField = new JTextField(track.getName(), 8);
            nameField.addActionListener(e -> {
                track.setName(nameField.getText());
                timelinePanel.repaint();
            });
            nameField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    track.setName(nameField.getText());
                    timelinePanel.repaint();
                }
            });
            
            // 楽器
            String[] instruments = {"PIANO", "SAX", "DRUMS", "BASS", "SYNTH", "GUITAR", "VIOLIN"};
            JComboBox<String> instCombo = new JComboBox<>(instruments);
            instCombo.setSelectedItem(track.getInstrument());
            instCombo.addActionListener(e -> track.setInstrument((String) instCombo.getSelectedItem()));
            
            // 単旋律 (Mono) 設定
            JCheckBox monoCheck = new JCheckBox("Mono");
            monoCheck.setSelected(track.isMonophonic());
            monoCheck.setOpaque(false);
            monoCheck.addActionListener(e -> track.setMonophonic(monoCheck.isSelected()));
            
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2, 4, 2, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            
            gbc.gridx = 0; gbc.gridy = 0;
            header.add(nameField, gbc);
            
            gbc.gridx = 0; gbc.gridy = 1;
            header.add(instCombo, gbc);
            
            gbc.gridx = 1; gbc.gridy = 1;
            gbc.weightx = 0.0;
            header.add(monoCheck, gbc);
            
            JPopupMenu trackPopupMenu = createTrackPopupMenu(track);

            header.addMouseListener(new MouseAdapter() {
                private void checkPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        trackPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    ArrangementFrame.this.requestFocusInWindow();
                    selectedTrack = track;
                    if (e.isControlDown()) {
                        if (selectedTracks.contains(track)) {
                            selectedTracks.remove(track);
                            if (selectedTracks.isEmpty() && !tracks.isEmpty()) {
                                selectedTracks.add(track);
                            }
                        } else {
                            selectedTracks.add(track);
                        }
                    } else {
                        selectedTracks.clear();
                        selectedTracks.add(track);
                    }
                    rebuildTrackHeaders();
                    timelinePanel.repaint();
                    checkPopup(e);
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    checkPopup(e);
                }
            });
            
            trackHeaderPanel.add(header);
        }
        
        // 空白エリアの右クリックポップアップメニュー (New Track)
        JPopupMenu emptyPopupMenu = new JPopupMenu();
        JMenuItem newTrackItem = new JMenuItem("New Track");
        newTrackItem.addActionListener(ev -> {
            Track newTrack = new Track("Track " + (tracks.size() + 1));
            tracks.add(newTrack);
            selectedTrack = newTrack;
            rebuildTrackHeaders();
            timelinePanel.recalculateSize();
            scrollPane.revalidate();
            scrollPane.repaint();
        });
        emptyPopupMenu.add(newTrackItem);

        trackHeaderPanel.addMouseListener(new MouseAdapter() {
            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    emptyPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                ArrangementFrame.this.requestFocusInWindow();
                checkPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                checkPopup(e);
            }
        });

        // 下部余白
        trackHeaderPanel.add(Box.createVerticalGlue());
        
        trackHeaderPanel.revalidate();
        trackHeaderPanel.repaint();
    }
    
    private void startPlayback() {
        // 全トラックのノートを統合してPlaybackManagerにロード
        List<Note> allNotes = new ArrayList<>();
        for (Track track : tracks) {
            allNotes.addAll(track.getNotes());
        }
        playbackManager.loadNotes(allNotes, ppqn);
        
        // テンポを設定
        try {
            float bpm = Float.parseFloat(bpmField.getText());
            playbackManager.setTempo(bpm);
        } catch (NumberFormatException e) {
            playbackManager.setTempo(120.0f);
        }
        
        playbackManager.play();
    }
    
    private void pausePlayback() {
        playbackManager.pause();
        if (playbackManager.getSequencer() != null) {
            long tick = playbackManager.getSequencer().getTickPosition();
            for (PianoRoll pr : activePianoRolls) {
                pr.updatePlaybackHeadOnly(tick);
            }
        }
    }
    
    private void stopPlayback() {
        playbackManager.stop();
        timelinePanel.repaint();
        for (PianoRoll pr : activePianoRolls) {
            pr.updatePlaybackHeadOnly(0);
        }
    }
    
    private void updateTempo() {
        try {
            float bpm = Float.parseFloat(bpmField.getText());
            playbackManager.setTempo(bpm);
        } catch (NumberFormatException ignored) {}
    }

    private void deleteSelectedRegion() {
        if (!selectedRegions.isEmpty()) {
            for (MidiRegion region : selectedRegions) {
                for (Track t : tracks) {
                    if (t.getRegions().contains(region)) {
                        t.removeRegion(region);
                        long start = region.getStartTick();
                        long end = region.getEndTick();
                        t.getNotes().removeIf(n -> n.getStartTimeTicks() >= start && n.getStartTimeTicks() < end);
                        break;
                    }
                }
            }
            selectedRegions.clear();
            selectedRegion = null;
            System.out.println("Arrangement: Deleted selected MIDI regions and their notes.");
            timelinePanel.repaint();
        }
    }

    private void selectPreviousTrack() {
        if (tracks.isEmpty()) return;
        int idx = tracks.indexOf(selectedTrack);
        if (idx > 0) {
            selectedTrack = tracks.get(idx - 1);
            selectedTracks.clear();
            selectedTracks.add(selectedTrack);
            rebuildTrackHeaders();
            timelinePanel.repaint();
        }
    }

    private void selectNextTrack() {
        if (tracks.isEmpty()) return;
        int idx = tracks.indexOf(selectedTrack);
        if (idx >= 0 && idx < tracks.size() - 1) {
            selectedTrack = tracks.get(idx + 1);
            selectedTracks.clear();
            selectedTracks.add(selectedTrack);
            rebuildTrackHeaders();
            timelinePanel.repaint();
        }
    }

    private void selectAllTracks() {
        selectedTracks.clear();
        selectedTracks.addAll(tracks);
        rebuildTrackHeaders();
        timelinePanel.repaint();
    }
    
    // --- タイムライン描画 & 操作用パネル ---
    private class TimelinePanel extends JPanel {
        
        private Point dragStartPoint = null;
        private Point dragCurrentPoint = null;
        private boolean isDrawingRegion = false;
        
        private boolean isDraggingRegionForMove = false;
        private long dragOffsetTicks = 0;
        private long dragStartTickOffset = 0;
        
        private final MouseAdapter mouseAdapter = new MouseAdapter() {
            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int trackIdx = (e.getY() - rulerHeight) / trackHeight;
                    if (trackIdx >= 0 && trackIdx < tracks.size()) {
                        Track track = tracks.get(trackIdx);
                        
                        long clickTick = (long) (e.getX() / zoomX);
                        MidiRegion clickedRegion = findRegionAt(track, clickTick);
                        
                        if (clickedRegion != null) {
                            if (!selectedRegions.contains(clickedRegion)) {
                                selectedRegions.clear();
                                selectedRegions.add(clickedRegion);
                                selectedRegion = clickedRegion;
                            }
                        } else {
                            selectedRegions.clear();
                            selectedRegion = null;
                        }

                        if (!selectedTracks.contains(track)) {
                            selectedTracks.clear();
                            selectedTracks.add(track);
                            selectedTrack = track;
                            rebuildTrackHeaders();
                        }
                        JPopupMenu popup = createTrackPopupMenu(track);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                        repaint();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                ArrangementFrame.this.requestFocusInWindow(); // Clear active text field focus

                if (e.getY() < rulerHeight) {
                    // ルーラーをクリックした場合は再生位置の移動
                    long tick = (long) (e.getX() / zoomX);
                    if (playbackManager.getSequencer() != null) {
                        playbackManager.setTickPosition(tick);
                    }
                    timelinePanel.repaint();
                    return;
                }
                
                int trackIdx = (e.getY() - rulerHeight) / trackHeight;
                if (trackIdx < 0 || trackIdx >= tracks.size()) return;
                
                Track track = tracks.get(trackIdx);
                selectedTrack = track;
                boolean isCtrl = e.isControlDown();
                boolean isLeft = SwingUtilities.isLeftMouseButton(e);
                System.out.println("Arrangement mousePressed: track=" + track.getName() + ", ctrl=" + isCtrl + ", left=" + isLeft);

                if (isCtrl && isLeft) {
                    if (!e.isShiftDown()) {
                        if (selectedTracks.contains(track)) {
                            selectedTracks.remove(track);
                            System.out.println("  Removed from selectedTracks. Size now: " + selectedTracks.size());
                            if (selectedTracks.isEmpty() && !tracks.isEmpty()) {
                                selectedTracks.add(track);
                            }
                        } else {
                            selectedTracks.add(track);
                            System.out.println("  Added to selectedTracks. Size now: " + selectedTracks.size());
                        }
                    }
                } else if (!isCtrl && isLeft) {
                    selectedTracks.clear();
                    selectedTracks.add(track);
                    System.out.println("  Reset selectedTracks to single track. Size: " + selectedTracks.size());
                }
                rebuildTrackHeaders();
                
                // クリックされた位置 of region selection
                long clickTick = (long) (e.getX() / zoomX);
                MidiRegion clickedRegion = findRegionAt(track, clickTick);
                
                if (clickedRegion != null) {
                    if (isCtrl && isLeft && !e.isShiftDown()) {
                        if (selectedRegions.contains(clickedRegion)) {
                            selectedRegions.remove(clickedRegion);
                        } else {
                            selectedRegions.add(clickedRegion);
                        }
                        selectedRegion = clickedRegion;
                    } else if (!isCtrl && isLeft) {
                        selectedRegions.clear();
                        selectedRegions.add(clickedRegion);
                        selectedRegion = clickedRegion;
                    }
                    System.out.println("Arrangement: Selected regions size: " + selectedRegions.size());
                } else {
                    if (!isCtrl && isLeft) {
                        selectedRegions.clear();
                        selectedRegion = null;
                    }
                }
                
                // ダブルクリックでピアノロールを開く
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (selectedRegion != null) {
                        openPianoRoll(track, selectedRegion);
                    }
                }
                // Ctrl + Shift + 左クリック + ドラッグで MIDI リージョンを描画
                else if (e.isControlDown() && e.isShiftDown() && SwingUtilities.isLeftMouseButton(e)) {
                    dragStartPoint = e.getPoint();
                    dragCurrentPoint = e.getPoint();
                    isDrawingRegion = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
                } else if (!e.isControlDown() && !e.isShiftDown() && SwingUtilities.isLeftMouseButton(e) && selectedRegion != null) {
                    // ドラッグ移動の開始
                    isDraggingRegionForMove = true;
                    dragStartTickOffset = clickTick - selectedRegion.getStartTick();
                    dragOffsetTicks = 0;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                
                checkPopup(e);
                repaint();
            }
            
            @Override
            public void mouseDragged(MouseEvent e) {
                if (isDrawingRegion) {
                    dragCurrentPoint = e.getPoint();
                    repaint();
                } else if (isDraggingRegionForMove && selectedRegion != null) {
                    long currentTick = (long) (e.getX() / zoomX);
                    long rawStartTick = currentTick - dragStartTickOffset;
                    long snapTicks = getSnapTicks();
                    long newStartTick = Math.max(0, (rawStartTick / snapTicks) * snapTicks);
                    
                    dragOffsetTicks = newStartTick - selectedRegion.getStartTick();
                    repaint();
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                if (isDrawingRegion) {
                    isDrawingRegion = false;
                    setCursor(Cursor.getDefaultCursor());
                    
                    if (dragStartPoint != null && dragCurrentPoint != null) {
                        int trackIdx = (dragStartPoint.y - rulerHeight) / trackHeight;
                        if (trackIdx >= 0 && trackIdx < tracks.size()) {
                            Track track = tracks.get(trackIdx);
                            
                            // グリッドにスナップさせてリージョンを作成
                            long t1 = (long) (dragStartPoint.x / zoomX);
                            long t2 = (long) (dragCurrentPoint.x / zoomX);
                            
                            long startTick = Math.min(t1, t2);
                            long endTick = Math.max(t1, t2);
                            
                            // スナップ値でスナップ
                            long snapTicks = getSnapTicks();
                            startTick = (startTick / snapTicks) * snapTicks;
                            endTick = ((endTick + snapTicks - 1) / snapTicks) * snapTicks;
                            
                            if (startTick < endTick) {
                                // 既存のリージョンとの重複チェック (重複させない仕様)
                                if (!hasOverlap(track, startTick, endTick)) {
                                    MidiRegion region = new MidiRegion(startTick, endTick);
                                    track.addRegion(region);
                                    System.out.println("Arrangement: Added MIDI Region to " + track.getName() + " -> " + region);
                                }
                            }
                        }
                    }
                    dragStartPoint = null;
                    dragCurrentPoint = null;
                    repaint();
                } else if (isDraggingRegionForMove && selectedRegion != null) {
                    isDraggingRegionForMove = false;
                    setCursor(Cursor.getDefaultCursor());
                    
                    if (dragOffsetTicks != 0) {
                        long newStart = selectedRegion.getStartTick() + dragOffsetTicks;
                        long newEnd = selectedRegion.getEndTick() + dragOffsetTicks;
                        
                        if (!hasOverlapExcluding(selectedTrack, newStart, newEnd, selectedRegion)) {
                            long oldStart = selectedRegion.getStartTick();
                            long oldEnd = selectedRegion.getEndTick();
                            
                            // ノートの位置も平行移動
                            for (Note note : selectedTrack.getNotes()) {
                                if (note.getStartTimeTicks() >= oldStart && note.getStartTimeTicks() < oldEnd) {
                                    note.setStartTimeTicks(note.getStartTimeTicks() + dragOffsetTicks);
                                }
                            }
                            
                            selectedRegion.setStartTick(newStart);
                            selectedRegion.setEndTick(newEnd);
                            System.out.println("Arrangement: Moved region " + selectedRegion.getId() + " by " + dragOffsetTicks + " ticks.");
                        }
                    }
                    dragOffsetTicks = 0;
                    repaint();
                }
                checkPopup(e);
            }
        };
        
        public TimelinePanel() {
            recalculateSize();
            setBackground(new Color(30, 30, 30));
            
            addMouseWheelListener(e -> {
                if (e.isControlDown()) {
                    if (e.getWheelRotation() < 0) {
                        zoomIn(e.getPoint());
                    } else {
                        zoomOut(e.getPoint());
                    }
                    e.consume();
                }
            });
            
            addMouseListener(mouseAdapter);
            addMouseMotionListener(mouseAdapter);
        }
        
        public void recalculateSize() {
            int totalHeight = rulerHeight + (tracks.size() * trackHeight) + 100;
            // 縦スクロールと横スクロールに対応する推奨サイズを設定 (デフォルトで120小節分の幅)
            setPreferredSize(new Dimension((int) (ticksPerMeasure * 120 * zoomX), totalHeight));
            revalidate();
        }
        
        private boolean hasOverlap(Track track, long start, long end) {
            for (MidiRegion r : track.getRegions()) {
                if (start < r.getEndTick() && end > r.getStartTick()) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean hasOverlapExcluding(Track track, long start, long end, MidiRegion excludeRegion) {
            for (MidiRegion r : track.getRegions()) {
                if (r == excludeRegion) continue;
                if (start < r.getEndTick() && end > r.getStartTick()) {
                    return true;
                }
            }
            return false;
        }

        private long getSnapTicks() {
            int beatsPerMeasure = 4;
            int ticksPerMeasure = ppqn * beatsPerMeasure;
            
            String selected = (String) quantizeComboBox.getSelectedItem();
            if (selected == null) return ticksPerMeasure;
            
            switch (selected) {
                case "1 Measure": return ticksPerMeasure;
                case "1/2": return ticksPerMeasure / 2;
                case "1/4": return ticksPerMeasure / 4;
                case "1/8": return ticksPerMeasure / 8;
                case "1/16": return ticksPerMeasure / 16;
                default: return ticksPerMeasure;
            }
        }
        
        private MidiRegion findRegionAt(Track track, long tick) {
            for (MidiRegion region : track.getRegions()) {
                if (tick >= region.getStartTick() && tick < region.getEndTick()) {
                    return region;
                }
            }
            return null;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int width = getWidth();
            int height = getHeight();
            
            // 背景の小節線（グリッド）描画
            g2.setColor(new Color(45, 45, 45));
            for (int i = 0; i < 120; i++) {
                int x = (int) (i * ticksPerMeasure * zoomX);
                g2.drawLine(x, 0, x, height);
            }
            
            // トラックの横仕切り線
            g2.setColor(new Color(60, 60, 60));
            for (int i = 0; i <= tracks.size(); i++) {
                int y = rulerHeight + (i * trackHeight);
                g2.drawLine(0, y, width, y);
            }
            
            // 各トラック内の MIDI リージョン (ブロック) を描画
            for (int t = 0; t < tracks.size(); t++) {
                Track track = tracks.get(t);
                int trackY = rulerHeight + (t * trackHeight);
                
                // 選択されているトラックの背景を少し明るく
                if (selectedTracks.contains(track)) {
                    g2.setColor(new Color(100, 100, 255, 25));
                    g2.fillRect(0, trackY, width, trackHeight);
                }
                
                for (MidiRegion region : track.getRegions()) {
                    long startTick = region.getStartTick();
                    if (isDraggingRegionForMove && region == selectedRegion) {
                        startTick += dragOffsetTicks;
                    }
                    int rx = (int) (startTick * zoomX);
                    int rw = (int) (region.getLengthTicks() * zoomX);
                    int ry = trackY + 10;
                    int rh = trackHeight - 20;
                    
                    // ブロックの塗りつぶし
                    g2.setColor(track.getColor());
                    g2.fillRoundRect(rx, ry, rw, rh, 8, 8);
                    
                    // 枠線 (選択されている場合は黄色い太枠にする)
                    if (selectedRegions.contains(region)) {
                        g2.setColor(new Color(255, 215, 0)); // 明るいゴールド/イエロー
                        g2.setStroke(new BasicStroke(2.5f));
                    } else {
                        g2.setColor(track.getColor().brighter());
                        g2.setStroke(new BasicStroke(2));
                    }
                    g2.drawRoundRect(rx, ry, rw, rh, 8, 8);
                    g2.setStroke(new BasicStroke(1));
                    
                    // リージョンラベル
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Outfit", Font.BOLD, 12));
                    g2.drawString(track.getName() + " (" + track.getInstrument() + ")", rx + 8, ry + 20);
                    
                    // プレビューノート (薄い線) の描画
                    g2.setColor(new Color(200, 200, 250, 120));
                    List<Note> regionNotes = new ArrayList<>();
                    for (Note note : track.getNotes()) {
                        if (note.getStartTimeTicks() >= region.getStartTick() && note.getStartTimeTicks() < region.getEndTick()) {
                            regionNotes.add(note);
                        }
                    }
                    
                    if (!regionNotes.isEmpty()) {
                        int minPitch = regionNotes.stream().mapToInt(Note::getPitch).min().orElse(36);
                        int maxPitch = regionNotes.stream().mapToInt(Note::getPitch).max().orElse(84);
                        int pitchRange = Math.max(1, maxPitch - minPitch);
                        
                        for (Note note : regionNotes) {
                            long noteStart = note.getStartTimeTicks();
                            if (isDraggingRegionForMove && region == selectedRegion) {
                                noteStart += dragOffsetTicks;
                            }
                            int nx = (int) (noteStart * zoomX);
                            int nw = (int) (note.getDurationTicks() * zoomX);
                            double pitchRatio = (double) (note.getPitch() - minPitch) / pitchRange;
                            int ny = ry + rh - 15 - (int) (pitchRatio * (rh - 25));
                            
                            g2.fillRect(nx, ny, Math.max(2, nw), 3);
                        }
                    }
                }
            }
            
            // ドラッグ中の新規リージョンプレビュー
            if (isDrawingRegion && dragStartPoint != null && dragCurrentPoint != null) {
                int trackIdx = (dragStartPoint.y - rulerHeight) / trackHeight;
                if (trackIdx >= 0 && trackIdx < tracks.size()) {
                    int trackY = rulerHeight + (trackIdx * trackHeight);
                    
                    long t1 = (long) (dragStartPoint.x / zoomX);
                    long t2 = (long) (dragCurrentPoint.x / zoomX);
                    long startTick = Math.min(t1, t2);
                    long endTick = Math.max(t1, t2);
                    
                    startTick = (startTick / ticksPerMeasure) * ticksPerMeasure;
                    endTick = ((endTick + ticksPerMeasure - 1) / ticksPerMeasure) * ticksPerMeasure;
                    
                    int rx = (int) (startTick * zoomX);
                    int rw = (int) ((endTick - startTick) * zoomX);
                    int ry = trackY + 10;
                    int rh = trackHeight - 20;
                    
                    g2.setColor(new Color(100, 200, 255, 80));
                    g2.fillRoundRect(rx, ry, rw, rh, 8, 8);
                    g2.setColor(new Color(100, 200, 255, 180));
                    g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 0, new float[]{4}, 0));
                    g2.drawRoundRect(rx, ry, rw, rh, 8, 8);
                    g2.setStroke(new BasicStroke(1));
                }
            }
            
            // ルーラーエリア描画 (最前面)
            g2.setColor(new Color(40, 43, 45));
            g2.fillRect(0, 0, width, rulerHeight);
            g2.setColor(Color.GRAY);
            g2.drawLine(0, rulerHeight, width, rulerHeight);
            
            // ルーラーの目盛りと数字
            g2.setColor(Color.LIGHT_GRAY);
            g2.setFont(new Font("Inter", Font.PLAIN, 10));
            for (int i = 0; i < 120; i++) {
                int x = (int) (i * ticksPerMeasure * zoomX);
                g2.drawLine(x, rulerHeight - 15, x, rulerHeight);
                g2.drawString(String.valueOf(i + 1), x + 4, rulerHeight - 4);
                
                // 拍（小節内拍）の目盛り
                for (int b = 1; b < beatsPerMeasure; b++) {
                    int bx = (int) ((i * ticksPerMeasure + b * ppqn) * zoomX);
                    g2.drawLine(bx, rulerHeight - 8, bx, rulerHeight);
                }
            }
            
            // 再生ヘッドの描画
            long playHeadTick = 0;
            if (playbackManager.getSequencer() != null) {
                playHeadTick = playbackManager.getSequencer().getTickPosition();
            }
            int px = (int) (playHeadTick * zoomX);
            g2.setColor(Color.RED);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(px, 0, px, height);
            
            // ヘッドのつまみ（三角）
            int[] tx = {px - 6, px + 6, px};
            int[] ty = {0, 0, 8};
            g2.fillPolygon(tx, ty, 3);
            g2.setStroke(new BasicStroke(1));
        }
    }
    
    private void openPianoRoll(Track track, MidiRegion region) {
        System.out.println("Arrangement: Opening Piano Roll for " + track.getName() + " at region " + region);
        
        PianoRoll pianoRoll = new PianoRoll(track, region, tracks, () -> {
            timelinePanel.repaint();
        });
        
        activePianoRolls.add(pianoRoll);
        pianoRoll.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                activePianoRolls.remove(pianoRoll);
            }
        });
        
        pianoRoll.setVisible(true);
    }

    public void zoomIn(Point mousePoint) {
        zoomXAt(mousePoint, 1.15);
    }
    
    public void zoomOut(Point mousePoint) {
        zoomXAt(mousePoint, 1.0 / 1.15);
    }
    
    private void zoomXAt(Point mousePoint, double factor) {
        double oldZoomX = zoomX;
        zoomX *= factor;
        zoomX = Math.max(0.01, Math.min(2.0, zoomX));
        
        timelinePanel.recalculateSize();
        
        if (mousePoint != null) {
            JViewport viewport = (JViewport) timelinePanel.getParent();
            Point viewPos = viewport.getViewPosition();
            long mouseTick = (long) (mousePoint.x / oldZoomX);
            int newMouseX = (int) (mouseTick * zoomX);
            int viewportMouseX = mousePoint.x - viewPos.x;
            int newViewX = newMouseX - viewportMouseX;
            int maxViewX = timelinePanel.getPreferredSize().width - viewport.getWidth();
            newViewX = Math.max(0, Math.min(newViewX, maxViewX));
            viewPos.x = newViewX;
            viewport.setViewPosition(viewPos);
        }
        
        timelinePanel.repaint();
    }

    private void mergeSelectedRegions() {
        if (selectedRegions.size() < 2) {
            JOptionPane.showMessageDialog(this, "Please select at least 2 regions to merge (Ctrl + Left Click).", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to merge " + selectedRegions.size() + " selected regions? Gaps between them will be kept as blank.",
            "Merge Regions", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        List<MidiRegion> sortedRegions = new ArrayList<>(selectedRegions);
        sortedRegions.sort(java.util.Comparator.comparingLong(MidiRegion::getStartTick));
        
        Track targetTrack = null;
        for (Track t : tracks) {
            if (t.getRegions().contains(sortedRegions.get(0))) {
                targetTrack = t;
                break;
            }
        }
        if (targetTrack == null) return;

        long startTick = sortedRegions.get(0).getStartTick();
        long endTick = sortedRegions.get(sortedRegions.size() - 1).getEndTick();

        for (MidiRegion r : sortedRegions) {
            Track sourceTrack = null;
            for (Track t : tracks) {
                if (t.getRegions().contains(r)) {
                    sourceTrack = t;
                    break;
                }
            }
            if (sourceTrack != null) {
                if (sourceTrack != targetTrack) {
                    long start = r.getStartTick();
                    long end = r.getEndTick();
                    List<Note> notesToMove = new ArrayList<>();
                    for (Note n : sourceTrack.getNotes()) {
                        if (n.getStartTimeTicks() >= start && n.getStartTimeTicks() < end) {
                            notesToMove.add(n);
                        }
                    }
                    sourceTrack.getNotes().removeAll(notesToMove);
                    targetTrack.getNotes().addAll(notesToMove);
                }
                sourceTrack.removeRegion(r);
            }
        }

        MidiRegion mergedRegion = new MidiRegion(startTick, endTick);
        targetTrack.addRegion(mergedRegion);

        selectedRegions.clear();
        selectedRegions.add(mergedRegion);
        selectedRegion = mergedRegion;

        rebuildTrackHeaders();
        timelinePanel.recalculateSize();
        timelinePanel.repaint();

        playbackManager.loadNotes(targetTrack.getNotes(), ppqn);
        
        JOptionPane.showMessageDialog(this, "Regions merged successfully. Gap areas kept as blank.", "Merge Complete", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            ArrangementFrame frame = new ArrangementFrame();
            frame.setVisible(true);
        });
    }
}
