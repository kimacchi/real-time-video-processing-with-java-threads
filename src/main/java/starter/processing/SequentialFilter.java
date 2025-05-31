package starter.processing;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import starter.filters.GrayscaleFilter;
import starter.filters.ContrastAdjustmentFilter;
import starter.filters.GaussianBlurFilter;
import starter.filters.SobelEdgeDetectionFilter;
import starter.filters.EdgeDetectionFilter;
import starter.filters.ASCIIArtFilter;



public class SequentialFilter {
    private BufferedImage input;
    private BufferedImage output;
    private String operation;
    private int contrastValueParam; // Parametre adını değiştirdim

    public SequentialFilter(BufferedImage input, BufferedImage output, String operation, int contrastValue) {
        this.input = input;
        this.output = output;
        this.operation = operation;
        this.contrastValueParam = contrastValue;
    }

    public void process() {
        if ("ASCII Art".equals(operation)) {
            BufferedImage asciiImage = ASCIIArtFilter.convertToASCIIImage(input);
            Graphics2D g = output.createGraphics();
            g.drawImage(asciiImage, 0, 0, null);
            g.dispose();
        } else {
            for (int y = 0; y < input.getHeight(); y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int originalRGB = input.getRGB(x,y); // Her piksel için orijinal RGB'yi al
                    int newRGB;
                    switch (operation) {
                        case "Gaussian Blur":
                            newRGB = GaussianBlurFilter.apply(input, x, y);
                            break;
                        case "Sobel Edge Detection":
                            newRGB = SobelEdgeDetectionFilter.apply(input, x, y);
                            break;
                        case "Grayscale":
                            newRGB = GrayscaleFilter.apply(originalRGB);
                            break;
                        case "Edge Detection": // Basit eşiklemeli
                            newRGB = EdgeDetectionFilter.apply(originalRGB);
                            break;
                        case "Contrast":
                            newRGB = ContrastAdjustmentFilter.apply(originalRGB, this.contrastValueParam);
                            break;
                        default: 
                            newRGB = originalRGB;
                            break;
                    }
                    output.setRGB(x, y, newRGB); 
                }
            }
        }
    }
}