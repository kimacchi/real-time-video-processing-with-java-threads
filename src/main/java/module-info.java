module starter {

    requires javafx.controls;
    requires javafx.swing;

    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.desktop;
    requires org.bytedeco.opencv;
    requires org.bytedeco.javacv;

    exports starter;

    // resources
    opens assets;
    opens assets.icons;
}