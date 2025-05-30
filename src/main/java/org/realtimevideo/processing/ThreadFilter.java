// src/main/java/org/realtimevideo/processing/ThreadFilter.java


package org.realtimevideo.processing;

import java.awt.image.BufferedImage;

import org.realtimevideo.filters.ASCIIArtFilter;
import org.realtimevideo.filters.ContrastAdjustmentFilter; // Yeni
import org.realtimevideo.filters.EdgeDetectionFilter;    // Yeni
import org.realtimevideo.filters.GaussianBlurFilter;
import org.realtimevideo.filters.GrayscaleFilter;        // Yeni
import org.realtimevideo.filters.SobelEdgeDetectionFilter;

public class ThreadFilter extends Thread {
    private BufferedImage input;
    private BufferedImage output;
    private int startY, endY;
    private String operation;
    private int contrastValueParam; // Parametre adını değiştirdim, çünkü contrastValue bir sınıf alanı değil

    public ThreadFilter(BufferedImage input, BufferedImage output, int startY, int endY, String operation, int contrastValue) {
        this.input = input;
        this.output = output;
        this.startY = startY;
        this.endY = endY;
        this.operation = operation;
        this.contrastValueParam = contrastValue;
    }

    @Override
    public void run() {
        if ("ASCII Art".equals(operation)) {
            BufferedImage asciiImage = ASCIIArtFilter.convertToASCIIImage(input);
            for (int y = startY; y < endY; y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                     if (x < asciiImage.getWidth() && y < asciiImage.getHeight()) {
                         output.setRGB(x, y, asciiImage.getRGB(x, y));
                    }
                }
            }
        } else {
            for (int y = startY; y < endY; y++) {
                for (int x = 0; x < input.getWidth(); x++) {
                    int originalRGB = input.getRGB(x,y); // Her piksel için orijinal RGB'yi al
                    int newRGB;
                    switch (operation) {
                        case "Gaussian Blur":
                            newRGB = GaussianBlurFilter.apply(input, x, y); // input, x, y alır
                            break;
                        case "Sobel Edge Detection":
                            newRGB = SobelEdgeDetectionFilter.apply(input, x, y); // input, x, y alır
                            break;
                        case "Grayscale":
                            newRGB = GrayscaleFilter.apply(originalRGB); // sadece rgb alır
                            break;
                        case "Edge Detection": // Basit eşiklemeli
                            newRGB = EdgeDetectionFilter.apply(originalRGB); // sadece rgb alır
                            break;
                        case "Contrast":
                            newRGB = ContrastAdjustmentFilter.apply(originalRGB, this.contrastValueParam); // rgb ve contrastValue alır
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