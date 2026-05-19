package com.brenis;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main extends Application {

    @Override
    public void start(Stage stage) {
        try {
            log.info("Iniciando la aplicación CajaApp...");

            FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("/vista_caja.fxml"));

            Parent root = fxmlLoader.load();

            Scene scene = new Scene(root, 800, 600); // Ajusta el tamaño si lo ves muy chico

            stage.setTitle("POS - Sistema de Caja Local (V1)");
            stage.setScene(scene);
            stage.show();

            log.info("Ventana de CajaApp desplegada correctamente.");

        } catch (Exception e) {
            log.error("Error crítico al levantar la interfaz de JavaFX: {}", e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}