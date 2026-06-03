package com.jabcodex.uiporter;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import javax.imageio.ImageIO;

/**
 * Handles all AI API calls (Gemini).
 * Stateless utility class - call {@link #callApi} from a background thread.
 */
public class AIService {

    public static final String MODE_IMAGE_TO_UI   = "Image upload to UI code";
    public static final String MODE_IMPROVE_UI    = "Improve existing UI code";
    public static final String MODE_CONSOLE_TO_UI = "Console app to GUI app";
    public static final String MODE_PROMPT_TO_UI  = "Generate UI from text prompt";

    private static final String ENDPOINT_TEMPLATE =
            "https://aiplatform.googleapis.com/v1/publishers/google/models/%s:generateContent?key=";

    // Shared system instruction injected into every request
    private static final String SYSTEM_INSTRUCTION =
        "You are an expert JavaFX UI developer. Your sole job is to produce complete, "
        + "compilable, runnable JavaFX 17+ source code - a single .java file with a "
        + "public class extending Application. Rules you MUST follow:\n"
        + "1. Output ONLY the raw Java source code - no markdown fences, no prose, no "
        + "   explanation before or after the code.\n"
        + "2. Include all necessary imports (javafx.application.*, javafx.scene.*, "
        + "   javafx.stage.*, javafx.geometry.*, javafx.scene.control.*, "
        + "   javafx.scene.layout.*, javafx.scene.paint.*, etc.).\n"
        + "3. Use modern JavaFX patterns: setStyle() with inline CSS, VBox/HBox/BorderPane/"
        + "   StackPane/GridPane for layout, Scene, Stage.\n"
        + "4. Provide rich CSS styling: dark/glass themes, gradients, shadows, rounded "
        + "   corners, hover effects where appropriate.\n"
        + "5. The class name must be 'GeneratedApp' and it must have a "
        + "   public static void main(String[] args) { launch(args); } method.\n"
        + "6. Make the UI visually polished - readable fonts, consistent spacing, "
        + "   professional color palette.\n"
        + "7. Do NOT include placeholder comments like '// TODO' or '// add your code here'.\n"
        + "8. CRITICAL - JavaFX API correctness (these are compile-time errors if wrong):\n"
        + "   - FontWeight has NO 'ITALIC' value. Valid FontWeight values: THIN, EXTRA_LIGHT,\n"
        + "     LIGHT, NORMAL, MEDIUM, SEMI_BOLD, BOLD, EXTRA_BOLD, BLACK.\n"
        + "     For italic text use FontPosture.ITALIC with Font.font(..., FontPosture.ITALIC, size).\n"
        + "   - Color has NO 'TRANSPARENT' field (use Color.TRANSPARENT only - it is a static field,\n"
        + "     not an enum). Or use Color.web(\"transparent\") / Color.rgb(0,0,0,0).\n"
        + "   - Never use deprecated Timeline/KeyFrame constructors; use new KeyFrame(Duration.millis(X), e -> {}).\n"
        + "   - TableColumn generic type must match TableView: TableView<MyType> requires TableColumn<MyType, ?>.\n"
        + "   - Do NOT call getChildren().add() on controls that are not Pane subclasses (e.g. Button, Label).\n"
        + "   - Tooltip.install() is valid; do NOT use node.setTooltip() on non-Control nodes.\n"
        + "   - Always verify every enum constant and static field name is exact before using it.\n";

    // Styling rules appended to every user prompt
    private static final String STYLE_INSTRUCTION =
        "\n\nuse simple style methods which are easy to read, do not use getStylesheets or any method like that keep it simple. "
        + "set the styles directly no need to create seperate string for style aswell and setstyle seperately for every element. "
        + "DO NOT CREATE String for Style";

    private AIService() {}

    // ── Public entry point ─────────────────────────────────────────────────────

    /**
     * Sends a request to the Gemini API and returns the generated code string.
     * Must be called from a background thread.
     *
     * @param targetFramework hint for the output format (e.g. "JavaFX", "FXML")
     * @throws IllegalArgumentException if apiKey is blank
     * @throws Exception on network / HTTP errors
     */
    public static String callApi(String mode, String imagePath, String mainText,
                                 String instructions, String apiKey) throws Exception {
        return callApi(mode, imagePath, mainText, instructions, apiKey, "JavaFX");
    }

    public static String callApi(String mode, String imagePath, String mainText,
                                 String instructions, String apiKey,
                                 String targetFramework) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                "No API key configured. Add your Gemini key in Settings → AI.");
        }

        String model       = AppSettings.get("aiModel", "gemini-3-flash-preview");
        String endpoint    = String.format(ENDPOINT_TEMPLATE, model) + apiKey;
        String userPrompt  = buildPrompt(mode, mainText, instructions, targetFramework);
        String escapedSys  = escapeJson(SYSTEM_INSTRUCTION);
        String escapedUser = escapeJson(userPrompt);

        StringBuilder parts = new StringBuilder("[");
        parts.append("{\"text\": \"").append(escapedUser).append("\"}");

        if (mode.equals(MODE_IMAGE_TO_UI) && imagePath != null && !imagePath.isEmpty()) {
            byte[] imageBytes  = compressImage(new File(imagePath), 1280);
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            parts.append(", {\"inlineData\": {\"mimeType\": \"image/jpeg\",\"data\": \"")
                 .append(base64Image).append("\"}}");
        }
        parts.append("]");

        String jsonPayload = """
            {
              "system_instruction": {
                "parts": [{"text": "%s"}]
              },
              "contents": [{
                "role": "user",
                "parts": %s
              }],
              "generationConfig": {
                "temperature": 0.35,
                "topP": 0.95,
                "maxOutputTokens": 65535
              }
            }
            """.formatted(escapedSys, parts.toString());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            return "Error (HTTP " + response.statusCode() + "):\n" + response.body();
        }
        return parseResponse(response.body());
    }

    // ── Prompt builder ─────────────────────────────────────────────────────────

    private static String buildPrompt(String mode, String mainText, String instructions,
                                      String targetFramework) {
        boolean wantFxml = "FXML".equalsIgnoreCase(targetFramework);
        String outputNote = wantFxml
            ? "Output an FXML file (not Java code). Use correct FXML namespace declarations."
            : "Output a single complete Java file (class GeneratedApp extends Application).";

        return switch (mode) {
            case MODE_IMAGE_TO_UI -> {
                String p = "Reproduce the UI shown in the attached screenshot as a complete JavaFX application. "
                    + "Match the layout, spacing, colors, and component types as closely as possible. "
                    + outputNote;
                if (instructions != null && !instructions.isBlank())
                    p += "\n\nAdditional requirements: " + instructions;
                p += STYLE_INSTRUCTION;
                yield p;
            }
            case MODE_IMPROVE_UI -> {
                String p = "You are redesigning a JavaFX application. Take the source code below and "
                    + "modernize it: improve the visual hierarchy, use a cohesive color palette, "
                    + "add CSS styling (glassmorphism, gradients, drop shadows, rounded corners), "
                    + "improve spacing and typography, and make the overall design look professional "
                    + "and polished. Preserve all existing functionality and data.\n"
                    + outputNote;
                if (instructions != null && !instructions.isBlank())
                    p += "\n\nSpecific improvements requested: " + instructions;
                p += STYLE_INSTRUCTION;
                p += "\n\n--- SOURCE CODE TO IMPROVE ---\n" + mainText;
                yield p;
            }
            case MODE_CONSOLE_TO_UI -> {
                String p = "Convert the following console-based Java application into a modern, "
                    + "fully functional JavaFX GUI application. Map console prompts to labeled "
                    + "TextFields, print statements to Labels/TextAreas, and menu-driven choices "
                    + "to Buttons or ComboBoxes. Add a professional dark theme with CSS styling. "
                    + "Preserve all business logic exactly as-is - just add a JavaFX GUI wrapper.\n"
                    + outputNote;
                if (instructions != null && !instructions.isBlank())
                    p += "\n\nStyling and layout preferences: " + instructions;
                p += STYLE_INSTRUCTION;
                p += "\n\n--- CONSOLE APPLICATION SOURCE ---\n" + mainText;
                yield p;
            }
            case MODE_PROMPT_TO_UI -> {
                String p = "Create a complete JavaFX application based on the following description. "
                    + "The UI should be modern, visually polished, and fully functional with realistic "
                    + "placeholder data where appropriate. Use a professional dark or semi-dark theme "
                    + "with CSS inline styling, smooth layout, proper padding, and readable fonts.\n"
                    + outputNote
                    + "\n\nDescription: " + mainText;
                if (instructions != null && !instructions.isBlank())
                    p += "\n\nAdditional constraints: " + instructions;
                p += STYLE_INSTRUCTION;
                yield p;
            }
            default -> "Generate a JavaFX UI application. " + outputNote;
        };
    }

    // ── JSON helpers ───────────────────────────────────────────────────────────

    static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    static String parseResponse(String responseBody) {
        try {
            // Step 1: Extract raw text value from JSON "text" field
            String rawText = extractJsonTextField(responseBody);
            if (rawText == null) return "No code found in response.\n\nRaw:\n" + responseBody;

            // Step 2: Strip markdown code fences if present
            String code = stripCodeFences(rawText).trim();

            // Step 3: If we got something that looks like code, return it
            if (looksLikeCode(code)) return code;

            // Step 4: Fallback - return raw text as-is
            return rawText.trim();
        } catch (Exception e) {
            return "Parsing error: " + e.getMessage() + "\n\nRaw Response:\n" + responseBody;
        }
    }

    /** Reads the first "text" string value from a Gemini JSON response. */
    private static String extractJsonTextField(String json) {
        String lookFor = "\"text\":";
        int pos = 0;
        while (pos < json.length()) {
            int idx = json.indexOf(lookFor, pos);
            if (idx == -1) return null;
            // skip whitespace after colon
            int valStart = idx + lookFor.length();
            while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;
            if (valStart >= json.length() || json.charAt(valStart) != '"') {
                pos = idx + 1; continue;
            }
            valStart++; // skip opening quote
            // Read until unescaped closing quote
            StringBuilder sb = new StringBuilder();
            for (int i = valStart; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(++i);
                    switch (next) {
                        case 'n'  -> sb.append('\n');
                        case 't'  -> sb.append('\t');
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'r'  -> sb.append('\r');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u'  -> {
                            if (i + 4 < json.length()) {
                                try {
                                    sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                    i += 4;
                                } catch (NumberFormatException ignored) { sb.append("\\u"); }
                            } else sb.append("\\u");
                        }
                        default -> sb.append(next);
                    }
                } else if (c == '"') {
                    return sb.toString();
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * Strips markdown code fences from a string, returning the largest code block found.
     * Handles ```java, ```javafx, ``` (plain), and text surrounding blocks.
     */
    static String stripCodeFences(String text) {
        if (text == null) return "";
        // Find all fenced code blocks and return the largest one
        java.util.List<String> blocks = new java.util.ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int fence = text.indexOf("```", i);
            if (fence == -1) break;
            // Skip the fence marker and optional language tag
            int lineEnd = text.indexOf('\n', fence);
            if (lineEnd == -1) { i = fence + 3; continue; }
            int blockStart = lineEnd + 1;
            int blockEnd = text.indexOf("```", blockStart);
            if (blockEnd == -1) { i = blockStart; continue; }
            String block = text.substring(blockStart, blockEnd).trim();
            if (!block.isEmpty()) blocks.add(block);
            i = blockEnd + 3;
        }
        if (!blocks.isEmpty()) {
            // Return the largest block (most likely the full class)
            return blocks.stream().max(java.util.Comparator.comparingInt(String::length)).orElse(text);
        }
        return text;
    }

    private static boolean looksLikeCode(String text) {
        if (text == null || text.isBlank()) return false;
        return text.contains("class ") || text.contains("import ") || text.contains("<?xml");
    }

    // ── Image helper ───────────────────────────────────────────────────────────

    static byte[] compressImage(File file, int maxWidth) throws IOException {
        BufferedImage original = ImageIO.read(file);
        if (original == null) throw new IOException("Invalid image file.");
        int width  = original.getWidth();
        int height = original.getHeight();
        if (width > maxWidth) {
            double scale = (double) maxWidth / width;
            width  = maxWidth;
            height = (int) (height * scale);
        }
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, width, height, null);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resized, "jpg", baos);
        return baos.toByteArray();
    }
}
