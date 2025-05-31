package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * EdgeDetectionFilter sınıfı, basit bir eşikleme yöntemiyle kenar tespiti yapar.
 * Görüntü önce gri tonlamalı yapılır, ardından parlaklık değeri bir eşik değere göre
 * siyah veya beyaza dönüştürülür.
 */
public class EdgeDetectionFilter {

    private static final int THRESHOLD = 128; // Kenar tespiti için eşik değeri

    /**
     * Belirtilen (x, y) koordinatındaki piksele basit kenar tespiti uygular.
     *
     * @param input Girdi {@link BufferedImage} nesnesi.
     * @param x Uygulanacak pikselin x koordinatı.
     * @param y Uygulanacak pikselin y koordinatı.
     * @return Kenar tespiti uygulanmış (siyah veya beyaz) pikselin RGB tam sayı değeri.
     */
    public static int apply(BufferedImage input, int x, int y) {
        Color color = new Color(input.getRGB(x, y));
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int gray = (r + g + b) / 3; // Önce gri tonlama
        int edgePixelValue = gray > THRESHOLD ? 255 : 0; // Eşikleme
        return new Color(edgePixelValue, edgePixelValue, edgePixelValue).getRGB();
    }

    /**
     * Verilen renk değerine basit kenar tespiti uygular.
     * @param rgb Orijinal renk değeri.
     * @return Kenar tespiti uygulanmış (siyah veya beyaz) renk değeri.
     */
    public static int apply(int rgb) {
        Color color = new Color(rgb);
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int gray = (r + g + b) / 3;
        int edgePixelValue = gray > THRESHOLD ? 255 : 0;
        return new Color(edgePixelValue, edgePixelValue, edgePixelValue).getRGB();
    }
}