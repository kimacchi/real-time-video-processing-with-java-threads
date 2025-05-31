package starter.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Görüntü işleme performans metriklerini (toplam kare sayısı, toplam süre,
 * ortalama, min, maks kare işleme süresi ve FPS) depolamak ve formatlamak için
 * kullanılan bir sınıftır.
 */
public class PerformanceMetrics {
    public List<Long> frameTimes = new ArrayList<>();
    public long totalTime;
    public int totalFrames;

    /**
     * Toplanan performans metriklerini okunabilir bir string formatında döndürür.
     * Eğer hiç kare işlenmemişse, uygun bir mesaj döndürür.
     *
     * @return Performans metriklerinin string temsili.
     */
    @Override
    public String toString() {
        if (frameTimes.isEmpty()) return "No frames processed";
        
        long min = Collections.min(frameTimes);
        long max = Collections.max(frameTimes);
        double avg = frameTimes.stream().mapToLong(Long::longValue).average().orElse(0);
        double fps = (totalFrames > 0 && totalTime > 0) ? (totalFrames * 1000.0) / totalTime : 0;
        
        return String.format(
            "Total Frames: %d\n" +
            "Total Time: %d ms\n" +
            "Average Frame Time: %.2f ms\n" +
            "Min Frame Time: %d ms\n" +
            "Max Frame Time: %d ms\n" +
            "FPS: %.2f",
            totalFrames,
            totalTime,
            avg,
            min,
            max,
            fps
        );
    }
}