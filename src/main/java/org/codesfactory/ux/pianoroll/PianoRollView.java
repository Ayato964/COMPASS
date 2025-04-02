package org.codesfactory.ux.pianoroll;

import javax.swing.*;
import java.awt.*;

public class PianoRollView extends JPanel{
    public boolean isFullscreen = false;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, getWidth(), getHeight());
    }
}
