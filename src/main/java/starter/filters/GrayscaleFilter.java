package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * GrayscaleFilter sınıfı, bir görüntüyü gri tonlamalı hale getirir.
 */
public class GrayscaleFilter {

    /**
     * Belirtilen (x, y) koordinatındaki pikseli gri tonlamalı yapar.
     * Basit ortalama metodu kullanılır (R+G+B)/3.
     *
     * @param input Girdi {@link BufferedImage} nesnesi.
     * @param x Uygulanacak pikselin x koordinatı.
     * @param y Uygulanacak pikselin y koordinatı.
     * @return Gri tonlamalı pikselin RGB tam sayı değeri.
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
     * @param rgb Orijinal renk değeri.
     * @return Gri tonlamalı renk değeri.
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