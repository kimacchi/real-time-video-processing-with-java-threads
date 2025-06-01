// src/main/java/starter/Launcher.java

package starter;

import static java.nio.charset.StandardCharsets.UTF_8;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.PrimerDark;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import starter.core.ImageUtils;
import starter.core.PerformanceMetrics;
import starter.filters.*; 
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte; 
import javafx.scene.control.Alert; 
import java.awt.Color;
import starter.processing.ImageProcessor;
import org.opencv.videoio.VideoCapture;

/**
 * Launcher sınıfı, görüntü işleme uygulamasının ana GUI bileşenini ve
 * kamera işlemlerini yönetir. JavaFX tabanlı bir kullanıcı arayüzü sunar
 * ve gerçek zamanlı görüntü işleme özelliklerini içerir.
 * 
 * Özellikler:
 * - Kamera görüntüsünü yakalama ve işleme
 * - Çeşitli görüntü filtrelerini uygulama
 * - Sıralı ve paralel işleme modları
 * - Performans metriklerini görselleştirme
 * - 5 saniyelik video kaydı ve analizi
 */
public class Launcher extends Application {

    /** Uygulama kaynakları için temel dizin */
    static final String ASSETS_DIR = "/assets/";

    /** Uygulama ikonu dosya yolu */
    static final String APP_ICON_PATH = Objects.requireNonNull(
            Launcher.class.getResource(ASSETS_DIR + "icons/app-icon.png")
    ).toExternalForm();

    /** Uygulama özellikleri dosya yolu */
    static final String APP_PROPERTIES_PATH = "/application.properties";

    private OpenCVFrameGrabber grabber;
    private OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();
    private volatile boolean running = false;
    private ImageView cameraView;
    private ImageView processedView;
    private Map<String, ToggleSwitch> filterSwitches = new HashMap<>();
    private int contrastValue = 100; 
    private Label originalTimeLabel;
    private Label processedTimeLabel;
    private ToggleSwitch parallelProcessingSwitch;
    private List<BufferedImage> recordedFrames = new ArrayList<>();
    private boolean isRecording = false;
    private Label metricsLabel;
    private VBox controlsVBox;
    private PerformanceMetrics sequentialMetrics = new PerformanceMetrics();
    private PerformanceMetrics parallelMetrics = new PerformanceMetrics();

    /**
     * Uygulamanın giriş noktası.
     * @param args Komut satırı argümanları
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * JavaFX uygulamasını başlatır. Ana pencereyi oluşturur ve
     * kullanıcı arayüzünü yapılandırır.
     * 
     * @param stage Ana uygulama penceresi
     */
    @Override
    public void start(Stage stage) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        loadApplicationProperties();

        var scene = new Scene(createWelcomePane(), 1280, 900);
        scene.getStylesheets().add(Objects.requireNonNull(Launcher.class.getResource(ASSETS_DIR + "index.css")).toExternalForm());

        stage.setScene(scene);
        stage.setTitle("Image Processing Edu");
        try {
            stage.getIcons().add(new Image(APP_ICON_PATH));
        } catch (NullPointerException e) {
            System.err.println("App icon not found at: " + APP_ICON_PATH);
        }
        stage.setOnCloseRequest(t -> {
            stopCameraFeed();
            Platform.exit();
            System.exit(0);
        });
        stage.setMinWidth(1280);
        stage.setMinHeight(900);

        Platform.runLater(() -> {
            stage.show();
            stage.requestFocus();
        });
    }

    /**
     * Ana kullanıcı arayüzünü oluşturur. Üç ana bölümden oluşur:
     * - Sol panel: Kamera görüntüleri
     * - Orta panel: Performans grafikleri
     * - Sağ panel: Kontroller ve metrikler
     * 
     * @return Oluşturulan ana panel
     */
    private Pane createWelcomePane() {
        var root = new HBox(20);
        root.getStyleClass().add("welcome");
        root.setPadding(new Insets(20));

        // Left side - Camera views
        var leftPanel = new VBox(20);
        leftPanel.setPrefWidth(400);
        leftPanel.setAlignment(Pos.CENTER);
        leftPanel.setSpacing(10);

        var originalCard = new Card();
        originalCard.setMinWidth(400);
        originalCard.setMinHeight(400);
        originalCard.setMaxWidth(400);
        originalCard.setMaxHeight(400);
        originalCard.getStyleClass().addAll("camera-card", "card");

        var originalTitle = new Label("Original Image");
        originalTitle.getStyleClass().add("h4");

        cameraView = new ImageView();
        cameraView.setFitWidth(380);
        cameraView.setFitHeight(280);
        cameraView.setPreserveRatio(true);

        originalTimeLabel = new Label("Processing time: 0ms");
        originalTimeLabel.getStyleClass().add("processing-time");

        var originalContent = new VBox(10);
        originalContent.setAlignment(Pos.CENTER);
        originalContent.setPadding(new Insets(10));
        originalContent.getChildren().addAll(originalTitle, cameraView, originalTimeLabel);

        originalCard.setBody(originalContent);

        var processedCard = new Card();
        processedCard.setMinWidth(400);
        processedCard.setMinHeight(400);
        processedCard.setMaxWidth(400);
        processedCard.setMaxHeight(400);
        processedCard.getStyleClass().addAll("camera-card", "card");

        var processedTitle = new Label("Processed Image");
        processedTitle.getStyleClass().add("h4");

        processedView = new ImageView();
        processedView.setFitWidth(380);
        processedView.setFitHeight(280);
        processedView.setPreserveRatio(true);

        processedTimeLabel = new Label("Processing time: 0ms");
        processedTimeLabel.getStyleClass().add("processing-time");

        var processedContent = new VBox(10);
        processedContent.setAlignment(Pos.CENTER);
        processedContent.setPadding(new Insets(10));
        processedContent.getChildren().addAll(processedTitle, processedView, processedTimeLabel);

        processedCard.setBody(processedContent);

        leftPanel.getChildren().addAll(originalCard, processedCard);

        // Center - Performance Charts
        var centerPanel = new VBox(20);
        centerPanel.setPrefWidth(620);
        centerPanel.setAlignment(Pos.CENTER);

        // Create chart containers
        var speedupChart = new VBox(10);
        speedupChart.setAlignment(Pos.CENTER);
        speedupChart.getStyleClass().add("chart-container");
        speedupChart.setPadding(new Insets(10));
        speedupChart.setMinHeight(200);

        var speedupTitle = new Label("Speedup Comparison");
        speedupTitle.getStyleClass().add("h4");

        var speedupArea = new Pane();
        speedupArea.setMinHeight(180);
        speedupArea.getStyleClass().add("chart-area");

        speedupChart.getChildren().addAll(speedupTitle, speedupArea);

        var timeChart = new VBox(10);
        timeChart.setAlignment(Pos.CENTER);
        timeChart.getStyleClass().add("chart-container");
        timeChart.setPadding(new Insets(10));
        timeChart.setMinHeight(200);

        var timeTitle = new Label("Processing Time");
        timeTitle.getStyleClass().add("h4");

        var timeArea = new Pane();
        timeArea.setMinHeight(180);
        timeArea.getStyleClass().add("chart-area");

        timeChart.getChildren().addAll(timeTitle, timeArea);

        var fpsChart = new VBox(10);
        fpsChart.setAlignment(Pos.CENTER);
        fpsChart.getStyleClass().add("chart-container");
        fpsChart.setPadding(new Insets(10));
        fpsChart.setMinHeight(200);

        var fpsTitle = new Label("Frames Per Second");
        fpsTitle.getStyleClass().add("h4");

        var fpsArea = new Pane();
        fpsArea.setMinHeight(180);
        fpsArea.getStyleClass().add("chart-area");

        fpsChart.getChildren().addAll(fpsTitle, fpsArea);

        centerPanel.getChildren().addAll(speedupChart, timeChart, fpsChart);

        // Right side - Metrics and Controls
        var rightPanel = new VBox(20);
        rightPanel.setPrefWidth(400);
        rightPanel.setAlignment(Pos.TOP_CENTER);

        parallelProcessingSwitch = new ToggleSwitch("Parallel Processing");
        parallelProcessingSwitch.setSelected(true);
        parallelProcessingSwitch.getStyleClass().add("parallel-switch");

        var parallelSwitchContainer = new VBox(5);
        parallelSwitchContainer.setAlignment(Pos.CENTER);
        parallelSwitchContainer.getChildren().add(parallelProcessingSwitch);

        controlsVBox = new VBox(15);
        controlsVBox.setPadding(new Insets(20));
        controlsVBox.setAlignment(Pos.TOP_CENTER);
        controlsVBox.setMaxWidth(400);

        var startCameraButton = new Button("Start Camera");
        startCameraButton.getStyleClass().add("button-primary");
        startCameraButton.setMaxWidth(Double.MAX_VALUE);
        startCameraButton.setOnAction(e -> {
            if (!running) {
                startCameraFeed();
                startCameraButton.setText("Stop Camera");
            } else {
                stopCameraFeed();
                startCameraButton.setText("Start Camera");
            }
        });

        var cameraControls = new VBox(10);
        var cameraLabel = new Label("Image Filters");
        cameraLabel.getStyleClass().add("h4");

        var switchesRow1 = new HBox(10);
        var switchesRow2 = new HBox(10);
        switchesRow1.setAlignment(Pos.CENTER);
        switchesRow2.setAlignment(Pos.CENTER);

        var grayscaleSwitch = new ToggleSwitch("Grayscale");
        var edgeDetectionSwitch = new ToggleSwitch("Edge Detection");
        var sobelSwitch = new ToggleSwitch("Sobel Edge Detection");
        var gaussianSwitch = new ToggleSwitch("Gaussian Blur");
        var asciiSwitch = new ToggleSwitch("ASCII Art");
        var contrastSwitch = new ToggleSwitch("Contrast");

        filterSwitches.put("Grayscale", grayscaleSwitch);
        filterSwitches.put("Edge Detection", edgeDetectionSwitch);
        filterSwitches.put("Sobel Edge Detection", sobelSwitch);
        filterSwitches.put("Gaussian Blur", gaussianSwitch);
        filterSwitches.put("ASCII Art", asciiSwitch);
        filterSwitches.put("Contrast", contrastSwitch);

        filterSwitches.values().forEach(currentSwitch -> {
            currentSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal) {
                    filterSwitches.forEach((name, otherSwitch) -> {
                        if (otherSwitch != currentSwitch) {
                            otherSwitch.setSelected(false);
                        }
                    });
                }
            });
        });

        var col1 = new VBox(5, grayscaleSwitch, edgeDetectionSwitch, sobelSwitch);
        var col2 = new VBox(5, gaussianSwitch, asciiSwitch, contrastSwitch);
        col1.setAlignment(Pos.CENTER_LEFT);
        col2.setAlignment(Pos.CENTER_LEFT);
        switchesRow1.getChildren().addAll(col1, col2);

        cameraControls.getChildren().addAll(cameraLabel, switchesRow1);

        // Add Contrast Slider
        var contrastSlider = new javafx.scene.control.Slider(0, 200, contrastValue); // Min: 0, Max: 200, Initial: contrastValue (default 100)
        contrastSlider.setShowTickLabels(true);
        contrastSlider.setShowTickMarks(true);
        contrastSlider.setMajorTickUnit(50);
        contrastSlider.setMinorTickCount(5);
        contrastSlider.setBlockIncrement(10);

        // Add a label to show current contrast value
        Label contrastValueLabel = new Label(String.format("Contrast: %d", contrastValue));
        contrastValueLabel.getStyleClass().add("contrast-label");

        // Update contrastValue when slider value changes
        contrastSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            contrastValue = newVal.intValue();
            contrastValueLabel.setText(String.format("Contrast: %d", contrastValue));
        });

        var contrastControl = new VBox(5);
        contrastControl.setAlignment(Pos.CENTER_LEFT);
        contrastControl.getChildren().addAll(contrastValueLabel, contrastSlider);

        // Add the contrast control below the filter switches
        cameraControls.getChildren().add(contrastControl);

        var recordButton = new Button("Record 5s Video");
        recordButton.getStyleClass().add("button-primary");
        recordButton.setMaxWidth(Double.MAX_VALUE);
        recordButton.setOnAction(e -> {
            if (!isRecording) {
                startRecording();
                recordButton.setText("Recording...");
            }
        });

        metricsLabel = new Label("Performance Metrics: Not Available");
        metricsLabel.getStyleClass().add("metrics-label");
        metricsLabel.setWrapText(true);
        metricsLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px; -fx-alignment: center;");
        metricsLabel.setMaxWidth(Double.MAX_VALUE);
        metricsLabel.setAlignment(Pos.CENTER);

        var metricsScrollPane = new javafx.scene.control.ScrollPane(metricsLabel);
        metricsScrollPane.setFitToWidth(true);
        metricsScrollPane.setPrefWidth(600);
        metricsScrollPane.setPrefHeight(400);
        metricsScrollPane.getStyleClass().add("metrics-scroll-pane");
        metricsScrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        metricsScrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        metricsScrollPane.setPadding(new Insets(10));

        rightPanel.getChildren().addAll(
                startCameraButton,
                new Separator(),
                cameraControls,
                new Spacer(10),
                parallelSwitchContainer,
                recordButton,
                new Spacer(10),
                metricsScrollPane
        );

        root.getChildren().addAll(leftPanel, centerPanel, rightPanel);
        return root;
    }

    /**
     * Performans grafiklerini günceller. Üç farklı grafik içerir:
     * - Hızlanma karşılaştırması
     * - İşleme süresi
     * - Saniyedeki kare sayısı (FPS)
     * 
     * @param sequentialMetrics Sıralı işleme metrikleri
     * @param parallelMetrics Paralel işleme metrikleri
     * @param seqOverall Genel sıralı işleme metrikleri
     * @param parOverall Genel paralel işleme metrikleri
     */
    private void updateCharts(Map<String, PerformanceMetrics> sequentialMetrics, 
                            Map<String, PerformanceMetrics> parallelMetrics,
                            PerformanceMetrics seqOverall,
                            PerformanceMetrics parOverall) {
        Platform.runLater(() -> {
            // Get chart containers
            var centerPanel = (VBox) ((HBox) metricsLabel.getScene().getRoot()).getChildren().get(1);
            var speedupChart = (Pane) ((VBox) centerPanel.getChildren().get(0)).getChildren().get(1);
            var timeChart = (Pane) ((VBox) centerPanel.getChildren().get(1)).getChildren().get(1);
            var fpsChart = (Pane) ((VBox) centerPanel.getChildren().get(2)).getChildren().get(1);

            // Clear previous charts
            speedupChart.getChildren().clear();
            timeChart.getChildren().clear();
            fpsChart.getChildren().clear();

            // Draw new charts
            drawSpeedupChart(speedupChart, sequentialMetrics, parallelMetrics);
            drawTimeChart(timeChart, seqOverall, parOverall);
            drawFPSChart(fpsChart, seqOverall, parOverall);
        });
    }

    /**
     * Hızlanma karşılaştırma grafiğini çizer. Her filtre için
     * sıralı ve paralel işleme sürelerinin oranını gösterir.
     * 
     * @param chartArea Grafik alanı
     * @param seq Sıralı işleme metrikleri
     * @param par Paralel işleme metrikleri
     */
    private void drawSpeedupChart(Pane chartArea, Map<String, PerformanceMetrics> seq, Map<String, PerformanceMetrics> par) {
        double width = chartArea.getWidth() > 0 ? chartArea.getWidth() : 600;
        double height = chartArea.getHeight() > 0 ? chartArea.getHeight() : 180;
        var canvas = new javafx.scene.canvas.Canvas(width, height);
        var gc = canvas.getGraphicsContext2D();

        // Prepare data with short filter names
        Map<String, String> shortNames = Map.of(
            "Edge Detection", "Edge",
            "Sobel Edge Detection", "Sobel",
            "Gaussian Blur", "Gauss",
            "ASCII Art", "ASCII",
            "Contrast", "Cont",
            "Grayscale", "Gray"
        );
        List<String> filters = new ArrayList<>(seq.keySet());
        int n = filters.size();
        double margin = 60;
        double barAreaWidth = width - 2 * margin;
        double barWidth = Math.max(18, Math.min(40, barAreaWidth / (n * 1.4)));
        double spacing = (barAreaWidth - n * barWidth) / (n + 1);
        double chartBottom = height - 40;
        double chartTop = 30;
        double chartHeight = chartBottom - chartTop;

        // Find max speedup
        double maxSpeedup = 1.0;
        for (String filter : filters) {
            double speedup = (double) seq.get(filter).totalTime / par.get(filter).totalTime;
            if (Double.isFinite(speedup)) maxSpeedup = Math.max(maxSpeedup, speedup);
        }
        maxSpeedup = Math.ceil(maxSpeedup * 1.1); // add 10% headroom
        double scale = chartHeight / maxSpeedup;

        // Draw axes
        gc.setStroke(javafx.scene.paint.Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeLine(margin, chartBottom, width - margin, chartBottom); // X axis
        gc.strokeLine(margin, chartTop, margin, chartBottom); // Y axis

        // Axis names
        gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 12));
        gc.fillText("Filter", width / 2 - 20, height - 10);
        gc.save();
        gc.translate(20, height / 2 + 20);
        gc.rotate(-90);
        gc.fillText("Speedup (x)", 0, 0);
        gc.restore();

        // Draw bars and labels
        double x = margin + spacing;
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
        for (String filter : filters) {
            double speedup = (double) seq.get(filter).totalTime / par.get(filter).totalTime;
            double barHeight = speedup * scale;
            // Bar
            gc.setFill(javafx.scene.paint.Color.DODGERBLUE);
            gc.fillRect(x, chartBottom - barHeight, barWidth, barHeight);
            // Bar value label (above bar)
            gc.setFill(javafx.scene.paint.Color.YELLOW);
            gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
            gc.fillText(String.format("%.2fx", speedup), x + barWidth / 2 - 15, chartBottom - barHeight - 5);
            // Filter name (less angled, below bar)
            gc.setFill(javafx.scene.paint.Color.WHITE);
            gc.save();
            gc.translate(x + barWidth / 2, chartBottom + 15);
            gc.rotate(-15);
            String shortName = shortNames.getOrDefault(filter, filter.length() > 8 ? filter.substring(0, 8) : filter);
            gc.fillText(shortName, 0, 0);
            gc.restore();
            x += barWidth + spacing;
        }
        chartArea.getChildren().add(canvas);
    }

    /**
     * İşleme süresi grafiğini çizer. Sıralı ve paralel işleme
     * sürelerini karşılaştırmalı olarak gösterir.
     * 
     * @param chartArea Grafik alanı
     * @param seq Sıralı işleme metrikleri
     * @param par Paralel işleme metrikleri
     */
    private void drawTimeChart(Pane chartArea, PerformanceMetrics seq, PerformanceMetrics par) {
        var canvas = new javafx.scene.canvas.Canvas(chartArea.getWidth(), chartArea.getHeight());
        var gc = canvas.getGraphicsContext2D();

        // Draw axes
        gc.setStroke(javafx.scene.paint.Color.WHITE);
        gc.strokeLine(50, chartArea.getHeight() - 30, chartArea.getWidth() - 20, chartArea.getHeight() - 30);
        gc.strokeLine(50, 20, 50, chartArea.getHeight() - 30);

        // Draw bars
        int barWidth = 40;
        int spacing = 20;
        int startX = 70;

        // Sequential bar
        gc.setFill(javafx.scene.paint.Color.RED);
        gc.fillRect(startX, chartArea.getHeight() - 30 - (seq.totalTime / 1000.0), barWidth, seq.totalTime / 1000.0);
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillText("Seq", startX, chartArea.getHeight() - 10);

        // Parallel bar
        gc.setFill(javafx.scene.paint.Color.GREEN);
        gc.fillRect(startX + barWidth + spacing, chartArea.getHeight() - 30 - (par.totalTime / 1000.0), 
                   barWidth, par.totalTime / 1000.0);
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillText("Par", startX + barWidth + spacing, chartArea.getHeight() - 10);

        chartArea.getChildren().add(canvas);
    }

    /**
     * FPS (saniyedeki kare sayısı) grafiğini çizer. Sıralı ve
     * paralel işleme modlarının performansını karşılaştırır.
     * 
     * @param chartArea Grafik alanı
     * @param seq Sıralı işleme metrikleri
     * @param par Paralel işleme metrikleri
     */
    private void drawFPSChart(Pane chartArea, PerformanceMetrics seq, PerformanceMetrics par) {
        var canvas = new javafx.scene.canvas.Canvas(chartArea.getWidth(), chartArea.getHeight());
        var gc = canvas.getGraphicsContext2D();

        // Draw axes
        gc.setStroke(javafx.scene.paint.Color.WHITE);
        gc.strokeLine(50, chartArea.getHeight() - 30, chartArea.getWidth() - 20, chartArea.getHeight() - 30);
        gc.strokeLine(50, 20, 50, chartArea.getHeight() - 30);

        // Calculate FPS
        double seqFPS = (seq.totalFrames * 1000.0) / seq.totalTime;
        double parFPS = (par.totalFrames * 1000.0) / par.totalTime;

        // Draw line chart
        gc.setStroke(javafx.scene.paint.Color.YELLOW);
        gc.setLineWidth(2);

        // Draw FPS lines
        gc.strokeLine(70, chartArea.getHeight() - 30 - seqFPS, 
                     150, chartArea.getHeight() - 30 - seqFPS);
        gc.strokeLine(170, chartArea.getHeight() - 30 - parFPS, 
                     250, chartArea.getHeight() - 30 - parFPS);

        // Draw labels
        gc.setFill(javafx.scene.paint.Color.WHITE);
        gc.fillText(String.format("Seq: %.1f FPS", seqFPS), 70, chartArea.getHeight() - 10);
        gc.fillText(String.format("Par: %.1f FPS", parFPS), 170, chartArea.getHeight() - 10);

        chartArea.getChildren().add(canvas);
    }

    /**
     * Kaydedilen kareleri işler ve performans analizini gerçekleştirir.
     * Her filtre için ayrı ayrı ve tüm filtreler birlikte test edilir.
     * Sonuçlar metrikler panelinde ve grafiklerde gösterilir.
     */
    private void processRecordedFrames() {
        List<String> filtersToTest = filterSwitches.keySet().stream()
                .collect(Collectors.toList());

        if (filtersToTest.isEmpty()) {
            Platform.runLater(() ->
                    metricsLabel.setText("Please select at least one filter to test performance.")
            );
            return;
        }

        // Update UI with progress
        Platform.runLater(() -> metricsLabel.setText("Testing individual filters..."));

        // Calculate metrics for each filter individually
        Map<String, PerformanceMetrics> sequentialFilterMetrics = new HashMap<>();
        Map<String, PerformanceMetrics> parallelFilterMetrics = new HashMap<>();

        // Test each filter individually
        for (String filter : filtersToTest) {
            // Update progress
            Platform.runLater(() -> metricsLabel.setText("Testing filter: " + filter));
            
            PerformanceMetrics seq = new PerformanceMetrics();
            PerformanceMetrics par = new PerformanceMetrics();
            long seqTotal = 0;
            long parTotal = 0;

            for (BufferedImage frame : recordedFrames) {
                BufferedImage tmp = new BufferedImage(frame.getWidth(), frame.getHeight(), frame.getType());
                
                // Sequential test
                long start = System.nanoTime();
                ImageProcessor.runFiltersSequentially(frame, tmp, List.of(filter), contrastValue);
                long ms = (System.nanoTime() - start) / 1_000_000;
                seq.frameTimes.add(ms);
                seqTotal += ms;

                // Parallel test
                start = System.nanoTime();
                ImageProcessor.runFiltersInParallel(frame, tmp, List.of(filter), contrastValue);
                ms = (System.nanoTime() - start) / 1_000_000;
                par.frameTimes.add(ms);
                parTotal += ms;
            }

            seq.totalFrames = recordedFrames.size();
            seq.totalTime = seqTotal;
            par.totalFrames = recordedFrames.size();
            par.totalTime = parTotal;

            sequentialFilterMetrics.put(filter, seq);
            parallelFilterMetrics.put(filter, par);
        }

        // Update progress
        Platform.runLater(() -> metricsLabel.setText("Testing all filters together..."));

        // Calculate overall metrics
        PerformanceMetrics seqOverall = new PerformanceMetrics();
        PerformanceMetrics parOverall = new PerformanceMetrics();
        long seqTotal = 0;
        long parTotal = 0;

        for (BufferedImage frame : recordedFrames) {
            BufferedImage tmp = new BufferedImage(frame.getWidth(), frame.getHeight(), frame.getType());
            
            // Sequential overall
            long start = System.nanoTime();
            ImageProcessor.runFiltersSequentially(frame, tmp, filtersToTest, contrastValue);
            long ms = (System.nanoTime() - start) / 1_000_000;
            seqOverall.frameTimes.add(ms);
            seqTotal += ms;

            // Parallel overall
            start = System.nanoTime();
            ImageProcessor.runFiltersInParallel(frame, tmp, filtersToTest, contrastValue);
            ms = (System.nanoTime() - start) / 1_000_000;
            parOverall.frameTimes.add(ms);
            parTotal += ms;
        }

        seqOverall.totalFrames = recordedFrames.size();
        seqOverall.totalTime = seqTotal;
        parOverall.totalFrames = recordedFrames.size();
        parOverall.totalTime = parTotal;

        double speedup = seqOverall.totalTime > 0 ? (double) seqOverall.totalTime / parOverall.totalTime : 1.0;

        // Update progress
        Platform.runLater(() -> metricsLabel.setText("Generating final report..."));

        StringBuilder metricsText = new StringBuilder();
        metricsText.append("=== Image Processing Performance Analysis ===\n\n");
        
        // Test Configuration in a more compact format
        metricsText.append("Test Configuration:\n");
        metricsText.append("┌─────────────────────────────────────────────┐\n");
        metricsText.append("│ Image Size: 640x480 pixels                   │\n");
        metricsText.append("│ Test Duration: 5 seconds                     │\n");
        metricsText.append("│ Frame Rate: 30 FPS                           │\n");
        metricsText.append("│ Total Frames: ").append(String.format("%-30d", recordedFrames.size())).append("│\n");
        metricsText.append("│ Active Filters: ").append(String.format("%-28s", String.join(", ", filtersToTest))).append("│\n");
        metricsText.append("└─────────────────────────────────────────────┘\n\n");

        // Individual filter performance in a table format
        metricsText.append("=== Individual Filter Performance ===\n");
        metricsText.append("┌─────────────────────────────────────────────────────────────────────────┐\n");
        metricsText.append("│ Filter Performance Comparison                                           │\n");
        metricsText.append("├─────────────────────────────────────────────────────────────────────────┤\n");
        metricsText.append("│ Filter Name          │ Sequential (ms) │ Parallel (ms) │ Speedup (x)    │\n");
        metricsText.append("├─────────────────────────────────────────────────────────────────────────┤\n");

        for (String filter : filtersToTest) {
            PerformanceMetrics seq = sequentialFilterMetrics.get(filter);
            PerformanceMetrics par = parallelFilterMetrics.get(filter);
            double filterSpeedup = seq.totalTime > 0 ? (double) seq.totalTime / par.totalTime : 1.0;
            
            metricsText.append(String.format("│ %-20s │ %-14d │ %-12d │ %-14.2f │\n",
                    filter,
                    seq.totalTime,
                    par.totalTime,
                    filterSpeedup));
        }
        metricsText.append("└─────────────────────────────────────────────────────────────────────────┘\n\n");

        // Overall performance in a more visual format
        metricsText.append("=== Overall Performance (All Filters) ===\n");
        metricsText.append("┌─────────────────────────────────────────────────────────────────────────┐\n");
        metricsText.append("│ Processing Mode      │ Total Time (ms) │ Avg Time (ms) │ FPS            │\n");
        metricsText.append("├─────────────────────────────────────────────────────────────────────────┤\n");
        metricsText.append(String.format("│ Sequential          │ %-14d │ %-12.2f │ %-14.2f │\n",
                seqOverall.totalTime,
                seqOverall.frameTimes.stream().mapToLong(Long::longValue).average().orElse(0),
                (seqOverall.totalFrames * 1000.0) / seqOverall.totalTime));
        metricsText.append(String.format("│ Parallel            │ %-14d │ %-12.2f │ %-14.2f │\n",
                parOverall.totalTime,
                parOverall.frameTimes.stream().mapToLong(Long::longValue).average().orElse(0),
                (parOverall.totalFrames * 1000.0) / parOverall.totalTime));
        metricsText.append("└─────────────────────────────────────────────────────────────────────────┘\n\n");

        // Performance comparison with visual indicators
        metricsText.append("=== Performance Comparison ===\n");
        metricsText.append("┌─────────────────────────────────────────────────────────────────────────┐\n");
        metricsText.append(String.format("│ Overall Speedup: %-8.2fx │ Time Saved: %-8.2f%% │ CPU Cores: %-8d │\n",
                speedup,
                ((seqOverall.totalTime - parOverall.totalTime) * 100.0) / seqOverall.totalTime,
                Runtime.getRuntime().availableProcessors()));
        metricsText.append("└─────────────────────────────────────────────────────────────────────────┘\n");

        // Update UI with final results
        Platform.runLater(() -> {
            metricsLabel.setText(metricsText.toString());
            metricsLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
            
            // Adjust ScrollPane size
            javafx.scene.control.ScrollPane scrollPane = (javafx.scene.control.ScrollPane) metricsLabel.getParent();
            if (scrollPane != null) {
                scrollPane.setPrefWidth(800); // Wider scroll pane
                scrollPane.setPrefHeight(400); // Taller scroll pane
                scrollPane.setFitToWidth(true);
                scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
                scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
            }
        });

        // Update charts with the new metrics
        updateCharts(sequentialFilterMetrics, parallelFilterMetrics, seqOverall, parOverall);
    }

    /**
     * Java BufferedImage'i JavaFX Image'e dönüştürür.
     * Farklı görüntü formatlarını (BGR, Gri ton) destekler.
     * 
     * @param image Dönüştürülecek BufferedImage
     * @return JavaFX Image nesnesi
     */
    private Image convertToFxImage(BufferedImage image) {
        if (image == null) return null;

        WritableImage wr = new WritableImage(image.getWidth(), image.getHeight());
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] bgraPixels = new byte[width * height * 4];

        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            byte[] bgrData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            int bgrIndex = 0;
            for (int i = 0; i < bgraPixels.length; i += 4) {
                bgraPixels[i]     = bgrData[bgrIndex++];   // Blue
                bgraPixels[i + 1] = bgrData[bgrIndex++]; // Green
                bgraPixels[i + 2] = bgrData[bgrIndex++]; // Red
                bgraPixels[i + 3] = (byte) 0xFF;         // Alpha
            }
        } else if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            byte[] grayData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            int grayIndex = 0;
            for (int i = 0; i < bgraPixels.length; i += 4) {
                byte grayValue = grayData[grayIndex++];
                bgraPixels[i]     = grayValue;
                bgraPixels[i + 1] = grayValue;
                bgraPixels[i + 2] = grayValue;
                bgraPixels[i + 3] = (byte) 0xFF;
            }
        } else {
            int[] argbPixels = new int[width * height];
            image.getRGB(0, 0, width, height, argbPixels, 0, width);
            for (int i = 0; i < argbPixels.length; i++) {
                int pixel = argbPixels[i];
                bgraPixels[i * 4]     = (byte) (pixel & 0xFF);
                bgraPixels[i * 4 + 1] = (byte) ((pixel >> 8)  & 0xFF);
                bgraPixels[i * 4 + 2] = (byte) ((pixel >> 16) & 0xFF);
                bgraPixels[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF);
            }
        }
        wr.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getByteBgraInstance(), bgraPixels, 0, width * 4);
        return wr;
    }

    /**
     * Seçili filtreleri görüntüye uygular. ImageProcessor sınıfını
     * kullanarak sıralı veya paralel işleme modunu seçer.
     * 
     * @param input İşlenecek görüntü
     * @return İşlenmiş görüntü
     */
    private BufferedImage applyFilters(BufferedImage input) {
        // Bu fonksiyonu ImageProcessor'a taşıdık, buradan çağıracağız
        return ImageProcessor.applyFilters(input, filterSwitches, parallelProcessingSwitch.isSelected(), contrastValue);
    }

    /**
     * Kamera görüntüsünü yakalamaya başlar. 30 FPS hızında
     * görüntü yakalar ve işler. Her kare için performans
     * metriklerini hesaplar.
     */
    private void startCameraFeed() {
        running = true;
        new Thread(() -> {
            try {
                System.out.println("Initializing camera...");
                grabber = new OpenCVFrameGrabber(0);
                grabber.setFormat("MJPG");
                grabber.setImageWidth(640);
                grabber.setImageHeight(480);
                grabber.setFrameRate(30);

                System.out.println("Starting camera...");
                grabber.start();
                System.out.println("Camera started successfully");

                long lastFrameTime = System.nanoTime();
                long targetFrameTime = 1_000_000_000 / 30; // 30 FPS

                while (running) {
                    long currentTime = System.nanoTime();
                    long elapsedTime = currentTime - lastFrameTime;

                    if (elapsedTime >= targetFrameTime) {
                        org.bytedeco.javacv.Frame frame = grabber.grab();
                        if (frame == null || frame.image == null) {
                            System.err.println("Failed to grab frame or frame.image is null");
                            continue;
                        }

                        long frameGrabTime = System.nanoTime();
                        Mat mat = converter.convert(frame);
                        if (mat == null || mat.empty()) {
                            System.err.println("Failed to convert frame to Mat or Mat is empty");
                            continue;
                        }

                        BufferedImage original = ImageUtils.matToBufferedImage(mat);
                        if (original == null) {
                            System.err.println("Failed to convert Mat to BufferedImage");
                            continue;
                        }
                        long originalConvertTime = System.nanoTime() - frameGrabTime;

                        long filterStartTime = System.nanoTime();
                        BufferedImage processed = applyFilters(original);
                        long filterProcessingTime = System.nanoTime() - filterStartTime;

                        final BufferedImage finalOriginal = original;
                        final BufferedImage finalProcessed = processed;
                        final long finalOriginalTimeMs = originalConvertTime / 1_000_000;
                        final long finalFilterTimeMs = filterProcessingTime / 1_000_000;

                        Platform.runLater(() -> {
                            try {
                                Image fxOriginalImage = convertToFxImage(finalOriginal);
                                Image fxProcessedImage = convertToFxImage(finalProcessed);

                                if (fxOriginalImage != null) cameraView.setImage(fxOriginalImage);
                                if (fxProcessedImage != null) processedView.setImage(fxProcessedImage);

                                originalTimeLabel.setText(String.format("Original Prep: %d ms", finalOriginalTimeMs));
                                processedTimeLabel.setText(String.format("Filter Time: %d ms", finalFilterTimeMs));
                            } catch (Exception e) {
                                System.err.println("Error updating UI: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                        lastFrameTime = currentTime; // Her başarılı kareden sonra güncelle
                    } else {
                        // CPU'yu boşa yormamak için kısa bir uyku
                        try {
                            Thread.sleep(Math.max(0, (targetFrameTime - elapsedTime) / 1_000_000 -1 ));
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            running = false; // Kesinti durumunda döngüden çık
                        }
                    }
                }
            } catch (OpenCVFrameGrabber.Exception e) {
                System.err.println("Camera start error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Camera Error");
                    alert.setHeaderText("Failed to start camera");
                    alert.setContentText("Error: " + e.getMessage() + "\nPlease ensure your camera is connected and not in use.");
                    alert.showAndWait();
                });
            } catch (Exception e) {
                System.err.println("Unexpected error in camera feed: " + e.getMessage());
                e.printStackTrace();
            }
            finally {
                if (grabber != null) {
                    try {
                        System.out.println("Stopping camera...");
                        grabber.stop();
                        grabber.release();
                        System.out.println("Camera stopped successfully");
                    } catch (OpenCVFrameGrabber.Exception ex) {
                        System.err.println("Error stopping camera: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                }
                running = false;
                Platform.runLater(() -> {
                    // Kamera durduğunda butonu güncelle (eğer başlat butonuysa)
                    var startButton = (Button) controlsVBox.getChildren().get(0); // Varsayım: ilk eleman başlat butonu
                    if (startButton.getText().equals("Stop Camera")) {
                        startButton.setText("Start Camera");
                    }
                });
            }
        }).start();
    }

    /**
     * Kamera görüntüsünü yakalamayı durdurur ve kaynakları temizler.
     */
    private void stopCameraFeed() {
        running = false;
    }

    /**
     * Uygulama özelliklerini yükler. application.properties
     * dosyasından yapılandırma ayarlarını okur.
     */
    private void loadApplicationProperties() {
        try {
            Properties properties = new Properties();
            var resource = getClass().getResourceAsStream(APP_PROPERTIES_PATH);
            if (resource == null) {
                System.err.println("Application properties not found at: " + APP_PROPERTIES_PATH);
                System.setProperty("app.name", "Default App Name"); // Fallback
                return;
            }
            properties.load(new InputStreamReader(resource, UTF_8));
            properties.forEach((key, value) -> System.setProperty(
                    String.valueOf(key),
                    String.valueOf(value)
            ));
        } catch (IOException e) {
            System.err.println("Failed to load application properties: " + e.getMessage());
            // throw new RuntimeException(e); // Uygulamanın çökmesini önle
        }
    }

    /**
     * 5 saniyelik video kaydını başlatır. Test görüntüsü
     * oluşturur ve performans testlerini gerçekleştirir.
     * Sonuçlar metrikler panelinde gösterilir.
     */
    private void startRecording() {
        if (isRecording) {
            return;
        }

        isRecording = true;
        recordedFrames.clear();
        sequentialMetrics = new PerformanceMetrics();
        parallelMetrics = new PerformanceMetrics();

        Button recordButton = null;
        for(var node : controlsVBox.getChildren()) {
            if(node instanceof Button && ((Button)node).getText().contains("Record")) {
                recordButton = (Button)node;
                break;
            }
        }
        final Button finalRecordButton = recordButton;
        if(finalRecordButton != null) finalRecordButton.setDisable(true);

        // Test görüntüsü yerine kameradan fotoğraf çek
        org.bytedeco.opencv.opencv_videoio.VideoCapture capture = new org.bytedeco.opencv.opencv_videoio.VideoCapture(0); // 0: varsayılan kamera
        if (!capture.isOpened()) {
            System.err.println("Kamera açılamadı!");
            return;
        }

        org.bytedeco.opencv.opencv_core.Mat frame = new org.bytedeco.opencv.opencv_core.Mat();
        if (!capture.read(frame)) {
            System.err.println("Kameradan görüntü okunamadı!");
            return;
        }

        // Mat'i BufferedImage'e dönüştür
        BufferedImage testImage = ImageUtils.matToBufferedImage(frame);

        // Kaynakları serbest bırak
        frame.release();
        capture.release();

        // Eğer görüntü alınamadıysa hata ver
        if (testImage == null) {
            System.err.println("Görüntü dönüştürülemedi!");
            return;
        }

        // Create 5 seconds worth of frames (assuming 30 FPS)
        for (int i = 0; i < 150; i++) {
            recordedFrames.add(testImage);
        }

        // Start performance testing in background
        new Thread(() -> {
            try {
                Platform.runLater(() -> metricsLabel.setText("Starting performance test..."));
                processRecordedFrames();
            } finally {
                isRecording = false;
                Platform.runLater(() -> {
                    if(finalRecordButton != null) {
                        finalRecordButton.setText("Record 5s Video");
                        finalRecordButton.setDisable(false);
                    }
                });
            }
        }).start();
    }
}