package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real native views with fake input callbacks; no Bluetooth or device enrollment. */
@RunWith(AndroidJUnit4.class)
public class RemoteScreenTest {
    private Context context(int width, int height, float fontScale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.screenWidthDp = width;
        c.screenHeightDp = height;
        c.orientation = width > height ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        c.fontScale = fontScale;
        return new ContextThemeWrapper(base.createConfigurationContext(c), R.style.RemoteTheme);
    }

    private void layout(RemoteScreen screen, Context context, int width, int height) {
        float density = context.getResources().getDisplayMetrics().density;
        screen.root.measure(View.MeasureSpec.makeMeasureSpec((int)(width * density), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((int)(height * density), View.MeasureSpec.EXACTLY));
        screen.root.layout(0, 0, screen.root.getMeasuredWidth(), screen.root.getMeasuredHeight());
    }

    private void reachable(RemoteScreen screen, Button button) {
        assertEquals(View.VISIBLE, button.getVisibility());
        assertTrue(button.getText().length() > 0);
        float density = button.getResources().getDisplayMetrics().density;
        assertTrue("48dp target: " + button.getText(), button.getHeight() >= (int)(48 * density));
        View parent = (View)button.getParent();
        while (parent != screen.root && !(parent instanceof ScrollView)) parent = (View)parent.getParent();
        if (parent instanceof ScrollView scroll) {
            Rect inside = new Rect(0, 0, button.getWidth(), button.getHeight());
            scroll.offsetDescendantRectToMyCoords(button, inside);
            scroll.scrollTo(0, inside.bottom - scroll.getHeight());
            assertTrue("inside scroll viewport: " + button.getText(),
                inside.top >= scroll.getScrollY() && inside.bottom <= scroll.getScrollY() + scroll.getHeight());
        }
        Rect bounds = new Rect(0, 0, button.getWidth(), button.getHeight());
        screen.root.offsetDescendantRectToMyCoords(button, bounds);
        assertTrue("reachable top: " + button.getText(), bounds.top >= 0);
        assertTrue("reachable bottom: " + button.getText(), bounds.bottom <= screen.root.getHeight());
        assertTrue("reachable horizontal: " + button.getText(), bounds.left >= 0 && bounds.right <= screen.root.getWidth());
    }

    private void capture(RemoteScreen screen, Context context, String name) {
        Bitmap bitmap = Bitmap.createBitmap(screen.root.getWidth(), screen.root.getHeight(), Bitmap.Config.ARGB_8888);
        screen.root.draw(new Canvas(bitmap));
        try (FileOutputStream out = new FileOutputStream(new File(context.getCacheDir(), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        bitmap.recycle();
    }

    @Test public void remoteAndSetupFitPortraitLandscapeAndLargeText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[][] sizes = {{360,800}, {800,360}};
            for (int[] size : sizes) for (float scale : new float[]{1f,1.5f}) {
                Context c = context(size[0],size[1],scale);
                RemoteScreen screen = new RemoteScreen(c, (x,y) -> fail("Layout sent motion"), () -> fail("Layout clicked"));
                screen.status.setText("Saved projector — disconnected. Start, then Connect.");
                screen.migrationNotice.setVisibility(View.GONE);
                screen.showSetup(false);
                layout(screen,c,size[0],size[1]);
                for (Button control : screen.inputs) assertFalse("disabled before admission", control.isEnabled());
                assertFalse(screen.touchpad.isEnabled());
                reachable(screen,screen.stop); reachable(screen,screen.close); reachable(screen,screen.setup);
                for (Button control : screen.scrollButtons.values()) reachable(screen,control);
                reachable(screen,screen.start); reachable(screen,screen.connect);
                {
                    screen.showSetup(false); layout(screen,c,size[0],size[1]);
                    capture(screen,c,"remote-" + (size[0] < size[1] ? "portrait" : "landscape") + (scale == 1f ? "" : "-large"));
                }
                screen.showSetup(true); layout(screen,c,size[0],size[1]);
                capture(screen,c,"setup-" + (size[0] < size[1] ? "portrait" : "landscape") + (scale == 1f ? "" : "-large"));
                reachable(screen,screen.visible); reachable(screen,screen.confirm); reachable(screen,screen.forget);
                reachable(screen,screen.stop); reachable(screen,screen.close);
                screen.showSetup(false); layout(screen,c,size[0],size[1]);
                assertFalse("switching pages must not authorize",screen.touchpad.isEnabled());
            }
        });
    }

    @Test public void setupDownAndKeyboardClickCancelPendingPadTap() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c=context(360,800,1f); AtomicInteger taps=new AtomicInteger();
            RemoteScreen screen=new RemoteScreen(c,(x,y)->{},taps::incrementAndGet);
            screen.showSetup(false); layout(screen,c,360,800); screen.touchpad.setEnabled(true);
            for (boolean down : new boolean[]{true,false}) {
                screen.showSetup(false); layout(screen,c,360,800);
                long time=SystemClock.uptimeMillis();
                MotionEvent start=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,20,20,0);
                screen.touchpad.onTouchEvent(start); start.recycle();
                if (down) {
                    MotionEvent toggle=MotionEvent.obtain(time,time+10,MotionEvent.ACTION_DOWN,20,20,0);
                    screen.setup.dispatchTouchEvent(toggle); toggle.recycle();
                } else screen.setup.performClick();
                MotionEvent up=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,20,20,0);
                screen.touchpad.onTouchEvent(up);up.recycle();
                assertEquals("page interaction cannot leave a pending tap",0,taps.get());
            }
        });
    }
}
