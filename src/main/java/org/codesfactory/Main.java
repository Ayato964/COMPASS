package org.codesfactory;

import com.formdev.flatlaf.FlatDarkLaf;
import org.codesfactory.ux.pianoroll.PianoRoll;

import javax.swing.*;

public class Main {
    /**
     * Main method to run the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        // Set up the look and feel and create the UI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            PianoRoll pianoRoll = new PianoRoll(true, 1200, 800);
            pianoRoll.setVisible(true);
        });
    }
}