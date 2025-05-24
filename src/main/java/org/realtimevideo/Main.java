package org.realtimevideo;

import javax.swing.SwingUtilities;

public class Main {
    /**
     * Uygulamanın ana giriş noktası.
     * {@link InteractiveImageProcessor} sınıfından bir nesne oluşturarak GUI'yi başlatır.
     * GUI işlemleri Swing'in Event Dispatch Thread (EDT) üzerinde güvenli bir şekilde
     * çalıştırılması için {@link SwingUtilities#invokeLater(Runnable)} kullanılır.
     *
     * @param args Komut satırı argümanları (bu uygulamada kullanılmaz).
     */
    public static void main(String[] args) {
        // GUI'yi Event Dispatch Thread üzerinde başlat
        SwingUtilities.invokeLater(InteractiveImageProcessor::new);
    }
}