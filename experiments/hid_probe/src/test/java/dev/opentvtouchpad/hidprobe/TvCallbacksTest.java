package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Execute the runtime's actual callback bridge with queued main-thread delivery. */
public class TvCallbacksTest {
    @Test public void delayedResultsCannotEnrollAfterStopExpiryOrNewOwner() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) module = module.resolve("experiments/hid_probe");
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        assertTrue("Service-owned runtime must exist", Files.isRegularFile(source.resolve("RemoteRuntime.java")));
        String runtime = new String(Files.readAllBytes(source.resolve("RemoteRuntime.java")),
            java.nio.charset.StandardCharsets.UTF_8);
        int begin = runtime.indexOf("        TvRemoteClient createTv() {");
        int end = runtime.indexOf("        void connectSelected(", begin);
        assertTrue(begin >= 0 && end > begin);
        int forgetBegin = runtime.indexOf("    private void forgetTv() {");
        int forgetEnd = runtime.indexOf("    private void tvCommand(", forgetBegin);
        assertTrue(forgetBegin >= 0 && forgetEnd > forgetBegin);
        String template;
        try (var input = getClass().getResourceAsStream("/TvCallbacksHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempDirectory("nord-tv-callbacks-");
        try {
            Path harness = temporary.resolve("TvCallbacksHarness.java");
            Files.write(harness, template.replace("__CALLBACK_METHOD__", runtime.substring(begin, end))
                .replace("__FORGET_METHODS__", runtime.substring(forgetBegin, forgetEnd))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Process compiler = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-d", temporary.toString(), harness.toString(), source.resolve("NativeRunGate.java").toString())
                .redirectErrorStream(true).start();
            String diagnostic = new String(compiler.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(diagnostic, 0, compiler.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.TvCallbacksHarness")
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
