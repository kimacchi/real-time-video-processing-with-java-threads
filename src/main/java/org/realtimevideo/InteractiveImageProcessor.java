package org.realtimevideo;

import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.concurrent.atomic.AtomicLong;

public class InteractiveImageProcessor extends JFrame {
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    private JProgressBar progressBar;

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
        setLayout(new BorderLayout(10, 10));

        setupImageDisplayPanel();
        setupControlPanel();
        setupProgressPanel();

        setSize(900, 700);
        setLocationRelativeTo(null);
        setVisible(true);
        add(performanceTracker.getMetricsPanel(), BorderLayout.SOUTH);
    }

    private void setupImageDisplayPanel() {
        originalImageLabel = new JLabel("Original Image", JLabel.CENTER);
        originalImageLabel.setPreferredSize(new Dimension(400, 300));
        processedImageLabel = new JLabel("Processed Image", JLabel.CENTER);
        processedImageLabel.setPreferredSize(new Dimension(400, 300));
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, 10, 10));
        imagePanel.add(originalImageLabel);
        imagePanel.add(processedImageLabel);
        add(imagePanel, BorderLayout.NORTH);
    }

    private void setupControlPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        JButton startCameraButton = new JButton("Start Camera");
        String[] operations = {"Edge Detection", "Grayscale"};
        operationSelector = new JComboBox<>(operations);
        leftPanel.add(startCameraButton);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(new JLabel("Choose Operation:"));
        leftPanel.add(operationSelector);
        add(leftPanel, BorderLayout.WEST);

        startCameraButton.addActionListener(e -> toggleCamera(startCameraButton));
    }

    private void setupProgressPanel() {
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        rightPanel.add(progressBar, BorderLayout.NORTH);
        add(rightPanel, BorderLayout.EAST);
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

