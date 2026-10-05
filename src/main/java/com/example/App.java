package com.example;

import com.example.model.LinkOpener;
import java.util.Locale;
import java.util.ResourceBundle;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        LinkOpener.init(getHostServices());

        ResourceBundle bundle = ResourceBundle.getBundle("com.example.i18n.messages", Locale.getDefault());

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/view/Main.fxml"), bundle);
        Parent root = loader.load();

        Scene scene = new Scene(root);

        Image icon = new Image(getClass().getResourceAsStream("/com/example/logo.png"));
        stage.getIcons().add(icon);
        stage.setTitle("TypO");
        stage.setWidth(600);
        stage.setHeight(420);

        stage.setScene(scene);
        stage.show();
    }
}