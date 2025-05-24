package org.realtimevideo;

import javax.swing.*;
import java.awt.*;

public class PerformanceTracker {
    private final JTextArea metricsTextArea;

    public PerformanceTracker() {
        metricsTextArea = new JTextArea(20, 30);
        metricsTextArea.setEditable(false);
    }

    public JPanel getMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(metricsTextArea), BorderLayout.CENTER);
        return panel;
    }

    public void updateMetrics(String metrics) {
        SwingUtilities.invokeLater(() -> metricsTextArea.setText(metrics));
    }
}