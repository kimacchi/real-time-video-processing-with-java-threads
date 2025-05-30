package org.realtimevideo;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicLong;

public class InteractiveImageProcessor extends JFrame {
    private static final Color BACKGROUND_COLOR = new Color(245, 245, 245);
    private static final Color ACCENT_COLOR = new Color(70, 130, 180);
    private static final Color TEXT_COLOR = new Color(51, 51, 51);
    private static final int PADDING = 15;
    
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    private JProgressBar progressBar;
    private JButton startCameraButton;

    private OpenCVFrameGrabber grabber;
    private OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    private volatile boolean running = false;
    private PerformanceTracker performanceTracker = new PerformanceTracker();

    public InteractiveImageProcessor() {
        initializeUI();
    }

    private void initializeUI() {
        setTitle("Interactive Camera Processor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BACKGROUND_COLOR);
        
        // Main container with padding
        JPanel mainPanel = new JPanel(new BorderLayout(PADDING, PADDING));
        mainPanel.setBackground(BACKGROUND_COLOR);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        
        setupImageDisplayPanel(mainPanel);
        setupControlPanel(mainPanel);
        setupProgressPanel(mainPanel);
        
        add(mainPanel);
        add(performanceTracker.getMetricsPanel(), BorderLayout.SOUTH);
        
        setSize(1000, 800);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void setupImageDisplayPanel(JPanel mainPanel) {
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, PADDING, PADDING));
        imagePanel.setBackground(BACKGROUND_COLOR);
        
        originalImageLabel = createImageLabel("Original Image");
        processedImageLabel = createImageLabel("Processed Image");
        
        imagePanel.add(createImageContainer(originalImageLabel));
        imagePanel.add(createImageContainer(processedImageLabel));
        
        mainPanel.add(imagePanel, BorderLayout.CENTER);
    }

    private JLabel createImageLabel(String text) {
        JLabel label = new JLabel(text, JLabel.CENTER);
        label.setPreferredSize(new Dimension(450, 350));
        label.setForeground(TEXT_COLOR);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        return label;
    }

    private JPanel createImageContainer(JLabel label) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(BACKGROUND_COLOR);
        container.add(label, BorderLayout.CENTER);
        return container;
    }

    private void setupControlPanel(JPanel mainPanel) {
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBackground(BACKGROUND_COLOR);
        controlPanel.setBorder(BorderFactory.createEmptyBorder(0, PADDING, 0, PADDING));

        startCameraButton = createStyledButton("Start Camera");
        String[] operations = {"Edge Detection", "Grayscale"};
        operationSelector = new JComboBox<>(operations);
        styleComboBox(operationSelector);

        JLabel operationLabel = new JLabel("Choose Operation:");
        operationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        operationLabel.setForeground(TEXT_COLOR);

        controlPanel.add(startCameraButton);
        controlPanel.add(Box.createVerticalStrut(20));
        controlPanel.add(operationLabel);
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(operationSelector);

        mainPanel.add(controlPanel, BorderLayout.WEST);
        startCameraButton.addActionListener(e -> toggleCamera(startCameraButton));
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        button.setForeground(Color.WHITE);
        button.setBackground(ACCENT_COLOR);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(150, 40));
        button.setPreferredSize(new Dimension(150, 40));
        return button;
    }

    private void styleComboBox(JComboBox<String> comboBox) {
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        comboBox.setBackground(Color.WHITE);
        comboBox.setForeground(TEXT_COLOR);
        comboBox.setMaximumSize(new Dimension(150, 30));
    }

    private void setupProgressPanel(JPanel mainPanel) {
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBackground(BACKGROUND_COLOR);
        
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(ACCENT_COLOR);
        progressBar.setBackground(Color.WHITE);
        progressBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_COLOR, 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        progressPanel.add(progressBar, BorderLayout.NORTH);
        mainPanel.add(progressPanel, BorderLayout.EAST);
    }

    private void toggleCamera(JButton button) {
        if (!running) {
            startCameraFeed();
            button.setText("Stop Camera");
        } else {
            stopCameraFeed();
            button.setText("Start Camera");
        }
    }

    private void startCameraFeed() {
        running = true;
        new Thread(() -> {
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.start();

                BufferedImage buffer = null;
                long bufferStartTime = System.currentTimeMillis();

                while (running) {
                    org.bytedeco.javacv.Frame frame = grabber.grab();
                    if (frame == null) continue;
                    Mat mat = converter.convert(frame);
                    BufferedImage original = matToBufferedImage(mat);
                    SwingUtilities.invokeLater(() -> originalImageLabel.setIcon(new ImageIcon(getScaledImage(original, 400, 300))));

                    BufferedImage processed = applyFilter(original);
                    SwingUtilities.invokeLater(() -> processedImageLabel.setIcon(new ImageIcon(getScaledImage(processed, 400, 300))));

                    // Buffer for 10 seconds
                    if (buffer == null) {
                        buffer = original;
                    }
                    if (System.currentTimeMillis() - bufferStartTime >= 10000) {
                        compareAllOperations(buffer);
                        bufferStartTime = System.currentTimeMillis();
                    }
                }

                grabber.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stopCameraFeed() {
        running = false;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] b = new byte[bufferSize];
        mat.data().get(b);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    private BufferedImage getScaledImage(BufferedImage img, int maxW, int maxH) {
        int originalW = img.getWidth();
        int originalH = img.getHeight();
        double aspectRatio = (double) originalW / originalH;
        int newW = maxW, newH = maxH;

        if (aspectRatio >= 1) {
            newH = (int) (maxW / aspectRatio);
        } else {
            newW = (int) (maxH * aspectRatio);
        }

        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return scaled;
    }

    private BufferedImage applyFilter(BufferedImage input) {
        int threadsCount = 8;
        int height = input.getHeight();
        int chunkSize = height / threadsCount;
        BufferedImage result = new BufferedImage(input.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        Thread[] threads = new Thread[threadsCount];
        AtomicLong totalProcessingTime = new AtomicLong();

        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : (i + 1) * chunkSize;
            String operation = (String) operationSelector.getSelectedItem();
            threads[i] = new FilterThread(input, result, startY, endY, operation) {
                @Override
                public void run() {
                    long startTime = System.currentTimeMillis();
                    super.run();
                    long endTime = System.currentTimeMillis();
                    totalProcessingTime.addAndGet(endTime - startTime);
                }
            };
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        performanceTracker.updateMetrics(String.format(
            "Operation: %s\nThreads: %d\nTotal Processing Time: %d ms",
            operationSelector.getSelectedItem(), threadsCount, totalProcessingTime.get()
        ));

        return result;
    }

    public void compareAllOperations(BufferedImage input) {
        String[] operations = {"Edge Detection", "Grayscale"};
        StringBuilder comparisonResults = new StringBuilder("Performance Comparison:\n");

        for (String operation : operations) {
            operationSelector.setSelectedItem(operation);
            long startTime = System.currentTimeMillis();
            applyFilter(input);
            long endTime = System.currentTimeMillis();
            comparisonResults.append(String.format(
                "Operation: %s, Time Taken: %d ms\n",
                operation, endTime - startTime
            ));
        }

        performanceTracker.updateMetrics(comparisonResults.toString());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractiveImageProcessor::new);
    }
}

