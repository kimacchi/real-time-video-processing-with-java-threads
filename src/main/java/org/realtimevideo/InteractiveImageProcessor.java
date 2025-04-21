package org.realtimevideo;
import org.bytedeco.javacv.*;
import org.bytedeco.opencv.opencv_core.Mat;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class InteractiveImageProcessor extends JFrame {
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    private JProgressBar progressBar;

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

        // Control Panel
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

        // Progress Panel
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        rightPanel.add(progressBar, BorderLayout.NORTH);
        add(rightPanel, BorderLayout.EAST);

        // Button Action
        startCameraButton.addActionListener(e -> {
            if (!running) {
                startCameraFeed();
                startCameraButton.setText("Stop Camera");
            } else {
                stopCameraFeed();
                startCameraButton.setText("Start Camera");
            }
        });

        setSize(900, 700);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void startCameraFeed() {
        running = true;
        new Thread(() -> {
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.start();

                System.out.println("running: " + running);
                while (running) {
                    org.bytedeco.javacv.Frame frame = grabber.grab();
                    System.out.println("Grabbed frame: " + (frame != null));
                    if (frame == null) continue;
                    Mat mat = converter.convert(frame);
                    BufferedImage original = matToBufferedImage(mat);
                    SwingUtilities.invokeLater(() -> originalImageLabel.setIcon(new ImageIcon(getScaledImage(original, 400, 300))));

                    // Processed image
                    BufferedImage processed = applyFilter(original);
                    SwingUtilities.invokeLater(() -> processedImageLabel.setIcon(new ImageIcon(getScaledImage(processed, 400, 300))));

                    Thread.sleep(100); // approx. 10 fps
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

        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : (i + 1) * chunkSize;
            String operation = (String) operationSelector.getSelectedItem();
            threads[i] = new FilterThread(input, result, startY, endY, operation);
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(InteractiveImageProcessor::new);
    }
}

class FilterThread extends Thread {
    private BufferedImage input;
    private BufferedImage output;
    private int startY, endY;
    private String operation;

    public FilterThread(BufferedImage input, BufferedImage output, int startY, int endY, String operation) {
        this.input = input;
        this.output = output;
        this.startY = startY;
        this.endY = endY;
        this.operation = operation;
    }

    @Override
    public void run() {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                Color color = new Color(input.getRGB(x, y));
                int r = color.getRed();
                int g = color.getGreen();
                int b = color.getBlue();

                int newRGB = switch (operation) {
                    case "Grayscale" -> {
                        int gray = (r + g + b) / 3;
                        yield new Color(gray, gray, gray).getRGB();
                    }
                    case "Edge Detection" -> {
                        // Dummy basic edge detection placeholder
                        int gray = (r + g + b) / 3;
                        int edge = gray > 128 ? 255 : 0;
                        yield new Color(edge, edge, edge).getRGB();
                    }
                    default -> color.getRGB();
                };

                output.setRGB(x, y, newRGB);
            }
        }
    }
}

