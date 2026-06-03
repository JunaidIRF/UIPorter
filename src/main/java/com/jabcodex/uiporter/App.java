package com.jabcodex.uiporter;

import com.jabcodex.uiporter.adapters.FxmlAdapter;
import com.jabcodex.uiporter.adapters.JavaFxAdapter;
import com.jabcodex.uiporter.adapters.WinFormsAdapter;
import com.jabcodex.uiporter.core.FrameworkRegistry;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.HeaderBar;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.io.IOException;
import java.nio.file.Path;

public class App extends Application {

    private static Scene scene;

    public static Path PROJECT_ROOT;

    @Override
    public void start(Stage stage) throws IOException {
        try {
            Path classesDir = Path.of(App.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            PROJECT_ROOT = classesDir.getParent().getParent();
        } catch (Exception ignored) {
        }

        stage.initStyle(StageStyle.EXTENDED);
        stage.setTitle("UIPorter");
        HeaderBar.setPrefButtonHeight(stage, 0);
        try {
            Image icon = new Image(App.class.getResourceAsStream("icons/icon.png"));
            stage.getIcons().add(icon);
        } catch (Exception ignored) {}
        scene = new Scene(loadFXML("primary"), 1100, 700);
        scene.setFill(Color.web("#1e1e1e"));
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(500);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        FrameworkRegistry r = FrameworkRegistry.getInstance();
        r.register(new JavaFxAdapter());
        r.register(new FxmlAdapter());
        r.register(new WinFormsAdapter());
        launch();
    }

}