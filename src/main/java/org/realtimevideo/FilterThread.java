package org.realtimevideo;

import java.awt.*;
import java.awt.image.BufferedImage;

public class FilterThread extends Thread {
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
