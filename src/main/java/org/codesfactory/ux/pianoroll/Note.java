package org.codesfactory.ux.pianoroll;

public class Note {
    private int pitch;          // MIDIノート番号 (0-127)
    private long startTimeTicks; // 開始時間 (Tick単位)
    private long durationTicks;  // 音の長さ (Tick単位)
    private int velocity;       // ベロシティ (0-127)
    private int channel;        // MIDIチャンネル (0-15)

    public Note(int pitch, long startTimeTicks, long durationTicks, int velocity, int channel) {
        this.pitch = pitch;
        this.startTimeTicks = startTimeTicks;
        this.durationTicks = durationTicks;
        this.velocity = velocity;
        this.channel = channel;
    }

    public int getPitch() {
        return pitch;
    }

    public void setPitch(int pitch) {
        this.pitch = pitch;
    }

    public long getStartTimeTicks() {
        return startTimeTicks;
    }

    public void setStartTimeTicks(long startTimeTicks) {
        this.startTimeTicks = startTimeTicks;
    }

    public long getDurationTicks() {
        return durationTicks;
    }

    public void setDurationTicks(long durationTicks) {
        this.durationTicks = durationTicks;
    }

    public int getVelocity() {
        return velocity;
    }

    public void setVelocity(int velocity) {
        this.velocity = velocity;
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "Note{" +
                "pitch=" + pitch +
                ", startTimeTicks=" + startTimeTicks +
                ", durationTicks=" + durationTicks +
                ", velocity=" + velocity +
                ", channel=" + channel +
                '}';
    }
}