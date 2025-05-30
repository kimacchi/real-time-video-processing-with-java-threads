package org.realtimevideo;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

public class PerformanceTracker {
    private static final Color BACKGROUND_COLOR = new Color(245, 245, 245);
    private static final Color ACCENT_COLOR = new Color(70, 130, 180);
    private static final Color TEXT_COLOR = new Color(51, 51, 51);
    private final JTextArea metricsTextArea;

    public PerformanceTracker() {
        metricsTextArea = new JTextArea(5, 30);
        metricsTextArea.setEditable(false);
        metricsTextArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        metricsTextArea.setForeground(TEXT_COLOR);
        metricsTextArea.setBackground(Color.WHITE);
        metricsTextArea.setMargin(new Insets(10, 10, 10, 10));
    }

    public JPanel getMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BACKGROUND_COLOR);
        
        JScrollPane scrollPane = new JScrollPane(metricsTextArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_COLOR, 1),
                "Performance Metrics",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                ACCENT_COLOR
            ),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    public void updateMetrics(String metrics) {
        SwingUtilities.invokeLater(() -> metricsTextArea.setText(metrics));
    }
}