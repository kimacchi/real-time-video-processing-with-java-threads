// src/main/java/org/realtimevideo/Main.java

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

        // Native CUDA JNI kütüphanesinin bulunduğu yeri JVM'e bildir
        System.setProperty("java.library.path", "target/classes");

        // (İsteğe bağlı) Test amaçlı: java.library.path gerçekten değişmiş mi görelim
        System.out.println("Library path: " + System.getProperty("java.library.path"));

        SwingUtilities.invokeLater(InteractiveImageProcessor::new);
    }
}