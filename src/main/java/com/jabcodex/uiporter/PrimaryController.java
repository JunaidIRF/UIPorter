package com.jabcodex.uiporter;

import com.jabcodex.uiporter.core.FrameworkAdapter;
import com.jabcodex.uiporter.core.FrameworkRegistry;
import com.jabcodex.uiporter.model.AppMetadata;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HeaderBar;
import javafx.scene.layout.HeaderButtonType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

public class PrimaryController {

    @FXML private ComboBox<String> fromCombo;
    @FXML private ComboBox<String> toCombo;
    @FXML private CodeArea sourceCodeArea;
    @FXML private CodeArea targetCodeArea;
    @FXML private Button convertButton;
    @FXML private Button openFileButton;
    @FXML private Button swapButton;
    @FXML private Button copyButton;
    @FXML private Button saveAsButton;
    @FXML private Button previewButton;
    @FXML private Label statusLabel;
    @FXML private CheckBox matchDefaultFontCheck;
    @FXML private CheckBox wfPanelNestCheck;

    @FXML private HeaderBar headerBar;
    @FXML private Button tabConvertBtn;
    @FXML private Button tabSettingsBtn;
    @FXML private Button tabAIBtn;
    @FXML private Button btnMinimize;
    @FXML private Button btnMaximize;
    @FXML private Button btnClose;
    @FXML private VBox pageConvert;
    @FXML private VBox pageSettings;
    @FXML private VBox pageAI;

    @FXML private Button aiTabImageBtn;
    @FXML private Button aiTabImproveBtn;
    @FXML private Button aiTabConsoleBtn;
    @FXML private Button aiTabPromptBtn;

    @FXML private VBox aiPageImage;
    @FXML private VBox aiPageImprove;
    @FXML private VBox aiPageConsole;
    @FXML private VBox aiPagePrompt;

    @FXML private Button    aiImageBrowseBtn;
    @FXML private TextField aiImagePathField;
    @FXML private TextField aiImageInstructField;
    @FXML private Button    aiImageGenerateBtn;
    @FXML private CodeArea  aiImageOutput;
    @FXML private Button    aiImageCopyBtn;
    @FXML private Button    aiImageSaveBtn;
    @FXML private Button    aiImagePreviewBtn;

    @FXML private CodeArea  aiImproveSourceArea;
    @FXML private TextField aiImproveInstructField;
    @FXML private Button    aiImproveGenerateBtn;
    @FXML private CodeArea  aiImproveOutput;
    @FXML private Button    aiImproveCopyBtn;
    @FXML private Button    aiImproveSaveBtn;
    @FXML private Button    aiImprovePreviewBtn;

    @FXML private CodeArea  aiConsoleSourceArea;
    @FXML private TextField aiConsoleInstructField;
    @FXML private Button    aiConsoleGenerateBtn;
    @FXML private CodeArea  aiConsoleOutput;
    @FXML private Button    aiConsoleCopyBtn;
    @FXML private Button    aiConsoleSaveBtn;
    @FXML private Button    aiConsolePreviewBtn;

    @FXML private TextArea  aiPromptTextArea;
    @FXML private Button    aiPromptGenerateBtn;
    @FXML private CodeArea  aiPromptOutput;
    @FXML private Button    aiPromptCopyBtn;
    @FXML private Button    aiPromptSaveBtn;
    @FXML private Button    aiPromptPreviewBtn;

    @FXML private Label aiStatusLabel;

    @FXML private PasswordField    aiApiKeyPasswordField;
    @FXML private TextField        aiApiKeyTextField;
    @FXML private Button           aiApiKeyRevealBtn;
    @FXML private ComboBox<String> aiModelComboBox;
    @FXML private Button           settingsResetBtn;

    @FXML private Button    aiImproveBrowseBtn;
    @FXML private Button    aiConsoleBrowseBtn;

    private static final String MODE_IMAGE_TO_UI   = AIService.MODE_IMAGE_TO_UI;
    private static final String MODE_IMPROVE_UI    = AIService.MODE_IMPROVE_UI;
    private static final String MODE_CONSOLE_TO_UI = AIService.MODE_CONSOLE_TO_UI;
    private static final String MODE_PROMPT_TO_UI  = AIService.MODE_PROMPT_TO_UI;


    private static final String[] JAVA_KEYWORDS = {
        "abstract","assert","boolean","break","byte","case","catch","char",
        "class","const","continue","default","do","double","else","enum",
        "extends","final","finally","float","for","goto","if","implements",
        "import","instanceof","int","interface","long","native","new",
        "package","private","protected","public","return","short","static",
        "strictfp","super","switch","synchronized","this","throw","throws",
        "transient","try","var","void","volatile","while","true","false","null"
    };

    private static final String[] CS_KEYWORDS = {
        "abstract","as","base","bool","break","byte","case","catch","char",
        "checked","class","const","continue","decimal","default","delegate",
        "do","double","else","enum","event","explicit","extern","false",
        "finally","fixed","float","for","foreach","goto","if","implicit",
        "in","int","interface","internal","is","lock","long","namespace",
        "new","null","object","operator","out","override","params","private",
        "protected","public","readonly","ref","return","sbyte","sealed",
        "short","sizeof","stackalloc","static","string","struct","switch",
        "this","throw","true","try","typeof","uint","ulong","unchecked",
        "unsafe","ushort","using","virtual","void","volatile","while","var"
    };

    private static final Pattern JAVA_PATTERN = buildPattern(JAVA_KEYWORDS);
    private static final Pattern CS_PATTERN   = buildPattern(CS_KEYWORDS);

    private static Pattern buildPattern(String[] keywords) {
        String kw      = "\\b(" + String.join("|", keywords) + ")\\b";
        String mlCom   = "/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/";
        String slCom   = "//[^\n]*";
        String str     = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
        String num     = "\\b\\d+(\\.\\d+)?[fFdDlL]?\\b";
        return Pattern.compile(
            "(?<MLCOMMENT>" + mlCom + ")"
            + "|(?<COMMENT>" + slCom + ")"
            + "|(?<STRING>" + str + ")"
            + "|(?<KEYWORD>" + kw + ")"
            + "|(?<NUMBER>" + num + ")"
        );
    }


    private void switchPage(Button activeBtn, VBox activePage) {
        for (Button b : new Button[]{tabConvertBtn, tabSettingsBtn, tabAIBtn}) {
            b.getStyleClass().remove("tab-btn-selected");
        }
        for (VBox p : new VBox[]{pageConvert, pageSettings, pageAI}) {
            p.setVisible(false);
            p.setManaged(false);
        }
        activeBtn.getStyleClass().add("tab-btn-selected");
        activePage.setVisible(true);
        activePage.setManaged(true);
    }


    @FXML
    public void initialize() {
        java.util.List<String> names = FrameworkRegistry.getInstance().getDisplayNames();
        fromCombo.setItems(FXCollections.observableArrayList(names));
        toCombo.setItems(FXCollections.observableArrayList(names));
        if (!names.isEmpty()) fromCombo.setValue(names.get(0));
        if (names.size() > 1)  toCombo.setValue(names.get(1));

        setupCodeArea(sourceCodeArea, false);
        setupCodeArea(targetCodeArea, true);

        sourceCodeArea.textProperty().addListener((obs, o, n) ->
            Platform.runLater(() -> applyHighlighting(sourceCodeArea, langFor(fromCombo))));

        fromCombo.valueProperty().addListener((obs, o, n) ->
            applyHighlighting(sourceCodeArea, langFor(fromCombo)));
        toCombo.valueProperty().addListener((obs, o, n) ->
            applyHighlighting(targetCodeArea, langFor(toCombo)));

        swapButton.setOnAction(e -> handleSwap());
        copyButton.setOnAction(e -> handleCopy());
        openFileButton.setOnAction(e -> handleOpenFile());
        saveAsButton.setOnAction(e -> handleSaveAs());

        tabConvertBtn.setOnAction(e -> switchPage(tabConvertBtn, pageConvert));
        tabSettingsBtn.setOnAction(e -> switchPage(tabSettingsBtn, pageSettings));
        tabAIBtn.setOnAction(e -> switchPage(tabAIBtn, pageAI));

        switchAIPage(aiTabImageBtn, aiPageImage);
        aiTabImageBtn.setOnAction(e -> switchAIPage(aiTabImageBtn, aiPageImage));
        aiTabImproveBtn.setOnAction(e -> switchAIPage(aiTabImproveBtn, aiPageImprove));
        aiTabConsoleBtn.setOnAction(e -> switchAIPage(aiTabConsoleBtn, aiPageConsole));
        aiTabPromptBtn.setOnAction(e -> switchAIPage(aiTabPromptBtn, aiPagePrompt));

        aiImageBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select UI Screenshot");
            fc.getExtensionFilters().add(new ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
            File f = fc.showOpenDialog(aiImageBrowseBtn.getScene().getWindow());
            if (f != null) aiImagePathField.setText(f.getAbsolutePath());
        });

        aiImageGenerateBtn.setOnAction(e -> runAI(
                MODE_IMAGE_TO_UI,
                aiImagePathField.getText().trim(),
                "",
                aiImageInstructField.getText().trim(),
                aiImageOutput, aiImageGenerateBtn));

        aiImproveGenerateBtn.setOnAction(e -> runAI(
                MODE_IMPROVE_UI,
                "",
                aiImproveSourceArea.getText().trim(),
                aiImproveInstructField.getText().trim(),
                aiImproveOutput, aiImproveGenerateBtn));

        aiConsoleGenerateBtn.setOnAction(e -> runAI(
                MODE_CONSOLE_TO_UI,
                "",
                aiConsoleSourceArea.getText().trim(),
                aiConsoleInstructField.getText().trim(),
                aiConsoleOutput, aiConsoleGenerateBtn));

        aiPromptGenerateBtn.setOnAction(e -> runAI(
                MODE_PROMPT_TO_UI,
                "",
                aiPromptTextArea.getText().trim(),
                "",
                aiPromptOutput, aiPromptGenerateBtn));

        aiImageCopyBtn.setOnAction(e -> copyToClipboardAI(aiImageOutput.getText()));
        aiImproveCopyBtn.setOnAction(e -> copyToClipboardAI(aiImproveOutput.getText()));
        aiConsoleCopyBtn.setOnAction(e -> copyToClipboardAI(aiConsoleOutput.getText()));
        aiPromptCopyBtn.setOnAction(e -> copyToClipboardAI(aiPromptOutput.getText()));

        aiImageSaveBtn.setOnAction(e -> saveAIOutput(aiImageOutput.getText()));
        aiImproveSaveBtn.setOnAction(e -> saveAIOutput(aiImproveOutput.getText()));
        aiConsoleSaveBtn.setOnAction(e -> saveAIOutput(aiConsoleOutput.getText()));
        aiPromptSaveBtn.setOnAction(e -> saveAIOutput(aiPromptOutput.getText()));
        updatePreviewButtonVisibility();
        toCombo.valueProperty().addListener((obs, o, n) -> updatePreviewButtonVisibility());
        previewButton.setOnAction(e -> handlePreview(targetCodeArea, statusLabel));

        aiImagePreviewBtn.setOnAction(e -> handlePreview(aiImageOutput, aiStatusLabel));
        aiImprovePreviewBtn.setOnAction(e -> handlePreview(aiImproveOutput, aiStatusLabel));
        aiConsolePreviewBtn.setOnAction(e -> handlePreview(aiConsoleOutput, aiStatusLabel));
        aiPromptPreviewBtn.setOnAction(e -> handlePreview(aiPromptOutput, aiStatusLabel));

        aiImproveBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open UI File");
            fc.getExtensionFilters().add(new ExtensionFilter("Java Files", "*.java"));
            fc.getExtensionFilters().add(new ExtensionFilter("All Files", "*.*"));
            File f = fc.showOpenDialog(aiImproveBrowseBtn.getScene().getWindow());
            if (f != null) {
                try {
                    aiImproveSourceArea.replaceText(Files.readString(f.toPath()));
                    applyHighlighting(aiImproveSourceArea, "java");
                } catch (IOException ex) {
                    aiStatusLabel.setText("Error reading file: " + ex.getMessage());
                }
            }
        });


        aiConsoleBrowseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Open Console App File");
            fc.getExtensionFilters().add(new ExtensionFilter("Java Files", "*.java"));
            fc.getExtensionFilters().add(new ExtensionFilter("All Files", "*.*"));
            File f = fc.showOpenDialog(aiConsoleBrowseBtn.getScene().getWindow());
            if (f != null) {
                try {
                    aiConsoleSourceArea.replaceText(Files.readString(f.toPath()));
                    applyHighlighting(aiConsoleSourceArea, "java");
                } catch (IOException ex) {
                    aiStatusLabel.setText("Error reading file: " + ex.getMessage());
                }
            }
        });

        matchDefaultFontCheck.setSelected(AppSettings.getBool("matchDefaultFont", false));
        wfPanelNestCheck.setSelected(AppSettings.getBool("wfPanelNest", false));
        String savedKey = AppSettings.get("aiApiKey", "");
        aiApiKeyPasswordField.setText(savedKey);
        aiApiKeyTextField.setText(savedKey);
        aiModelComboBox.getItems().addAll(
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-3-pro-preview",
            "gemini-2.5-pro",
            "gemini-3.1-flash-lite-preview",
            "gemini-2.5-flash"
        );
        aiModelComboBox.setValue(AppSettings.get("aiModel", "gemini-3-flash-preview"));

        matchDefaultFontCheck.selectedProperty().addListener((obs, o, n) ->
                AppSettings.setBool("matchDefaultFont", n));
        wfPanelNestCheck.selectedProperty().addListener((obs, o, n) ->
                AppSettings.setBool("wfPanelNest", n));
        aiApiKeyPasswordField.textProperty().addListener((obs, o, n) -> {
            AppSettings.set("aiApiKey", n);
            if (!aiApiKeyTextField.getText().equals(n)) aiApiKeyTextField.setText(n);
        });
        aiApiKeyTextField.textProperty().addListener((obs, o, n) -> {
            AppSettings.set("aiApiKey", n);
            if (!aiApiKeyPasswordField.getText().equals(n)) aiApiKeyPasswordField.setText(n);
        });
        aiModelComboBox.valueProperty().addListener((obs, o, n) -> {
            if (n != null) AppSettings.set("aiModel", n);
        });

        aiApiKeyRevealBtn.setOnAction(e -> {
            boolean hidden = aiApiKeyPasswordField.isVisible();
            aiApiKeyPasswordField.setVisible(!hidden);
            aiApiKeyPasswordField.setManaged(!hidden);
            aiApiKeyTextField.setVisible(hidden);
            aiApiKeyTextField.setManaged(hidden);
        });

        settingsResetBtn.setOnAction(e -> {
            matchDefaultFontCheck.setSelected(false);
            wfPanelNestCheck.setSelected(false);
            aiApiKeyPasswordField.setText("");
            aiApiKeyPasswordField.setVisible(true);
            aiApiKeyPasswordField.setManaged(true);
            aiApiKeyTextField.setVisible(false);
            aiApiKeyTextField.setManaged(false);
        });
        setupCodeArea(aiImproveSourceArea, false);
        setupCodeArea(aiConsoleSourceArea, false);
        setupCodeArea(aiImageOutput, true);
        setupCodeArea(aiImproveOutput, true);
        setupCodeArea(aiConsoleOutput, true);
        setupCodeArea(aiPromptOutput, true);

        aiImproveSourceArea.textProperty().addListener((obs, o, n) ->
                Platform.runLater(() -> applyHighlighting(aiImproveSourceArea, "java")));
        aiConsoleSourceArea.textProperty().addListener((obs, o, n) ->
                Platform.runLater(() -> applyHighlighting(aiConsoleSourceArea, "java")));

        HeaderBar.setButtonType(btnMinimize, HeaderButtonType.ICONIFY);
        HeaderBar.setButtonType(btnMaximize, HeaderButtonType.MAXIMIZE);
        HeaderBar.setButtonType(btnClose,    HeaderButtonType.CLOSE);

        btnMaximize.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) return;
            javafx.stage.Stage s = (javafx.stage.Stage) newScene.getWindow();
            if (s != null) {
                wireMaximizeIcon(s);
            } else {
                newScene.windowProperty().addListener((obs2, o2, win) -> {
                    if (win instanceof javafx.stage.Stage st) wireMaximizeIcon(st);
                });
            }
        });
    }

    private void wireMaximizeIcon(javafx.stage.Stage stage) {
        stage.maximizedProperty().addListener((obs, wasMax, isMax) -> updateMaximizeIcon(isMax));
    }

    private void updateMaximizeIcon(boolean maximized) {
        String res = maximized ? "icons/icon_wc_restore.png" : "icons/icon_wc_maximize.png";
        java.io.InputStream stream = getClass().getResourceAsStream(res);
        if (stream == null) return;
        javafx.scene.image.Image img = new javafx.scene.image.Image(stream);
        javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(img);
        iv.setFitWidth(12);
        iv.setFitHeight(12);
        iv.setPickOnBounds(true);
        iv.setPreserveRatio(true);
        btnMaximize.setGraphic(iv);
    }

    private void setupCodeArea(CodeArea area, boolean readOnly) {
        area.setEditable(!readOnly);
        area.setWrapText(false);
        area.setPadding(new Insets(6, 14, 6, 4));
        area.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        final double GUTTER_W = 44.0;
        area.setParagraphGraphicFactory(idx -> {
            Label lbl = new Label(String.valueOf(idx + 1));
            lbl.setMinWidth(GUTTER_W);
            lbl.setPrefWidth(GUTTER_W);
            lbl.setMaxWidth(GUTTER_W);
            lbl.getStyleClass().add("lineno");
            lbl.setStyle("-fx-text-fill: #505050; -fx-background-color: #1e1e1e;"
                + " -fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 12px;"
                + " -fx-padding: 0 10 0 0; -fx-alignment: center-right;");
            return lbl;
        });
        area.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB) {
                e.consume();
                if (!readOnly) area.replaceSelection("    ");
            }
        });
        VBox parent = (VBox) area.getParent();
        int savedIdx = parent != null ? parent.getChildren().indexOf(area) : -1;
        VirtualizedScrollPane<CodeArea> vsp = new VirtualizedScrollPane<>(area);
        vsp.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        VBox.setVgrow(vsp, Priority.ALWAYS);
        if (parent != null && savedIdx >= 0) {
            parent.getChildren().add(savedIdx, vsp);
        }
    }

    private void handleOpenFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open Source File");
        FrameworkAdapter srcAdapter = FrameworkRegistry.getInstance().get(fromCombo.getValue());
        if (srcAdapter != null) {
            String desc = srcAdapter.getDisplayName() + " Files";
            java.util.List<String> patterns = new java.util.ArrayList<>();
            for (String ext : srcAdapter.getFileExtensions()) patterns.add("*" + ext);
            chooser.getExtensionFilters().add(
                new ExtensionFilter(desc, patterns));
        }
        chooser.getExtensionFilters().add(new ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(openFileButton.getScene().getWindow());
        if (file != null) {
            try {
                sourceCodeArea.replaceText(Files.readString(file.toPath()));
                applyHighlighting(sourceCodeArea, langFor(fromCombo));
                statusLabel.setText("Opened: " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Error reading file: " + e.getMessage());
            }
        }
    }

    private void handleSaveAs() {
        String content = targetCodeArea.getText();
        if (content == null || content.trim().isEmpty()) {
            statusLabel.setText("Nothing to save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Converted File");
        FrameworkAdapter tgtAdapter = FrameworkRegistry.getInstance().get(toCombo.getValue());
        if (tgtAdapter != null) {
            String desc = tgtAdapter.getDisplayName() + " Files";
            java.util.List<String> patterns = new java.util.ArrayList<>();
            for (String ext : tgtAdapter.getFileExtensions()) patterns.add("*" + ext);
            chooser.getExtensionFilters().add(new ExtensionFilter(desc, patterns));
            String primaryExt = tgtAdapter.getFileExtensions()[0];
            chooser.setInitialFileName("Converted" + primaryExt);
        } else {
            chooser.setInitialFileName("Converted.txt");
        }
        chooser.getExtensionFilters().add(new ExtensionFilter("All Files", "*.*"));
        File file = chooser.showSaveDialog(saveAsButton.getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), content);
                statusLabel.setText("Saved: " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Error saving file: " + e.getMessage());
            }
        }
    }

    @FXML
    private void handleConvert() {
        String source = sourceCodeArea.getText();
        if (source == null || source.isBlank()) {
            statusLabel.setText("Source code is empty.");
            return;
        }
        FrameworkAdapter srcAdapter = FrameworkRegistry.getInstance().get(fromCombo.getValue());
        FrameworkAdapter tgtAdapter = FrameworkRegistry.getInstance().get(toCombo.getValue());
        if (srcAdapter == null || tgtAdapter == null) {
            statusLabel.setText("Please select valid frameworks.");
            return;
        }
        try {
            Map<String, Object> opts = buildOptionsMap();
            AppMetadata meta = srcAdapter.parse(source);
            String result = tgtAdapter.generate(meta, opts);
            targetCodeArea.replaceText(result);
            applyHighlighting(targetCodeArea, langFor(toCombo));
            updatePreviewButtonVisibility();
            statusLabel.setText("Converted successfully.");
        } catch (Exception ex) {
            statusLabel.setText("Error: " + ex.getMessage());
        }
    }

    private void handleSwap() {
        String tempCombo = fromCombo.getValue();
        fromCombo.getSelectionModel().select(toCombo.getValue());
        toCombo.getSelectionModel().select(tempCombo);
        String tempText = sourceCodeArea.getText();
        sourceCodeArea.replaceText(targetCodeArea.getText());
        targetCodeArea.replaceText(tempText);
        statusLabel.setText("Frameworks and code swapped.");
        Platform.runLater(() -> {
            applyHighlighting(sourceCodeArea, langFor(fromCombo));
            applyHighlighting(targetCodeArea, langFor(toCombo));
        });
    }

    private void handleCopy() {
        String text = targetCodeArea.getText();
        if (text != null && !text.isEmpty()) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            statusLabel.setText("Target code copied to clipboard!");
        } else {
            statusLabel.setText("Nothing to copy.");
        }
    }

    private Map<String, Object> buildOptionsMap() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("matchSourceDefaultFont",
            matchDefaultFontCheck != null && matchDefaultFontCheck.isSelected());
        opts.put("wfPanelNest",
            wfPanelNestCheck != null && wfPanelNestCheck.isSelected());
        return opts;
    }


    private void updatePreviewButtonVisibility() {
        FrameworkAdapter tgt = FrameworkRegistry.getInstance().get(toCombo.getValue());
        boolean canPreview = tgt != null &&
            ("java".equals(tgt.getSyntaxLanguage()) || "xml".equals(tgt.getSyntaxLanguage()));
        previewButton.setVisible(canPreview);
    }

    private void handlePreview(CodeArea outputArea, Label status) {
        String code = outputArea.getText();
        if (code == null || code.isBlank()) {
            status.setText("Nothing to preview.");
            return;
        }
        status.setText("Compiling preview\u2026");

        Thread t = new Thread(() -> {
            try {
                PreviewLauncher.launch(code);
                Platform.runLater(() -> status.setText("Preview launched."));
            } catch (PreviewLauncher.PreviewException ex) {
                Platform.runLater(() -> showPreviewError("Preview Error", ex.getMessage(), status));
            } catch (Exception ex) {
                Platform.runLater(() -> showPreviewError("Preview Failed",
                        ex.getClass().getSimpleName() + ": " + ex.getMessage(), status));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void showPreviewError(String title, String message, Label status) {
        status.setText("Preview error.");
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea(message);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(10);
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setPrefWidth(600);
        alert.showAndWait();
    }


    private void switchAIPage(Button activeBtn, VBox activePage) {
        for (Button b : new Button[]{aiTabImageBtn, aiTabImproveBtn, aiTabConsoleBtn, aiTabPromptBtn}) {
            b.getStyleClass().remove("ai-sub-tab-btn-selected");
        }
        for (VBox p : new VBox[]{aiPageImage, aiPageImprove, aiPageConsole, aiPagePrompt}) {
            p.setVisible(false);
            p.setManaged(false);
        }
        activeBtn.getStyleClass().add("ai-sub-tab-btn-selected");
        activePage.setVisible(true);
        activePage.setManaged(true);
    }


    private void runAI(String mode, String imagePath, String mainText,
                       String instruction, CodeArea outputArea, Button generateBtn) {
        if (mode.equals(MODE_IMAGE_TO_UI) && imagePath.isEmpty()) {
            outputArea.replaceText("Please select an image file first.");
            aiStatusLabel.setText("No image selected.");
            return;
        }
        if (!mode.equals(MODE_IMAGE_TO_UI) && mainText.isEmpty()) {
            outputArea.replaceText("Please provide the required input.");
            aiStatusLabel.setText("Input is empty.");
            return;
        }
        outputArea.replaceText("Cooking\u2026");
        aiStatusLabel.setText("AI is generating\u2026");
        generateBtn.setDisable(true);

        new Thread(() -> {
            try {
                String result = AIService.callApi(mode, imagePath, mainText, instruction,
                        AppSettings.get("aiApiKey", ""));
                Platform.runLater(() -> {
                    outputArea.replaceText(result);
                    applyHighlighting(outputArea, "java");
                    generateBtn.setDisable(false);
                    aiStatusLabel.setText("Done.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    outputArea.replaceText("Error: " + ex.getMessage());
                    generateBtn.setDisable(false);
                    aiStatusLabel.setText("Error: " + ex.getMessage());
                });
            }
        }).start();
    }


    private void copyToClipboardAI(String text) {
        if (text == null || text.isBlank()) {
            aiStatusLabel.setText("Nothing to copy.");
            return;
        }
        ClipboardContent cc = new ClipboardContent();
        cc.putString(text);
        Clipboard.getSystemClipboard().setContent(cc);
        aiStatusLabel.setText("Copied to clipboard.");
    }

    private void saveAIOutput(String content) {
        if (content == null || content.isBlank()) {
            aiStatusLabel.setText("Nothing to save.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Generated File");
        chooser.getExtensionFilters().add(new ExtensionFilter("Java Files", "*.java"));
        chooser.getExtensionFilters().add(new ExtensionFilter("All Files", "*.*"));
        chooser.setInitialFileName("GeneratedUI.java");
        File file = chooser.showSaveDialog(aiStatusLabel.getScene().getWindow());
        if (file != null) {
            try {
                Files.writeString(file.toPath(), content);
                aiStatusLabel.setText("Saved: " + file.getName());
            } catch (IOException ex) {
                aiStatusLabel.setText("Error saving: " + ex.getMessage());
            }
        }
    }


    private String langFor(ComboBox<String> combo) {
        FrameworkAdapter a = FrameworkRegistry.getInstance().get(combo.getValue());
        return a != null ? a.getSyntaxLanguage() : "java";
    }

    private void applyHighlighting(CodeArea area, String lang) {
        String code = area.getText();
        if (code == null || code.isEmpty()) return;
        area.setStyleSpans(0, computeHighlighting(code, lang));
    }

    private StyleSpans<Collection<String>> computeHighlighting(String text, String lang) {
        String[] KEYWORDS = {
            "abstract","assert","boolean","break","byte","case","catch","char","class",
            "const","continue","default","do","double","else","enum","extends","final",
            "finally","float","for","goto","if","implements","import","instanceof","int",
            "interface","long","native","new","package","private","protected","public",
            "return","short","static","strictfp","super","switch","synchronized","this",
            "throw","throws","transient","try","var","void","volatile","while","sealed",
            "permits","record","yield","null","true","false",
            "async","await","base","checked","decimal","delegate","dynamic","event",
            "explicit","extern","fixed","foreach","implicit","in","is","lock","namespace",
            "object","operator","out","override","params","partial","readonly","ref",
            "sbyte","sizeof","stackalloc","string","struct","typeof","unchecked","unsafe",
            "ushort","uint","ulong","using","virtual","when","where"
        };
        String KW_PATTERN   = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
        String STRING_PAT   = "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'";
        String COMMENT_PAT  = "//[^\n]*|/\\*.*?\\*/";
        String NUMBER_PAT   = "\\b\\d+(\\.\\d+)?[fFdDlL]?\\b";
        String PATTERN_STR  = "(?<COMMENT>" + COMMENT_PAT + ")"
                            + "|(?<STRING>" + STRING_PAT + ")"
                            + "|(?<KEYWORD>" + KW_PATTERN + ")"
                            + "|(?<NUMBER>" + NUMBER_PAT + ")";
        Pattern PATTERN = Pattern.compile(PATTERN_STR, Pattern.DOTALL);

        Matcher m = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int last = 0;
        while (m.find()) {
            String cls = m.group("KEYWORD") != null ? "hl-keyword"
                       : m.group("STRING")  != null ? "hl-string"
                       : m.group("COMMENT") != null ? "hl-comment"
                       : "hl-number";
            builder.add(Collections.emptyList(), m.start() - last);
            builder.add(Collections.singleton(cls), m.end() - m.start());
            last = m.end();
        }
        builder.add(Collections.emptyList(), text.length() - last);
        return builder.create();
    }

}
