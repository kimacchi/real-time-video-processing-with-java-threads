// src/main/java/starter/filters/EdgeDetectionFilter.java

package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * EdgeDetectionFilter sınıfı, basit bir eşikleme yöntemiyle kenar tespiti yapar.
 * Görüntü önce gri tonlamalı yapılır, ardından parlaklık değeri bir eşik
 * değere göre siyah veya beyaza dönüştürülür. Bu, görüntüdeki ani parlaklık
 * değişimlerini belirgin hale getirir.
 */
public class EdgeDetectionFilter {

    // Kenar tespiti için eşik değeri (0-255 arası)
    private static final int THRESHOLD = 128;

    /**
     * Belirtilen (x, y) koordinatındaki piksele basit kenar tespiti uygular.
     * Önce piksel gri tonlamalı yapılır, sonra parlaklık değeri eşik değere
     * göre siyah (0) veya beyaz (255) olarak belirlenir.
     *
     * @param input Girdi görüntüsü
     * @param x İşlenecek pikselin x koordinatı
     * @param y İşlenecek pikselin y koordinatı
     * @return Kenar tespiti uygulanmış pikselin RGB değeri (siyah veya beyaz)
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
     * Önce renk gri tonlamalı yapılır, sonra parlaklık değeri eşik değere
     * göre siyah (0) veya beyaz (255) olarak belirlenir.
     *
     * @param rgb Orijinal renk değeri
     * @return Kenar tespiti uygulanmış renk değeri (siyah veya beyaz)
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