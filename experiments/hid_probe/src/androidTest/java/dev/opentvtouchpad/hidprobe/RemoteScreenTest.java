package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
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

    private void reachable(RemoteScreen screen, TextView button) {
        assertEquals(View.VISIBLE, button.getVisibility());
        assertTrue(button.getText().length() > 0 || button.getHint() != null);
        float density = button.getResources().getDisplayMetrics().density;
        assertTrue("48dp target: " + button.getText(), button.getHeight() >= (int)(48 * density));
        assertTrue("48dp width: " + button.getText(), button.getWidth() >= (int)(48 * density));
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
            int[][] sizes = {{320,800}, {360,800}, {800,360}};
            for (int[] size : sizes) for (float scale : new float[]{1f,1.5f}) {
                Context c = context(size[0],size[1],scale);
                RemoteScreen screen = new RemoteScreen(c, (x,y) -> fail("Layout sent motion"), () -> fail("Layout clicked"));
                screen.status.setText("Saved projector — disconnected. Start, then Connect.");
                screen.migrationNotice.setVisibility(View.GONE);
                screen.showSetup(false);
                layout(screen,c,size[0],size[1]);
                for (Button control : screen.scrollButtons.values()) {
                    Rect scrollBounds = bounds(screen,control);
                    assertTrue("scroll follows touchpad", scrollBounds.top >= bounds(screen,screen.touchpad).bottom);
                    assertTrue("scroll precedes TV controls", scrollBounds.bottom <= bounds(screen,screen.tvButtons.get(TvCommand.UP)).top);
                }
                for (Button control : screen.tvButtons.values())
                    assertTrue("TV controls precede mouse alternatives",
                        bounds(screen,control).bottom <= bounds(screen,screen.moveLeft).top);
                for (Button control : screen.inputs) assertFalse("disabled before admission", control.isEnabled());
                assertEquals(TvCommand.values().length, screen.tvButtons.size());
                for (TvCommand command : TvCommand.values()) {
                    Button control = screen.tvButtons.get(command);
                    assertNotNull(control);
                    assertFalse("TV disabled before admission", control.isEnabled());
                    assertFalse("TV controls must not enter mouse admission", screen.inputs.contains(control));
                    assertTrue(control.isFocusable());
                    assertNotNull(control.getContentDescription());
                    reachable(screen, control);
                }
                assertFalse(screen.touchpad.isEnabled());
                reachable(screen,screen.stop); reachable(screen,screen.close); reachable(screen,screen.setup);
                for (Button control : screen.inputs) reachable(screen,control);
                reachable(screen,screen.start); reachable(screen,screen.connect);
                {
                    screen.showSetup(false); layout(screen,c,size[0],size[1]);
                    capture(screen,c,"remote-" + (size[0] < size[1] ? "portrait" : "landscape") + (scale == 1f ? "" : "-large"));
                }
                screen.showSetup(true); layout(screen,c,size[0],size[1]);
                reachable(screen,screen.visible); reachable(screen,screen.confirm); reachable(screen,screen.forget);
                reachable(screen,screen.tvAddress); reachable(screen,screen.tvCode);
                for (Button control : new Button[]{screen.tvSaveAddress,screen.tvPair,screen.tvConfirm,screen.tvForget}) {
                    assertFalse("TV setup disabled before admission", control.isEnabled());
                    reachable(screen,control);
                }
                reachable(screen,screen.stop); reachable(screen,screen.close);
                screen.showSetup(false); layout(screen,c,size[0],size[1]);
                assertFalse("switching pages must not authorize",screen.touchpad.isEnabled());
            }
        });
    }

    @Test public void dpadHasSpatialDirectionsAndMouseStatusDoesNotOverwriteTvStatus() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(320,800,1.5f);
            RemoteScreen screen = new RemoteScreen(c,(x,y)->{},()->{});
            layout(screen,c,320,800);
            Rect up = bounds(screen,screen.tvButtons.get(TvCommand.UP));
            Rect down = bounds(screen,screen.tvButtons.get(TvCommand.DOWN));
            Rect left = bounds(screen,screen.tvButtons.get(TvCommand.LEFT));
            Rect right = bounds(screen,screen.tvButtons.get(TvCommand.RIGHT));
            Rect ok = bounds(screen,screen.tvButtons.get(TvCommand.OK));
            assertTrue(up.bottom <= ok.top);
            assertTrue(down.top >= ok.bottom);
            assertTrue(left.right <= ok.left);
            assertTrue(right.left >= ok.right);
            assertEquals(ok.centerX(), up.centerX(), 1);
            assertEquals(ok.centerX(), down.centerX(), 1);
            assertEquals("TV: not paired",screen.tvStatus.getText().toString());
            screen.tvStatus.setText("TV: connected");
            screen.setConnected(true);
            assertTrue(hasText(screen.root,"Mouse: connected"));
            screen.setConnected(false);
            assertTrue(hasText(screen.root,"Mouse: disconnected"));
            assertEquals("TV: connected",screen.tvStatus.getText().toString());
        });
    }

    @Test public void pairingCodeIsMaskedBoundedAndExcludedFromSavedStateAndAutofill() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(360,800,1f);
            RemoteScreen screen = new RemoteScreen(c,(x,y)->{},()->{});
            assertFalse(screen.tvAddress.isEnabled());
            assertFalse(screen.tvCode.isEnabled());
            assertFalse(screen.tvCode.isSaveEnabled());
            assertFalse(screen.tvCode.isSaveFromParentEnabled());
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                screen.tvCode.getImportantForAutofill());
            assertNull(screen.tvCode.getAutofillHints());
            assertTrue(screen.tvCode.getTransformationMethod() instanceof PasswordTransformationMethod);
            assertTrue((screen.tvCode.getInputType() & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
            assertTrue((screen.tvCode.getImeOptions() & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
            assertTrue((screen.tvCode.getImeOptions() & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0);
            screen.showSetup(true);
            screen.tvCode.setEnabled(true);
            screen.tvCode.setId(View.generateViewId());
            // Synthetic input only; never capture a pairing-field screenshot.
            screen.tvCode.setText("0123456789abcdef");
            assertEquals(6, screen.tvCode.length());
            screen.tvCode.setText("");
            screen.tvCode.getText().append("gGaB09");
            assertEquals("aB09", screen.tvCode.getText().toString());
            CharSequence masked = screen.tvCode.getTransformationMethod()
                .getTransformation(screen.tvCode.getText(),screen.tvCode);
            assertNotEquals(screen.tvCode.getText().toString(),masked.toString());
            SparseArray<Parcelable> saved = new SparseArray<>();
            screen.root.saveHierarchyState(saved);
            assertNull(saved.get(screen.tvCode.getId()));
            screen.tvAddress.setText("123.123.123.123456");
            assertEquals(15,screen.tvAddress.length());
        });
    }

    @Test public void imeInsetsKeepStopCloseAndPairingFieldsReachable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] size : new int[][]{{360,800,280},{800,360,100}}) {
                Context c = context(size[0],size[1],1.5f);
                float density = c.getResources().getDisplayMetrics().density;
                RemoteScreen screen = new RemoteScreen(c,(x,y)->{},()->{});
                screen.showSetup(true);
                int keyboard = Math.round(size[2] * density);
                screen.root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,Math.round(24*density),0,Math.round(24*density)))
                    .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,keyboard)).build());
                layout(screen,c,size[0],size[1]);
                reachable(screen,screen.stop); reachable(screen,screen.close);
                reachable(screen,screen.tvCode); reachable(screen,screen.tvConfirm);
                assertTrue(bounds(screen,screen.stop).bottom <= screen.root.getHeight() - keyboard);
                assertTrue(bounds(screen,screen.close).bottom <= screen.root.getHeight() - keyboard);
                assertEquals(Math.round(12*density) + keyboard,screen.root.getPaddingBottom());
            }
        });
    }

    private Rect bounds(RemoteScreen screen, View view) {
        Rect result = new Rect(0,0,view.getWidth(),view.getHeight());
        screen.root.offsetDescendantRectToMyCoords(view,result);
        return result;
    }

    private boolean hasText(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return true;
        if (view instanceof ViewGroup group) for (int i=0;i<group.getChildCount();i++)
            if (hasText(group.getChildAt(i),text)) return true;
        return false;
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
