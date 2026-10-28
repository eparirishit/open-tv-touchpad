package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Executes the runtime's real visibility/attachment methods with native observations controlled. */
public class RemoteVisibilityTest {
    private String method(String source,String signature) {
        int start=source.indexOf(signature);assertTrue(signature,start>=0);
        int end=source.indexOf('{',start)+1,depth=1;
        while(depth>0){char c=source.charAt(end++);if(c=='{')depth++;if(c=='}')depth--;}
        return source.substring(start,end);
    }

    @Test public void hiddenAndStaleScreensCannotAuthorizeRetainedConnections() throws Exception {
        Path module = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(module.resolve("src/main/java"))) module = module.resolve("experiments/hid_probe");
        Path source = module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        String runtime = new String(Files.readAllBytes(source.resolve("RemoteRuntime.java")), StandardCharsets.UTF_8);
        int begin = runtime.indexOf("    private boolean attended() {");
        int end = runtime.indexOf("    void connectionLost(long token)", begin);
        assertTrue("Runtime visibility policy must exist", begin >= 0 && end > begin);
        String template;
        try (var input = getClass().getResourceAsStream("/RemoteVisibilityHarness.java.template")) {
            assertNotNull(input);
            template = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path temporary = Files.createTempDirectory("nord-visibility-");
        try {
            Path harness = temporary.resolve("RemoteVisibilityHarness.java");
            Files.write(harness, template.replace("__POLICY__", runtime.substring(begin, end)).replace("__CONNECT_REMOTE__", method(runtime,"    private void connectRemote() {"))
                .replace("__REQUEST__", method(runtime,"    boolean request(long token, Request request, String value) {")).getBytes(StandardCharsets.UTF_8));
            var command = new java.util.ArrayList<String>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "javac").toString());
            command.add("-d"); command.add(temporary.toString()); command.add(harness.toString());
            for (String name : new String[]{"InputVisibilityGate", "SessionLifetime"}) command.add(source.resolve(name + ".java").toString());
            Process compile = new ProcessBuilder(command).redirectErrorStream(true).start();
            String diagnostics = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(diagnostics, 0, compile.waitFor());
            try (var loader = new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()}, null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.RemoteVisibilityHarness")
                    .getMethod("main", String[].class).invoke(null, (Object)new String[0]);
            }
        } finally {
            try (var files = Files.walk(temporary)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
            }
        }
    }
}
