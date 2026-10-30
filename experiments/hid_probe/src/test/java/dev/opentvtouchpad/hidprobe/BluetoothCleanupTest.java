package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Runs the runtime's real cleanup callbacks with unavailable-proxy defaults. */
public class BluetoothCleanupTest {
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing production method: " + signature, start >= 0);
        int end = source.indexOf('{', start) + 1;
        int depth = 1;
        while (depth > 0) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        return source.substring(start, end);
    }

    @Test public void callbackOrderRequiresActualCleanupBeforeAnotherRun() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) module = module.resolve("experiments/hid_probe");
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        assertTrue("Service-owned runtime must exist", Files.isRegularFile(source.resolve("RemoteRuntime.java")));
        String runtime = new String(Files.readAllBytes(source.resolve("RemoteRuntime.java")), java.nio.charset.StandardCharsets.UTF_8);
        String template;
        try (var input = getClass().getResourceAsStream("/BluetoothCleanupHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        String[] signatures = {
            "        void stop(String message) {", "        void pendingLink(BluetoothDevice peer) {", "        void finishProfile() {",
            "        void cleanupProgress() {", "public void onServiceDisconnected(int profile) {",
            "public void onAppStatusChanged(BluetoothDevice plugged, boolean registered) {",
            "public void onConnectionStateChanged(BluetoothDevice peer, int state) {",
            "        void connect(BluetoothDevice peer) {"
        };
        StringBuilder methods = new StringBuilder();
        for (String signature : signatures) methods.append(method(runtime, signature)).append('\n');
        Path temporary = Files.createTempDirectory("nord-bluetooth-cleanup-");
        try {
            Path harness = temporary.resolve("BluetoothCleanupHarness.java");
            Files.write(harness, template.replace("__METHODS__", methods).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Process compiler = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-d", temporary.toString(), harness.toString(), source.resolve("SetupState.java").toString(), source.resolve("NativeRunGate.java").toString(), source.resolve("InputVisibilityGate.java").toString(), source.resolve("SessionLifetime.java").toString(),
                source.resolve("ProbeTrace.java").toString(), source.resolve("MouseReport.java").toString())
                .redirectErrorStream(true).start();
            String output = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(output, 0, compiler.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.BluetoothCleanupHarness")
                    .getMethod("main", String[].class).invoke(null, (Object)new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
