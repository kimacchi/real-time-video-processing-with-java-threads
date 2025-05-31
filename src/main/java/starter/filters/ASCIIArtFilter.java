package starter.filters;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ASCIIArtFilter sınıfı, bir {@link BufferedImage}'ı metin tabanlı bir ASCII sanat
 * temsiline dönüştürür.
 */
public class ASCIIArtFilter {
    private static final String ASCII_CHARS = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    private static final int CHAR_WIDTH = 4;
    private static final int CHAR_HEIGHT = 8;
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    /**
     * Verilen bir {@link BufferedImage}'ı ASCII sanatına dönüştürür.
     * Paralel işleme kullanarak performansı artırır.
     *
     * @param input Dönüştürülecek orijinal {@link BufferedImage}.
     * @return ASCII karakterlerinden oluşan, orijinal görüntüyle aynı boyutlarda yeni bir {@link BufferedImage}.
     */
    public static BufferedImage convertToASCIIImage(BufferedImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        
        int asciiWidth = width / CHAR_WIDTH;
        int asciiHeight = height / CHAR_HEIGHT;
        
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();
        g2d.setColor(Color.BLACK); 
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE); 
        g2d.setFont(new Font("Monospaced", Font.PLAIN, CHAR_HEIGHT)); 
        
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Create a semaphore to synchronize Graphics2D operations
        Semaphore graphicsSemaphore = new Semaphore(1);
        
        // Create thread pool
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREADS);
        
        // Calculate chunk size for parallel processing
        int chunkSize = Math.max(1, asciiHeight / MAX_THREADS);
        
        // Process chunks in parallel
        for (int chunk = 0; chunk < asciiHeight; chunk += chunkSize) {
            final int startY = chunk;
            final int endY = Math.min(chunk + chunkSize, asciiHeight);
            
            executor.submit(() -> {
                try {
                    for (int yAscii = startY; yAscii < endY; yAscii++) {
                        for (int xAscii = 0; xAscii < asciiWidth; xAscii++) {
                            // Calculate brightness for this character block
                            long totalBrightness = 0;
                            int pixelCount = 0;
                            
                            for (int py = yAscii * CHAR_HEIGHT; py < (yAscii + 1) * CHAR_HEIGHT && py < height; py++) {
                                for (int px = xAscii * CHAR_WIDTH; px < (xAscii + 1) * CHAR_WIDTH && px < width; px++) {
                                    Color color = new Color(input.getRGB(px, py));
                                    totalBrightness += (color.getRed() + color.getGreen() + color.getBlue());
                                    pixelCount++;
                                }
                            }
                            
                            int avgBrightness = 0;
                            if (pixelCount > 0) {
                                avgBrightness = (int) (totalBrightness / (pixelCount * 3.0));
                            }
                            
                            int charIndex = (int) ((avgBrightness / 255.0) * (ASCII_CHARS.length() - 1));
                            char asciiChar = ASCII_CHARS.charAt(charIndex);
                            
                            // Acquire semaphore before drawing
                            graphicsSemaphore.acquire();
                            try {
                                g2d.drawString(String.valueOf(asciiChar), 
                                    xAscii * CHAR_WIDTH, 
                                    (yAscii * CHAR_HEIGHT) + CHAR_HEIGHT - (CHAR_HEIGHT / 5));
                            } finally {
                                graphicsSemaphore.release();
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        
        // Wait for all tasks to complete
        executor.shutdown();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        g2d.dispose();
        return output;
    }
}