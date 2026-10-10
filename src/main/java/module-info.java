module com.example.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.gluonhq.charm.glisten;
    requires com.gluonhq.attach.util;
    requires org.fxmisc.richtext;
    requires transitive javafx.graphics;
    requires reactfx;
    requires org.fxmisc.flowless;

    requires javafx.swing;
    requires java.desktop;
    requires java.prefs;

    requires java.xml;

    requires org.apache.poi.ooxml;

    requires org.apache.pdfbox;
    requires org.apache.fontbox;
    requires javafx.base;
    requires org.apache.poi.poi;

    requires com.google.gson;

    opens com.example to javafx.graphics, javafx.fxml;
    opens com.example.controller to javafx.fxml;
    opens com.example.view to javafx.fxml;
    opens com.example.model.help to com.google.gson;

    exports com.example;
    exports com.example.view;
}