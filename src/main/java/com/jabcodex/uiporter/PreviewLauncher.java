package com.jabcodex.uiporter;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Compiles and launches generated JavaFX code in a separate JVM.
 *
 * Two modes:
 *   DEV (Maven) mode  - preview-runner/pom.xml is present (project checkout).
 *     Uses mvn compile + mvn javafx:run inside the preview-runner subproject.
 *   STANDALONE mode   - preview-runner/pom.xml is absent (jpackage app-image).
 *     Uses javax.tools.JavaCompiler + the bundled java executable.
 *     Source/classes are kept in ~/.uiporter/preview-work/.
 */
public class PreviewLauncher {

    // Target package and class inside preview-runner
    private static final String TARGET_PACKAGE   = "com.jabcodex.preview";
    private static final String TARGET_CLASS     = "PreviewApp";

    // ── Public entry point ─────────────────────────────────────────────────────

    /**
     * Normalizes {@code code}, writes it into the preview-runner project,
     * compiles it, then launches it in a new JVM process.
     *
     * Must be called from a background thread (compile is blocking).
     *
     * @throws PreviewException if the code is invalid or compilation fails.
     * @throws IOException      if file I/O fails.
     * @throws InterruptedException if the compile process is interrupted.
     */
    public static void launch(String code) throws PreviewException, IOException, InterruptedException {
        String trimmed = code.trim();
        // FXML content - wrap it in a minimal JavaFX Application that loads it via FXMLLoader
        if (trimmed.startsWith("<?xml") || (trimmed.startsWith("<") && !trimmed.startsWith("<java"))) {
            launchStandalone(buildFxmlWrapper(trimmed));
            return;
        }
        launchStandalone(normalizeCode(code));
    }

    /**
     * Generates a self-contained PreviewApp.java wrapper that embeds the given FXML
     * content as a string constant and loads it via FXMLLoader.
     * The fx:controller attribute is stripped so the preview doesn't require the
     * controller class to be on the classpath.
     */
    private static String buildFxmlWrapper(String fxmlContent) {
        // Strip fx:controller to avoid ClassNotFoundException in preview
        // Strip onXxx="#method" event handlers - no controller means no handlers
        String cleanFxml = fxmlContent
            .replaceAll("\\s+fx:controller\\s*=\\s*\"[^\"]*\"", "")
            .replaceAll("\\s+on[A-Za-z]+\\s*=\\s*\"#[^\"]*\"", "");

        // JavaFX 25 FXMLLoader cannot resolve PieChart.Data as an inner-class type.
        // Strip <data>...</data> blocks from PieChart elements and collect the data
        // items so we can add them programmatically after loader.load().
        StringBuilder pieInit = new StringBuilder();
        cleanFxml = extractAndStripPieChartData(cleanFxml, pieInit);

        // Escape for embedding in a Java string literal
        String escaped = cleanFxml
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r\n", "\\n")
            .replace("\n",   "\\n")
            .replace("\r",   "\\n");

        return "package " + TARGET_PACKAGE + ";\n"
            + "import javafx.application.Application;\n"
            + "import javafx.fxml.FXMLLoader;\n"
            + "import javafx.scene.Parent;\n"
            + "import javafx.scene.Scene;\n"
            + "import javafx.stage.Stage;\n"
            + "import java.io.ByteArrayInputStream;\n"
            + "import java.nio.charset.StandardCharsets;\n\n"
            + "public class " + TARGET_CLASS + " extends Application {\n"
            + "    private static final String FXML = \"" + escaped + "\";\n"
            + "    @Override\n"
            + "    public void start(Stage stage) throws Exception {\n"
            + "        FXMLLoader loader = new FXMLLoader();\n"
            + "        loader.setClassLoader(PreviewApp.class.getClassLoader());\n"
            + "        Parent root = loader.load(\n"
            + "            new ByteArrayInputStream(FXML.getBytes(StandardCharsets.UTF_8)));\n"
            + pieInit.toString()
            + "        stage.setScene(new Scene(root));\n"
            + "        stage.setTitle(\"FXML Preview\");\n"
            + "        stage.show();\n"
            + "    }\n"
            + "    public static void main(String[] args) { launch(args); }\n"
            + "}\n";
    }

    /**
     * Removes {@code <data>…</data>} blocks that contain {@code <PieChart.Data>}
     * children from the FXML string (JavaFX 25 cannot resolve the inner class via
     * FXML) and appends equivalent Java statements to {@code codeOut} so the data
     * is populated programmatically after the FXML is loaded.
     */
    private static String extractAndStripPieChartData(String fxml, StringBuilder codeOut) {
        java.util.regex.Pattern dataBlock = java.util.regex.Pattern.compile(
                "<data>(\\s*<PieChart\\.Data[\\s\\S]*?)</data>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Pattern dataItem = java.util.regex.Pattern.compile(
                "<PieChart\\.Data\\b([^>]*)/?>",
                java.util.regex.Pattern.DOTALL);
        java.util.regex.Pattern nameAttr  = java.util.regex.Pattern.compile("name=\"([^\"]*)\"");
        java.util.regex.Pattern valueAttr = java.util.regex.Pattern.compile("pieValue=\"([^\"]*)\"");
        java.util.regex.Pattern fxIdAttr  = java.util.regex.Pattern.compile("fx:id=\"([^\"]*)\"");

        java.util.regex.Matcher m = dataBlock.matcher(fxml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            // Determine the owning PieChart's fx:id by scanning backwards
            String before = fxml.substring(0, m.start());
            int lastPie = before.lastIndexOf("<PieChart");
            String chartId = "pieChart";        // sensible default
            if (lastPie >= 0) {
                String tagFragment = before.substring(lastPie,
                        Math.min(before.length(), lastPie + 500));
                java.util.regex.Matcher idm = fxIdAttr.matcher(tagFragment);
                if (idm.find()) chartId = idm.group(1);
            }

            // Collect PieChart.Data items from the block
            java.util.List<String[]> items = new java.util.ArrayList<>();
            java.util.regex.Matcher di = dataItem.matcher(m.group(1));
            while (di.find()) {
                java.util.regex.Matcher nm = nameAttr.matcher(di.group(1));
                java.util.regex.Matcher vm = valueAttr.matcher(di.group(1));
                if (nm.find() && vm.find())
                    items.add(new String[]{nm.group(1), vm.group(1)});
            }

            if (!items.isEmpty()) {
                codeOut.append("        { // PieChart data - added programmatically\n");
                codeOut.append("            javafx.scene.Node _n = root.lookup(\"#")
                       .append(chartId).append("\");\n");
                codeOut.append("            if (_n instanceof javafx.scene.chart.PieChart) {\n");
                codeOut.append("                javafx.scene.chart.PieChart _pc =")
                       .append(" (javafx.scene.chart.PieChart) _n;\n");
                for (String[] item : items) {
                    codeOut.append("                _pc.getData().add(")
                           .append("new javafx.scene.chart.PieChart.Data(\"")
                           .append(item[0]).append("\", ").append(item[1]).append("));\n");
                }
                codeOut.append("            }\n");
                codeOut.append("        }\n");
            }

            // Remove the <data>…</data> block from the FXML
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ── Launch ────────────────────────────────────────────────────────────────

    private static void launchStandalone(String normalized)
            throws PreviewException, IOException, InterruptedException {
        Path workDir    = resolveStandaloneWorkDir();
        Path sourceFile = workDir.resolve("PreviewApp.java");
        Path outputDir  = workDir.resolve("out");
        Files.createDirectories(outputDir);
        Files.writeString(sourceFile, normalized);

        compileStandalone(sourceFile, outputDir);
        runStandalone(outputDir);
    }

    private static Path resolveStandaloneWorkDir() throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".uiporter", "preview-work");
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Compiles PreviewApp.java using javax.tools (requires jdk.compiler module
     * to be present in the runtime image - ensured by build-release.bat).
     */
    private static void compileStandalone(Path sourceFile, Path outputDir)
            throws PreviewException {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new PreviewException(
                "Java compiler not available in this runtime.\n" +
                "The app must be packaged with --add-modules jdk.compiler,java.compiler.");
        }

        String modulePath = findJavaFxModulePath();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (javax.tools.StandardJavaFileManager fm =
                compiler.getStandardFileManager(diagnostics, null, null)) {

            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjects(sourceFile.toFile());
            List<String> options = List.of(
                "--module-path", modulePath,
                "--add-modules", "javafx.controls,javafx.fxml",
                "-d", outputDir.toString()
            );

            boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();
            if (!ok) {
                String errors = diagnostics.getDiagnostics().stream()
                    .filter(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
                throw new PreviewException("Compilation failed:\n\n" + errors);
            }
        } catch (IOException e) {
            throw new PreviewException("Compiler file manager error: " + e.getMessage());
        }
    }

    private static void runStandalone(Path outputDir) throws PreviewException, IOException, InterruptedException {
        String modulePath = findJavaFxModulePath();
        String javaExe   = findJavaExe();

        List<String> cmd = new ArrayList<>();
        cmd.add(javaExe);
        cmd.add("--module-path");
        cmd.add(modulePath);
        cmd.add("--add-modules");
        cmd.add("javafx.controls,javafx.fxml");
        cmd.add("-cp");
        cmd.add(outputDir.toString());
        cmd.add(TARGET_PACKAGE + "." + TARGET_CLASS);

        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();

        // Drain output in a background thread so the pipe buffer never fills and blocks the process.
        // Also capture it so we can report the error if the process exits immediately.
        StringBuilder output = new StringBuilder();
        Thread drainer = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) output.append(line).append('\n');
            } catch (IOException ignored) {}
        });
        drainer.setDaemon(true);
        drainer.start();

        // Give the process 6 seconds. If it is still running → window opened successfully.
        // If it already exited with a non-zero code → it crashed; surface the output as an error.
        boolean exited = proc.waitFor(6, java.util.concurrent.TimeUnit.SECONDS);
        if (exited && proc.exitValue() != 0) {
            drainer.join(1500);
            throw new PreviewException("Preview process failed (exit " + proc.exitValue() + "):\n\n"
                    + output.toString().trim());
        }
    }

    /**
     * Finds a usable java executable for the preview subprocess.
     * Checks (in order):
     *   1. java.home/bin/java[.exe]  - works when running from a full JDK;
     *      jpackage bundles a stripped JRE that may not have this file.
     *   2. JAVA_HOME env var
     *   3. java / java.exe on the system PATH
     */
    private static String findJavaExe() throws PreviewException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String exeName = isWindows ? "java.exe" : "java";

        // 1. java.home/bin/
        Path fromJavaHome = Path.of(System.getProperty("java.home"), "bin", exeName);
        if (Files.isExecutable(fromJavaHome)) return fromJavaHome.toString();

        // 2. JAVA_HOME env var
        String javaHomeEnv = System.getenv("JAVA_HOME");
        if (javaHomeEnv != null) {
            Path candidate = Path.of(javaHomeEnv, "bin", exeName);
            if (Files.isExecutable(candidate)) return candidate.toString();
        }

        // 3. java on PATH
        if (tryJavaExe("java"))        return "java";
        if (isWindows && tryJavaExe("java.exe")) return "java.exe";

        throw new PreviewException(
            "Cannot find a java executable for the preview subprocess.\n" +
            "Make sure a JDK is installed and either JAVA_HOME is set or java is on your PATH.");
    }

    private static boolean tryJavaExe(String cmd) {
        try {
            Process p = new ProcessBuilder(cmd, "-version")
                    .redirectErrorStream(true).start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Finds all javafx-*.jar files needed by the preview subprocess.
     *
     * 1. jpackage app-image: scans app/javafx-mods/ (bundled by build-release.bat).
     * 2. Dev mode: scans jdk.module.path entries (set by the Maven JavaFX plugin).
     */
    private static String findJavaFxModulePath() throws PreviewException {
        // 1. app-image: javafx JARs bundled in app/javafx-mods/
        Path appDir = App.PROJECT_ROOT != null ? App.PROJECT_ROOT.resolve("app") : null;
        if (appDir != null && Files.isDirectory(appDir.resolve("javafx-mods"))) {
            try (Stream<Path> listing = Files.list(appDir.resolve("javafx-mods"))) {
                String joined = listing
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.startsWith("javafx-") && n.endsWith(".jar");
                    })
                    .map(Path::toString)
                    .collect(Collectors.joining(File.pathSeparator));
                if (!joined.isEmpty()) return joined;
            } catch (IOException e) {
                throw new PreviewException("Failed to scan javafx-mods/: " + e.getMessage());
            }
        }

        // 2. Dev mode: scan jdk.module.path (populated by Maven JavaFX plugin)
        String modulePath = System.getProperty("jdk.module.path");
        if (modulePath != null) {
            String joined = java.util.Arrays.stream(
                        modulePath.split(Pattern.quote(File.pathSeparator)))
                .flatMap(entry -> {
                    Path p = Path.of(entry);
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (Files.isRegularFile(p) && n.startsWith("javafx-") && n.endsWith(".jar"))
                        return Stream.of(p);
                    if (Files.isDirectory(p)) {
                        try (Stream<Path> ls = Files.list(p)) {
                            return ls.filter(f -> {
                                String fn = f.getFileName().toString().toLowerCase(Locale.ROOT);
                                return fn.startsWith("javafx-") && fn.endsWith(".jar");
                            }).collect(Collectors.toList()).stream();
                        } catch (IOException ignored) { return Stream.empty(); }
                    }
                    return Stream.empty();
                })
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
            if (!joined.isEmpty()) return joined;
        }

        throw new PreviewException(
            "Cannot locate JavaFX module JARs.\n" +
            "In release mode, re-run build-release.bat to regenerate app/javafx-mods/.\n" +
            "In dev mode, launch via 'mvn javafx:run' so jdk.module.path is set.");
    }

    // ── Code normalization ─────────────────────────────────────────────────────

    private static String normalizeCode(String code) throws PreviewException {
        // Must extend Application
        if (!code.contains("extends Application")) {
            throw new PreviewException(
                "The generated code doesn't appear to be a runnable JavaFX Application.\n" +
                "It must contain a class that extends Application with a start() method."
            );
        }

        // Capture original class name
        Matcher m = Pattern.compile("public\\s+class\\s+(\\w+)\\s+extends\\s+Application")
                           .matcher(code);
        String originalName = m.find() ? m.group(1) : null;

        // 1. Strip any existing package declaration
        code = code.replaceAll("(?m)^\\s*package\\s+[\\w.]+\\s*;\\s*\\R?", "");

        // 2. Prepend the target package
        code = "package " + TARGET_PACKAGE + ";\n\n" + code.stripLeading();

        // 3. Rename the public class to PreviewApp
        code = code.replaceAll(
            "public\\s+class\\s+\\w+\\s+extends\\s+Application",
            "public class " + TARGET_CLASS + " extends Application"
        );

        // 4. Rename original class references (e.g. OriginalName.launch(...))
        if (originalName != null && !originalName.equals(TARGET_CLASS)) {
            code = code.replace(originalName + ".launch(",  TARGET_CLASS + ".launch(");
            code = code.replace(originalName + ".main(",    TARGET_CLASS + ".main(");
        }

        // 5. Inject main() if missing (AI output can omit it)
        if (!code.contains("public static void main")) {
            int lastBrace = code.lastIndexOf('}');
            if (lastBrace >= 0) {
                code = code.substring(0, lastBrace)
                     + "\n    public static void main(String[] args) { launch(args); }\n}\n";
            }
        }

        return code;
    }

    // ── Exception type ────────────────────────────────────────────────────────

    /** Thrown for expected preview errors (compile failure, bad code, missing dirs). */
    public static class PreviewException extends Exception {
        public PreviewException(String message) { super(message); }
    }
}
