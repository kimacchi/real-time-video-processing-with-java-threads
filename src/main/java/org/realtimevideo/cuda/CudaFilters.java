package org.realtimevideo.cuda;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;

@Properties(
        target = "org.realtimevideo.cuda.CudaFilters", // JNI sarmalayıcı sınıfının adı
        value = @Platform(
                // Eğer pom.xml'de <buildCommand> kullanıyorsanız,
                // buradaki compiler, include, link gibi direktifler
                // o buildCommand tarafından geçersiz kılınır.
                // Ancak 'library' parametresi Loader.load() için hala önemlidir.
                compiler = "nvcc", // Bu satır <buildCommand> varken gereksiz olabilir
                include = "CudaFilters.cu", // Bu satır <buildCommand> varken gereksiz olabilir
                link = {"cudart"}, // Bu satır <buildCommand> varken gereksiz olabilir
                library = "jniCudaFilters" // BU ÇOK ÖNEMLİ! Loader.load()'un arayacağı kütüphane adı.
        )
)
public class CudaFilters {

    public static native void applySetFirstChannelToZeroCuda(BytePointer data, int width, int height, int channels);

    static {
        try {
            // Bu çağrı, classpath'ten (target/classes/.../linux-x86_64/libjniCudaFilters.so)
            // kütüphaneyi bulup yüklemeye çalışır.
            Loader.load();
        } catch (UnsatisfiedLinkError e) {
            System.err.println("FATAL: Native CUDA library (jniCudaFilters) failed to load from CudaFilters.java static block.");
            System.err.println("Ensure that 'libjniCudaFilters.so' exists in the correct classpath location (e.g., target/classes/org/realtimevideo/cuda/linux-x86_64/).");
            System.err.println("Check LD_LIBRARY_PATH if the .so file has unresolved dependencies (use 'ldd libjniCudaFilters.so' to check).");
            System.err.println("Original error: " + e.getMessage());
            e.printStackTrace(); // Hatayı daha detaylı görmek için
            // Uygulamanın burada çökmesi veya bir hata durumu bildirmesi iyi olur.
            // System.exit(1); // veya
            // throw new RuntimeException("Failed to load native CUDA library", e);
        }
    }
}