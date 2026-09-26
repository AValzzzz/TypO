module com.example.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.gluonhq.charm.glisten;
    requires com.gluonhq.attach.util;
    
    requires org.fxmisc.richtext;
    requires javafx.graphics;
    requires reactfx;
    requires org.fxmisc.flowless;

    opens com.example to javafx.graphics, javafx.fxml;
    opens com.example.controller to javafx.fxml;
    
    opens com.example.view to javafx.fxml;

    exports com.example;
    exports com.example.view;
}