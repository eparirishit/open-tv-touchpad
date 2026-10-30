package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Runs production attachment, pause, detach and service completion with the real session/gate. */
public class RemoteOwnershipTest {
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing production method: " + signature, start >= 0);
        int end = source.indexOf('{', start) + 1, depth = 1;
        while (depth > 0) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        return source.substring(start, end);
    }

    @Test public void detachedScreensCannotAuthorizeAndServiceDeathWaitsForWorkers() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) module = module.resolve("experiments/hid_probe");
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        assertTrue("Service-owned runtime must exist", Files.isRegularFile(source.resolve("RemoteRuntime.java")));
        String runtime = new String(Files.readAllBytes(source.resolve("RemoteRuntime.java")), java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder methods = new StringBuilder();
        for (String signature : new String[] {"    private boolean attended() {", "    private void setInputEligible(boolean eligible) {",
                "    private void refreshEligibility() {", "    private void deferVisibilityRefresh() {", "    void uiState(long token, boolean focused, boolean surfaceVisible) {", "    long attach() {", "    boolean attached(long token) {",
                "    void foreground(long token, boolean foreground) {", "    void detach(long token) {",
                "    void destroyed() {", "    void serviceStopped(String reason) {", "    void connectionLost(long token) {"}) methods.append(method(runtime, signature)).append('\n');
        String template;
        try (var input = getClass().getResourceAsStream("/RemoteOwnershipHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempDirectory("nord-ownership-");
        try {
            Path harness = temporary.resolve("RemoteOwnershipHarness.java");
            Files.write(harness, template.replace("__METHODS__", methods).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Process compiler = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-d", temporary.toString(), harness.toString(), source.resolve("NativeRunGate.java").toString(), source.resolve("InputVisibilityGate.java").toString(), source.resolve("SessionLifetime.java").toString(),
                source.resolve("ProbeSession.java").toString(), source.resolve("EnrollmentStore.java").toString())
                .redirectErrorStream(true).start();
            String diagnostic = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(diagnostic, 0, compiler.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.RemoteOwnershipHarness")
                    .getMethod("main", String[].class).invoke(null, (Object)new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
