// src/main/java/starter/filters/GaussianBlurFilter.java

package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * GaussianBlurFilter sınıfı, bir görüntüye Gaussian bulanıklaştırma uygular.
 * 5x5 Gaussian çekirdeği kullanarak her pikselin değerini komşu piksellerin
 * ağırlıklı ortalaması olarak hesaplar. Bu, görüntüdeki gürültüyü azaltır
 * ve yumuşak geçişler sağlar.
 */
public class GaussianBlurFilter {
    // 5x5 Gaussian çekirdeği
    private static final double[][] KERNEL = {
        {0.003, 0.013, 0.022, 0.013, 0.003},
        {0.013, 0.059, 0.097, 0.059, 0.013},
        {0.022, 0.097, 0.159, 0.097, 0.022},
        {0.013, 0.059, 0.097, 0.059, 0.013},
        {0.003, 0.013, 0.022, 0.013, 0.003}
    };

    /**
     * Belirtilen (x, y) koordinatındaki piksele 5x5 Gaussian çekirdeğini uygular.
     * Her piksel için, 5x5'lik komşuluk bölgesindeki piksellerin ağırlıklı
     * ortalaması hesaplanır. Görüntü sınırlarında, sınır dışındaki pikseller
     * için en yakın geçerli piksel değeri kullanılır.
     *
     * @param input Girdi görüntüsü
     * @param x İşlenecek pikselin x koordinatı
     * @param y İşlenecek pikselin y koordinatı
     * @return Gaussian bulanıklaştırma uygulanmış pikselin RGB değeri
     */
    public static int apply(BufferedImage input, int x, int y) {
        double sumR = 0, sumG = 0, sumB = 0;
        
        for (int ky = -2; ky <= 2; ky++) {
            for (int kx = -2; kx <= 2; kx++) {
                int pixelX = Math.min(Math.max(x + kx, 0), input.getWidth() - 1);
                int pixelY = Math.min(Math.max(y + ky, 0), input.getHeight() - 1);
                
                Color color = new Color(input.getRGB(pixelX, pixelY));
                double weight = KERNEL[ky + 2][kx + 2];
                
                sumR += color.getRed() * weight;
                sumG += color.getGreen() * weight;
                sumB += color.getBlue() * weight;
            }
        }
        
        return new Color(
            (int) Math.min(255, Math.max(0, sumR)),
            (int) Math.min(255, Math.max(0, sumG)),
            (int) Math.min(255, Math.max(0, sumB))
        ).getRGB();
    }
}