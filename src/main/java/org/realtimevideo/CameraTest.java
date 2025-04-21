package org.realtimevideo;
import org.bytedeco.javacv.*;

public class CameraTest {
    public static void main(String[] args) throws Exception {
        OpenCVFrameGrabber grabber = new OpenCVFrameGrabber(0);
        grabber.start();
        CanvasFrame canvas = new CanvasFrame("Camera Test", CanvasFrame.getDefaultGamma() / grabber.getGamma());

        while (canvas.isVisible()) {
            Frame frame = grabber.grab();
            canvas.showImage(frame);
        }

        grabber.stop();
        canvas.dispose();
    }
}

