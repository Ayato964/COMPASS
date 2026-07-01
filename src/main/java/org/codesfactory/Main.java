package org.codesfactory;

import com.formdev.flatlaf.FlatDarkLaf;
import org.codesfactory.ux.pianoroll.ArrangementFrame;

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
            ArrangementFrame arrangementFrame = new ArrangementFrame();
            arrangementFrame.setVisible(true);
        });
    }
}