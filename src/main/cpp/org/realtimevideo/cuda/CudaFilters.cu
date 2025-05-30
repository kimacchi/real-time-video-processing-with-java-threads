// src/main/java/org/realtimevideo/cuda/CudaFilters.cu
#include <cuda_runtime.h>
#include <stdio.h> // printf gibi fonksiyonlar için

// CUDA Kernel: Her pikselin ilk bileşenini (BGR formatında Mavi) sıfırlar.
// Eğer RGB ise Kırmızı'yı sıfırlar. OpenCV Mat genellikle BGR'dir.
// BufferedImage TYPE_3BYTE_BGR veya TYPE_INT_RGB olabilir.
// Bu örnekte, her 3 byte'lık grubun ilk byte'ını (B veya R) hedefliyoruz.
__global__ void setFirstChannelToZeroKernel(unsigned char* data, int width, int height, int channels) {
    int x = blockIdx.x * blockDim.x + threadIdx.x;
    int y = blockIdx.y * blockDim.y + threadIdx.y;

    if (x < width && y < height) {
        // Görüntü verisi tek boyutlu bir dizi olduğu için pikselin başlangıç indeksini hesapla
        // Her piksel 'channels' kadar byte kaplar (örn: BGR için 3 byte)
        int index = (y * width + x) * channels;
        if (channels >= 3) { // En az 3 kanal varsa (örn: BGR, RGB)
            data[index + 0] = 0; // İlk kanalı (B veya R) sıfırla
                                 // Eğer OpenCV Mat (BGR) ise bu Mavi kanal olur.
                                 // Eğer BufferedImage TYPE_INT_RGB ise ve byte dizisine dönüştürülmüşse, bu Kırmızı olabilir.
                                 // Java tarafındaki veri hazırlığına bağlı.
                                 // Şu anki varsayımımız, Java'dan gelen BytePointer'ın BGR sıralamasında olduğu.
        } else if (channels == 1) { // Gri tonlamalı ise (bu kernel için pek anlamlı değil ama örnek)
            // data[index] = 0; // Gri tonlamalıda tek kanal var, onu sıfırla
        }
    }
}

// Bu fonksiyon Java'dan çağrılacak
extern "C" {
    // Java'daki BytePointer (temelde signed char*) ile eşleşmesi için signed char* kullanıyoruz.
    // Bu pointer hem girdi hem de çıktı verisini taşır.
    void applySetFirstChannelToZeroCuda(signed char* h_input_output_data, int width, int height, int channels) {
        // Kernel ve CUDA bellek işlemleri için unsigned char* kullanacağız, çünkü görüntü verisi genellikle bu şekilde işlenir.
        unsigned char* uc_pixel_data_cpu = reinterpret_cast<unsigned char*>(h_input_output_data);

        unsigned char* d_pixel_data_gpu; // GPU'daki piksel verisi için bellek pointer'ı

        // Bellek boyutunu hesaplarken olası taşmaları önlemek için size_t kullan
        size_t image_size_in_bytes = static_cast<size_t>(width) * height * channels * sizeof(unsigned char);

        cudaError_t err; // CUDA API çağrılarının durumunu tutmak için

        // 1. GPU'da bellek ayır
        err = cudaMalloc((void**)&d_pixel_data_gpu, image_size_in_bytes);
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Malloc Error (%d): %s\n", err, cudaGetErrorString(err));
            return; // Hata durumunda fonksiyondan çık
        }

        // 2. Veriyi CPU'dan (uc_pixel_data_cpu) GPU'ya (d_pixel_data_gpu) kopyala
        err = cudaMemcpy(d_pixel_data_gpu, uc_pixel_data_cpu, image_size_in_bytes, cudaMemcpyHostToDevice);
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Memcpy Host to Device Error (%d): %s\n", err, cudaGetErrorString(err));
            cudaFree(d_pixel_data_gpu); // Ayrılan GPU belleğini serbest bırak
            return;
        }

        // 3. Kernel'ı başlatmak için grid ve blok boyutlarını ayarla
        // Her blokta 16x16 thread (256 thread) iyi bir başlangıç noktasıdır.
        dim3 threads_per_block(16, 16);
        // Görüntü boyutuna göre gerekli blok sayısını hesapla
        dim3 num_blocks((width + threads_per_block.x - 1) / threads_per_block.x,
                        (height + threads_per_block.y - 1) / threads_per_block.y);

        // 4. Kernel'ı GPU üzerinde çalıştır
        setFirstChannelToZeroKernel<<<num_blocks, threads_per_block>>>(d_pixel_data_gpu, width, height, channels);

        // Kernel çağrısından hemen sonra son hatayı kontrol et (asenkron olabilir)
        err = cudaGetLastError();
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Kernel Launch Error (%d): %s\n", err, cudaGetErrorString(err));
            cudaFree(d_pixel_data_gpu);
            return;
        }

        // Tüm GPU işlemlerinin tamamlanmasını bekle (senkronizasyon)
        err = cudaDeviceSynchronize();
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Device Sync Error (%d): %s\n", err, cudaGetErrorString(err));
            cudaFree(d_pixel_data_gpu);
            return;
        }

        // 5. İşlenmiş sonucu GPU'dan (d_pixel_data_gpu) CPU'ya (uc_pixel_data_cpu) geri kopyala
        err = cudaMemcpy(uc_pixel_data_cpu, d_pixel_data_gpu, image_size_in_bytes, cudaMemcpyDeviceToHost);
        if (err != cudaSuccess) {
            fprintf(stderr, "CUDA Memcpy Device to Host Error (%d): %s\n", err, cudaGetErrorString(err));
            // Hata olsa bile GPU belleğini serbest bırakmaya çalışacağız.
            // CPU'daki veri bozulmuş olabilir.
        }

        // 6. GPU'da ayrılan belleği serbest bırak
        err = cudaFree(d_pixel_data_gpu);
        if (err != cudaSuccess) {
            // Bu hata genellikle kritik bir soruna işaret etmez ama loglamak iyi olabilir.
            fprintf(stderr, "CUDA Free Error (%d): %s\n", err, cudaGetErrorString(err));
        }
    }
}