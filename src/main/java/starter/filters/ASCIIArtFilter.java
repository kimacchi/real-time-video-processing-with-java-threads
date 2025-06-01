// src/main/java/starter/filters/ASCIIArtFilter.java

package starter.filters; // Paket adını projenize göre güncelleyin

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * ASCIIArtFilter sınıfı, bir görüntüyü ASCII karakterleri kullanarak
 * metin tabanlı bir sanata dönüştürür. Görüntüyü küçük bloklara böler
 * ve her bloğun ortalama parlaklığına göre uygun bir ASCII karakteri
 * seçer. Bu, görüntünün metin tabanlı bir temsilini oluşturur.
 */
public class ASCIIArtFilter {
    // Parlaklık değerlerine göre ASCII karakterleri (koyudan açığa)
    private static final String ASCII_CHARS = " .'`^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$";
    // Her ASCII karakteri için piksel genişliği
    private static final int CHAR_WIDTH = 4;
    // Her ASCII karakteri için piksel yüksekliği
    private static final int CHAR_HEIGHT = 8;

    /**
     * Verilen bir görüntüyü ASCII sanatına dönüştürür. Görüntüyü CHAR_WIDTH x CHAR_HEIGHT
     * boyutundaki bloklara böler ve her bloğun ortalama parlaklığına göre uygun bir
     * ASCII karakteri seçer. Karakterler, orijinal görüntüyle aynı boyutta yeni bir
     * görüntü üzerine çizilir.
     *
     * @param input Dönüştürülecek orijinal görüntü
     * @return ASCII karakterlerinden oluşan, orijinal görüntüyle aynı boyutlarda
     *         yeni bir görüntü. Girdi null ise veya çok küçükse, siyah bir görüntü döndürür.
     */
    public static BufferedImage convertToASCIIImage(BufferedImage input) {
        if (input == null) {
            System.err.println("ASCIIArtFilter (Sequential): Input image is null.");
            return null; // veya boş bir görüntü
        }

        int width = input.getWidth();
        int height = input.getHeight();

        int asciiWidth = width / CHAR_WIDTH;
        int asciiHeight = height / CHAR_HEIGHT;

        if (asciiWidth == 0 || asciiHeight == 0) {
            System.err.println("ASCIIArtFilter (Sequential): Image is too small for ASCII conversion.");
            // Uygun bir çıktı döndür, örneğin boş siyah bir görüntü
            BufferedImage emptyOutput = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = emptyOutput.createGraphics();
            g.setColor(Color.BLACK);
            g.fillRect(0,0, width, height);
            g.dispose();
            return emptyOutput;
        }

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = output.createGraphics();
        g2d.setColor(Color.BLACK);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Monospaced", Font.PLAIN, CHAR_HEIGHT));

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        for (int yAscii = 0; yAscii < asciiHeight; yAscii++) {
            for (int xAscii = 0; xAscii < asciiWidth; xAscii++) {
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

                // Karakteri dikeyde biraz daha iyi konumlandırmak için CHAR_HEIGHT - (CHAR_HEIGHT / 5) kullanılıyor.
                g2d.drawString(String.valueOf(asciiChar),
                        xAscii * CHAR_WIDTH,
                        (yAscii * CHAR_HEIGHT) + CHAR_HEIGHT - (CHAR_HEIGHT / 5));
            }
        }

        g2d.dispose();
        return output;
    }
}