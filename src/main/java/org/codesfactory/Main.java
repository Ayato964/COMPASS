package org.codesfactory;

import org.codesfactory.ux.pianoroll.PianoRoll;

public class Main {
    /**
     * Main method to run the application.
     *
     * @param args command line arguments
     */
    public static final PianoRoll pianoRoll = new PianoRoll(true, 1200, 800);
    public static void main(String[] args) {
        System.out.println("Hello world!");
        pianoRoll.setVisible(true);
    }
}