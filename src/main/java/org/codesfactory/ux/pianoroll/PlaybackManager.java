package org.codesfactory.ux.pianoroll;

import javax.sound.midi.*;
import javax.swing.SwingUtilities; // ★★★ SwingUtilities をインポート ★★★
import java.util.List;

public class PlaybackManager {

    private Sequencer sequencer;
    private Sequence sequence;
    private final PianoRollView pianoRollView;
    private Thread playbackHeadUpdaterThread;
    private boolean isLoopingEnabled = false; // ★★★ ループ状態を管理するフラグを追加 ★★★

    /**
     * PlaybackManagerのコンストラクタ
     * @param view 再生ヘッドの更新対象となるPianoRollView
     */
    public PlaybackManager(PianoRollView view) {
        this.pianoRollView = view;
        try {
            // Get a sequencer that is not connected to a default device
            sequencer = MidiSystem.getSequencer(false);
            if (sequencer == null) {
                System.err.println("Cannot get a sequencer device.");
                return;
            }
            sequencer.open();

            // Explicitly connect the sequencer to a synthesizer
            Synthesizer synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            Receiver receiver = synthesizer.getReceiver();
            Transmitter transmitter = sequencer.getTransmitter();
            transmitter.setReceiver(receiver);

            // Add a listener for the end of the track
            sequencer.addMetaEventListener(meta -> {
                if (meta.getType() == 47) { // End of Track meta event
                    // ★★★ ループ状態を自身のフラグで確認 ★★★
                    if (!this.isLoopingEnabled) { // ループ中でなければ停止処理
                        stopAndReset(); // 再生停止とヘッドリセット
                    } else {
                        // ループ中の場合、Sequencerが自動でループ開始点に戻る
                        // 必要であれば再生ヘッド表示を強制的にループ開始点に合わせる？
                        // updatePlaybackHead(sequencer.getLoopStartPoint());
                        System.out.println("PlaybackManager: Looping back to " + sequencer.getLoopStartPoint());
                    }
                }
            });

        } catch (MidiUnavailableException e) {
            System.err.println("MIDI Unavailable: " + e.getMessage());
            sequencer = null;
        }
    }

    /**
     * 再生するノートデータをロードします。
     * @param notes 再生するノートのリスト (java.util.List<Note>)
     * @param ppqn MIDIシーケンスの解像度 (Pulse Per Quarter Note)
     */
    public void loadNotes(List<Note> notes, int ppqn) {
        if (sequencer == null) {
            System.err.println("Sequencer not available, cannot load notes.");
            return;
        }
        if (sequencer.isRunning()) {
            stopAndReset();
        }

        try {
            sequence = new Sequence(Sequence.PPQ, ppqn);
            Track track = sequence.createTrack();

            if (notes != null) {
                for (Note note : notes) {
                    try {
                        ShortMessage noteOn = new ShortMessage(ShortMessage.NOTE_ON, note.getChannel(), note.getPitch(), note.getVelocity());
                        track.add(new MidiEvent(noteOn, note.getStartTimeTicks()));
                        ShortMessage noteOff = new ShortMessage(ShortMessage.NOTE_OFF, note.getChannel(), note.getPitch(), 0);
                        track.add(new MidiEvent(noteOff, note.getStartTimeTicks() + note.getDurationTicks()));
                    } catch (InvalidMidiDataException e) {
                        System.err.println("Error creating MIDI message for note: " + note + " - " + e.getMessage());
                    }
                }
            } else {
                System.out.println("loadNotes: Input notes list is null.");
            }

            sequencer.setSequence(sequence);
            System.out.println("PlaybackManager: Loaded " + (notes != null ? notes.size() : 0) + " notes into sequence.");
            sequencer.setTickPosition(0);
            updatePlaybackHead(0);
            this.isLoopingEnabled = false; // 新規ロード時はループは無効

        } catch (InvalidMidiDataException e) {
            System.err.println("Error setting MIDI sequence: " + e.getMessage());
        }
    }

    /**
     * 再生を開始します。
     */
    // PlaybackManager.java

    public void play() {
        if (sequencer != null && sequence != null && !sequencer.isRunning()) {
            System.out.println("PlaybackManager: Starting playback.");
            long startTick = 0;
            if (pianoRollView != null) {
                // ★★★ private な playbackTick の代わりに public なゲッターを使う ★★★
                startTick = pianoRollView.getPlaybackTick();
                // ★★★ ここまで修正 ★★★
            }
            sequencer.setTickPosition(startTick);
            updatePlaybackHead(startTick); // 開始位置を即時反映

            sequencer.start();
            startPlaybackHeadUpdater();
            // ボタン状態の更新を View 経由で親フレームに依頼
            if (pianoRollView != null) {
                pianoRollView.updateParentPlayButtonState(true);
            }
        } else if (sequencer == null) {
            System.err.println("PlaybackManager: Cannot play, sequencer not available.");
        } else if (sequence == null) {
            System.err.println("PlaybackManager: Cannot play, no sequence loaded.");
        } else {
            System.out.println("PlaybackManager: Already playing.");
        }
    }

    /**
     * 再生を停止し、再生位置を先頭に戻します。
     */
    public void stop() {
        if (sequencer != null) { // isRunning() でなくても停止処理は試みる
            System.out.println("PlaybackManager: Stopping playback requested.");
            stopAndReset();
        } else {
            System.out.println("PlaybackManager: Sequencer not available.");
        }
    }

    public void pause() {
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop(); // This effectively pauses playback
            stopPlaybackHeadUpdaterThread();
            if (pianoRollView != null) {
                pianoRollView.updateParentPlayButtonState(false);
            }
            System.out.println("PlaybackManager: Paused at tick " + sequencer.getTickPosition());
        }
    }

    /**
     * 内部的な再生停止処理（ヘッド位置リセットとView更新を含む）
     */
    private void stopAndReset() {
        this.isLoopingEnabled = false; // 停止時はループも解除
        if (sequencer != null && sequencer.isRunning()) {
            sequencer.stop();
            System.out.println("PlaybackManager: Sequencer stopped.");
        }
        // 再生ヘッド更新スレッドが動いていれば停止を待つ
        if (playbackHeadUpdaterThread != null && playbackHeadUpdaterThread.isAlive()) {
            try {
                playbackHeadUpdaterThread.interrupt();
                playbackHeadUpdaterThread.join(100);
                System.out.println("PlaybackManager: Head updater thread stopped.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        playbackHeadUpdaterThread = null; // スレッド参照をクリア

        if(sequencer != null){
            sequencer.setTickPosition(0); // 再生位置を先頭にリセット
        }
        updatePlaybackHead(0); // Viewの再生ヘッドも0に更新

        // ★★★ View経由で親フレームのボタン状態更新メソッドを呼び出す ★★★
        if (pianoRollView != null) {
            pianoRollView.updateParentPlayButtonState(false); // false を渡して "Play" テキストに設定させる
        }
        // ★★★ ここまで修正 ★★★
    }


    /**
     * 現在再生中かどうかを返します。
     * @return 再生中であればtrue
     */
    public boolean isPlaying() {
        return sequencer != null && sequencer.isRunning();
    }

    public Sequence getSequence() {
        return sequence;
    }

    public float getTempo() {
        if (sequencer != null) {
            return sequencer.getTempoInBPM();
        }
        return 120.0f; // Default tempo
    }

    public void setTickPosition(long tick) {
        if (sequencer != null) {
            sequencer.setTickPosition(tick);
            updatePlaybackHead(tick);
        }
    }

    /**
     * ループ再生範囲を設定します。
     * @param startTick ループ開始Tick
     * @param endTick ループ終了Tick (このTickは含まれない)
     */
    // PlaybackManager.java
    public void setLoop(long startTick, long endTick) {
        if (sequencer != null && sequence != null) { // ★ sequence の null チェック追加
            long sequenceLength = sequence.getTickLength();
            if (sequenceLength <= 0) { // シーケンス長が0または負ならループ設定不可
                System.err.println("PlaybackManager: Cannot set loop, sequence length is not positive: " + sequenceLength);
                clearLoop();
                return;
            }

            // startTick と endTick をシーケンス長内に丸める
            long validStartTick = Math.max(0, Math.min(startTick, sequenceLength -1)); // 終了点は含まないので -1
            long validEndTick = Math.max(validStartTick +1 , Math.min(endTick, sequenceLength)); // 開始点より大きく、シーケンス長以内

            if (validStartTick < validEndTick) {
                sequencer.setLoopStartPoint(validStartTick);
                sequencer.setLoopEndPoint(validEndTick); // EndPoint は通常そのTickの手前まで
                sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                this.isLoopingEnabled = true;
                System.out.println("PlaybackManager: Loop set from " + validStartTick + " to " + validEndTick + " (Sequence length: " + sequenceLength + ")");
            } else {
                System.err.println("PlaybackManager: Invalid loop points after clamping. start=" + validStartTick + ", end=" + validEndTick);
                clearLoop();
            }
        } else {
            System.err.println("PlaybackManager: Sequencer or sequence not available for setting loop.");
        }
    }

    /**
     * ループ再生を解除します。
     */
    public void clearLoop() {
        if (sequencer != null) {
            sequencer.setLoopCount(0);
            this.isLoopingEnabled = false; // ★ ループフラグを下ろす
            System.out.println("PlaybackManager: Loop cleared.");
        }
    }

    /**
     * 再生ヘッドの位置をViewに通知して更新します。
     * @param tick 現在のTick位置
     */
    private void updatePlaybackHead(long tick) {
        if (pianoRollView != null) {
            // ★★★ SwingUtilities.invokeLater を使用 ★★★
            SwingUtilities.invokeLater(() -> pianoRollView.setPlaybackTick(tick));
        }
    }

    /**
     * 再生ヘッドの位置を更新するための別スレッドを開始します。
     */
    private void startPlaybackHeadUpdater() {
        if (playbackHeadUpdaterThread != null && playbackHeadUpdaterThread.isAlive()) {
            System.out.println("PlaybackManager: Head updater thread already running.");
            return;
        }

        playbackHeadUpdaterThread = new Thread(() -> {
            System.out.println("PlaybackManager: Head updater thread started.");
            try {
                while (sequencer != null && sequencer.isRunning()) {
                    long currentTick = sequencer.getTickPosition();
                    updatePlaybackHead(currentTick);
                    // より正確なスリープ時間 (ただし負荷は上がる可能性)
                    // long sleepTime = Math.max(5, 30 - (System.currentTimeMillis() % 30));
                    Thread.sleep(30); // 約33fps
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("PlaybackManager: Head updater thread interrupted.");
            } catch (Exception e) { // 予期せぬ例外もキャッチ
                System.err.println("Error in playback head updater thread: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("PlaybackManager: Head updater thread finished.");
                // スレッド終了時にも最終位置を更新
                if (sequencer != null) {
                    updatePlaybackHead(sequencer.getTickPosition());
                } else {
                    updatePlaybackHead(0);
                }
                playbackHeadUpdaterThread = null; // スレッド参照をクリア
            }
        });
        playbackHeadUpdaterThread.setDaemon(true);
        playbackHeadUpdaterThread.setName("Playback Head Updater");
        playbackHeadUpdaterThread.start();
    }

    private void stopPlaybackHeadUpdaterThread() {
        if (playbackHeadUpdaterThread != null && playbackHeadUpdaterThread.isAlive()) {
            try {
                playbackHeadUpdaterThread.interrupt();
                playbackHeadUpdaterThread.join(100);
                System.out.println("PlaybackManager: Head updater thread stopped.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        playbackHeadUpdaterThread = null;
    }

    /**
     * Sequencerリソースを解放します。アプリケーション終了時に呼び出されるべきです。
     */
    public void close() {
        System.out.println("PlaybackManager: Closing...");
        stopAndReset(); // 停止処理を呼ぶ
        if (sequencer != null && sequencer.isOpen()) {
            sequencer.close();
            System.out.println("PlaybackManager: Sequencer closed.");
        }
    }
}