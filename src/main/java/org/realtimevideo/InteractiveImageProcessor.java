// src/main/java/org/realtimevideo/InteractiveImageProcessor.java

package org.realtimevideo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D; // Görüntü tipi dönüşümü için eklendi
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte; // CUDA için eklendi
import java.util.ArrayList;
import java.util.Arrays; // CUDA için eklendi (operationSelector)
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
// import javax.swing.JCheckBox; // JComboBox ile değiştirildi
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

import org.bytedeco.javacpp.BytePointer; // CUDA için eklendi
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import org.realtimevideo.core.ImageUtils;
import org.realtimevideo.core.PerformanceMetrics;
import org.realtimevideo.processing.SequentialFilter;
import org.realtimevideo.processing.ThreadFilter;
import org.realtimevideo.cuda.CudaFilters; // CUDA için eklendi

/**
 * InteractiveImageProcessor sınıfı, gerçek zamanlı kamera görüntüsünü alıp
 * kullanıcı tarafından seçilen çeşitli görüntü işleme filtrelerini uygulayan
 * bir Swing tabanlı GUI uygulamasıdır. Ayrıca, sıralı ve paralel işlem
 * performansını karşılaştırmak için bir ölçüm aracı içerir.
 * CUDA ile işleme yeteneği eklenmiştir.
 */
public class InteractiveImageProcessor extends JFrame {
    private JLabel originalImageLabel;
    private JLabel processedImageLabel;
    private JComboBox<String> operationSelector;
    // private JCheckBox parallelProcessingCheckbox; // JComboBox ile değiştirildi
    private JComboBox<String> processingModeSelector; // YENİ: İşlem modu seçimi için
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

    // CUDA için işlem modu sabitleri
    private static final String MODE_SEQUENTIAL = "Sequential CPU";
    private static final String MODE_PARALLEL_CPU = "Parallel CPU";
    private static final String MODE_PARALLEL_CUDA = "Parallel CUDA";

    // CUDA filtresi adı
    private static final String OP_CUDA_SET_FIRST_CHANNEL_ZERO = "Set First Channel Zero (CUDA)";
    // CUDA filtresi için sıralı CPU eşdeğeri adı (performans karşılaştırması için)
    private static final String OP_CPU_SET_FIRST_CHANNEL_ZERO = "Set First Channel Zero (CPU Sequential)";


    /**
     * InteractiveImageProcessor sınıfının yapıcı metodu.
     * GUI bileşenlerini başlatır, düzeni ayarlar ve olay dinleyicilerini tanımlar.
     */
    public InteractiveImageProcessor() {
        setTitle("Interactive Camera Processor (with CUDA)"); // Başlık güncellendi
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

        // ---- DEĞİŞİKLİK BAŞLANGICI: Operasyon listesine CPU eşdeğeri eklendi ----
        String[] cpuOperations = {"ASCII Art", "Gaussian Blur", "Sobel Edge Detection", "Edge Detection", "Grayscale", "Contrast", OP_CPU_SET_FIRST_CHANNEL_ZERO};
        List<String> allOperations = new ArrayList<>(Arrays.asList(cpuOperations));
        allOperations.add(OP_CUDA_SET_FIRST_CHANNEL_ZERO); // CUDA operasyonunu ekle
        operationSelector = new JComboBox<>(allOperations.toArray(new String[0]));
        // ---- DEĞİŞİKLİK SONU ----

        String[] processingModes = {MODE_SEQUENTIAL, MODE_PARALLEL_CPU, MODE_PARALLEL_CUDA};
        processingModeSelector = new JComboBox<>(processingModes);
        processingModeSelector.setSelectedItem(MODE_PARALLEL_CPU);

        processingTimeLabel = new JLabel("Processing Time: 0 ms");

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

            // ---- DEĞİŞİKLİK BAŞLANGICI: İşlem modu seçimi için mantık ----
            if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(selectedOperation)) {
                // CUDA operasyonu seçilirse, sadece CUDA modu mantıklı.
                // Kullanıcının hala diğer modları seçmesine izin verilebilir,
                // ancak applyFilter'da bu durum ele alınacaktır.
                // İdeal olarak, processingModeSelector'daki seçenekler dinamik olarak güncellenebilir.
                // Şimdilik, sadece kullanıcıyı bilgilendiriyoruz veya applyFilter'da yönetiyoruz.
                if (!processingModeSelector.getSelectedItem().equals(MODE_PARALLEL_CUDA)) {
                    // İsteğe bağlı: Otomatik olarak CUDA moduna geç
                    // processingModeSelector.setSelectedItem(MODE_PARALLEL_CUDA);
                }
            } else if (OP_CPU_SET_FIRST_CHANNEL_ZERO.equals(selectedOperation)) {
                // CPU operasyonu, CUDA modunda çalışmamalı
                if (processingModeSelector.getSelectedItem().equals(MODE_PARALLEL_CUDA)) {
                    // processingModeSelector.setSelectedItem(MODE_SEQUENTIAL); // Veya MODE_PARALLEL_CPU
                }
            }
            // ---- DEĞİŞİKLİK SONU ----
        });


        contrastSlider.addChangeListener(e -> {
            contrastValueLabel.setText("Contrast: " + contrastSlider.getValue());
        });

        leftPanel.add(startCameraButton);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(new JLabel("Choose Operation:"));
        leftPanel.add(operationSelector);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(new JLabel("Choose Processing Mode:"));
        leftPanel.add(processingModeSelector);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(processingTimeLabel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(contrastValueLabel);
        leftPanel.add(contrastSlider);
        add(leftPanel, BorderLayout.WEST);

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
                        Thread.sleep(1);
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
            }
        }).start();
    }

    private void stopCameraFeed() {
        running = false;
    }

    private BufferedImage applyFilter(BufferedImage input) {
        String operation = (String) operationSelector.getSelectedItem();
        String mode = (String) processingModeSelector.getSelectedItem();

        if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
            if (!MODE_PARALLEL_CUDA.equals(mode)) {
                System.err.println("CUDA operation '" + operation + "' selected, but mode is '" + mode + "'. Switching to CUDA mode or returning original.");
                // Otomatik CUDA moduna geçiş veya kullanıcıya uyarı. Şimdilik orijinali döndür.
                // return input; // Veya applyCudaFilter çağır.
                return applyCudaFilter(input, operation); // Deneyelim, CUDA op ise CUDA ile çalışsın
            }
            return applyCudaFilter(input, operation);
        } else if (MODE_PARALLEL_CUDA.equals(mode)) {
            // CPU operasyonu seçili ama mod CUDA. Bu desteklenmiyor.
            System.err.println("CUDA processing mode selected, but operation '" + operation + "' is a CPU operation. Returning original image.");
            return input;
        }

        // ---- DEĞİŞİKLİK BAŞLANGICI: OP_CPU_SET_FIRST_CHANNEL_ZERO için ----
        if (OP_CPU_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
            if (MODE_PARALLEL_CPU.equals(mode)) {
                // Bu basit filtre için paralel CPU uygulaması yazılabilir, şimdilik sıralı kullanalım.
                return applySetFirstChannelToZeroSequentialCPU(input);
            }
            // MODE_SEQUENTIAL veya bilinmeyen mod için
            return applySetFirstChannelToZeroSequentialCPU(input);
        }
        // ---- DEĞİŞİKLİK SONU ----


        switch (mode) {
            case MODE_SEQUENTIAL:
                return applySequentialFilter(input, operation);
            case MODE_PARALLEL_CPU:
                return applyParallelFilter(input, operation);
            // MODE_PARALLEL_CUDA durumu yukarıda ele alındı.
            default:
                System.err.println("Unknown processing mode: " + mode + ". Returning original image.");
                return input;
        }
    }

    // ---- YENİ METOD: Basit filtrenin sıralı CPU uygulaması ----
    private BufferedImage applySetFirstChannelToZeroSequentialCPU(BufferedImage input) {
        int imageType = (input.getType() == BufferedImage.TYPE_CUSTOM || input.getType() == 0) ? BufferedImage.TYPE_3BYTE_BGR : input.getType();
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), imageType);

        if (imageType == BufferedImage.TYPE_3BYTE_BGR || imageType == BufferedImage.TYPE_4BYTE_ABGR || imageType == BufferedImage.TYPE_INT_BGR || imageType == BufferedImage.TYPE_INT_RGB || imageType == BufferedImage.TYPE_INT_ARGB) {
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int rgb = input.getRGB(x, y);
                    // BGR formatında ilk bileşen Mavi'dir. RGB'de Kırmızı.
                    // BufferedImage.getRGB() ARGB döndürür.
                    // (alpha << 24) | (red << 16) | (green << 8) | blue
                    // Mavi'yi sıfırlamak için: (rgb & 0xFFFFFF00)
                    // Kırmızı'yı sıfırlamak için: (rgb & 0xFF00FFFF)
                    // Yeşil'i sıfırlamak için: (rgb & 0xFFFF00FF)
                    // Bizim CUDA kernel'ımız byte array üzerinde çalışıyor ve ilk byte'ı (B veya R) sıfırlıyor.
                    // TYPE_3BYTE_BGR için bu mavi olur.
                    // getRGB/setRGB ile çalışırken, hangi kanalı hedeflediğimize dikkat etmeliyiz.
                    // CUDA kernel'ı BGR byte dizisinde ilk byte'ı (Blue) sıfırladığı için burada da Blue'yu sıfırlayalım.
                    int alpha = (rgb >> 24) & 0xFF;
                    int red   = (rgb >> 16) & 0xFF;
                    int green = (rgb >>  8) & 0xFF;
                    int blue  =  rgb        & 0xFF;

                    blue = 0; // İlk kanalı (mavi) sıfırla (CUDA kernel'ına benzer şekilde)

                    int newRgb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                    output.setRGB(x, y, newRgb);
                }
            }
        } else {
            // Desteklenmeyen tip, orijinali kopyala
            Graphics2D g = output.createGraphics();
            g.drawImage(input, 0, 0, null);
            g.dispose();
        }
        return output;
    }
    // ---- YENİ METOD SONU ----


    private BufferedImage applyParallelFilter(BufferedImage input, String operation) {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        if (threadsCount <= 0) threadsCount = 8;
        int height = input.getHeight();
        int chunkSize = Math.max(1, height / threadsCount);
        int imageType = (input.getType() == BufferedImage.TYPE_CUSTOM || input.getType() == 0) ? BufferedImage.TYPE_INT_RGB : input.getType();
        BufferedImage result = new BufferedImage(input.getWidth(), height, imageType);
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

    private BufferedImage applySequentialFilter(BufferedImage input, String operation) {
        int imageType = (input.getType() == BufferedImage.TYPE_CUSTOM || input.getType() == 0) ? BufferedImage.TYPE_INT_RGB : input.getType();
        BufferedImage result = new BufferedImage(input.getWidth(), input.getHeight(), imageType);
        SequentialFilter filter = new SequentialFilter(input, result, operation, contrastSlider.getValue());
        filter.process();
        return result;
    }

    private BufferedImage applyCudaFilter(BufferedImage input, String operation) {
        if (!OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
            System.err.println("Unsupported CUDA operation: " + operation);
            return input;
        }

        // CUDA kernel'ı TYPE_3BYTE_BGR veya benzeri byte-tabanlı formatlar için yazıldı.
        // Girdi görüntüsünün bu formatta olduğundan emin olalım veya dönüştürelim.
        BufferedImage imageToProcess = input;
        if (input.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            System.out.println("Converting input image to TYPE_3BYTE_BGR for CUDA processing. Original type: " + input.getType());
            imageToProcess = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = imageToProcess.createGraphics();
            g.drawImage(input, 0, 0, null);
            g.dispose();
        }

        byte[] pixels = ((DataBufferByte) imageToProcess.getRaster().getDataBuffer()).getData();
        BytePointer dataPointer = new BytePointer(pixels);

        int width = imageToProcess.getWidth();
        int height = imageToProcess.getHeight();
        // TYPE_3BYTE_BGR için kanal sayısı 3'tür.
        int channels = 3; // Varsayılan olarak TYPE_3BYTE_BGR için

        try {
            CudaFilters.applySetFirstChannelToZeroCuda(dataPointer, width, height, channels);
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Failed to call CUDA native method. Ensure native libraries are loaded and in path.");
            ule.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "CUDA Native Library Error: " + ule.getMessage() + "\nCheck console for details. Ensure CUDA drivers and libraries are correctly set up.",
                    "CUDA Error", JOptionPane.ERROR_MESSAGE);
            return input; // Hata durumunda orijinal (dönüştürülmemiş) input'u döndür
        } catch (Exception e) {
            System.err.println("Error during CUDA processing: " + e.getMessage());
            e.printStackTrace();
            return input; // Hata durumunda orijinal (dönüştürülmemiş) input'u döndür
        }

        // imageToProcess (potansiyel olarak dönüştürülmüş görüntü) şimdi CUDA tarafından değiştirildi.
        return imageToProcess;
    }


    private void startPerformanceMeasurement() {
        if (running) {
            JOptionPane.showMessageDialog(this, "Please stop the camera feed before starting the measurement.");
            return;
        }

        isMeasuring = true;
        startMeasurementButton.setEnabled(false);
        recordedFrames = new ArrayList<>();
        resultsArea.setText("Starting performance measurement...\nRecording frames for " + (RECORDING_DURATION / 1000) + " seconds...\n");

        new Thread(() -> {
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
                        if (mat != null && !mat.empty()) {
                            recordedFrames.add(ImageUtils.matToBufferedImage(mat));
                        }
                    }
                    int progress = (int) ((System.currentTimeMillis() - recordStartTime) * 100 / RECORDING_DURATION);
                    final int currentProgress = progress;
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
            measurementProgressBar.setValue(0);

            if (recordedFrames.isEmpty()) {
                SwingUtilities.invokeLater(() -> resultsArea.append("No frames were recorded. Measurement aborted.\n"));
            } else {
                List<String> opsForMeasurementList = new ArrayList<>();
                for (int i = 0; i < operationSelector.getItemCount(); i++) {
                    opsForMeasurementList.add(operationSelector.getItemAt(i));
                }
                String[] operationsToTest = opsForMeasurementList.toArray(new String[0]);

                Map<String, PerformanceMetrics> sequentialResults = new HashMap<>();
                Map<String, PerformanceMetrics> parallelCpuResults = new HashMap<>();
                Map<String, PerformanceMetrics> parallelCudaResults = new HashMap<>();

                // Sıralı İşleme
                SwingUtilities.invokeLater(() -> resultsArea.append("\n" + MODE_SEQUENTIAL + " Results:\n"));
                for (String operation : operationsToTest) {
                    if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) continue; // CUDA op, CPU sıralı ile test edilmez

                    PerformanceMetrics metrics = processFrames(operation, MODE_SEQUENTIAL);
                    sequentialResults.put(operation, metrics);
                    final String opName = operation;
                    final PerformanceMetrics finalMetrics = metrics;
                    SwingUtilities.invokeLater(() -> {
                        resultsArea.append(String.format("\n%s:\n", opName));
                        resultsArea.append(finalMetrics.toString() + "\n");
                    });
                }

                // Paralel CPU İşleme
                SwingUtilities.invokeLater(() -> resultsArea.append("\n" + MODE_PARALLEL_CPU + " Results:\n"));
                for (String operation : operationsToTest) {
                    if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) continue; // CUDA op, CPU paralel ile test edilmez
                    if (OP_CPU_SET_FIRST_CHANNEL_ZERO.equals(operation)) continue; // Bu basit op için paralel CPU yazılmadı, sıralı ile aynı olur

                    PerformanceMetrics metrics = processFrames(operation, MODE_PARALLEL_CPU);
                    parallelCpuResults.put(operation, metrics);
                    final String opName = operation;
                    final PerformanceMetrics finalMetrics = metrics;
                    SwingUtilities.invokeLater(() -> {
                        resultsArea.append(String.format("\n%s:\n", opName));
                        resultsArea.append(finalMetrics.toString() + "\n");
                    });
                }

                // Paralel CUDA İşleme
                SwingUtilities.invokeLater(() -> resultsArea.append("\n" + MODE_PARALLEL_CUDA + " Results:\n"));
                for (String operation : operationsToTest) {
                    if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
                        PerformanceMetrics metrics = processFrames(operation, MODE_PARALLEL_CUDA);
                        parallelCudaResults.put(operation, metrics);
                        final String opName = operation;
                        final PerformanceMetrics finalMetrics = metrics;
                        SwingUtilities.invokeLater(() -> {
                            resultsArea.append(String.format("\n%s:\n", opName));
                            resultsArea.append(finalMetrics.toString() + "\n");
                        });
                    }
                }

                // Performans Karşılaştırması
                SwingUtilities.invokeLater(() -> resultsArea.append("\nPerformance Gains (vs Sequential CPU):\n"));
                for (String operation : operationsToTest) {
                    PerformanceMetrics seq = sequentialResults.get(operation);
                    PerformanceMetrics parCpu = parallelCpuResults.get(operation);
                    PerformanceMetrics parCuda = parallelCudaResults.get(operation);
                    final String opName = operation;

                    if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(opName)) {
                        PerformanceMetrics seqEquivalent = sequentialResults.get(OP_CPU_SET_FIRST_CHANNEL_ZERO);
                        if (parCuda != null && seqEquivalent != null && parCuda.totalTime > 0 && seqEquivalent.totalTime > 0) {
                            double speedupCudaVsSeqCpu = (double) seqEquivalent.totalTime / parCuda.totalTime;
                            SwingUtilities.invokeLater(() -> resultsArea.append(String.format("%s (CUDA vs %s): %.2fx speedup\n", opName, OP_CPU_SET_FIRST_CHANNEL_ZERO, speedupCudaVsSeqCpu)));
                        } else {
                            SwingUtilities.invokeLater(() -> resultsArea.append(String.format("%s (CUDA): N/A for speedup (missing sequential equivalent data)\n", opName)));
                        }
                    } else if (!OP_CPU_SET_FIRST_CHANNEL_ZERO.equals(opName)) { // Diğer CPU operasyonları için
                        if (parCpu != null && seq != null && parCpu.totalTime > 0 && seq.totalTime > 0) {
                            double speedupCpu = (double) seq.totalTime / parCpu.totalTime;
                            SwingUtilities.invokeLater(() -> resultsArea.append(String.format("%s (Parallel CPU): %.2fx speedup\n", opName, speedupCpu)));
                        } else {
                            SwingUtilities.invokeLater(() -> resultsArea.append(String.format("%s (Parallel CPU): N/A\n", opName)));
                        }
                    }
                }
            }

            isMeasuring = false;
            SwingUtilities.invokeLater(() -> {
                startMeasurementButton.setEnabled(true);
                measurementProgressBar.setValue(100);
                resultsArea.append("\nPerformance measurement finished.\n");
                resultsArea.setCaretPosition(resultsArea.getDocument().getLength());
            });
        }).start();
    }

    // ---- DEĞİŞİKLİK: Performans ölçümü için tek bir processFrames metodu ----
    private PerformanceMetrics processFrames(String operation, String mode) {
        PerformanceMetrics metrics = new PerformanceMetrics();
        if (recordedFrames == null || recordedFrames.isEmpty()) return metrics;

        long totalProcessingTimeForOperation = 0;
        AtomicInteger processedFramesCount = new AtomicInteger(0);

        for (BufferedImage frame : recordedFrames) {
            // Her frame için taze bir kopya oluşturalım ki önceki filtrelemelerden etkilenmesin.
            // Görüntü tipini korumak önemli.
            int originalType = frame.getType();
            if (originalType == 0 || originalType == BufferedImage.TYPE_CUSTOM) {
                // Eğer tip bilinmiyorsa veya custom ise, genellikle TYPE_INT_ARGB veya TYPE_3BYTE_BGR kullanılır.
                // OpenCV Mat'ten dönüştürme TYPE_3BYTE_BGR veya TYPE_BYTE_GRAY üretiyor.
                originalType = BufferedImage.TYPE_3BYTE_BGR; // Varsayılan BGR
                if (frame.getColorModel().getNumColorComponents() == 1) {
                    originalType = BufferedImage.TYPE_BYTE_GRAY;
                }
            }

            BufferedImage frameCopy = new BufferedImage(frame.getWidth(), frame.getHeight(), originalType);
            Graphics2D g = frameCopy.createGraphics();
            g.drawImage(frame, 0, 0, null);
            g.dispose();


            long frameStart = System.currentTimeMillis();

            // ---- DEĞİŞİKLİK: Doğru filtre uygulama metodunu seç ----
            if (MODE_SEQUENTIAL.equals(mode)) {
                if (OP_CPU_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
                    applySetFirstChannelToZeroSequentialCPU(frameCopy);
                } else {
                    applySequentialFilter(frameCopy, operation);
                }
            } else if (MODE_PARALLEL_CPU.equals(mode)) {
                // OP_CPU_SET_FIRST_CHANNEL_ZERO için paralel CPU yazılmadı, bu yüzden bu modda test edilmemeli
                applyParallelFilter(frameCopy, operation);
            } else if (MODE_PARALLEL_CUDA.equals(mode)) {
                if (OP_CUDA_SET_FIRST_CHANNEL_ZERO.equals(operation)) {
                    applyCudaFilter(frameCopy, operation);
                } else {
                    // Bu CUDA operasyonu desteklenmiyor, atla
                    continue;
                }
            }
            // ---- DEĞİŞİKLİK SONU ----

            long frameTime = System.currentTimeMillis() - frameStart;

            metrics.frameTimes.add(frameTime);
            totalProcessingTimeForOperation += frameTime;
            processedFramesCount.incrementAndGet();
        }

        metrics.totalFrames = processedFramesCount.get();
        metrics.totalTime = totalProcessingTimeForOperation;
        return metrics;
    }
    // ---- DEĞİŞİKLİK SONU ----

} // InteractiveImageProcessor sınıfının sonu