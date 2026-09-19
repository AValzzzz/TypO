package com.example;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application{

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/com/example/view/Main.fxml"));
        Scene scene = new Scene(root);

        Image icon = new Image (getClass().getResourceAsStream("/com/example/logo.png"));
        stage.getIcons().add(icon);
        stage.setTitle("TypO");
        stage.setWidth(600);
        stage.setHeight(420);

        stage.setScene(scene);
        stage.show();
    }
    
}
