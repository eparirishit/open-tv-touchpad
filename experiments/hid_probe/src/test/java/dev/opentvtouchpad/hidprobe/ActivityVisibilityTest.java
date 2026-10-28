package dev.opentvtouchpad.hidprobe;

import org.junit.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URLClassLoader;
import java.util.Comparator;
import static org.junit.Assert.*;

/** Executes actual Activity loss callbacks with a snapshot that blocks/throws if requested inline. */
public class ActivityVisibilityTest {
    private String method(String source,String signature) {
        int start=source.indexOf(signature); assertTrue(signature,start>=0);
        int end=source.indexOf('{',start)+1,depth=1;
        while(depth>0) { char c=source.charAt(end++); if(c=='{')depth++;if(c=='}')depth--; }
        return source.substring(start,end);
    }
    @Test public void pauseAndFocusLossNeverFetchHealthSnapshotsBeforeReturning() throws Exception {
        Path module=Path.of("").toAbsolutePath();
        if(!Files.isDirectory(module.resolve("src/main/java")))module=module.resolve("experiments/hid_probe");
        Path source=module.resolve("src/main/java/dev/opentvtouchpad/hidprobe/ProbeActivity.java");
        String activity=new String(Files.readAllBytes(source),StandardCharsets.UTF_8);
        String template;
        try(var input=getClass().getResourceAsStream("/ActivityVisibilityHarness.java.template")) {
            assertNotNull(input);template=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
        String pauseControls=activity.contains("    private void pauseControls() {")
            ?method(activity,"    private void pauseControls() {"):"private void pauseControls() {}";
        Path temp=Files.createTempDirectory("nord-activity-visibility-");
        try {
            Path java=temp.resolve("ActivityVisibilityHarness.java");
            Files.write(java,template.replace("__PAUSE_CONTROLS__",pauseControls)
                .replace("__ON_PAUSE__",method(activity,"    @Override protected void onPause() {"))
                .replace("__ON_FOCUS__",method(activity,"    @Override public void onWindowFocusChanged(boolean hasFocus) {"))
                .getBytes(StandardCharsets.UTF_8));
            Process compiler=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","javac").toString(),
                "-d",temp.toString(),java.toString()).redirectErrorStream(true).start();
            String diagnostics=new String(compiler.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
            assertEquals(diagnostics,0,compiler.waitFor());
            try(var loader=new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},null)) {
                loader.loadClass("dev.opentvtouchpad.hidprobe.ActivityVisibilityHarness").getMethod("main",String[].class)
                    .invoke(null,(Object)new String[0]);
            }
        } finally {
            try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
        }
    }
}
