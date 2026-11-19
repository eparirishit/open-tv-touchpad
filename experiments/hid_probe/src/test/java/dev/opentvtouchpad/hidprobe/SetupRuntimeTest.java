package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Executes production setup handoff/result methods with real cleanup and lifetime kernels. */
public class SetupRuntimeTest {
    private String method(String source, String signature) {
        int start=source.indexOf(signature); assertTrue("Missing wizard behavior: " + signature,start>=0);
        int end=source.indexOf('{',start)+1, depth=1;
        while(depth>0){char c=source.charAt(end++);if(c=='{')depth++;if(c=='}')depth--;}
        return source.substring(start,end);
    }
    @Test public void setupTransitionsRequireCurrentIntentAndActualCleanup() throws Exception {
        Path module=Path.of("").toAbsolutePath();
        if(!Files.isDirectory(module.resolve("src/main/java")))module=module.resolve("experiments/hid_probe");
        Path source=module.resolve("src/main/java/dev/opentvtouchpad/hidprobe");
        String runtime=new String(Files.readAllBytes(source.resolve("RemoteRuntime.java")),StandardCharsets.UTF_8);
        String template;
        try(var input=getClass().getResourceAsStream("/SetupRuntimeHarness.java.template")) {
            assertNotNull(input); template=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        String methods="";
        for(String signature:new String[]{
                "    boolean takeDiscoverabilityRequest(long token, long generation) {",
                "    void discoveryResult(long token, long generation, int grantedSeconds) {",
                "    private void setupRegistered(long generation) {",
                "    private void bluetoothSetupConnected(long generation) {",
                "    private void advanceSetupCleanup() {",
                "    private void refreshEligibility() {",
                "    private void refreshSetupEligibility() {",
                "    private void startWizardRun(SetupIntent intent) {",
                "    private void closeSetup(SetupState.Page target, String message) {",
                "    private void completePendingTvForget() {"}) methods += method(runtime,signature) + "\n";
        Path temporary=Files.createTempDirectory("open-tv-setup-runtime-");
        try {
            Path java=temporary.resolve("SetupRuntimeHarness.java");Files.write(java,template.replace("__METHODS__",methods).getBytes(StandardCharsets.UTF_8));
            Process compiler=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","javac").toString(),
                "-d",temporary.toString(),java.toString(),source.resolve("NativeRunGate.java").toString(),
                source.resolve("SessionLifetime.java").toString(),source.resolve("InputVisibilityGate.java").toString(),source.resolve("SetupState.java").toString()).redirectErrorStream(true).start();
            String output=new String(compiler.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(output,0,compiler.waitFor());
            try(var loader=new URLClassLoader(new java.net.URL[]{temporary.toUri().toURL()},null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.SetupRuntimeHarness").getMethod("main",String[].class).invoke(null,(Object)new String[0]);
            }
        } finally {
            try(var files=Files.walk(temporary)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
}
