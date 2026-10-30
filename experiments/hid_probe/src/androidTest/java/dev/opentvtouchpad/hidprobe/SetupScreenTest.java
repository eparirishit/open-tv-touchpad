package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Native presentation contracts only; synthetic values, no transport or pairing screenshots. */
@RunWith(AndroidJUnit4.class)
public class SetupScreenTest {
    private SetupState.Snapshot snapshot(SetupState.Page page, boolean bt, boolean ip, boolean tv,
            long time, boolean busy, boolean enabled, long generation) {
        return new SetupState.Snapshot(page, bt, ip, tv, ip ? "192.168.10.20" : "", "", "",
                time, busy, enabled, generation, false);
    }

    @Test public void overviewUsesIndependentSavedItemsAndEmitsOnlyExplicitStart() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen = new SetupScreen(context(390,844,1));
            List<SetupState.Action> actions = new ArrayList<>();
            screen.setActionListener((a,v) -> actions.add(a));
            screen.render(snapshot(SetupState.Page.OVERVIEW,false,true,true,0,false,true,1));
            assertEquals("Next", screen.chips[0].getText().toString());
            assertEquals("Done", screen.chips[1].getText().toString());
            assertEquals("Done", screen.chips[2].getText().toString());
            assertEquals("Continue setup", screen.primary.getText().toString());
            assertTrue(actions.isEmpty());
            screen.primary.performClick();
            assertEquals(List.of(SetupState.Action.START), actions);
            screen.render(snapshot(SetupState.Page.OVERVIEW,true,true,true,0,false,true,1));
            assertEquals("Review setup", screen.primary.getText().toString());
            screen.primary.performClick();
            assertEquals(SetupState.Action.MANAGE, actions.get(1));
            screen.render(snapshot(SetupState.Page.OVERVIEW,false,false,false,0,false,true,1));
            assertEquals("Start setup",screen.primary.getText().toString());
            assertEquals("To do",screen.chips[1].getText().toString());
            assertEquals("",screen.address.getText().toString());
        });
    }

    @Test public void countdownAndCodeFollowEligibilityGenerationAndActualRemainingTime() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen = new SetupScreen(context(360,800,1));
            List<String> sent = new ArrayList<>();
            screen.setActionListener((a,v) -> sent.add(a+":"+v));
            screen.render(snapshot(SetupState.Page.BT_WAIT,false,false,false,61001,true,true,7));
            assertTrue(screen.countdown.getText().toString().contains("1:02"));
            assertFalse(screen.primary.isEnabled());
            screen.secondary.performClick();
            assertEquals(List.of("CANCEL:"),sent);
            screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,15001,false,true,8));
            assertTrue(screen.countdown.getText().toString().contains("0:16"));
            assertFalse(screen.primary.isEnabled());
            screen.code.setText("aB1234");
            assertTrue(screen.primary.isEnabled());
            screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,14000,false,true,8));
            assertEquals("aB1234", screen.code.getText().toString());
            screen.primary.performClick();
            assertEquals("CONFIRM_TV:aB1234",sent.get(1));
            screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,13000,false,true,9));
            assertEquals(0,screen.code.length());
            screen.code.setText("aB1234");
            screen.pauseControls();
            assertEquals(0,screen.code.length());
            assertFalse(screen.code.isEnabled());
            screen.primary.performClick();
            assertEquals(2,sent.size());
            screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,0,false,true,9));
            assertFalse(screen.code.isEnabled());
            assertFalse(screen.primary.isEnabled());
        });
    }

    @Test public void stalePressedComparisonAndCodeCannotClickANewPageOrGeneration() {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var activity=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),ProbeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        SetupScreen[] current=new SetupScreen[1];
        List<SetupState.Action> sent=new ArrayList<>();
        try {
            instrumentation.runOnMainSync(() -> {
                SetupScreen screen=new SetupScreen(activity);current[0]=screen;
                screen.setActionListener((action,value)->sent.add(action));
                screen.render(snapshot(SetupState.Page.BT_COMPARE,false,false,false,0,false,true,1));
                activity.setContentView(screen.root);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                SetupScreen screen=current[0];
                press(screen.primary,android.view.MotionEvent.ACTION_DOWN);
                screen.render(snapshot(SetupState.Page.BT_COMPARE,false,false,false,0,false,true,2));
                press(screen.primary,android.view.MotionEvent.ACTION_UP);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                SetupScreen screen=current[0];
                assertTrue("old comparison cannot confirm the next generation",sent.isEmpty());
                screen.primary.performClick();
                assertEquals(List.of(SetupState.Action.BT_CONFIRM),sent);
                press(screen.primary,android.view.MotionEvent.ACTION_DOWN);
                screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,30000,false,true,2));
                screen.code.setText("aB1234");
                press(screen.primary,android.view.MotionEvent.ACTION_UP);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                SetupScreen screen=current[0];
                assertEquals("old press cannot become a code submission",1,sent.size());
                screen.primary.performClick();
                assertEquals(SetupState.Action.CONFIRM_TV,sent.get(1));
                press(screen.primary,android.view.MotionEvent.ACTION_DOWN);
                screen.render(snapshot(SetupState.Page.TV_CODE,true,true,false,30000,false,true,3));
                assertEquals(0,screen.code.length());
                screen.code.setText("aB1234");
                press(screen.primary,android.view.MotionEvent.ACTION_UP);
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertEquals(2,sent.size()));
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private void press(Button button,int action) {
        long time=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event=android.view.MotionEvent.obtain(time,time,action,
                button.getWidth()/2f,button.getHeight()/2f,0);
        button.dispatchTouchEvent(event);event.recycle();
    }

    @Test public void visibilityApprovalPendingHasNoFakeCountdownAndEnabledIsThePrimaryGate() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen=new SetupScreen(context(360,800,1));
            screen.render(new SetupState.Snapshot(SetupState.Page.BT_WAIT,false,false,false,"",
                    "Waiting for Android approval.","",0,false,true,1,true));
            assertEquals(View.GONE,screen.countdown.getVisibility());
            assertFalse(screen.primary.isEnabled());
            assertEquals("Waiting for Android approval.",screen.message.getText().toString());
            screen.render(snapshot(SetupState.Page.BT_WAIT,false,false,false,45000,true,true,1));
            assertEquals(View.VISIBLE,screen.countdown.getVisibility());
            assertTrue(screen.countdown.getText().toString().contains("0:45"));
            screen.render(snapshot(SetupState.Page.BT_COMPARE,false,false,false,0,true,true,1));
            assertTrue("busy is informational; enabled owns native safety",screen.primary.isEnabled());
            screen.render(snapshot(SetupState.Page.BT_COMPARE,false,false,false,0,false,false,1));
            assertFalse(screen.primary.isEnabled());
        });
    }

    @Test public void addressValidationAndImeSubmitUseOneTypedValue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen = new SetupScreen(context(360,800,1));
            List<String> sent = new ArrayList<>();
            screen.setActionListener((a,v) -> sent.add(a+":"+v));
            screen.render(snapshot(SetupState.Page.ADDRESS,true,false,false,0,false,true,1));
            assertEquals("192.168.1.50",screen.address.getHint().toString());
            assertEquals("Example address must never prefill the buffer",0,screen.address.length());
            assertFalse("An example is not a saved address",screen.primary.isEnabled());
            assertTrue(sent.isEmpty());
            for (String bad : new String[]{"", "8.8.8.8", "192.168.01.2", "192.168.1.999"}) {
                screen.address.setText(bad);
                assertFalse(screen.primary.isEnabled());
            }
            screen.address.setText("192.168.10.20");
            assertTrue(screen.primary.isEnabled());
            screen.address.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            assertEquals(List.of("SAVE_ADDRESS:192.168.10.20"),sent);
        });
    }

    @Test public void closingAndRecoveryNeverEnableContinueOrEraseSavedSummary() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen = new SetupScreen(context(360,800,1));
            for (SetupState.Page page : new SetupState.Page[]{SetupState.Page.BT_CLOSING,
                    SetupState.Page.TV_CLOSING, SetupState.Page.CLOSING, SetupState.Page.RECOVERY}) {
                screen.render(snapshot(page,true,true,true,0,true,false,2));
                assertFalse(screen.primary.isEnabled());
                assertTrue(screen.back.isEnabled());
                assertTrue(screen.secondary.isEnabled());
                assertEquals(View.VISIBLE,screen.message.getVisibility());
            }
            screen.render(new SetupState.Snapshot(SetupState.Page.TV_ERROR,true,true,false,
                    "192.168.10.20","TV identity verification failed.","PAIRING_TLS",0,false,true,2,false));
            assertEquals("TV identity verification failed.",screen.message.getText().toString());
            assertTrue(screen.diagnostic.getText().toString().contains("PAIRING_TLS"));
            assertTrue(screen.primary.isEnabled());
            assertEquals("Change address",screen.secondary.getText().toString());
        });
    }

    @Test public void errorGuidanceKeepsNetworkIdentityCodeAndExchangeFailuresDistinct() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SetupScreen screen=new SetupScreen(context(360,800,1));
            String[][] cases={{"PAIRING_CONNECT","Guest networks"},{"CONTROL_CONNECT","Guest networks"},
                    {"PAIRING_TLS","Never bypass"},{"CONTROL_TLS","Never bypass"},
                    {"IDENTITY","Never bypass"},{"SESSION_VALIDATION","Never bypass"},
                    {"CODE","new code"},{"PAIRING_EXCHANGE","exchange did not complete"}};
            for(String[] example:cases) {
                screen.render(new SetupState.Snapshot(SetupState.Page.TV_ERROR,true,true,false,
                        "192.168.10.20","Current native failure.",example[0],0,false,true,1,false));
                assertTrue(example[0],contains(screen.root,example[1]));
                if(!example[0].endsWith("CONNECT")) assertFalse(contains(screen.root,"Guest networks"));
                assertEquals("Current native failure.",screen.message.getText().toString());
            }
        });
    }
    private boolean contains(View view,String text) {
        if(view instanceof TextView label && label.getText().toString().contains(text)) return true;
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++)
            if(contains(group.getChildAt(i),text)) return true;
        return false;
    }

    @Test public void allPagesKeepNamedActionsReachableWithLargeFontsAndShortViewport() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int[] size : new int[][]{{320,800},{800,360},{800,220}})
                for (float scale : new float[]{1,2}) {
                    Context c=context(size[0],size[1],scale);
                    SetupScreen screen=new SetupScreen(c);
                    for (SetupState.Page page : SetupState.Page.values()) {
                        screen.render(snapshot(page,true,true,true,45000,false,true,1));
                        screen.applyPalette(true); screen.applyPalette(false);
                        float density=c.getResources().getDisplayMetrics().density;
                        screen.root.measure(View.MeasureSpec.makeMeasureSpec(Math.round(size[0]*density),View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(Math.round(size[1]*density),View.MeasureSpec.EXACTLY));
                        screen.root.layout(0,0,screen.root.getMeasuredWidth(),screen.root.getMeasuredHeight());
                        List<Button> visibleButtons=new ArrayList<>();
                        collectButtons(screen.root,visibleButtons);
                        for (Button button : visibleButtons) {
                            if (button.getVisibility()!=View.VISIBLE) continue;
                            assertNotNull(button.getContentDescription());
                            assertTrue(button.isFocusable());
                            assertTrue(button.getHeight()>=Math.round(48*density));
                            View parent=(View)button.getParent();
                            while (!(parent instanceof ScrollView)) parent=(View)parent.getParent();
                            ScrollView scroll=(ScrollView)parent;
                            Rect rect=new Rect(0,0,button.getWidth(),button.getHeight());
                            scroll.offsetDescendantRectToMyCoords(button,rect);
                            scroll.scrollTo(0,rect.bottom-scroll.getHeight());
                            assertTrue(page+" reachable",rect.top>=scroll.getScrollY()
                                    && rect.bottom<=scroll.getScrollY()+scroll.getHeight());
                        }
                    }
                }
        });
    }

    @Test public void perItemConfirmationDismissesSafelyAndCannotOutliveEligibility() throws Exception {
        var instrumentation=InstrumentationRegistry.getInstrumentation();
        var activity=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),ProbeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.waitForIdleSync();
        try {
            instrumentation.runOnMainSync(() -> {
                SetupScreen screen=new SetupScreen(activity);
                activity.setContentView(screen.root);
                List<SetupState.Action> sent=new ArrayList<>();
                screen.setActionListener((a,v)->sent.add(a));
                Button[] controls={screen.repairBluetooth,screen.repairTv,screen.forgetBluetooth,screen.forgetTv};
                SetupState.Action[] expected={SetupState.Action.REPAIR_BT,SetupState.Action.REPAIR_TV,
                        SetupState.Action.FORGET_BT,SetupState.Action.FORGET_TV};
                for (int i=0;i<controls.length;i++) {
                    screen.render(snapshot(SetupState.Page.MANAGE,true,true,true,0,false,true,10));
                    controls[i].performClick();
                    ViewGroup sheet=sheet(screen);
                    assertSame(screen.root,sheet.getParent());
                    assertTrue("sheet must retain Activity window focus",activity.hasWindowFocus());
                    find(sheet,"Keep pairing").performClick();
                    assertEquals(i,sent.size());
                    controls[i].performClick();
                    sheet=sheet(screen);
                    find(sheet,controls[i].getText().toString()).performClick();
                    assertEquals(expected[i],sent.get(i));
                }
                screen.forgetTv.performClick();
                ViewGroup sheet=sheet(screen);
                screen.pauseControls();
                assertNull(sheet.getParent());
                assertEquals(4,sent.size());
                screen.render(snapshot(SetupState.Page.MANAGE,true,true,true,0,false,true,10));
                screen.forgetTv.performClick(); sheet=sheet(screen);
                sheet.performClick(); assertEquals(4,sent.size());
                screen.forgetTv.performClick(); sheet=sheet(screen);
                screen.render(snapshot(SetupState.Page.MANAGE,true,true,true,0,false,true,11));
                assertNull(sheet.getParent());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private void collectButtons(View view,List<Button> result) {
        if(view.getVisibility()!=View.VISIBLE) return;
        if(view instanceof Button button) result.add(button);
        if(view instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) collectButtons(group.getChildAt(i),result);
    }

    private ViewGroup sheet(SetupScreen screen) {
        try {
            var field=SetupScreen.class.getDeclaredField("confirmation");field.setAccessible(true);
            return (ViewGroup)field.get(screen);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private Button find(View view,String text) {
        if (view instanceof Button b && text.contentEquals(b.getText())) return b;
        if (view instanceof ViewGroup g) for(int i=0;i<g.getChildCount();i++) {
            Button result=find(g.getChildAt(i),text);if(result!=null)return result;
        }
        return null;
    }
    private Context context(int width,int height,float scale) {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config=new Configuration(base.getResources().getConfiguration());
        config.screenWidthDp=width;config.screenHeightDp=height;config.fontScale=scale;
        return new ContextThemeWrapper(base.createConfigurationContext(config),R.style.RemoteTheme);
    }
}
