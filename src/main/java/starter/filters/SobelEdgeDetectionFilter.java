package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * SobelEdgeDetectionFilter sınıfı, bir görüntüde Sobel operatörlerini kullanarak kenar tespiti yapar.
 */
public class SobelEdgeDetectionFilter {
    private static final int[][] SOBEL_X = {
        {-1, 0, 1},
        {-2, 0, 2},
        {-1, 0, 1}
    };

    private static final int[][] SOBEL_Y = {
        {-1, -2, -1},
        {0, 0, 0},
        {1, 2, 1}
    };

    /**
     * Belirtilen (x, y) koordinatındaki piksele Sobel operatörlerini uygular.
     *
     * @param input Girdi {@link BufferedImage} nesnesi.
     * @param x Uygulanacak pikselin x koordinatı.
     * @param y Uygulanacak pikselin y koordinatı.
     * @return Sobel kenar tespiti uygulanmış pikselin (gradyan büyüklüğü) RGB tam sayı değeri.
     */
    public static int apply(BufferedImage input, int x, int y) {
        int gx = 0;
        int gy = 0;

        for (int ky = -1; ky <= 1; ky++) {
            for (int kx = -1; kx <= 1; kx++) {
                int pixelX = Math.min(Math.max(x + kx, 0), input.getWidth() - 1);
                int pixelY = Math.min(Math.max(y + ky, 0), input.getHeight() - 1);
                
                Color color = new Color(input.getRGB(pixelX, pixelY));
                int gray = (color.getRed() + color.getGreen() + color.getBlue()) / 3;
                
                gx += gray * SOBEL_X[ky + 1][kx + 1];
                gy += gray * SOBEL_Y[ky + 1][kx + 1];
            }
        }

        int magnitude = (int) Math.sqrt(gx * gx + gy * gy);
        magnitude = Math.min(255, Math.max(0, magnitude));

        return new Color(magnitude, magnitude, magnitude).getRGB();
    }
}