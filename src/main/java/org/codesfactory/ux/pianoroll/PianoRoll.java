package org.codesfactory.ux.pianoroll;

import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class PianoRoll extends JFrame{
    public final PianoRollView pianoRollView;

    /**
     * Constructor for PianoRoll.
     */
    public PianoRoll(){
        setTitle("Piano Roll");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        /*
          PianoRollView option
         */
        pianoRollView = new PianoRollView();
        add(pianoRollView);
        pianoRollView.repaint();

    }
    public  PianoRoll(int width, int height) {
        this();
        setSize(width, height);
        pianoRollView.setPreferredSize(new java.awt.Dimension(width, height));
    }

    public PianoRoll(boolean isFullScreen, int width, int height) {
        this(width, height);
        if(isFullScreen) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if(pianoRollView.isFullscreen){
                        setSize(width, height);
                    }
                }
            });
        }
    }

}
