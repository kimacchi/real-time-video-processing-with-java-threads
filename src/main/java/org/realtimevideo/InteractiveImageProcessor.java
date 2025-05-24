package org.realtimevideo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import org.realtimevideo.core.ImageUtils;
import org.realtimevideo.core.PerformanceMetrics;
import org.realtimevideo.processing.SequentialFilter; // Güncellendi (eski FilterThread)
import org.realtimevideo.processing.ThreadFilter;

/**
 * InteractiveImageProcessor sınıfı, gerçek zamanlı kamera görüntüsünü alıp
 * kullanıcı tarafından seçilen çeşitli görüntü işleme filtrelerini uygulayan
 * bir Swing tabanlı GUI uygulamasıdır. Ayrıca, sıralı ve paralel işlem
 * performansını karşılaştırmak için bir ölçüm aracı içerir.
 */
public class InteractiveImageProcessor extends JFrame {
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    private JCheckBox parallelProcessingCheckbox;
    private JLabel processingTimeLabel;
    private JSlider contrastSlider;
    private JLabel contrastValueLabel;
    
    private JPanel measurementPanel;
    private JButton startMeasurementButton;
    private JTextArea resultsArea;
    private JProgressBar measurementProgressBar;
    private volatile boolean isMeasuring = false;
    private List<BufferedImage> recordedFrames;
    private static final int RECORDING_DURATION = 5000; // 5 saniye

    private OpenCVFrameGrabber grabber;
    private OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    private volatile boolean running = false;

    /**
     * InteractiveImageProcessor sınıfının yapıcı metodu.
     * GUI bileşenlerini başlatır, düzeni ayarlar ve olay dinleyicilerini tanımlar.
     */
    public InteractiveImageProcessor() {
        setTitle("Interactive Camera Processor");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Görüntü Gösterim Paneli
        originalImageLabel = new JLabel("Original Image", JLabel.CENTER);
        originalImageLabel.setPreferredSize(new Dimension(400, 300));
        processedImageLabel = new JLabel("Processed Image", JLabel.CENTER);
        processedImageLabel.setPreferredSize(new Dimension(400, 300));
        JPanel imagePanel = new JPanel(new GridLayout(1, 2, 10, 10));
        imagePanel.add(originalImageLabel);
        imagePanel.add(processedImageLabel);
        add(imagePanel, BorderLayout.NORTH);

        // Kontrol Paneli (Sol)
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        JButton startCameraButton = new JButton("Start Camera");
        String[] operations = {"ASCII Art", "Gaussian Blur", "Sobel Edge Detection", "Edge Detection", "Grayscale", "Contrast"};
        operationSelector = new JComboBox<>(operations);
        parallelProcessingCheckbox = new JCheckBox("Parallel Processing", true);
        processingTimeLabel = new JLabel("Processing Time: 0 ms");

        // Kontrast kontrolleri
        contrastSlider = new JSlider(JSlider.HORIZONTAL, 0, 200, 100); 
        contrastSlider.setMajorTickSpacing(50);
        contrastSlider.setMinorTickSpacing(10);
        contrastSlider.setPaintTicks(true);
        contrastSlider.setPaintLabels(true);
        contrastValueLabel = new JLabel("Contrast: 100"); 
        
        contrastSlider.setVisible(false);
        contrastValueLabel.setVisible(false);

        operationSelector.addActionListener(e -> {
            String selectedOperation = (String) operationSelector.getSelectedItem();
            boolean isContrastSelected = "Contrast".equals(selectedOperation);
            contrastSlider.setVisible(isContrastSelected);
            contrastValueLabel.setVisible(isContrastSelected);
        });

        contrastSlider.addChangeListener(e -> {
            contrastValueLabel.setText("Contrast: " + contrastSlider.getValue());
        });

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

        // Ölçüm Paneli (Sağ)
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

        // Buton Eylemleri
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

    /**
     * Kamera akışını başlatır. Ayrı bir iş parçacığında çalışır.
     * Belirlenen FPS'e ulaşmak için zamanlama yapar.
     */
    private void startCameraFeed() {
        running = true;
        new Thread(() -> {
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.setFormat("MJPG"); 
                grabber.setImageWidth(640); 
                grabber.setImageHeight(480);
                grabber.setFrameRate(30); 
                grabber.start();

                long lastFrameTime = System.nanoTime();
                long targetFrameTime = 1_000_000_000 / 30; 

                while (running) {
                    long currentTime = System.nanoTime();
                    long elapsedTime = currentTime - lastFrameTime;

                    if (elapsedTime >= targetFrameTime) {
                        org.bytedeco.javacv.Frame frame = grabber.grab();
                        if (frame == null || frame.image == null) continue;
                        
                        Mat mat = converter.convert(frame);
                        if (mat == null || mat.empty()) continue;

                        BufferedImage original = ImageUtils.matToBufferedImage(mat);
                        
                        long startTime = System.currentTimeMillis();
                        BufferedImage processed = applyFilter(original);
                        long endTime = System.currentTimeMillis();
                        long processingTime = endTime - startTime;
                        
                        final long finalProcessingTime = processingTime;
                        SwingUtilities.invokeLater(() -> {
                            originalImageLabel.setIcon(new ImageIcon(ImageUtils.getScaledImage(original, 400, 300)));
                            processedImageLabel.setIcon(new ImageIcon(ImageUtils.getScaledImage(processed, 400, 300)));
                            processingTimeLabel.setText(String.format("Processing Time: %d ms", finalProcessingTime));
                        });

                        lastFrameTime = currentTime;
                    } else {
                        Thread.sleep(1); // CPU kullanımını azaltmak için kısa bir bekleme
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> 
                    JOptionPane.showMessageDialog(this, "Camera error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE)
                );
            } finally {
                if (grabber != null) {
                    try {
                        grabber.stop();
                        grabber.release();
                    } catch (OpenCVFrameGrabber.Exception ex) {
                        ex.printStackTrace();
                    }
                }
                running = false;
                 // GUI güncellemesi (buton metni) zaten ActionListener içinde ele alınıyor.
            }
        }).start();
    }

    /**
     * Kamera akışını durdurur.
     */
    private void stopCameraFeed() {
        running = false;
    }

    /**
     * Seçilen filtreyi uygular (paralel veya sıralı).
     * @param input İşlenecek BufferedImage.
     * @return İşlenmiş BufferedImage.
     */
    private BufferedImage applyFilter(BufferedImage input) {
        String operation = (String) operationSelector.getSelectedItem();
        
        if (parallelProcessingCheckbox.isSelected()) {
            return applyParallelFilter(input, operation);
        } else {
            return applySequentialFilter(input, operation);
        }
    }

    /**
     * Filtreyi paralel olarak uygular.
     * @param input İşlenecek BufferedImage.
     * @param operation Uygulanacak operasyonun adı.
     * @return Paralel işlenmiş BufferedImage.
     */
    private BufferedImage applyParallelFilter(BufferedImage input, String operation) {
        int threadsCount = Runtime.getRuntime().availableProcessors(); 
        if (threadsCount <= 0) threadsCount = 8; 
        int height = input.getHeight();
        int chunkSize = Math.max(1, height / threadsCount); 
        BufferedImage result = new BufferedImage(input.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        Thread[] threads = new Thread[threadsCount];

        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : Math.min(height, (i + 1) * chunkSize); 
            if (startY >= endY) continue; 

            threads[i] = new ThreadFilter(input, result, startY, endY, operation, contrastSlider.getValue());
            threads[i].start();
        }

        for (Thread t : threads) {
            if (t != null) { 
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
        return result;
    }

    /**
     * Filtreyi sıralı olarak uygular.
     * @param input İşlenecek BufferedImage.
     * @param operation Uygulanacak operasyonun adı.
     * @return Sıralı işlenmiş BufferedImage.
     */
    private BufferedImage applySequentialFilter(BufferedImage input, String operation) {
        BufferedImage result = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        SequentialFilter filter = new SequentialFilter(input, result, operation, contrastSlider.getValue());
        filter.process();
        return result;
    }

    /**
     * Performans ölçümünü başlatır. Ayrı bir iş parçacığında çalışır.
     * Kareleri kaydeder, ardından sıralı ve paralel işler, sonuçları gösterir.
     */
    private void startPerformanceMeasurement() {
        if (running) {
            JOptionPane.showMessageDialog(this, "Please stop the camera feed before starting the measurement.");
            return;
        }

        isMeasuring = true;
        startMeasurementButton.setEnabled(false);
        recordedFrames = new ArrayList<>();
        resultsArea.setText("Starting performance measurement...\nRecording frames for " + (RECORDING_DURATION/1000) + " seconds...\n");
        
        new Thread(() -> {
            // Frame Kayıt Aşaması
            try {
                grabber = new OpenCVFrameGrabber(0);
                grabber.setFormat("MJPG");
                grabber.setImageWidth(640);
                grabber.setImageHeight(480);
                grabber.setFrameRate(30);
                grabber.start();

                long recordStartTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - recordStartTime < RECORDING_DURATION) {
                    org.bytedeco.javacv.Frame frame = grabber.grab();
                    if (frame != null && frame.image != null) {
                        Mat mat = converter.convert(frame);
                        if (mat != null && !mat.empty()){
                           recordedFrames.add(ImageUtils.matToBufferedImage(mat));
                        }
                    }
                    int progress = (int) ((System.currentTimeMillis() - recordStartTime) * 100 / RECORDING_DURATION);
                    final int currentProgress = progress; // Final for lambda
                    SwingUtilities.invokeLater(() -> measurementProgressBar.setValue(currentProgress));
                    Thread.sleep(10); 
                }
            } catch (Exception e) {
                e.printStackTrace();
                final String errorMessage = e.getMessage();
                SwingUtilities.invokeLater(() -> resultsArea.append("Error during frame recording: " + errorMessage + "\n"));
            } finally {
                 if (grabber != null) {
                    try {
                        grabber.stop();
                        grabber.release();
                    } catch (OpenCVFrameGrabber.Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            SwingUtilities.invokeLater(() -> resultsArea.append("Frame recording complete. Processing " + recordedFrames.size() + " frames...\n"));
            measurementProgressBar.setValue(0); // İkinci aşama için progress bar'ı sıfırla (isteğe bağlı)

            // İşleme Aşaması
            if (recordedFrames.isEmpty()) {
                SwingUtilities.invokeLater(() -> resultsArea.append("No frames were recorded. Measurement aborted.\n"));
            } else {
                String[] operations = {"ASCII Art", "Gaussian Blur", "Sobel Edge Detection", "Edge Detection", "Grayscale", "Contrast"};
                Map<String, PerformanceMetrics> sequentialResults = new HashMap<>();
                Map<String, PerformanceMetrics> parallelResults = new HashMap<>();

                // Sıralı İşleme
                SwingUtilities.invokeLater(() -> resultsArea.append("\nSequential Processing Results:\n"));
                for (int i = 0; i < operations.length; i++) {
                    String operation = operations[i];
                    PerformanceMetrics metrics = processFramesSequentially(operation);
                    sequentialResults.put(operation, metrics);
                    final String opName = operation; // Final for lambda
                    final PerformanceMetrics finalMetrics = metrics; // Final for lambda
                    SwingUtilities.invokeLater(() -> {
                        resultsArea.append(String.format("\n%s:\n", opName));
                        resultsArea.append(finalMetrics.toString());
                        // measurementProgressBar.setValue(...); // Her operasyon için ilerleme gösterilebilir
                    });
                }

                // Paralel İşleme
                SwingUtilities.invokeLater(() -> resultsArea.append("\nParallel Processing Results:\n"));
                for (int i = 0; i < operations.length; i++) {
                    String operation = operations[i];
                    PerformanceMetrics metrics = processFramesParallel(operation);
                    parallelResults.put(operation, metrics);
                    final String opName = operation; // Final for lambda
                    final PerformanceMetrics finalMetrics = metrics; // Final for lambda
                    SwingUtilities.invokeLater(() -> {
                        resultsArea.append(String.format("\n%s:\n", opName));
                        resultsArea.append(finalMetrics.toString());
                    });
                }

                // Performans Karşılaştırması
                SwingUtilities.invokeLater(() -> resultsArea.append("\nPerformance Gains (Parallel vs Sequential):\n"));
                for (String operation : operations) {
                    PerformanceMetrics seq = sequentialResults.get(operation);
                    PerformanceMetrics par = parallelResults.get(operation);
                    final String opName = operation; // Final for lambda
                    if (par != null && seq != null && par.totalTime > 0 && seq.totalTime > 0) {
                        double speedup = (double) seq.totalTime / par.totalTime;
                        SwingUtilities.invokeLater(() -> resultsArea.append(String.format("\n%s: %.2fx speedup\n", opName, speedup)));
                    } else {
                        SwingUtilities.invokeLater(() -> resultsArea.append(String.format("\n%s: N/A (insufficient data for speedup calculation)\n", opName)));
                    }
                }
            }

            isMeasuring = false;
            SwingUtilities.invokeLater(() -> {
                startMeasurementButton.setEnabled(true);
                measurementProgressBar.setValue(100); // Ölçüm bittiğinde %100 yap
                resultsArea.append("\nPerformance measurement finished.\n");
                resultsArea.setCaretPosition(resultsArea.getDocument().getLength()); // En sona scroll et
            });
        }).start();
    }

    /**
     * Kaydedilmiş kareleri sıralı olarak işler.
     * @param operation Uygulanacak operasyonun adı.
     * @return Performans metrikleri.
     */
    private PerformanceMetrics processFramesSequentially(String operation) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        if (recordedFrames == null || recordedFrames.isEmpty()) return metrics;

        long totalProcessingTimeForOperation = 0;
        AtomicInteger processedFramesCount = new AtomicInteger(0);

        for (BufferedImage frame : recordedFrames) {
            long frameStart = System.currentTimeMillis();
            // applySequentialFilter doğrudan bir BufferedImage döndürür, bu yüzden sonucu atamaya gerek yok.
            // Ancak, performans ölçümü için işlenmiş kareye ihtiyacımız yok, sadece süreye ihtiyacımız var.
            applySequentialFilter(frame, operation); 
            long frameTime = System.currentTimeMillis() - frameStart;
            
            metrics.frameTimes.add(frameTime);
            totalProcessingTimeForOperation += frameTime;
            processedFramesCount.incrementAndGet();
        }

        metrics.totalFrames = processedFramesCount.get();
        metrics.totalTime = totalProcessingTimeForOperation;
        return metrics;
    }

    /**
     * Kaydedilmiş kareleri paralel olarak işler.
     * @param operation Uygulanacak operasyonun adı.
     * @return Performans metrikleri.
     */
    private PerformanceMetrics processFramesParallel(String operation) {
        PerformanceMetrics metrics = new PerformanceMetrics();
         if (recordedFrames == null || recordedFrames.isEmpty()) return metrics;

        long totalProcessingTimeForOperation = 0;
        AtomicInteger processedFramesCount = new AtomicInteger(0);

        for (BufferedImage frame : recordedFrames) {
            long frameStart = System.currentTimeMillis();
            applyParallelFilter(frame, operation); 
            long frameTime = System.currentTimeMillis() - frameStart;
            
            metrics.frameTimes.add(frameTime);
            totalProcessingTimeForOperation += frameTime;
            processedFramesCount.incrementAndGet();
        }

        metrics.totalFrames = processedFramesCount.get();
        metrics.totalTime = totalProcessingTimeForOperation;
        return metrics;
    }
}