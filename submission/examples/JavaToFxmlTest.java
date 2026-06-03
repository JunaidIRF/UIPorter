import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class UIPorterApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Root container with dark blue gradient background
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #0d1b2a, #1b263b, #000000);");

        // Main text "UIPorter"
        Text text = new Text("UIPorter");
        
        // Use a modern sans-serif font
        text.setFont(Font.font("System", FontWeight.BOLD, 80));
        text.setFill(Color.WHITE);

        // Apply a slight glow effect
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#4cc9f0")); // Light blue glow
        glow.setRadius(25);
        glow.setSpread(0.3);
        text.setEffect(glow);

        // Add text to the center
        root.getChildren().add(text);
        StackPane.setAlignment(text, Pos.CENTER);

        // Scene setup
        Scene scene = new Scene(root, 900, 600);
        
        primaryStage.setTitle("UIPorter - Modern UI");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}