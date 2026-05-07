package com.autojudge;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root);

        // Load VNOJ-style CSS
        scene.getStylesheets().add(
            getClass().getResource("/css/style.css").toExternalForm()
        );

        primaryStage.setTitle("AI Auto Judge System");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(620);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}