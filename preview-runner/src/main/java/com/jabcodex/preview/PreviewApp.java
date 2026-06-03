package com.jabcodex.preview;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

public class PreviewApp extends Application {

    private static final String DARK_BG_COLOR = "#1A1A1A";
    private static final String CARD_BG_COLOR = "#2C2C2C";
    private static final String SEARCH_BG_COLOR = "#3A3A3A";
    private static final String ACCENT_BLUE = "#3388FF";
    private static final String ACCENT_GREEN = "#88FF33";
    private static final String ACCENT_PURPLE = "#9966FF";
    private static final String ACCENT_ORANGE = "#FF9933";
    private static final String TEXT_COLOR_LIGHT = "#FFFFFF";
    private static final String TEXT_COLOR_GREY = "#AAAAAA";

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox(20); // Spacing between major sections
        root.setStyle("-fx-background-color: " + DARK_BG_COLOR + ";");
        root.setPadding(new Insets(20));

        // Top Bar
        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Button backButton = createIconButton("<", 16, ACCENT_BLUE, 10, 30);
        Label titleLabel = new Label("Daily progress");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.web(TEXT_COLOR_LIGHT));
        HBox.setHgrow(titleLabel, Priority.ALWAYS); // Push elements to sides

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button searchIcon = createIconButton("Q", 16, CARD_BG_COLOR, 10, 30); // Placeholder for search icon
        searchIcon.setStyle(searchIcon.getStyle() + "-fx-text-fill: " + TEXT_COLOR_LIGHT + ";"); // Make Q white

        // Avatar (using a StackPane with Circle and Label for self-contained code)
        Circle avatarCircle = new Circle(15, Color.web("#666666")); // Dark grey circle
        Label avatarText = new Label("👤"); // Unicode person icon
        avatarText.setFont(Font.font("System", FontWeight.NORMAL, 16)); // Use System font for emojis
        avatarText.setTextFill(Color.web(TEXT_COLOR_LIGHT));
        StackPane avatarStack = new StackPane(avatarCircle, avatarText);
        avatarStack.setPrefSize(30, 30);
        avatarStack.setAlignment(Pos.CENTER);
        
        topBar.getChildren().addAll(backButton, titleLabel, spacer, searchIcon, avatarStack);


        // Search Field
        TextField searchField = new TextField();
        searchField.setPromptText("Search");
        searchField.setStyle(
                "-fx-background-color: " + SEARCH_BG_COLOR + ";" +
                "-fx-text-fill: " + TEXT_COLOR_LIGHT + ";" +
                "-fx-prompt-text-fill: " + TEXT_COLOR_GREY + ";" +
                "-fx-background-radius: 15;" +
                "-fx-padding: 10 15;" +
                "-fx-font-size: 14px;"
        );
        searchField.setPrefHeight(40);

        // Category Tabs
        HBox categoryTabs = new HBox(10);
        Button allButton = new Button("All");
        allButton.setStyle(
                "-fx-background-color: " + ACCENT_BLUE + ";" +
                "-fx-text-fill: " + TEXT_COLOR_LIGHT + ";" +
                "-fx-background-radius: 15;" +
                "-fx-padding: 8 20;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;"
        );

        Button favoriteButton = new Button("Favorite");
        favoriteButton.setStyle(
                "-fx-background-color: " + CARD_BG_COLOR + ";" +
                "-fx-text-fill: " + TEXT_COLOR_GREY + ";" +
                "-fx-background-radius: 15;" +
                "-fx-padding: 8 20;" +
                "-fx-font-size: 14px;"
        );

        categoryTabs.getChildren().addAll(allButton, favoriteButton);

        // Task List
        VBox taskList = new VBox(10); // Spacing between tasks

        // Task 1: Read "The Lean Startup"
        taskList.getChildren().add(createTaskItem("\uD83D\uDCD6", ACCENT_BLUE, "Read \"The Lean Startup\"", false)); // Open Book emoji

        // Task 2: Fix landing page
        taskList.getChildren().add(createTaskItem("\uD83D\uDD14", ACCENT_GREEN, "Fix landing page", true)); // Bell emoji

        // Task 3: Share prototype with team
        taskList.getChildren().add(createTaskItem("\u2713", ACCENT_PURPLE, "Share prototype with team", true)); // Check Mark emoji

        // Task 4: Reply to Richard
        taskList.getChildren().add(createTaskItem("\uD83D\uDCE6", ACCENT_ORANGE, "Reply to Richard", false)); // Package emoji

        // Task 5: Finalize pitch deck
        taskList.getChildren().add(createTaskItem("\u2713", ACCENT_PURPLE, "Finalize pitch deck", true)); // Check Mark emoji


        root.getChildren().addAll(topBar, searchField, categoryTabs, taskList);

        Scene scene = new Scene(root, 360, 700); // Approximate mobile screen size
        primaryStage.setTitle("Daily Progress");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private Button createIconButton(String text, int fontSize, String bgColor, double radius, double size) {
        Button button = new Button(text);
        button.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: " + radius + ";" +
                "-fx-text-fill: " + TEXT_COLOR_LIGHT + ";" +
                "-fx-font-size: " + fontSize + "px;" +
                "-fx-font-weight: bold;"
        );
        button.setPrefSize(size, size);
        button.setMinSize(size, size);
        button.setMaxSize(size, size);
        button.setAlignment(Pos.CENTER);
        return button;
    }

    private HBox createTaskItem(String iconChar, String iconBgColor, String taskText, boolean hasBorder) {
        HBox item = new HBox(15);
        item.setAlignment(Pos.CENTER_LEFT);
        item.setPadding(new Insets(12, 15, 12, 15));
        item.setStyle(
                "-fx-background-color: " + CARD_BG_COLOR + ";" +
                "-fx-background-radius: 15;"
        );
        if (hasBorder) {
            item.setBorder(new Border(new BorderStroke(
                    Color.web(iconBgColor),
                    BorderStrokeStyle.SOLID,
                    new CornerRadii(15),
                    new BorderWidths(2)
            )));
        }

        // Icon container
        StackPane iconContainer = new StackPane();
        iconContainer.setPrefSize(40, 40);
        iconContainer.setStyle(
                "-fx-background-color: " + iconBgColor + ";" +
                "-fx-background-radius: 10;" // Slightly less rounded than the card
        );

        Label iconLabel = new Label(iconChar);
        iconLabel.setFont(Font.font("System", FontWeight.NORMAL, 18)); // Use System font for emojis
        iconLabel.setTextFill(Color.web(TEXT_COLOR_LIGHT));
        iconContainer.getChildren().add(iconLabel);

        Label taskLabel = new Label(taskText);
        taskLabel.setFont(Font.font("Arial", FontWeight.NORMAL, 16));
        taskLabel.setTextFill(Color.web(TEXT_COLOR_LIGHT));
        HBox.setHgrow(taskLabel, Priority.ALWAYS);

        Label arrowLabel = new Label(">");
        arrowLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        arrowLabel.setTextFill(Color.web(TEXT_COLOR_GREY));

        item.getChildren().addAll(iconContainer, taskLabel, arrowLabel);
        return item;
    }

    public static void main(String[] args) {
        launch(args);
    }
}