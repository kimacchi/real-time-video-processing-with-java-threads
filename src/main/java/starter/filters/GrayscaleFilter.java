// src/main/java/starter/filters/GrayscaleFilter.java

package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * GrayscaleFilter sınıfı, bir görüntüyü gri tonlamalı hale getirir.
 * Her pikselin RGB değerlerinin ortalaması alınarak gri ton değeri
 * hesaplanır. Bu, görüntüyü tek kanallı (gri ton) hale getirir.
 */
public class GrayscaleFilter {

    /**
     * Belirtilen (x, y) koordinatındaki pikseli gri tonlamalı yapar.
     * Pikselin RGB değerlerinin ortalaması alınarak gri ton değeri
     * hesaplanır. Bu değer, yeni bir renk nesnesi oluşturmak için
     * kullanılır.
     *
     * @param input Girdi görüntüsü
     * @param x İşlenecek pikselin x koordinatı
     * @param y İşlenecek pikselin y koordinatı
     * @return Gri tonlamalı pikselin RGB değeri
     */
    public static int apply(BufferedImage input, int x, int y) {
        Color color = new Color(input.getRGB(x, y));
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int gray = (r + g + b) / 3;
        return new Color(gray, gray, gray).getRGB();
    }

    /**
     * Verilen renk değerini gri tonlamalı yapar.
     * RGB değerlerinin ortalaması alınarak gri ton değeri hesaplanır.
     * Bu değer, yeni bir renk nesnesi oluşturmak için kullanılır.
     *
     * @param rgb Orijinal renk değeri
     * @return Gri tonlamalı renk değeri
     */
    public static int apply(int rgb) {
        Color color = new Color(rgb);
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int gray = (r + g + b) / 3;
        return new Color(gray, gray, gray).getRGB();
    }
}