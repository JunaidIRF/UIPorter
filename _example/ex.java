import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrazyModernDashboard extends Application {

    private static final Color ACCENT_COLOR = Color.web("#7209b7");
    private static final Color SECONDARY_COLOR = Color.web("#3f37c9");
    private static final Color BG_DARK = Color.web("#0b090a");
    private final Random random = new Random();

    @Override
    public void start(Stage primaryStage) {
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: #0b090a;");

        // 1. Animated Particle Background
        Canvas canvas = new Canvas(1200, 800);
        setupParticleBackground(canvas);
        root.getChildren().add(canvas);

        // 2. Main Layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setPadding(new Insets(20));

        // Sidebar
        VBox sidebar = createSidebar();
        mainLayout.setLeft(sidebar);

        // Content Area
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-padding: 0 0 0 20;");
        
        VBox content = new VBox(25);
        content.setPadding(new Insets(0, 20, 0, 0));
        
        // Header
        content.getChildren().add(createHeader());

        // Top Stats Row
        HBox statsRow = new HBox(20);
        statsRow.getChildren().addAll(
                createStatCard("Total Revenue", "$124,592", "+12.5%", Color.web("#4cc9f0")),
                createStatCard("Active Users", "12,843", "+5.2%", Color.web("#4895ef")),
                createStatCard("Conversion", "3.8%", "-1.1%", Color.web("#f72585")),
                createStatCard("System Load", "42%", "Stable", Color.web("#7209b7"))
        );
        content.getChildren().add(statsRow);

        // Middle Row (Charts)
        HBox chartRow = new HBox(20);
        chartRow.getChildren().addAll(createLineChart(), createPieChart());
        content.getChildren().add(chartRow);

        // Bottom Row (Table and Controls)
        HBox bottomRow = new HBox(20);
        bottomRow.getChildren().addAll(createProjectTable(), createControlsPanel());
        content.getChildren().add(bottomRow);

        scrollPane.setContent(content);
        mainLayout.setCenter(scrollPane);

        root.getChildren().add(mainLayout);

        Scene scene = new Scene(root, 1280, 850);
        scene.getStylesheets().add("data:text/css," + CSS_STYLES);
        
        primaryStage.setTitle("NEBULA OS - Advanced Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Bind canvas size
        canvas.widthProperty().bind(scene.widthProperty());
        canvas.heightProperty().bind(scene.heightProperty());
    }

    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPrefWidth(240);
        sidebar.getStyleClass().add("glass-panel");
        sidebar.setPadding(new Insets(30, 15, 30, 15));

        Label logo = new Label("NEBULA");
        logo.setStyle("-fx-font-size: 28; -fx-font-weight: bold; -fx-text-fill: white; -fx-padding: 0 0 30 0;");
        
        sidebar.getChildren().add(logo);

        String[] menuItems = {"Dashboard", "Analytics", "Project Map", "Team", "Cloud Storage", "Settings"};
        for (String item : menuItems) {
            Button btn = new Button(item);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.getStyleClass().add("sidebar-btn");
            if (item.equals("Dashboard")) btn.getStyleClass().add("sidebar-btn-active");
            sidebar.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        VBox upgradeCard = new VBox(10);
        upgradeCard.getStyleClass().add("upgrade-card");
        upgradeCard.getChildren().addAll(
            new Label("PRO PLAN"),
            new Label("Get unlimited access to AI tools"),
            new Button("Upgrade Now")
        );
        sidebar.getChildren().add(upgradeCard);

        return sidebar;
    }

    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Enterprise Overview");
        title.setStyle("-fx-font-size: 24; -fx-font-weight: bold; -fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField search = new TextField();
        search.setPromptText("Search anything...");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(300);

        Circle avatar = new Circle(20, Color.web("#7209b7"));
        avatar.setStroke(Color.WHITE);
        avatar.setStrokeWidth(2);

        header.getChildren().addAll(title, spacer, search, avatar);
        return header;
    }

    private VBox createStatCard(String title, String val, String trend, Color accent) {
        VBox card = new VBox(8);
        card.getStyleClass().add("glass-panel");
        card.setPrefWidth(280);
        card.setPadding(new Insets(20));

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-text-fill: #adb5bd; -fx-font-size: 14;");
        
        Label lblVal = new Label(val);
        lblVal.setStyle("-fx-text-fill: white; -fx-font-size: 24; -fx-font-weight: bold;");

        Label lblTrend = new Label(trend);
        lblTrend.setStyle("-fx-text-fill: " + (trend.startsWith("+") ? "#4cc9f0" : "#f72585") + "; -fx-font-weight: bold;");

        card.getChildren().addAll(lblTitle, lblVal, lblTrend);
        
        // Add a small glowing bar at the bottom
        Rectangle bar = new Rectangle(40, 4);
        bar.setArcWidth(4); bar.setArcHeight(4);
        bar.setFill(accent);
        card.getChildren().add(bar);

        return card;
    }

    private StackPane createLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time");
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("System Performance");
        lineChart.setLegendVisible(false);
        lineChart.setAnimated(true);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("00:00", 23));
        series.getData().add(new XYChart.Data<>("04:00", 45));
        series.getData().add(new XYChart.Data<>("08:00", 32));
        series.getData().add(new XYChart.Data<>("12:00", 67));
        series.getData().add(new XYChart.Data<>("16:00", 89));
        series.getData().add(new XYChart.Data<>("20:00", 54));

        lineChart.getData().add(series);
        
        StackPane container = new StackPane(lineChart);
        container.getStyleClass().add("glass-panel");
        container.setPadding(new Insets(10));
        HBox.setHgrow(container, Priority.ALWAYS);
        return container;
    }

    private StackPane createPieChart() {
        PieChart pieChart = new PieChart();
        pieChart.getData().addAll(
                new PieChart.Data("Cloud", 40),
                new PieChart.Data("Edge", 25),
                new PieChart.Data("Local", 35)
        );
        pieChart.setLabelsVisible(true);
        pieChart.setPrefWidth(350);

        StackPane container = new StackPane(pieChart);
        container.getStyleClass().add("glass-panel");
        container.setPadding(new Insets(10));
        return container;
    }

    private VBox createProjectTable() {
        VBox container = new VBox(15);
        container.getStyleClass().add("glass-panel");
        container.setPadding(new Insets(20));
        HBox.setHgrow(container, Priority.ALWAYS);

        Label title = new Label("Recent Projects");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18; -fx-font-weight: bold;");

        TableView<Project> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(250);

        TableColumn<Project, String> nameCol = new TableColumn<>("Project");
        nameCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().name));

        TableColumn<Project, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().status));

        TableColumn<Project, Double> progressCol = new TableColumn<>("Progress");
        progressCol.setCellFactory(column -> new TableCell<>() {
            private final ProgressBar pb = new ProgressBar();
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    pb.setProgress(getTableView().getItems().get(getIndex()).progress);
                    pb.setPrefWidth(Double.MAX_VALUE);
                    setGraphic(pb);
                }
            }
        });

        table.getColumns().addAll(nameCol, statusCol, progressCol);
        table.setItems(FXCollections.observableArrayList(
                new Project("Quantum Engine", "Running", 0.85),
                new Project("Neural Link", "Optimizing", 0.42),
                new Project("Cyber Shield", "Testing", 0.61),
                new Project("Data Vortex", "Complete", 1.0)
        ));

        container.getChildren().addAll(title, table);
        return container;
    }

    private VBox createControlsPanel() {
        VBox container = new VBox(20);
        container.getStyleClass().add("glass-panel");
        container.setPadding(new Insets(20));
        container.setPrefWidth(350);

        Label title = new Label("System Controls");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 18; -fx-font-weight: bold;");

        Slider slider = new Slider(0, 100, 75);
        Label sliderVal = new Label("Network Throttle: 75%");
        sliderVal.setStyle("-fx-text-fill: #adb5bd;");
        slider.valueProperty().addListener((obs, oldV, newV) -> 
            sliderVal.setText(String.format("Network Throttle: %.0f%%", newV.doubleValue())));

        CheckBox c1 = new CheckBox("Enable AI Optimization");
        CheckBox c2 = new CheckBox("Hardware Acceleration");
        CheckBox c3 = new CheckBox("Auto-Scale Nodes");
        c1.setSelected(true);

        Button actionBtn = new Button("DEPLOY SYSTEM");
        actionBtn.getStyleClass().add("action-btn");
        actionBtn.setMaxWidth(Double.MAX_VALUE);

        container.getChildren().addAll(title, sliderVal, slider, c1, c2, c3, actionBtn);
        return container;
    }

    private void setupParticleBackground(Canvas canvas) {
        GraphicsContext gc = canvas.getGraphicsContext2D();
        List<Particle> particles = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            particles.add(new Particle(random.nextDouble() * 1200, random.nextDouble() * 800));
        }

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setFill(Color.web("#0b090a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

                for (Particle p : particles) {
                    p.update(canvas.getWidth(), canvas.getHeight());
                    gc.setFill(p.color);
                    gc.setGlobalAlpha(p.opacity);
                    gc.fillOval(p.x, p.y, p.size, p.size);
                }
                
                // Draw connecting lines
                gc.setStroke(Color.web("#7209b7"));
                gc.setGlobalAlpha(0.1);
                for (int i = 0; i < particles.size(); i++) {
                    for (int j = i + 1; j < particles.size(); j++) {
                        double dist = Math.hypot(particles.get(i).x - particles.get(j).x, particles.get(i).y - particles.get(j).y);
                        if (dist < 150) {
                            gc.strokeLine(particles.get(i).x, particles.get(i).y, particles.get(j).x, particles.get(j).y);
                        }
                    }
                }
                gc.setGlobalAlpha(1.0);
            }
        };
        timer.start();
    }

    private static class Particle {
        double x, y, vx, vy, size, opacity;
        Color color;
        Particle(double x, double y) {
            this.x = x; this.y = y;
            this.vx = (Math.random() - 0.5) * 0.5;
            this.vy = (Math.random() - 0.5) * 0.5;
            this.size = Math.random() * 4 + 2;
            this.opacity = Math.random() * 0.5 + 0.2;
            this.color = Math.random() > 0.5 ? Color.web("#7209b7") : Color.web("#4cc9f0");
        }
        void update(double w, double h) {
            x += vx; y += vy;
            if (x < 0 || x > w) vx *= -1;
            if (y < 0 || y > h) vy *= -1;
        }
    }

    public static class Project {
        String name, status;
        double progress;
        Project(String n, String s, double p) { name = n; status = s; progress = p; }
    }

    private static final String CSS_STYLES = 
        ".glass-panel {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.05);" +
        "    -fx-background-radius: 15;" +
        "    -fx-border-color: rgba(255, 255, 255, 0.1);" +
        "    -fx-border-radius: 15;" +
        "    -fx-border-width: 1;" +
        "}" +
        ".sidebar-btn {" +
        "    -fx-background-color: transparent;" +
        "    -fx-text-fill: #adb5bd;" +
        "    -fx-alignment: CENTER_LEFT;" +
        "    -fx-padding: 12 20;" +
        "    -fx-font-size: 14;" +
        "    -fx-cursor: hand;" +
        "    -fx-background-radius: 10;" +
        "}" +
        ".sidebar-btn:hover {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.08);" +
        "    -fx-text-fill: white;" +
        "}" +
        ".sidebar-btn-active {" +
        "    -fx-background-color: #7209b7;" +
        "    -fx-text-fill: white;" +
        "}" +
        ".search-box {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.08);" +
        "    -fx-background-radius: 20;" +
        "    -fx-text-fill: white;" +
        "    -fx-padding: 8 15;" +
        "    -fx-border-color: transparent;" +
        "}" +
        ".upgrade-card {" +
        "    -fx-background-color: linear-gradient(to bottom right, #7209b7, #3f37c9);" +
        "    -fx-background-radius: 15;" +
        "    -fx-padding: 20;" +
        "}" +
        ".upgrade-card Label {" +
        "    -fx-text-fill: white;" +
        "}" +
        ".upgrade-card Button {" +
        "    -fx-background-color: white;" +
        "    -fx-text-fill: #7209b7;" +
        "    -fx-font-weight: bold;" +
        "    -fx-background-radius: 10;" +
        "    -fx-padding: 8 15;" +
        "}" +
        ".table-view {" +
        "    -fx-background-color: transparent;" +
        "    -fx-base: transparent;" +
        "    -fx-control-inner-background: transparent;" +
        "}" +
        ".table-view .column-header-background {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.05);" +
        "}" +
        ".table-view .column-header {" +
        "    -fx-background-color: transparent;" +
        "}" +
        ".table-view .cell {" +
        "    -fx-text-fill: white;" +
        "    -fx-border-color: transparent;" +
        "}" +
        ".table-row-cell:odd {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.02);" +
        "}" +
        ".progress-bar {" +
        "    -fx-accent: #4cc9f0;" +
        "}" +
        ".progress-bar .track {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.1);" +
        "    -fx-background-radius: 5;" +
        "}" +
        ".check-box {" +
        "    -fx-text-fill: #adb5bd;" +
        "}" +
        ".check-box .box {" +
        "    -fx-background-color: rgba(255, 255, 255, 0.1);" +
        "}" +
        ".action-btn {" +
        "    -fx-background-color: #f72585;" +
        "    -fx-text-fill: white;" +
        "    -fx-font-weight: bold;" +
        "    -fx-padding: 15;" +
        "    -fx-background-radius: 10;" +
        "    -fx-cursor: hand;" +
        "}" +
        ".chart-plot-background {" +
        "    -fx-background-color: transparent;" +
        "}" +
        ".chart-vertical-grid-lines, .chart-horizontal-grid-lines {" +
        "    -fx-stroke: rgba(255, 255, 255, 0.05);" +
        "}" +
        ".axis {" +
        "    -fx-tick-label-fill: #adb5bd;" +
        "}" +
        ".default-color0.chart-series-line {" +
        "    -fx-stroke: #7209b7;" +
        "}" +
        ".default-color0.chart-line-symbol {" +
        "    -fx-background-color: #7209b7, white;" +
        "}";

    public static void main(String[] args) {
        launch(args);
    }
}