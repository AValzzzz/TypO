package com.example;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class App extends Application{

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Group root = new Group();
        Scene scene = new Scene(root, Color.BLACK);

        // Image icon = new Image ("icon.png");
        // stage.getIcons().add(icon);
        stage.setTitle("TypO");
        stage.setWidth(600);
        stage.setHeight(420);

        stage.setScene(scene);
        stage.show();
    }
    
}
