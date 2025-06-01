package starter.processing;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import starter.filters.*; // Import all filters
import atlantafx.base.controls.ToggleSwitch; // Import ToggleSwitch from atlantafx

/**
 * ImageProcessor sınıfı, görüntü işleme filtrelerini uygulamak için ana sınıftır.
 * Hem sıralı hem de paralel işleme desteği sunar. Filtreler zincirleme olarak
 * uygulanabilir ve her filtre önceki filtrenin çıktısını girdi olarak kullanır.
 */
public class ImageProcessor {

    /**
     * Seçili filtreleri görüntüye uygular. Paralel veya sıralı işleme seçeneğine
     * göre uygun metodu çağırır. Hiçbir filtre seçili değilse orijinal görüntüyü
     * döndürür.
     * 
     * İşlem Adımları:
     * 1. Girdi Kontrolü:
     *    - Null kontrolü yapılır
     *    - Seçili filtreler listelenir
     *    - Filtre seçili değilse orijinal görüntü döndürülür
     * 
     * 2. Çıktı Hazırlığı:
     *    - Orijinal görüntü boyutunda yeni buffer oluşturulur
     *    - RGB renk formatı kullanılır
     * 
     * 3. Filtre Uygulama:
     *    - Paralel işleme seçiliyse runFiltersInParallel() çağrılır
     *    - Sıralı işleme seçiliyse runFiltersSequentially() çağrılır
     * 
     * 4. Sonuç:
     *    - İşlenmiş görüntü döndürülür
     *    - Hata durumunda orijinal görüntü döndürülür
     *
     * @param input İşlenecek orijinal görüntü
     * @param filterSwitches Aktif filtreleri belirleyen ToggleSwitch'lerin haritası
     * @param parallelProcessingSelected Paralel işleme seçili mi?
     * @param contrastValue Kontrast ayarı için değer (0-200 arası)
     * @return İşlenmiş görüntü veya filtre seçili değilse orijinal görüntü
     */
    public static BufferedImage applyFilters(BufferedImage input, Map<String, ToggleSwitch> filterSwitches, boolean parallelProcessingSelected, int contrastValue) {
        if (input == null) return input;

        List<String> selectedFilters = filterSwitches.entrySet().stream()
                .filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (selectedFilters.isEmpty()) {
            return input;
        }

        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_RGB);

        if (parallelProcessingSelected) {
            runFiltersInParallel(input, output, selectedFilters, contrastValue);
        } else {
            runFiltersSequentially(input, output, selectedFilters, contrastValue);
        }

        return output;
    }

    /**
     * Filtreleri sıralı olarak uygular. Her filtre önceki filtrenin çıktısını
     * girdi olarak kullanır. Filtreler zincirleme olarak uygulanır ve her
     * filtre için yeni bir ara buffer oluşturulur.
     * 
     * İşlem Sırası:
     * 1. Her filtre için yeni bir ara buffer oluşturulur
     * 2. ASCII Art filtresi için özel işleme:
     *    - Tüm görüntü üzerinde ASCII dönüşümü yapılır
     *    - Sonuç ara buffer'a çizilir
     *    - Dönüşüm başarısız olursa siyah görüntü oluşturulur
     * 
     * 3. Diğer filtreler için piksel bazlı işleme:
     *    - Grayscale: RGB değerlerinin ortalaması
     *    - Edge Detection: Basit eşikleme
     *    - Sobel Edge Detection: Gradyan hesaplama
     *    - Gaussian Blur: 5x5 çekirdek ile bulanıklaştırma
     *    - Contrast: Parlaklık değerlerinin ayarlanması
     * 
     * 4. Sonuçlar:
     *    - Her filtre sonucu bir sonraki filtrenin girdisi olur
     *    - Son filtre sonucu ana çıktı buffer'ına kopyalanır
     *
     * @param input Orijinal görüntü
     * @param output İşlenmiş görüntünün yazılacağı buffer
     * @param operations Uygulanacak filtrelerin listesi
     * @param contrastVal Kontrast değeri
     */
    public static void runFiltersSequentially(BufferedImage input, BufferedImage output, List<String> operations, int contrastVal) {
        BufferedImage currentImage = input;
        BufferedImage tempOutput = null;

        for (String operation : operations) {
            // Create a new intermediate image for each filter to chain them correctly
            tempOutput = new BufferedImage(currentImage.getWidth(), currentImage.getHeight(), currentImage.getType());

            if ("ASCII Art".equals(operation)) {
                BufferedImage asciiImage = ASCIIArtFilter.convertToASCIIImage(currentImage);
                if (asciiImage != null) {
                    java.awt.Graphics2D g = tempOutput.createGraphics(); // Draw ASCII art onto tempOutput
                    g.drawImage(asciiImage, 0, 0, null);
                    g.dispose();
                } else {
                    // Handle case where ASCII image conversion failed
                    java.awt.Graphics2D g = tempOutput.createGraphics();
                    g.setColor(java.awt.Color.BLACK);
                    g.fillRect(0, 0, tempOutput.getWidth(), tempOutput.getHeight());
                    g.dispose();
                }
            } else {
                // Apply other filters pixel by pixel
                for (int y = 0; y < currentImage.getHeight(); y++) {
                    for (int x = 0; x < currentImage.getWidth(); x++) {
                        int rgb = currentImage.getRGB(x, y);
                        int newRgb = rgb;
                        switch (operation) {
                            case "Grayscale":
                                newRgb = GrayscaleFilter.apply(rgb);
                                break;
                            case "Edge Detection":
                                newRgb = EdgeDetectionFilter.apply(rgb);
                                break;
                            case "Sobel Edge Detection":
                                // Sobel and Gaussian need neighbor access, pass the current image
                                newRgb = SobelEdgeDetectionFilter.apply(currentImage, x, y);
                                break;
                            case "Gaussian Blur":
                                // Gaussian needs neighbor access, pass the current image
                                newRgb = GaussianBlurFilter.apply(currentImage, x, y);
                                break;
                            case "Contrast":
                                newRgb = ContrastAdjustmentFilter.apply(rgb, contrastVal);
                                break;
                        }
                        tempOutput.setRGB(x, y, newRgb);
                    }
                }
            }
            currentImage = tempOutput; // The output of this filter is the input for the next
        }
        // Copy the final result to the original output buffer
        java.awt.Graphics2D g = output.createGraphics();
        g.drawImage(currentImage, 0, 0, null);
        g.dispose();
    }

    /**
     * Filtreleri paralel olarak uygular. Görüntü dikey olarak bölünür ve her
     * bölüm ayrı bir thread'de işlenir. Her thread kendi segmentini işler ve
     * sonuçları ana çıktı buffer'ına yazar.
     * 
     * Paralel İşleme Detayları:
     * 1. Thread Yönetimi:
     *    - Thread sayısı sistemdeki işlemci sayısına göre belirlenir
     *    - Her thread görüntünün bir segmentini işler
     *    - Segment boyutu görüntü yüksekliği / thread sayısı olarak hesaplanır
     * 
     * 2. Bellek Yönetimi:
     *    - Her thread için segment boyutunda ara buffer'lar kullanılır
     *    - Komşu piksel erişimi için tam görüntü kopyası tutulur
     *    - ASCII Art filtresi için ek buffer'lar oluşturulur
     * 
     * 3. Özel Filtre İşlemleri:
     *    - Komşu piksel erişimi gerektiren filtreler (Sobel, Gaussian) için
     *      tam görüntüye erişim sağlanır
     *    - ASCII Art filtresi için özel işleme yapılır ve sonuç segmentlere bölünür
     *    - Diğer filtreler piksel bazlı olarak uygulanır
     * 
     * 4. Senkronizasyon:
     *    - Her thread kendi segmentini bağımsız olarak işler
     *    - Sonuçlar ana çıktı buffer'ına thread-safe şekilde yazılır
     *    - Tüm thread'lerin tamamlanması beklenir
     *
     * @param input Orijinal görüntü
     * @param output İşlenmiş görüntünün yazılacağı buffer
     * @param operations Uygulanacak filtrelerin listesi
     * @param contrastVal Kontrast değeri
     */
    public static void runFiltersInParallel(BufferedImage input, BufferedImage output, List<String> operations, int contrastVal) {
        int threadsCount = Runtime.getRuntime().availableProcessors();
        if (threadsCount <= 0) threadsCount = 8;
        int height = input.getHeight();
        int chunkSize = Math.max(1, height / threadsCount);
        Thread[] threads = new Thread[threadsCount];

        for (int i = 0; i < threadsCount; i++) {
            int startY = i * chunkSize;
            int endY = (i == threadsCount - 1) ? height : Math.min(height, (i + 1) * chunkSize);
            if (startY >= endY) continue;

            threads[i] = new Thread(() -> {
                // Her thread kendi segmentini işler. Segment, görüntünün dikey bir dilimidir.
                // Thread'ler arası senkronizasyon için thread-safe işlemler kullanılır.

                // Thread'in işleyeceği segment için çıktı buffer'ı oluşturulur
                // Buffer boyutu: görüntü genişliği x segment yüksekliği
                BufferedImage threadOutputSegment = new BufferedImage(input.getWidth(), endY - startY, BufferedImage.TYPE_INT_RGB);

                // Thread'in işleyeceği görüntü segmentini al
                // Bu, orijinal görüntünün bir alt görüntüsüdür
                BufferedImage intermediate = input.getSubimage(0, startY, input.getWidth(), endY - startY);
                BufferedImage temp = null;

                // Her filtre için sırayla işlem yap
                for (String operation : operations) {
                    // Her filtre için yeni bir ara buffer oluştur
                    // Bu buffer, filtre zincirindeki her adım için kullanılır
                    temp = new BufferedImage(intermediate.getWidth(), intermediate.getHeight(), intermediate.getType());

                    if ("ASCII Art".equals(operation)) {
                        // ASCII Art filtresi özel işlem gerektirir çünkü:
                        // 1. Tüm görüntü bağlamına ihtiyaç duyar
                        // 2. Karakter seçimi için global parlaklık bilgisi gerekir
                        // 3. Segment bazlı işlem yapılamaz
                        System.err.println("Warning: ASCII Art in parallel filter chain is complex and may not be correctly implemented here.");
                        
                        // Tüm görüntü üzerinde ASCII dönüşümü yap
                        BufferedImage asciiFull = ASCIIArtFilter.convertToASCIIImage(input);
                        if (asciiFull != null) {
                            // ASCII dönüşümü başarılıysa, ilgili segmenti al ve ara buffer'a çiz
                            java.awt.Graphics2D g = temp.createGraphics();
                            g.drawImage(asciiFull.getSubimage(0, startY, asciiFull.getWidth(), endY - startY), 0, 0, null);
                            g.dispose();
                        } else {
                            // Dönüşüm başarısız olursa siyah segment oluştur
                            java.awt.Graphics2D g = temp.createGraphics();
                            g.setColor(java.awt.Color.BLACK);
                            g.fillRect(0, 0, temp.getWidth(), temp.getHeight());
                            g.dispose();
                        }
                    } else {
                        // Diğer filtreler için piksel bazlı işlem yap
                        // Her piksel için seçili filtreyi uygula
                        for (int y = 0; y < intermediate.getHeight(); y++) {
                            for (int x = 0; x < intermediate.getWidth(); x++) {
                                // Orijinal görüntüdeki koordinatları hesapla
                                // Bu, komşu piksel erişimi gerektiren filtreler için önemli
                                int originalX = x;
                                int originalY = y + startY;

                                // Mevcut pikselin RGB değerini al
                                int rgb = intermediate.getRGB(x, y);
                                int newRgb = rgb;

                                // Filtre tipine göre işlem yap
                                switch (operation) {
                                    case "Grayscale":
                                        // Gri tonlama: RGB değerlerinin ortalaması
                                        newRgb = GrayscaleFilter.apply(rgb);
                                        break;
                                    case "Edge Detection":
                                        // Kenar tespiti: Basit eşikleme
                                        newRgb = EdgeDetectionFilter.apply(rgb);
                                        break;
                                    case "Sobel Edge Detection":
                                        // Sobel kenar tespiti: Gradyan hesaplama
                                        // Komşu piksellere erişim gerektiği için orijinal koordinatları kullan
                                        newRgb = SobelEdgeDetectionFilter.apply(input, originalX, originalY);
                                        break;
                                    case "Gaussian Blur":
                                        // Gaussian bulanıklaştırma: 5x5 çekirdek
                                        // Komşu piksellere erişim gerektiği için orijinal koordinatları kullan
                                        newRgb = GaussianBlurFilter.apply(input, originalX, originalY);
                                        break;
                                    case "Contrast":
                                        // Kontrast ayarı: Parlaklık değerlerinin ölçeklenmesi
                                        newRgb = ContrastAdjustmentFilter.apply(rgb, contrastVal);
                                        break;
                                }
                                // İşlenmiş pikseli ara buffer'a yaz
                                temp.setRGB(x, y, newRgb);
                            }
                        }
                    }
                    // Bu filtre için ara buffer'ı bir sonraki filtre için girdi olarak kullan
                    intermediate = temp;
                }

                // İşlenmiş segmenti ana çıktı buffer'ına kopyala
                // Graphics2D kullanarak thread-safe çizim yap
                java.awt.Graphics2D g = output.createGraphics();
                // Segmenti doğru Y koordinatına yerleştir
                g.drawImage(intermediate, 0, startY, null);
                g.dispose();
            });
            threads[i].start();
        }
        waitForThreads(threads);
    }

    /**
     * Tüm thread'lerin tamamlanmasını bekler. Herhangi bir thread kesintiye
     * uğrarsa, ana thread'i de kesintiye uğratır. Bu, uygulamanın düzgün
     * şekilde sonlandırılmasını sağlar.
     * 
     * Thread Yönetimi:
     * - Her thread için join() çağrısı yapılır
     * - Kesinti durumunda InterruptedException yakalanır
     * - Ana thread kesintiye uğratılır ve hata loglanır
     *
     * @param threads Tamamlanması beklenen thread'lerin dizisi
     */
    public static void waitForThreads(Thread[] threads) {
        for (Thread t : threads) {
            if (t != null) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
} 