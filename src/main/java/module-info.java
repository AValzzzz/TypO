module com.example.javafx {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.gluonhq.charm.glisten;
    requires com.gluonhq.attach.util;

    opens com.example to javafx.graphics, javafx.fxml;
    opens com.example.controller to javafx.fxml;

    exports com.example;
}