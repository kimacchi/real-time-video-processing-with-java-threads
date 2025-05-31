package starter;

import static java.nio.charset.StandardCharsets.UTF_8;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
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
import javafx.scene.control.CheckBox;
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
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.opencv_core.Mat;
import starter.core.ImageUtils;
import starter.core.PerformanceMetrics;
import starter.filters.*;
import starter.processing.ThreadFilter;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;

public class Launcher extends Application {

    static final String ASSETS_DIR = "/assets/";

    static final String APP_ICON_PATH = Objects.requireNonNull(
        Launcher.class.getResource(ASSETS_DIR + "icons/app-icon.png")
    ).toExternalForm();

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

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // set AtlantaFX stylesheet
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        // obtain application properties from pom.xml
        loadApplicationProperties();

        var scene = new Scene(createWelcomePane(), 1280, 900); // make it resizable
        scene.getStylesheets().add(ASSETS_DIR + "index.css");

        stage.setScene(scene);
        stage.setTitle(System.getProperty("app.name"));
        stage.getIcons().add(new Image(APP_ICON_PATH));
        stage.setOnCloseRequest(t -> {
            stopCameraFeed();
            Platform.exit();
        });
        stage.setMinWidth(1280);
        stage.setMinHeight(900);

        Platform.runLater(() -> {
            stage.show();
            stage.requestFocus();
        });
    }

    private Pane createWelcomePane() {
        var root = new VBox(20); // 20 pixels spacing between elements
        root.getStyleClass().add("welcome");
        root.setAlignment(Pos.TOP_CENTER);
        root.setPadding(new Insets(20));

        // Camera Views
        var cameraViews = new HBox(20);
        cameraViews.setAlignment(Pos.CENTER);
        
        // Original Camera View
        var originalCard = new VBox();
        originalCard.setMinWidth(400);
        originalCard.setMinHeight(300);
        originalCard.setMaxWidth(400);
        originalCard.setMaxHeight(300);
        originalCard.getStyleClass().addAll("camera-card", "card");
        originalCard.setAlignment(Pos.CENTER);
        originalCard.setPadding(new Insets(10));
        
        cameraView = new ImageView();
        cameraView.setFitWidth(380);
        cameraView.setFitHeight(280);
        cameraView.setPreserveRatio(true);
        
        originalTimeLabel = new Label("Processing time: 0ms");
        originalTimeLabel.getStyleClass().add("processing-time");
        
        originalCard.getChildren().addAll(cameraView, originalTimeLabel);
        
        // Processed Camera View
        var processedCard = new VBox();
        processedCard.setMinWidth(400);
        processedCard.setMinHeight(300);
        processedCard.setMaxWidth(400);
        processedCard.setMaxHeight(300);
        processedCard.getStyleClass().addAll("camera-card", "card");
        processedCard.setAlignment(Pos.CENTER);
        processedCard.setPadding(new Insets(10));
        
        processedView = new ImageView();
        processedView.setFitWidth(380);
        processedView.setFitHeight(280);
        processedView.setPreserveRatio(true);
        
        processedTimeLabel = new Label("Processing time: 0ms");
        processedTimeLabel.getStyleClass().add("processing-time");
        
        processedCard.getChildren().addAll(processedView, processedTimeLabel);
        
        cameraViews.getChildren().addAll(originalCard, processedCard);

        // Parallel Processing Switch
        parallelProcessingSwitch = new ToggleSwitch("Parallel Processing");
        parallelProcessingSwitch.setSelected(true); // Enable by default
        parallelProcessingSwitch.getStyleClass().add("parallel-switch");
        
        var parallelSwitchContainer = new VBox(5);
        parallelSwitchContainer.setAlignment(Pos.CENTER);
        parallelSwitchContainer.getChildren().add(parallelProcessingSwitch);

        // Metrics Label
        metricsLabel = new Label("Performance Metrics: Not Available");
        metricsLabel.getStyleClass().add("metrics-label");
        metricsLabel.setWrapText(true);
        metricsLabel.setMaxWidth(800);
        
        // Create ScrollPane for metrics
        var metricsScrollPane = new javafx.scene.control.ScrollPane(metricsLabel);
        metricsScrollPane.setFitToWidth(true);
        metricsScrollPane.setPrefHeight(200);
        metricsScrollPane.getStyleClass().add("metrics-scroll-pane");

        // Record Video Button
        var recordButton = new Button("Record 5s Video");
        recordButton.getStyleClass().add("button-primary");
        recordButton.setMaxWidth(Double.MAX_VALUE);
        recordButton.setOnAction(e -> {
            if (!isRecording) {
                startRecording();
                recordButton.setText("Recording...");
            }
        });

        // Controls Section
        var controlsCard = new Card();
        controlsCard.setMaxWidth(200);
        controlsCard.setPadding(new Insets(20));

        controlsVBox = new VBox(15); // 15 pixels spacing between controls
        controlsVBox.setPadding(new Insets(20));

        // Start Camera Button
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

        // Camera Controls
        var cameraControls = new VBox(10);
        var cameraLabel = new Label("Image Filters");
        cameraLabel.getStyleClass().add("h4");
        
        // Create rows for switches
        var switchesRow1 = new HBox(10); // first row with 2 columns
        var switchesRow2 = new HBox(10); // second row with 2 columns
        switchesRow1.setMaxWidth(400);
        switchesRow2.setMaxWidth(400);
        
        // Create all switches first
        var grayscaleSwitch = new ToggleSwitch("Grayscale");
        var edgeDetectionSwitch = new ToggleSwitch("Edge Detection");
        var sobelSwitch = new ToggleSwitch("Sobel Edge");
        var gaussianSwitch = new ToggleSwitch("Gaussian Blur");
        var asciiSwitch = new ToggleSwitch("ASCII Art");
        var contrastSwitch = new ToggleSwitch("Contrast");
        
        // Add switches to the map
        filterSwitches.put("Grayscale", grayscaleSwitch);
        filterSwitches.put("Edge Detection", edgeDetectionSwitch);
        filterSwitches.put("Sobel Edge Detection", sobelSwitch);
        filterSwitches.put("Gaussian Blur", gaussianSwitch);
        filterSwitches.put("ASCII Art", asciiSwitch);
        filterSwitches.put("Contrast", contrastSwitch);
        
        // Add listeners to make switches mutually exclusive
        grayscaleSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                edgeDetectionSwitch.setSelected(false);
                sobelSwitch.setSelected(false);
                gaussianSwitch.setSelected(false);
                asciiSwitch.setSelected(false);
                contrastSwitch.setSelected(false);
            }
        });
        
        edgeDetectionSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                grayscaleSwitch.setSelected(false);
                sobelSwitch.setSelected(false);
                gaussianSwitch.setSelected(false);
                asciiSwitch.setSelected(false);
                contrastSwitch.setSelected(false);
            }
        });
        
        sobelSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                grayscaleSwitch.setSelected(false);
                edgeDetectionSwitch.setSelected(false);
                gaussianSwitch.setSelected(false);
                asciiSwitch.setSelected(false);
                contrastSwitch.setSelected(false);
            }
        });
        
        gaussianSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                grayscaleSwitch.setSelected(false);
                edgeDetectionSwitch.setSelected(false);
                sobelSwitch.setSelected(false);
                asciiSwitch.setSelected(false);
                contrastSwitch.setSelected(false);
            }
        });
        
        asciiSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                grayscaleSwitch.setSelected(false);
                edgeDetectionSwitch.setSelected(false);
                sobelSwitch.setSelected(false);
                gaussianSwitch.setSelected(false);
                contrastSwitch.setSelected(false);
            }
        });
        
        contrastSwitch.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                grayscaleSwitch.setSelected(false);
                edgeDetectionSwitch.setSelected(false);
                sobelSwitch.setSelected(false);
                gaussianSwitch.setSelected(false);
                asciiSwitch.setSelected(false);
            }
        });
        
        // First column
        var col1 = new VBox(5);
        col1.getChildren().addAll(grayscaleSwitch, edgeDetectionSwitch, sobelSwitch);
        
        // Second column
        var col2 = new VBox(5);
        col2.getChildren().addAll(gaussianSwitch, asciiSwitch, contrastSwitch);
        
        switchesRow1.getChildren().addAll(col1, col2);
        
        cameraControls.getChildren().addAll(
            cameraLabel,
            switchesRow1
        );

        controlsVBox.getChildren().addAll(
            startCameraButton,
            new Separator(),
            cameraControls,
            new Spacer(10),
            recordButton,
            new Spacer(10),
            metricsScrollPane
        );

        root.getChildren().addAll(cameraViews, parallelSwitchContainer, controlsVBox);

        return root;
    }

    private Image convertToFxImage(BufferedImage image) {
        if (image == null) return null;
        
        WritableImage wr = new WritableImage(image.getWidth(), image.getHeight());
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        
        // Convert ARGB to RGBA
        byte[] buffer = new byte[pixels.length * 4];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            buffer[i * 4] = (byte) ((pixel >> 16) & 0xFF);     // R
            buffer[i * 4 + 1] = (byte) ((pixel >> 8) & 0xFF);  // G
            buffer[i * 4 + 2] = (byte) (pixel & 0xFF);         // B
            buffer[i * 4 + 3] = (byte) ((pixel >> 24) & 0xFF); // A
        }
        
        wr.getPixelWriter().setPixels(0, 0, image.getWidth(), image.getHeight(),
                PixelFormat.getByteBgraInstance(), buffer, 0, image.getWidth() * 4);
        
        return wr;
    }

    private BufferedImage applyFilters(BufferedImage input) {
        if (input == null) return null;

        // Get selected filters
        List<String> selectedFilters = filterSwitches.entrySet().stream()
            .filter(entry -> entry.getValue().isSelected())
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());

        if (selectedFilters.isEmpty()) {
            return input;
        }

        // Create output image
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        
        if (parallelProcessingSwitch.isSelected()) {
            // Parallel processing
            int threadsCount = Runtime.getRuntime().availableProcessors();
            if (threadsCount <= 0) threadsCount = 8;
            
            int height = input.getHeight();
            int chunkSize = Math.max(1, height / threadsCount);
            
            Thread[] threads = new Thread[threadsCount];
            for (int i = 0; i < threadsCount; i++) {
                int startY = i * chunkSize;
                int endY = (i == threadsCount - 1) ? height : Math.min(height, (i + 1) * chunkSize);
                if (startY >= endY) continue;

                threads[i] = new ThreadFilter(input, output, startY, endY, selectedFilters, contrastValue);
                threads[i].start();
            }

            // Wait for all threads to complete
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
        } else {
            // Sequential processing
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int pixel = input.getRGB(x, y);
                    
                    // Apply each selected filter in sequence
                    for (String filter : selectedFilters) {
                        switch (filter) {
                            case "Grayscale":
                                pixel = GrayscaleFilter.apply(pixel);
                                break;
                            case "Edge Detection":
                                pixel = EdgeDetectionFilter.apply(pixel);
                                break;
                            case "Sobel Edge Detection":
                                pixel = SobelEdgeDetectionFilter.apply(input, x, y);
                                break;
                            case "Gaussian Blur":
                                pixel = GaussianBlurFilter.apply(input, x, y);
                                break;
                            case "Contrast":
                                pixel = ContrastAdjustmentFilter.apply(pixel, contrastValue);
                                break;
                            case "ASCII Art":
                                // ASCII Art needs to be handled differently as it processes the whole image
                                output = ASCIIArtFilter.convertToASCIIImage(input);
                                return output;
                        }
                    }
                    output.setRGB(x, y, pixel);
                }
            }
        }

        return output;
    }

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
                long targetFrameTime = 1_000_000_000 / 30;

                while (running) {
                    long currentTime = System.nanoTime();
                    long elapsedTime = currentTime - lastFrameTime;

                    if (elapsedTime >= targetFrameTime) {
                        org.bytedeco.javacv.Frame frame = grabber.grab();
                        if (frame == null || frame.image == null) {
                            System.out.println("Failed to grab frame");
                            continue;
                        }
                        
                        long frameStartTime = System.nanoTime();
                        Mat mat = converter.convert(frame);
                        if (mat == null || mat.empty()) {
                            System.out.println("Failed to convert frame to Mat");
                            continue;
                        }

                        BufferedImage original = ImageUtils.matToBufferedImage(mat);
                        if (original == null) {
                            System.out.println("Failed to convert Mat to BufferedImage");
                            continue;
                        }
                        
                        long originalProcessingTime = System.nanoTime() - frameStartTime;
                        
                        // Apply selected filters
                        long filterStartTime = System.nanoTime();
                        BufferedImage processed = applyFilters(original);
                        long filterProcessingTime = System.nanoTime() - filterStartTime;
                        
                        final BufferedImage finalOriginal = original;
                        final BufferedImage finalProcessed = processed;
                        final long finalOriginalTime = originalProcessingTime;
                        final long finalFilterTime = filterProcessingTime;
                        
                        Platform.runLater(() -> {
                            try {
                                Image fxImage = convertToFxImage(finalOriginal);
                                if (fxImage == null) {
                                    System.out.println("Failed to convert BufferedImage to FX Image");
                                    return;
                                }
                                cameraView.setImage(fxImage);
                                processedView.setImage(convertToFxImage(finalProcessed));
                                
                                // Update processing time labels
                                originalTimeLabel.setText(String.format("Processing time: %.2f ms", finalOriginalTime / 1_000_000.0));
                                processedTimeLabel.setText(String.format("Processing time: %.2f ms", finalFilterTime / 1_000_000.0));
                            } catch (Exception e) {
                                System.out.println("Error updating UI: " + e.getMessage());
                                e.printStackTrace();
                            }
                        });

                        lastFrameTime = currentTime;
                    } else {
                        Thread.sleep(1);
                    }
                }
            } catch (Exception e) {
                System.out.println("Camera error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Camera Error");
                    alert.setHeaderText("Failed to start camera");
                    alert.setContentText("Error: " + e.getMessage() + "\n\nPlease make sure your camera is connected and not in use by another application.");
                    alert.showAndWait();
                });
            } finally {
                if (grabber != null) {
                    try {
                        System.out.println("Stopping camera...");
                        grabber.stop();
                        grabber.release();
                        System.out.println("Camera stopped successfully");
                    } catch (OpenCVFrameGrabber.Exception ex) {
                        System.out.println("Error stopping camera: " + ex.getMessage());
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

    private void loadApplicationProperties() {
        try {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(APP_PROPERTIES_PATH)),
                UTF_8
            ));
            properties.forEach((key, value) -> System.setProperty(
                String.valueOf(key),
                String.valueOf(value)
            ));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void startRecording() {
        if (isRecording) return;
        
        isRecording = true;
        recordedFrames.clear();
        
        new Thread(() -> {
            try {
                System.out.println("Starting video recording...");
                long startTime = System.currentTimeMillis();
                long endTime = startTime + 5000; // 5 seconds
                
                while (System.currentTimeMillis() < endTime) {
                    org.bytedeco.javacv.Frame frame = grabber.grab();
                    if (frame == null || frame.image == null) continue;
                    
                    Mat mat = converter.convert(frame);
                    if (mat == null || mat.empty()) continue;
                    
                    BufferedImage image = ImageUtils.matToBufferedImage(mat);
                    if (image != null) {
                        recordedFrames.add(image);
                    }
                    
                    Thread.sleep(33); // ~30 FPS
                }
                
                System.out.println("Recording completed. Processing frames...");
                processRecordedFrames();
                
            } catch (Exception e) {
                System.out.println("Recording error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isRecording = false;
                Platform.runLater(() -> {
                    var recordButton = (Button) controlsVBox.getChildren().get(4);
                    recordButton.setText("Record 5s Video");
                });
            }
        }).start();
    }

    private void processRecordedFrames() {
        if (recordedFrames.isEmpty()) return;
        
        // Get all filters except ASCII Art
        List<String> filters = filterSwitches.entrySet().stream()
            .filter(entry -> !entry.getKey().equals("ASCII Art"))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        // Reset metrics
        sequentialMetrics = new PerformanceMetrics();
        parallelMetrics = new PerformanceMetrics();
        
        // Process frames sequentially
        sequentialMetrics.totalFrames = recordedFrames.size();
        long sequentialStartTime = System.nanoTime();
        for (BufferedImage frame : recordedFrames) {
            long frameStartTime = System.nanoTime();
            BufferedImage processed = applyFiltersSequentially(frame, filters);
            sequentialMetrics.frameTimes.add((System.nanoTime() - frameStartTime) / 1_000_000); // Convert to ms
        }
        sequentialMetrics.totalTime = (System.nanoTime() - sequentialStartTime) / 1_000_000; // Convert to ms
        
        // Process frames in parallel
        parallelMetrics.totalFrames = recordedFrames.size();
        long parallelStartTime = System.nanoTime();
        for (BufferedImage frame : recordedFrames) {
            long frameStartTime = System.nanoTime();
            BufferedImage processed = applyFiltersParallel(frame, filters);
            parallelMetrics.frameTimes.add((System.nanoTime() - frameStartTime) / 1_000_000); // Convert to ms
        }
        parallelMetrics.totalTime = (System.nanoTime() - parallelStartTime) / 1_000_000; // Convert to ms
        
        // Calculate speedup
        double speedup = (double) sequentialMetrics.totalTime / parallelMetrics.totalTime;
        
        // Update metrics label
        String metrics = String.format(
            "Performance Metrics:\n\n" +
            "Sequential Processing:\n%s\n\n" +
            "Parallel Processing:\n%s\n\n" +
            "Speedup: %.2fx",
            sequentialMetrics.toString(),
            parallelMetrics.toString(),
            speedup
        );
        
        Platform.runLater(() -> {
            metricsLabel.setText(metrics);
        });
    }

    private BufferedImage applyFiltersSequentially(BufferedImage input, List<String> filters) {
        if (input == null || filters.isEmpty()) return input;
        
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int pixel = input.getRGB(x, y);
                
                for (String filter : filters) {
                    switch (filter) {
                        case "Grayscale":
                            pixel = GrayscaleFilter.apply(pixel);
                            break;
                        case "Edge Detection":
                            pixel = EdgeDetectionFilter.apply(pixel);
                            break;
                        case "Sobel Edge Detection":
                            pixel = SobelEdgeDetectionFilter.apply(input, x, y);
                            break;
                        case "Gaussian Blur":
                            pixel = GaussianBlurFilter.apply(input, x, y);
                            break;
                        case "Contrast":
                            pixel = ContrastAdjustmentFilter.apply(pixel, contrastValue);
                            break;
                    }
                }
                output.setRGB(x, y, pixel);
            }
        }
        
        return output;
    }

    private BufferedImage applyFiltersParallel(BufferedImage input, List<String> filters) {
        if (input == null || filters.isEmpty()) return input;
        
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);
        
        int threadsCount = Runtime.getRuntime().availableProcessors();
        if (threadsCount <= 0) threadsCount = 8;
        
        int height = input.getHeight();
        int chunkSize = Math.max(1, height / threadsCount);
        
        Thread[] threads = new Thread[threadsCount];
        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : Math.min(height, (i + 1) * chunkSize);
            if (startY >= endY) continue;

            threads[i] = new ThreadFilter(input, output, startY, endY, filters, contrastValue);
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
        
        return output;
    }
}
