package org.realtimevideo;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class InteractiveImageProcessor extends JFrame {
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    private JProgressBar progressBar;
    private JCheckBox parallelProcessingCheckbox;
    private JLabel processingTimeLabel;
    private JSlider contrastSlider;
    private JLabel contrastValueLabel;
    
    // Performance measurement components
    private JPanel measurementPanel;
    private JButton startMeasurementButton;
    private JTextArea resultsArea;
    private JProgressBar measurementProgressBar;
    private volatile boolean isMeasuring = false;
    private List<BufferedImage> recordedFrames;
    private static final int RECORDING_DURATION = 5000; // 5 seconds in milliseconds

    private OpenCVFrameGrabber grabber;
    private OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    private volatile boolean running = false;

    public InteractiveImageProcessor() {
        setTitle("Interactive Camera Processor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Image Display Panel
        originalImageLabel = new JLabel("Original Image", JLabel.CENTER);
        originalImageLabel.setPreferredSize(new Dimension(400, 300));
        processedImageLabel = new JLabel("Processed Image", JLabel.CENTER);
        processedImageLabel.setPreferredSize(new Dimension(400, 300));
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, 10, 10));
        imagePanel.add(originalImageLabel);
        imagePanel.add(processedImageLabel);
        add(imagePanel, BorderLayout.NORTH);

        // Control Panel (Left)
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        JButton startCameraButton = new JButton("Start Camera");
        String[] operations = {"ASCII Art", "Gaussian Blur", "Sobel Edge Detection", "Edge Detection", "Grayscale", "Contrast"};
        operationSelector = new JComboBox<>(operations);
        parallelProcessingCheckbox = new JCheckBox("Parallel Processing", true);
        processingTimeLabel = new JLabel("Processing Time: 0 ms");

        // Contrast controls
        contrastSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 100);
        contrastSlider.setMajorTickSpacing(50);
        contrastSlider.setMinorTickSpacing(10);
        contrastSlider.setPaintTicks(true);
        contrastSlider.setPaintLabels(true);
        contrastValueLabel = new JLabel("Contrast: 100%");
        contrastSlider.setVisible(false);
        contrastValueLabel.setVisible(false);

        // Add components to left panel
        leftPanel.add(startCameraButton);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(new JLabel("Choose Operation:"));
        leftPanel.add(operationSelector);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(parallelProcessingCheckbox);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(processingTimeLabel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(contrastValueLabel);
        leftPanel.add(contrastSlider);
        add(leftPanel, BorderLayout.WEST);

        // Measurement Panel (Right)
        measurementPanel = new JPanel();
        measurementPanel.setLayout(new BoxLayout(measurementPanel, BoxLayout.Y_AXIS));
        measurementPanel.setBorder(BorderFactory.createTitledBorder("Performance Measurement"));
        
        startMeasurementButton = new JButton("Start Performance Test");
        measurementProgressBar = new JProgressBar(0, 100);
        measurementProgressBar.setStringPainted(true);
        resultsArea = new JTextArea(20, 30);
        resultsArea.setEditable(false);
        JScrollPane resultsScrollPane = new JScrollPane(resultsArea);
        
        measurementPanel.add(startMeasurementButton);
        measurementPanel.add(Box.createVerticalStrut(10));
        measurementPanel.add(measurementProgressBar);
        measurementPanel.add(Box.createVerticalStrut(10));
        measurementPanel.add(resultsScrollPane);
        add(measurementPanel, BorderLayout.EAST);

        // Button Actions
        startCameraButton.addActionListener(e -> {
            if (!running) {
                startCameraFeed();
                startCameraButton.setText("Stop Camera");
            } else {
                stopCameraFeed();
                startCameraButton.setText("Start Camera");
            }
        });

        startMeasurementButton.addActionListener(e -> {
            if (!isMeasuring) {
                startPerformanceMeasurement();
            }
        });

        setSize(1200, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void startCameraFeed() {
        running = true;
        new Thread(() -> {
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.setFormat("MJPG"); // Use MJPG format for better performance
                grabber.setImageWidth(640); // Set a reasonable resolution
                grabber.setImageHeight(480);
                grabber.setFrameRate(30); // Set to 30 FPS
                grabber.start();

                long lastFrameTime = System.nanoTime();
                long targetFrameTime = 1_000_000_000 / 30; // 30 FPS target

                while (running) {
                    long currentTime = System.nanoTime();
                    long elapsedTime = currentTime - lastFrameTime;

                    // Only process frame if enough time has passed
                    if (elapsedTime >= targetFrameTime) {
                        org.bytedeco.javacv.Frame frame = grabber.grab();
                        if (frame == null) continue;
                        
                        Mat mat = converter.convert(frame);
                        BufferedImage original = matToBufferedImage(mat);
                        
                        // Processed image with timing
                        long startTime = System.currentTimeMillis();
                        BufferedImage processed = applyFilter(original);
                        long endTime = System.currentTimeMillis();
                        long processingTime = endTime - startTime;
                        
                        final long finalProcessingTime = processingTime;
                        SwingUtilities.invokeLater(() -> {
                            originalImageLabel.setIcon(new ImageIcon(getScaledImage(original, 400, 300)));
                            processedImageLabel.setIcon(new ImageIcon(getScaledImage(processed, 400, 300)));
                            processingTimeLabel.setText(String.format("Processing Time: %d ms", finalProcessingTime));
                        });

                        lastFrameTime = currentTime;
                    } else {
                        // Small sleep to prevent CPU overuse
                        Thread.sleep(1);
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
        String operation = (String) operationSelector.getSelectedItem();
        
        if (parallelProcessingCheckbox.isSelected()) {
            return applyParallelFilter(input, operation);
        } else {
            return applySequentialFilter(input, operation);
        }
    }

    private BufferedImage applyParallelFilter(BufferedImage input, String operation) {
        int threadsCount = 8;
        int height = input.getHeight();
        int chunkSize = height / threadsCount;
        BufferedImage result = new BufferedImage(input.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        Thread[] threads = new Thread[threadsCount];
        long[] threadTimes = new long[threadsCount];

        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : (i + 1) * chunkSize;
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                long threadStartTime = System.currentTimeMillis();
                FilterThread filterThread = new FilterThread(input, result, startY, endY, operation, contrastSlider.getValue());
                filterThread.run();
                threadTimes[threadIndex] = System.currentTimeMillis() - threadStartTime;
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Log individual thread processing times
        System.out.println("Parallel Processing Times (ms):");
        for (int i = 0; i < threadsCount; i++) {
            System.out.printf("Thread %d: %d ms%n", i, threadTimes[i]);
        }

        return result;
    }

    private BufferedImage applySequentialFilter(BufferedImage input, String operation) {
        BufferedImage result = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        SequentialFilter filter = new SequentialFilter(input, result, operation, contrastSlider.getValue());
        filter.process();
        return result;
    }

    private void startPerformanceMeasurement() {
        if (running) {
            JOptionPane.showMessageDialog(this, "Please stop the camera feed before starting the measurement.");
            return;
        }

        isMeasuring = true;
        startMeasurementButton.setEnabled(false);
        recordedFrames = new ArrayList<>();
        resultsArea.setText("Starting performance measurement...\n");
        
        new Thread(() -> {
            try {
                // Start camera and record frames
                grabber = new OpenCVFrameGrabber(0);
                grabber.setFormat("MJPG");
                grabber.setImageWidth(640);
                grabber.setImageHeight(480);
                grabber.setFrameRate(30);
                grabber.start();

                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < RECORDING_DURATION) {
                    org.bytedeco.javacv.Frame frame = grabber.grab();
                    if (frame != null) {
                        Mat mat = converter.convert(frame);
                        recordedFrames.add(matToBufferedImage(mat));
                    }
                    int progress = (int) ((System.currentTimeMillis() - startTime) * 100 / RECORDING_DURATION);
                    SwingUtilities.invokeLater(() -> measurementProgressBar.setValue(progress));
                    Thread.sleep(10);
                }
                grabber.stop();

                // Process frames with each filter
                String[] operations = {"ASCII Art", "Gaussian Blur", "Sobel Edge Detection", "Edge Detection", "Grayscale", "Contrast"};
                Map<String, PerformanceMetrics> sequentialResults = new HashMap<>();
                Map<String, PerformanceMetrics> parallelResults = new HashMap<>();

                // Sequential processing
                resultsArea.append("\nSequential Processing Results:\n");
                for (String operation : operations) {
                    PerformanceMetrics metrics = processFramesSequentially(operation);
                    sequentialResults.put(operation, metrics);
                    resultsArea.append(String.format("\n%s:\n", operation));
                    resultsArea.append(metrics.toString());
                }

                // Parallel processing
                resultsArea.append("\nParallel Processing Results:\n");
                for (String operation : operations) {
                    PerformanceMetrics metrics = processFramesParallel(operation);
                    parallelResults.put(operation, metrics);
                    resultsArea.append(String.format("\n%s:\n", operation));
                    resultsArea.append(metrics.toString());
                }

                // Calculate and display performance gains
                resultsArea.append("\nPerformance Gains (Parallel vs Sequential):\n");
                for (String operation : operations) {
                    PerformanceMetrics seq = sequentialResults.get(operation);
                    PerformanceMetrics par = parallelResults.get(operation);
                    double speedup = (double) seq.totalTime / par.totalTime;
                    resultsArea.append(String.format("\n%s: %.2fx speedup\n", operation, speedup));
                }

            } catch (Exception e) {
                e.printStackTrace();
                resultsArea.append("Error during measurement: " + e.getMessage() + "\n");
            } finally {
                isMeasuring = false;
                SwingUtilities.invokeLater(() -> {
                    startMeasurementButton.setEnabled(true);
                    measurementProgressBar.setValue(0);
                });
            }
        }).start();
    }

    private PerformanceMetrics processFramesSequentially(String operation) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        long startTime = System.currentTimeMillis();
        AtomicInteger processedFrames = new AtomicInteger(0);

        for (BufferedImage frame : recordedFrames) {
            long frameStart = System.currentTimeMillis();
            BufferedImage processed = applySequentialFilter(frame, operation);
            long frameTime = System.currentTimeMillis() - frameStart;
            
            metrics.frameTimes.add(frameTime);
            metrics.totalTime += frameTime;
            processedFrames.incrementAndGet();
        }

        metrics.totalFrames = processedFrames.get();
        metrics.totalTime = System.currentTimeMillis() - startTime;
        return metrics;
    }

    private PerformanceMetrics processFramesParallel(String operation) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        long startTime = System.currentTimeMillis();
        AtomicInteger processedFrames = new AtomicInteger(0);

        for (BufferedImage frame : recordedFrames) {
            long frameStart = System.currentTimeMillis();
            BufferedImage processed = applyParallelFilter(frame, operation);
            long frameTime = System.currentTimeMillis() - frameStart;
            
            metrics.frameTimes.add(frameTime);
            metrics.totalTime += frameTime;
            processedFrames.incrementAndGet();
        }

        metrics.totalFrames = processedFrames.get();
        metrics.totalTime = System.currentTimeMillis() - startTime;
        return metrics;
    }

    private static class PerformanceMetrics {
        List<Long> frameTimes = new ArrayList<>();
        long totalTime;
        int totalFrames;

        @Override
        public String toString() {
            if (frameTimes.isEmpty()) return "No frames processed";
            
            long min = Collections.min(frameTimes);
            long max = Collections.max(frameTimes);
            double avg = frameTimes.stream().mapToLong(Long::longValue).average().orElse(0);
            
            return String.format(
                "Total Frames: %d\n" +
                "Total Time: %d ms\n" +
                "Average Frame Time: %.2f ms\n" +
                "Min Frame Time: %d ms\n" +
                "Max Frame Time: %d ms\n" +
                "FPS: %.2f",
                totalFrames,
                totalTime,
                avg,
                min,
                max,
                (totalFrames * 1000.0) / totalTime
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractiveImageProcessor::new);
    }
}

class GaussianKernel {
    private static final int KERNEL_SIZE = 5;
    private static final double[][] kernel = {
        {0.003, 0.013, 0.022, 0.013, 0.003},
        {0.013, 0.059, 0.097, 0.059, 0.013},
        {0.022, 0.097, 0.159, 0.097, 0.022},
        {0.013, 0.059, 0.097, 0.059, 0.013},
        {0.003, 0.013, 0.022, 0.013, 0.003}
    };

    public static int applyKernel(BufferedImage input, int x, int y) {
        double sumR = 0, sumG = 0, sumB = 0;
        
        for (int ky = -2; ky <= 2; ky++) {
            for (int kx = -2; kx <= 2; kx++) {
                int pixelX = Math.min(Math.max(x + kx, 0), input.getWidth() - 1);
                int pixelY = Math.min(Math.max(y + ky, 0), input.getHeight() - 1);
                
                Color color = new Color(input.getRGB(pixelX, pixelY));
                double weight = kernel[ky + 2][kx + 2];
                
                sumR += color.getRed() * weight;
                sumG += color.getGreen() * weight;
                sumB += color.getBlue() * weight;
            }
        }
        
        return new Color(
            (int) Math.min(255, Math.max(0, sumR)),
            (int) Math.min(255, Math.max(0, sumG)),
            (int) Math.min(255, Math.max(0, sumB))
        ).getRGB();
    }
}

class SobelKernel {
    private static final int[][] sobelX = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };

    private static final int[][] sobelY = {
        {-1, -2, -1},
        {0, 0, 0},
        {1, 2, 1}
    };

    public static int applyKernel(BufferedImage input, int x, int y) {
        int gx = 0;
        int gy = 0;

        // Apply Sobel kernels
        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {
                int pixelX = Math.min(Math.max(x + kx, 0), input.getWidth() - 1);
                int pixelY = Math.min(Math.max(y + ky, 0), input.getHeight() - 1);
                
                Color color = new Color(input.getRGB(pixelX, pixelY));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                
                gx += gray * sobelX[ky + 1][kx + 1];
                gy += gray * sobelY[ky + 1][kx + 1];
            }
        }

        // Calculate gradient magnitude
        int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
        magnitude = Math.min(255, Math.max(0, magnitude));

        return new Color(magnitude, magnitude, magnitude).getRGB();
    }
}

class FilterThread extends Thread {
    private BufferedImage input;
    private BufferedImage output;
    private int startY, endY;
    private String operation;
    private int contrastValue;

    public FilterThread(BufferedImage input, BufferedImage output, int startY, int endY, String operation, int contrastValue) {
        this.input = input;
        this.output = output;
        this.startY = startY;
        this.endY = endY;
        this.operation = operation;
        this.contrastValue = contrastValue;
    }

    @Override
    public void run() {
        if ("ASCII Art".equals(operation)) {
            // ASCII Art needs to process the entire image at once
            BufferedImage asciiImage = ASCIIArtConverter.convertToASCII(input);
            for (int y = startY; y < endY; y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    output.setRGB(x, y, asciiImage.getRGB(x, y));
                }
            }
        } else {
            for (int y = startY; y < endY; y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int newRGB = switch (operation) {
                        case "Gaussian Blur" -> GaussianKernel.applyKernel(input, x, y);
                        case "Sobel Edge Detection" -> SobelKernel.applyKernel(input, x, y);
                        case "Grayscale" -> {
                            Color color = new Color(input.getRGB(x, y));
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();
                            int gray = (r + g + b) / 3;
                            yield new Color(gray, gray, gray).getRGB();
                        }
                        case "Edge Detection" -> {
                            Color color = new Color(input.getRGB(x, y));
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();
                            int gray = (r + g + b) / 3;
                            int edge = gray > 128 ? 255 : 0;
                            yield new Color(edge, edge, edge).getRGB();
                        }
                        case "Contrast" -> {
                            Color color = new Color(input.getRGB(x, y));
                            double factor = (259.0 * (contrastValue + 255)) / (255.0 * (259 - contrastValue));
                            int r = (int) (factor * (color.getRed() - 128) + 128);
                            int g = (int) (factor * (color.getGreen() - 128) + 128);
                            int b = (int) (factor * (color.getBlue() - 128) + 128);
                            r = Math.min(255, Math.max(0, r));
                            g = Math.min(255, Math.max(0, g));
                            b = Math.min(255, Math.max(0, b));
                            yield new Color(r, g, b).getRGB();
                        }
                        default -> input.getRGB(x, y);
                    };
                    output.setRGB(x, y, newRGB);
                }
            }
        }
    }
}

class SequentialFilter {
    private BufferedImage input;
    private BufferedImage output;
    private String operation;
    private int contrastValue;

    public SequentialFilter(BufferedImage input, BufferedImage output, String operation, int contrastValue) {
        this.input = input;
        this.output = output;
        this.operation = operation;
        this.contrastValue = contrastValue;
    }

    public void process() {
        long startTime = System.currentTimeMillis();
        
        if ("ASCII Art".equals(operation)) {
            BufferedImage asciiImage = ASCIIArtConverter.convertToASCII(input);
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    output.setRGB(x, y, asciiImage.getRGB(x, y));
                }
            }
        } else {
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int newRGB = switch (operation) {
                        case "Gaussian Blur" -> GaussianKernel.applyKernel(input, x, y);
                        case "Sobel Edge Detection" -> SobelKernel.applyKernel(input, x, y);
                        case "Grayscale" -> {
                            Color color = new Color(input.getRGB(x, y));
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();
                            int gray = (r + g + b) / 3;
                            yield new Color(gray, gray, gray).getRGB();
                        }
                        case "Edge Detection" -> {
                            Color color = new Color(input.getRGB(x, y));
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();
                            int gray = (r + g + b) / 3;
                            int edge = gray > 128 ? 255 : 0;
                            yield new Color(edge, edge, edge).getRGB();
                        }
                        case "Contrast" -> {
                            Color color = new Color(input.getRGB(x, y));
                            double factor = (259.0 * (contrastValue + 255)) / (255.0 * (259 - contrastValue));
                            int r = (int) (factor * (color.getRed() - 128) + 128);
                            int g = (int) (factor * (color.getGreen() - 128) + 128);
                            int b = (int) (factor * (color.getBlue() - 128) + 128);
                            r = Math.min(255, Math.max(0, r));
                            g = Math.min(255, Math.max(0, g));
                            b = Math.min(255, Math.max(0, b));
                            yield new Color(r, g, b).getRGB();
                        }
                        default -> input.getRGB(x, y);
                    };
                    output.setRGB(x, y, newRGB);
                }
            }
        }
        
        long endTime = System.currentTimeMillis();
        System.out.printf("Sequential Processing Time: %d ms%n", endTime - startTime);
    }
}

class ASCIIArtConverter {
    private static final String ASCII_CHARS = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    private static final int CHAR_WIDTH = 4;   // Reduced from 8 to 4
    private static final int CHAR_HEIGHT = 8;  // Reduced from 16 to 8

    public static BufferedImage convertToASCII(BufferedImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        
        // Calculate dimensions for ASCII art
        int asciiWidth = width / CHAR_WIDTH;
        int asciiHeight = height / CHAR_HEIGHT;
        
        // Create a new image for the ASCII art
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Monospaced", Font.PLAIN, CHAR_HEIGHT));
        
        // Enable anti-aliasing for smoother text
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Process each character block
        for (int y = 0; y < asciiHeight; y++) {
            for (int x = 0; x < asciiWidth; x++) {
                // Calculate average brightness for this block
                int totalBrightness = 0;
                int pixelCount = 0;
                
                for (int py = y * CHAR_HEIGHT; py < (y + 1) * CHAR_HEIGHT && py < height; py++) {
                    for (int px = x * CHAR_WIDTH; px < (x + 1) * CHAR_WIDTH && px < width; px++) {
                        Color color = new Color(input.getRGB(px, py));
                        totalBrightness += (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                        pixelCount++;
                    }
                }
                
                int avgBrightness = totalBrightness / pixelCount;
                
                // Map brightness to ASCII character
                int charIndex = (int) ((avgBrightness / 255.0) * (ASCII_CHARS.length() - 1));
                char asciiChar = ASCII_CHARS.charAt(charIndex);
                
                // Draw the character
                g2d.drawString(String.valueOf(asciiChar), x * CHAR_WIDTH, y * CHAR_HEIGHT + CHAR_HEIGHT);
            }
        }
        
        g2d.dispose();
        return output;
    }
}

