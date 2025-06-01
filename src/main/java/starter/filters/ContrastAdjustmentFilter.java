// src/main/java/starter/filters/ContrastAdjustmentFilter.java

package starter.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * ContrastAdjustmentFilter sınıfı, bir görüntünün kontrastını ayarlar.
 * Kontrast değeri, görüntüdeki parlaklık değerlerinin dağılımını
 * etkiler. Yüksek kontrast, parlak ve koyu bölgeler arasındaki
 * farkı artırırken, düşük kontrast bu farkı azaltır.
 */
public class ContrastAdjustmentFilter {

    /**
     * Belirtilen (x, y) koordinatındaki pikselin kontrastını ayarlar.
     * Kontrast değeri, pikselin RGB bileşenlerinin parlaklık değerlerini
     * etkiler. 100 değeri nötr (kontrast değişikliği yok) anlamına gelir.
     *
     * @param input Girdi görüntüsü
     * @param x İşlenecek pikselin x koordinatı
     * @param y İşlenecek pikselin y koordinatı
     * @param contrastValue Kontrast değeri (0-200 arası, 100 nötr)
     * @return Kontrastı ayarlanmış pikselin RGB değeri
     */
    public static int apply(BufferedImage input, int x, int y, int contrastValue) {
        Color color = new Color(input.getRGB(x, y));
        return applyContrast(color.getRed(), color.getGreen(), color.getBlue(), contrastValue);
    }

    /**
     * Verilen renk değerinin kontrastını ayarlar.
     * Kontrast değeri, rengin RGB bileşenlerinin parlaklık değerlerini
     * etkiler. 100 değeri nötr (kontrast değişikliği yok) anlamına gelir.
     *
     * @param rgb Orijinal renk değeri
     * @param contrastValue Kontrast değeri (0-200 arası, 100 nötr)
     * @return Kontrastı ayarlanmış renk değeri
     */
    public static int apply(int rgb, int contrastValue) {
        Color color = new Color(rgb);
        return applyContrast(color.getRed(), color.getGreen(), color.getBlue(), contrastValue);
    }

    /**
     * RGB bileşenlerine kontrast ayarı uygular.
     * Her bileşen için parlaklık değeri, kontrast değerine göre
     * ayarlanır. 100 değeri nötr (kontrast değişikliği yok) anlamına gelir.
     *
     * @param r Kırmızı bileşen (0-255)
     * @param g Yeşil bileşen (0-255)
     * @param b Mavi bileşen (0-255)
     * @param contrastValue Kontrast değeri (0-200 arası, 100 nötr)
     * @return Kontrastı ayarlanmış rengin RGB değeri
     */
    private static int applyContrast(int r, int g, int b, int contrastValue) {
        // contrastValue (0-200), 100 nötr.
        // Formül için -128 ile +127 arasında bir değere map edebiliriz veya direkt formülü adapte edebiliriz.
        // Önceki gibi -100 ile +100 arasına map edelim.
        double adjustedContrast = contrastValue - 100.0; // Şimdi -100.0 ile +100.0 arasında

        // Standart kontrast formülü: factor = (259 * (C + 255)) / (255 * (259 - C))
        // Burada C -255 ile 255 arasında olmalı.
        // adjustedContrast'ı bu aralığa ölçekleyebiliriz. Ya da daha basit bir yaklaşım.
        // Daha basit bir yaklaşım (Photoshop benzeri):
        // Eğer adjustedContrast > 0: C = adjustedContrast / 100.0; F = 1/(1-C) -1 ; (Bu F'yi kullanmak karmaşık)
        // Wikipedia'daki formül:
        double factor = (259.0 * (adjustedContrast + 255.0)) / (255.0 * (259.0 - adjustedContrast));

        int newR = (int) (factor * (r - 128) + 128);
        int newG = (int) (factor * (g - 128) + 128);
        int newB = (int) (factor * (b - 128) + 128);

        newR = Math.min(255, Math.max(0, newR));
        newG = Math.min(255, Math.max(0, newG));
        newB = Math.min(255, Math.max(0, newB));

        return new Color(newR, newG, newB).getRGB();
    }
}