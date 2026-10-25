package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Executes the real Stop method with fake native collaborators; not an Android device test. */
public class NativeStopTest {
    @Test public void failedInputAndFailedNeutralStillAttemptRemainingCleanup() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) {
            module = module.resolve("experiments/hid_probe");
        }
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        String activity = new String(Files.readAllBytes(source.resolve("ProbeActivity.java")), StandardCharsets.UTF_8);
        int begin = activity.indexOf("        void stop(String message) {");
        assertTrue("Run.stop must exist", begin >= 0);
        int end = activity.indexOf("        void finishGuard() {", begin);
        assertTrue("Stop extraction must end at finishGuard", end > begin);
        int scrollBegin = activity.indexOf("        final ScrollInput scrolling =");
        int scrollEnd = activity.indexOf("        void trace(", scrollBegin);
        assertTrue("Native scroll dispatch field must exist", scrollBegin >= 0 && scrollEnd > scrollBegin);
        String template;
        try (var input = getClass().getResourceAsStream("/StopCleanupHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempDirectory("nord-stop-regression-");
        try {
            Path harness = temporary.resolve("StopCleanupHarness.java");
            Files.write(harness, template.replace("__STOP_METHOD__", activity.substring(begin, end))
                .replace("__SCROLL_FIELD__", activity.substring(scrollBegin, scrollEnd)).getBytes(StandardCharsets.UTF_8));
            Process compiler = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "javac").toString(),
                "-d", temporary.toString(),
                harness.toString(), source.resolve("InputSubmission.java").toString(),
                source.resolve("TouchpadGesture.java").toString(), source.resolve("NativeRunGate.java").toString(),
                source.resolve("MouseReport.java").toString(), source.resolve("ScrollInput.java").toString())
                .redirectErrorStream(true).start();
            String diagnostics = new String(compiler.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(diagnostics, 0, compiler.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[] {temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.StopCleanupHarness")
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
