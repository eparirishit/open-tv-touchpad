package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Executes service deadline/Stop/task/death methods; timers never stand in for native completion. */
public class RemoteServiceLifecycleTest {
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

    @Test public void stopAndElapsedDeadlineRequireDestructionAndActualResourceCompletion() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) module = module.resolve("experiments/hid_probe");
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        String service = new String(Files.readAllBytes(source.resolve("RemoteConnectionService.java")), java.nio.charset.StandardCharsets.UTF_8);
        StringBuilder methods = new StringBuilder();
        for (String signature : new String[] {"    boolean isAlive() {", "    void finish(String reason) {",
                "public int onStartCommand(Intent intent, int flags, int startId) {",
                "public void onTaskRemoved(Intent rootIntent) {", "public void onDestroy() {"})
            methods.append(method(service, signature)).append('\n');
        String template;
        try (var input = getClass().getResourceAsStream("/RemoteServiceLifecycleHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempDirectory("nord-service-lifecycle-");
        try {
            Path harness = temporary.resolve("RemoteServiceLifecycleHarness.java");
            Files.write(harness, template.replace("__METHODS__", methods).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Process compiler = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-d", temporary.toString(), harness.toString(), source.resolve("NativeRunGate.java").toString())
                .redirectErrorStream(true).start();
            String diagnostic = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(diagnostic, 0, compiler.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.RemoteServiceLifecycleHarness")
                    .getMethod("main", String[].class).invoke(null, (Object)new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
