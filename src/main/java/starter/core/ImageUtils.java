package starter.core;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.bytedeco.opencv.opencv_core.Mat;

public class ImageUtils {

    /**
     * Bir OpenCV {@link Mat} nesnesini Java {@link BufferedImage} nesnesine dönüştürür.
     * Bu dönüşüm, OpenCV ile işlenen görüntülerin Swing GUI bileşenlerinde (örn. JLabel)
     * gösterilebilmesi için gereklidir. Görüntünün renk formatının (BGR)
     * BufferedImage için uygun tipe (TYPE_3BYTE_BGR) dönüştürülmesini sağlar.
     *
     * @param mat Dönüştürülecek OpenCV Mat nesnesi. Mat nesnesinin geçerli ve dolu olduğu varsayılır.
     * @return Mat nesnesinden oluşturulan BufferedImage. Eğer Mat 3 kanallı değilse veya boşsa
     *         beklenmedik sonuçlar veya hatalar oluşabilir.
     */
    public static BufferedImage matToBufferedImage(Mat mat) {
        int type = BufferedImage.TYPE_3BYTE_BGR;
        if (mat.channels() == 1) {
            type = BufferedImage.TYPE_BYTE_GRAY;
        }
        
        int bufferSize = mat.channels() * mat.cols() * mat.rows();
        byte[] b = new byte[bufferSize];
        mat.data().get(b);
        BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);
        return image;
    }

    /**
     * Verilen bir {@link BufferedImage}'ı, en boy oranını koruyarak belirtilen maksimum
     * genişlik ve yüksekliğe sığacak şekilde ölçeklendirir.
     * Bu, görüntülerin GUI'deki sabit boyutlu alanlara (örn. JLabel) düzgün bir şekilde
     * yerleştirilmesi için kullanılır. Ölçekleme için {@link Image#SCALE_SMOOTH} kullanılır,
     * bu da daha kaliteli ama yavaş bir ölçekleme sağlar.
     *
     * @param img Ölçeklendirilecek orijinal BufferedImage.
     * @param maxW Ölçeklenmiş görüntünün maksimum genişliği.
     * @param maxH Ölçeklenmiş görüntünün maksimum yüksekliği.
     * @return Ölçeklendirilmiş yeni bir BufferedImage.
     */
    public static BufferedImage getScaledImage(BufferedImage img, int maxW, int maxH) {
        int originalW = img.getWidth();
        int originalH = img.getHeight();
        double aspectRatio = (double) originalW / originalH;

        int newW = maxW;
        int newH = maxH;

        if (originalW > maxW || originalH > maxH) {
            if (aspectRatio >= 1) { 
                newH = (int) (maxW / aspectRatio);
                if (newH > maxH) {
                    newH = maxH;
                    newW = (int) (maxH * aspectRatio);
                }
            } else { 
                newW = (int) (maxH * aspectRatio);
                 if (newW > maxW) {
                    newW = maxW;
                    newH = (int) (maxW / aspectRatio);
                }
            }
        } else { 
            newW = originalW;
            newH = originalH;
        }

        Image tmp = img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH);
        BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = scaled.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return scaled;
    }
}