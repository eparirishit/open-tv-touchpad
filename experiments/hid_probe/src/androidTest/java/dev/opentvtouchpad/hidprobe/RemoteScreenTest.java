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
        // Unattached previews need the layout direction normally inherited from the Window.
        screen.root.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
        float density = context.getResources().getDisplayMetrics().density;
        screen.root.measure(View.MeasureSpec.makeMeasureSpec((int)(width * density), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((int)(height * density), View.MeasureSpec.EXACTLY));
        screen.root.layout(0, 0, screen.root.getMeasuredWidth(), screen.root.getMeasuredHeight());
    }

    private void reachable(RemoteScreen screen, TextView button) {
        assertTrue("page visible: " + button.getText(), visible(screen,button));
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

    @Test public void actualActivityLaunchAndThemeChangesWaitForAttachedWindow() {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context target=instrumentation.getTargetContext();
        android.app.Activity activity=instrumentation.startActivitySync(
            new android.content.Intent(target,ProbeActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
        instrumentation.waitForIdleSync();
        try {
            instrumentation.runOnMainSync(() -> {
                View decor=activity.getWindow().getDecorView();
                assertTrue(decor.isAttachedToWindow());
                assertFalse(activity.isFinishing());
                Button display=findButton(decor,"Display");
                assertNotNull(display);display.performClick();
                Button dark=findButton(decor,"Dark");
                Button light=findButton(decor,"Light");
                dark.performClick();
                assertEquals(android.graphics.Color.parseColor("#0E1012"),activity.getWindow().getStatusBarColor());
                assertEquals(0,activity.getWindow().getInsetsController().getSystemBarsAppearance()
                    & android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                light.performClick();
                assertEquals(android.graphics.Color.parseColor("#F4F5F7"),activity.getWindow().getStatusBarColor());
                assertTrue((activity.getWindow().getInsetsController().getSystemBarsAppearance()
                    & android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)!=0);
                assertFalse("appearance cannot authorize Connect",findButton(decor,"Connect").isEnabled());
                assertFalse("Stop requires a connected run",findButton(decor,"Stop").isEnabled());
                assertEquals(View.GONE,findButton(decor,"Stop").getVisibility());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private Button findButton(View root,String label) {
        if(root instanceof Button button && label.contentEquals(button.getText()))return button;
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++){
            Button found=findButton(group.getChildAt(i),label);if(found!=null)return found;
        }
        return null;
    }

    @Test public void prototypeSeparatesRemoteAndTouchpadWithoutAdmittingInput() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c = context(390,844,1f);
            RemoteScreen screen = new RemoteScreen(c,(x,y)->fail("Navigation moved pointer"),()->fail("Navigation clicked"));
            layout(screen,c,390,844);
            assertFalse("Touchpad belongs on its own tab",visible(screen,screen.touchpad));
            assertTrue("Remote D-pad is initially visible",visible(screen,screen.tvButtons.get(TvCommand.OK)));
            for (Button b : screen.inputs) assertFalse(b.isEnabled());
        });
    }

    @Test public void setupHidesFooterAcrossKeyboardTransitions() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteScreen screen = new RemoteScreen(context(360,800,1f),(x,y)->{},()->{});
            for (boolean touchpad : new boolean[]{false,true}) {
                screen.showTouchpad(touchpad);
                assertTrue(visible(screen,screen.remoteTab));
                screen.showSetup(true);
                for (int keyboard : new int[]{0,200,0}) {
                    screen.root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                        .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,keyboard)).build());
                    assertFalse(visible(screen,screen.remoteTab));
                    assertFalse(visible(screen,screen.padTab));
                }
                screen.setup.performClick();
                assertTrue(visible(screen,screen.remoteTab));
                assertEquals(touchpad,visible(screen,screen.touchpad));
            }
        });
    }

    @Test public void headerAndDpadMatchRequestedStyleWithoutEnablingInput() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteScreen screen = new RemoteScreen(context(360,800,1f),(x,y)->{},()->{});
            assertNull(findButton(screen.root,"Close"));
            assertFalse("Stop disabled before connection",screen.stop.isEnabled());
            assertEquals("Stop hidden before connection",View.GONE,screen.stop.getVisibility());
            assertEquals("Stop",screen.stop.getText().toString());
            assertNotNull(screen.stop.getCompoundDrawablesRelative()[0]);
            assertEquals(screen.stop.getTextColors(),screen.stop.getCompoundDrawableTintList());
            screen.setConnected(true);
            layout(screen,screen.root.getContext(),360,800);
            assertTrue(visible(screen,screen.stop));
            boolean headerRtl = screen.root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            assertTrue("Settings is at the header end",headerRtl
                ? bounds(screen,screen.setup).right <= bounds(screen,screen.stop).left
                : bounds(screen,screen.stop).right <= bounds(screen,screen.setup).left);
            screen.showSetup(true);
            screen.setDark(true);
            assertTrue("Connected Stop remains available on Setup",visible(screen,screen.stop));
            boolean rtl = screen.stop.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            assertNotNull("leading icon resolves into visible layout",screen.stop.getCompoundDrawables()[rtl ? 2 : 0]);
            assertTrue(rtl ? screen.stop.getCompoundPaddingRight() > screen.stop.getPaddingRight()
                : screen.stop.getCompoundPaddingLeft() > screen.stop.getPaddingLeft());
            screen.setConnected(false);
            assertEquals("Stop hides again on disconnection",View.GONE,screen.stop.getVisibility());
            for (boolean dark : new boolean[]{false,true}) {
                screen.setDark(dark);
                assertEquals("Theme changes cannot reveal disconnected Stop",View.GONE,screen.stop.getVisibility());
                for (TvCommand command : new TvCommand[]{TvCommand.UP,TvCommand.DOWN,TvCommand.LEFT,TvCommand.RIGHT,TvCommand.OK}) {
                    Button button = screen.tvButtons.get(command);
                    assertFalse(button.isEnabled());
                    for (int[] state : new int[][]{{-android.R.attr.state_enabled},
                            {android.R.attr.state_enabled},{android.R.attr.state_enabled,android.R.attr.state_pressed},
                            {android.R.attr.state_enabled,android.R.attr.state_focused}}) {
                        android.graphics.drawable.Drawable background = button.getBackground();
                        background.setState(state);
                        Bitmap image = Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888);
                        background.setBounds(0,0,100,100);
                        background.draw(new Canvas(image));
                        boolean disabled = state[0] == -android.R.attr.state_enabled;
                        assertEquals(command == TvCommand.OK ? android.graphics.Color.parseColor(disabled
                                ? dark ? "#233B60" : "#DCE8FF"
                                : dark ? "#6AA8FF" : "#2F6FEB") : 0,
                            image.getPixel(50,50));
                        image.recycle();
                    }
                }
            }
        });
    }

    @Test public void selectedTabsHaveNoBorderAndThemeChoicesHaveIcons() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteScreen screen = new RemoteScreen(context(360,800,1f),(x,y)->{},()->{});
            assertNotNull(screen.lightTheme.getCompoundDrawablesRelative()[1]);
            assertNull(screen.lightTheme.getCompoundDrawablesRelative()[0]);
            assertNotNull(screen.darkTheme.getCompoundDrawablesRelative()[1]);
            assertNull(screen.darkTheme.getCompoundDrawablesRelative()[0]);
            assertEquals("Netflix",screen.tvButtons.get(TvCommand.NETFLIX).getText().toString());
            for (boolean dark : new boolean[]{false,true}) {
                screen.setDark(dark);
                for (Button tab : new Button[]{screen.remoteTab,screen.padTab,screen.projectorTab,
                        screen.tvTab,screen.displayTab,screen.surfaceTab,screen.buttonsTab,
                        screen.lightTheme,screen.darkTheme}) {
                    tab.performClick();
                    assertTrue("selection remains accessible",tab.isSelected());
                    for (int[] state : new int[][]{{android.R.attr.state_enabled,android.R.attr.state_selected},
                            {android.R.attr.state_enabled,android.R.attr.state_selected,android.R.attr.state_pressed},
                            {android.R.attr.state_enabled,android.R.attr.state_selected,android.R.attr.state_focused}}) {
                        android.graphics.drawable.Drawable background = tab.getBackground();
                        background.setState(state);
                        background.setBounds(0,0,200,100);
                        Bitmap image = Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);
                        background.draw(new Canvas(image));
                        assertEquals("tab border matches fill",image.getPixel(100,50) & 0xFFFFFF,
                            image.getPixel(1,50) & 0xFFFFFF);
                        image.recycle();
                    }
                }
            }
        });
    }

    @Test public void prototypeStatusDotsAndNumberedSetupAreVisibleWithoutAdmittingInput() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteScreen screen = new RemoteScreen(context(360,800,1f),(x,y)->fail("Status moved pointer"),()->fail("Status clicked"));
            assertNotNull("TV status has a leading indicator",screen.tvStatus.getCompoundDrawablesRelative()[0]);
            assertEquals(13f,screen.status.getTextSize()/screen.root.getResources().getDisplayMetrics().scaledDensity,0.1f);
            screen.showSetup(true);
            assertTrue(hasText(screen.root,"1"));
            assertTrue(hasText(screen.root,"2"));
            assertTrue(hasText(screen.root,"3"));
            assertFalse(screen.touchpad.isEnabled());
            for (Button control : screen.tvButtons.values()) assertFalse(control.isEnabled());
        });
    }

    @Test public void independentStatusDotsSurviveThemeChanges() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RemoteScreen screen = new RemoteScreen(context(360,800,1f),(x,y)->{},()->{});
            screen.setConnected(true);
            screen.setTvConnected(false);
            for (boolean dark : new boolean[]{false,true}) {
                screen.setDark(dark);
                android.graphics.drawable.GradientDrawable tvDot = (android.graphics.drawable.GradientDrawable)screen.tvStatus.getCompoundDrawablesRelative()[0];
                assertEquals(android.graphics.Color.parseColor(dark ? "#F4C675" : "#C27803"),tvDot.getColor().getDefaultColor());
                assertEquals("TV: not paired",screen.tvStatus.getText().toString());
                assertTrue(hasText(screen.root,"Mouse: connected"));
                screen.tvStatus.setText("TV: connected");
                screen.setTvConnected(true);
                tvDot = (android.graphics.drawable.GradientDrawable)screen.tvStatus.getCompoundDrawablesRelative()[0];
                assertEquals(android.graphics.Color.parseColor(dark ? "#4ADE80" : "#1F8A4C"),tvDot.getColor().getDefaultColor());
                screen.tvStatus.setText("TV: not paired");
                screen.setTvConnected(false);
                for (Button control : screen.tvButtons.values()) assertFalse(control.isEnabled());
            }
            screen.setDark(false);
        });
    }

    @Test public void sensitivityCancelsQueuedMotionAndTapWithoutGrantingAdmission() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicInteger moves = new AtomicInteger(), taps = new AtomicInteger();
        RemoteScreen[] screenRef = new RemoteScreen[1];
        int[] original = new int[1];
        instrumentation.runOnMainSync(() -> {
            Context c = context(360,800,1f);
            RemoteScreen screen = new RemoteScreen(c,(x,y)->moves.incrementAndGet(),taps::incrementAndGet);
            screenRef[0] = screen;
            original[0] = screen.sensitivity.getProgress();
            assertEquals(6,screen.sensitivity.getMax());
            screen.showTouchpad(true); layout(screen,c,360,800);
            assertTrue(screen.sensitivity.getHeight() >= 48*c.getResources().getDisplayMetrics().density);
            screen.touchpad.setEnabled(true);
            float density = c.getResources().getDisplayMetrics().density;
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,20*density,20*density,0);
            MotionEvent move = MotionEvent.obtain(now,now+10,MotionEvent.ACTION_MOVE,80*density,20*density,0);
            MotionEvent up = MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,80*density,20*density,0);
            screen.touchpad.onTouchEvent(down); screen.touchpad.onTouchEvent(move);
            screen.sensitivity.setProgress((original[0]+1)%7);
            screen.touchpad.onTouchEvent(up);
            down.recycle(); move.recycle(); up.recycle();
            assertTrue("Slider preserves existing admission",screen.touchpad.isEnabled());
            for (Button control : screen.inputs) assertFalse(control.isEnabled());
            for (Button control : screen.tvButtons.values()) assertFalse(control.isEnabled());
            RemoteScreen reopened = new RemoteScreen(c,(x,y)->{},()->{});
            assertEquals(screen.sensitivity.getProgress(),reopened.sensitivity.getProgress());
            assertFalse(reopened.touchpad.isEnabled());
        });
        SystemClock.sleep(60);
        try {
            instrumentation.runOnMainSync(() -> {
                assertEquals("Changing speed discards queued motion",0,moves.get());
                assertEquals(0,taps.get());
                RemoteScreen screen = screenRef[0];
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,20,20,0);
                MotionEvent up = MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,20,20,0);
                screen.touchpad.onTouchEvent(down);
                screen.sensitivity.setProgress((screen.sensitivity.getProgress()+1)%7);
                screen.touchpad.onTouchEvent(up);
                down.recycle();up.recycle();
                assertEquals("Changing speed also cancels pending tap",0,taps.get());
            });
        } finally {
            instrumentation.runOnMainSync(() -> {
                screenRef[0].touchpad.setEnabled(false);
                screenRef[0].sensitivity.setProgress(original[0]);
            });
        }
    }

    @Test public void remoteAndSetupFitPortraitLandscapeAndLargeText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            int[][] sizes = {{320,800}, {360,800}, {800,360}};
            for (int[] size : sizes) for (float scale : new float[]{1f,1.5f}) for (boolean dark : new boolean[]{false,true}) {
                Context c = context(size[0],size[1],scale);
                RemoteScreen screen = new RemoteScreen(c, (x,y) -> fail("Layout sent motion"), () -> fail("Layout clicked"));
                screen.setDark(dark);
                screen.setConnected(true);
                screen.status.setText("Synthetic connected header layout; no input admitted.");
                screen.migrationNotice.setVisibility(View.GONE);
                screen.showSetup(false);
                layout(screen,c,size[0],size[1]);
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
                reachable(screen,screen.stop); reachable(screen,screen.setup);
                reachable(screen,screen.start); reachable(screen,screen.connect);
                reachable(screen,screen.remoteTab); reachable(screen,screen.padTab);
                {
                    screen.showSetup(false); layout(screen,c,size[0],size[1]);
                    capture(screen,c,"remote-" + (size[0] < size[1] ? "portrait" : "landscape") + (scale == 1f ? "" : "-large"));
                }
                screen.showTouchpad(true); layout(screen,c,size[0],size[1]);
                assertTrue(visible(screen,screen.touchpad));
                assertFalse(visible(screen,screen.tvButtons.get(TvCommand.OK)));
                for (Button control : screen.scrollButtons.values()) reachable(screen,control);
                reachable(screen,screen.surfaceTab); reachable(screen,screen.buttonsTab);
                screen.showPadButtons(true); layout(screen,c,size[0],size[1]);
                for (Button control : new Button[]{screen.moveLeft,screen.moveRight,screen.moveUp,screen.moveDown,screen.click}) reachable(screen,control);
                screen.showSetup(true); screen.showSetupSection(0); layout(screen,c,size[0],size[1]);
                reachable(screen,screen.visible); reachable(screen,screen.confirm); reachable(screen,screen.forget);
                screen.showSetupSection(1); layout(screen,c,size[0],size[1]);
                reachable(screen,screen.tvAddress); reachable(screen,screen.tvCode);
                View container = (View)screen.tvAddress.getParent();
                while (!(container instanceof ScrollView)) container = (View)container.getParent();
                assertFalse("Content scrolls without a visible scrollbar",container.isVerticalScrollBarEnabled());
                for (Button control : new Button[]{screen.tvSaveAddress,screen.tvPair,screen.tvConfirm,screen.tvForget}) {
                    assertFalse("TV setup disabled before admission", control.isEnabled());
                    reachable(screen,control);
                }
                reachable(screen,screen.stop);
                screen.showSetupSection(2); layout(screen,c,size[0],size[1]);
                reachable(screen,screen.lightTheme); reachable(screen,screen.darkTheme);
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

    @Test public void pairingCodeIsVisibleOtpBoundedAndExcludedFromSavedStateAndAutofill() {
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
            assertFalse(screen.tvCode.getTransformationMethod() instanceof PasswordTransformationMethod);
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
            assertTrue("OTP characters are visible",screen.tvCode.getTransformationMethod() == null
                || screen.tvCode.getText().toString().contentEquals(screen.tvCode.getTransformationMethod()
                    .getTransformation(screen.tvCode.getText(),screen.tvCode)));
            SparseArray<Parcelable> saved = new SparseArray<>();
            screen.root.saveHierarchyState(saved);
            assertNull(saved.get(screen.tvCode.getId()));
            screen.tvAddress.setText("123.123.123.123456");
            assertEquals(15,screen.tvAddress.length());
        });
    }

    @Test public void imeInsetsKeepStopAndPairingFieldsReachable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] size : new int[][]{{360,800,280},{800,360,100}}) {
                Context c = context(size[0],size[1],1.5f);
                float density = c.getResources().getDisplayMetrics().density;
                RemoteScreen screen = new RemoteScreen(c,(x,y)->{},()->{});
                screen.setConnected(true);
                screen.showSetup(true); screen.showSetupSection(1);
                int keyboard = Math.round(size[2] * density);
                screen.root.dispatchApplyWindowInsets(new WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(),Insets.of(0,Math.round(24*density),0,Math.round(24*density)))
                    .setInsets(WindowInsets.Type.ime(),Insets.of(0,0,0,keyboard)).build());
                layout(screen,c,size[0],size[1]);
                reachable(screen,screen.stop);
                reachable(screen,screen.tvCode); reachable(screen,screen.tvConfirm);
                assertTrue(bounds(screen,screen.stop).bottom <= screen.root.getHeight() - keyboard);
                assertTrue(screen.root.getPaddingBottom() >= keyboard);
            }
        });
    }

    @Test public void navigationAndThemeCancelTouchWithoutChangingAdmissionOrFields() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c=context(360,800,1f); AtomicInteger taps=new AtomicInteger();
            RemoteScreen screen=new RemoteScreen(c,(x,y)->fail("Navigation sent movement"),taps::incrementAndGet);
            screen.tvAddress.setText("192.0.2.10");
            screen.tvCode.setText("aB1234");
            screen.status.setText("Native teardown is unconfirmed. Input disabled.");
            for (Button nav : new Button[]{screen.remoteTab,screen.padTab,screen.setup,
                    screen.projectorTab,screen.tvTab,screen.displayTab,screen.surfaceTab,
                    screen.buttonsTab,screen.lightTheme,screen.darkTheme}) {
                screen.showTouchpad(true);screen.showPadButtons(false);layout(screen,c,360,800);
                screen.touchpad.setEnabled(true);
                long t=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,20,20,0);
                screen.touchpad.onTouchEvent(down);down.recycle();
                nav.performClick();
                MotionEvent up=MotionEvent.obtain(t,t+30,MotionEvent.ACTION_UP,20,20,0);
                screen.touchpad.onTouchEvent(up);up.recycle();
                assertEquals("navigation cancels tap: "+nav.getText(),0,taps.get());
                assertTrue("presentation cannot revoke/admit transport",screen.touchpad.isEnabled());
                for (Button input:screen.inputs) assertFalse(input.isEnabled());
                for (Button input:screen.tvButtons.values()) assertFalse(input.isEnabled());
                assertEquals("192.0.2.10",screen.tvAddress.getText().toString());
                assertEquals("aB1234",screen.tvCode.getText().toString());
                assertEquals("Native teardown is unconfirmed. Input disabled.",screen.status.getText().toString());
                screen.touchpad.setEnabled(false);
            }
            screen.setDark(true);
            RemoteScreen reopened=new RemoteScreen(c,(x,y)->{},()->{});
            assertTrue("appearance survives reopen",reopened.isDark());
            assertFalse(reopened.touchpad.isEnabled());
            reopened.setDark(false);
        });
    }

    @Test public void capturePrototypeScreensAndThemes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Context c=context(390,844,1f);
            RemoteScreen screen=new RemoteScreen(c,(x,y)->fail("Capture sent motion"),()->fail("Capture clicked"));
            for(boolean dark:new boolean[]{false,true}) {
                screen.setDark(dark);String suffix=dark?"-dark":"-light";
                screen.status.setText("Five-minute diagnostic. Start, then Connect.");
                screen.showTouchpad(false);layout(screen,c,390,844);capture(screen,c,"prototype-remote"+suffix);
                screen.setConnected(true);screen.setTvConnected(true);screen.stop.setEnabled(true);screen.tvStatus.setText("TV: connected");
                screen.status.setText("Both controls ready. Keep this screen open.");
                for(Button b:screen.tvButtons.values())b.setEnabled(true);
                layout(screen,c,390,844);capture(screen,c,"prototype-connected"+suffix);
                screen.showTouchpad(true);screen.touchpad.setEnabled(true);
                for(Button b:screen.inputs)b.setEnabled(true);
                screen.showPadButtons(false);layout(screen,c,390,844);capture(screen,c,"prototype-pad"+suffix);
                screen.showPadButtons(true);layout(screen,c,390,844);capture(screen,c,"prototype-buttons"+suffix);
                screen.stop.setEnabled(false);screen.touchpad.setEnabled(false);for(Button b:screen.inputs)b.setEnabled(false);
                for(Button b:screen.tvButtons.values())b.setEnabled(false);
                screen.setConnected(false);screen.setTvConnected(false);screen.tvStatus.setText("TV: not paired");
                screen.status.setText("Start a five-minute run, then connect your projector.");
                screen.showSetup(true);
                for(int page=0;page<3;page++) {
                    screen.showSetupSection(page);layout(screen,c,390,844);
                    capture(screen,c,"prototype-setup-"+page+suffix);
                }
            }
            screen.setDark(false);
        });
    }

    private boolean visible(RemoteScreen screen, View view) {
        while (view != screen.root) {
            if (view.getVisibility() != View.VISIBLE) return false;
            if (!(view.getParent() instanceof View)) return false;
            view = (View)view.getParent();
        }
        return true;
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
            screen.showTouchpad(true); layout(screen,c,360,800); screen.touchpad.setEnabled(true);
            for (boolean down : new boolean[]{true,false}) {
                screen.showTouchpad(true); layout(screen,c,360,800);
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
