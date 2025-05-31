package starter.processing;

import java.awt.image.BufferedImage;
import java.util.List;
import starter.filters.*;

public class ThreadFilter extends Thread {
    private final BufferedImage input;
    private final BufferedImage output;
    private final int startY;
    private final int endY;
    private final List<String> operations;
    private final int contrastValue;

    public ThreadFilter(BufferedImage input, BufferedImage output, int startY, int endY, List<String> operations, int contrastValue) {
        this.input = input;
        this.output = output;
        this.startY = startY;
        this.endY = endY;
        this.operations = operations;
        this.contrastValue = contrastValue;
    }

    @Override
    public void run() {
        for (int y = startY; y < endY; y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int rgb = input.getRGB(x, y);
                
                // Apply each selected filter in sequence
                for (String operation : operations) {
                    switch (operation) {
                        case "Grayscale":
                            rgb = GrayscaleFilter.apply(rgb);
                            break;
                        case "Edge Detection":
                            rgb = EdgeDetectionFilter.apply(rgb);
                            break;
                        case "Sobel Edge Detection":
                            rgb = SobelEdgeDetectionFilter.apply(input, x, y);
                            break;
                        case "Gaussian Blur":
                            rgb = GaussianBlurFilter.apply(input, x, y);
                            break;
                        case "Contrast":
                            rgb = ContrastAdjustmentFilter.apply(rgb, contrastValue);
                            break;
                        case "ASCII Art":
                            // ASCII Art is handled differently as it needs the whole image
                            if (operations.contains("ASCII Art")) {
                                BufferedImage asciiImage = ASCIIArtFilter.convertToASCIIImage(input);
                                if (x < asciiImage.getWidth() && y < asciiImage.getHeight()) {
                                    rgb = asciiImage.getRGB(x, y);
                                }
                            }
                            break;
                    }
                }
                
                output.setRGB(x, y, rgb);
            }
        }
    }
}