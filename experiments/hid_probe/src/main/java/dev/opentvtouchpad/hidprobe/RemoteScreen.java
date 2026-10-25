package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;

/** Native presentation only; the Activity owns all session state and action binding. */
public final class RemoteScreen {
    private static final int SURFACE = Color.rgb(48, 48, 48);
    private static final int CONTROL = Color.rgb(32, 32, 32);
    private static final int TEXT = Color.rgb(244, 241, 235);
    private static final int MUTED = Color.rgb(189, 189, 189);
    private static final int GREEN = Color.rgb(166, 220, 174);
    private static final int[][] BUTTON_STATES = {
        {-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
        {android.R.attr.state_focused}, {}
    };

    public final LinearLayout root;
    public final TextView status, migrationNotice;
    public final Button start, connect, visible, confirm, forget, stop, close, setup;
    public final Button moveLeft, moveRight, moveUp, moveDown, click;
    public final EnumMap<MouseReport.Direction, Button> scrollButtons =
            new EnumMap<>(MouseReport.Direction.class);
    public final TouchpadView touchpad;
    public final List<Button> inputs = new ArrayList<>();

    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout remotePage, setupPage;
    private final TextView connection;
    private boolean showingSetup;

    public RemoteScreen(Context context, BiConsumer<Integer, Integer> move, Runnable tap) {
        this.context = context;
        root = column();
        root.setBackgroundColor(SURFACE);
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("Open TV", 22);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        setup = button("Setup");
        header.addView(setup, new LinearLayout.LayoutParams(-2, -2));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(0, dp(12), 0, dp(12));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        connection = text("Disconnected", 16);
        connection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(content, connection);
        status = text("Start a five-minute run, then connect your projector.", 16);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(content, status);
        start = button("Start");
        start.setContentDescription("Start five-minute HID run");
        connect = button("Connect");
        connect.setContentDescription("Connect saved projector");
        addRow(content, start, connect);

        remotePage = column();
        add(content, remotePage);
        add(remotePage, text("Touchpad", 20));
        touchpad = new TouchpadView(context, move, tap);
        boolean landscape = context.getResources().getConfiguration().screenWidthDp
                > context.getResources().getConfiguration().screenHeightDp;
        remotePage.addView(touchpad, new LinearLayout.LayoutParams(-1, dp(landscape ? 180 : 240)));
        add(remotePage, text("Slide to move · Tap to click", 16));
        for (MouseReport.Direction direction : MouseReport.Direction.values()) {
            String label = switch (direction) {
                case UP -> "Scroll up";
                case DOWN -> "Scroll down";
                case LEFT -> "Scroll left";
                case RIGHT -> "Scroll right";
            };
            Button control = button(label);
            scrollButtons.put(direction, control);
            inputs.add(control);
        }
        addRow(remotePage, scrollButtons.get(MouseReport.Direction.UP),
                scrollButtons.get(MouseReport.Direction.DOWN));
        addRow(remotePage, scrollButtons.get(MouseReport.Direction.LEFT),
                scrollButtons.get(MouseReport.Direction.RIGHT));
        add(remotePage, text("Mouse controls", 20));
        add(remotePage, text("Use these buttons instead of the touchpad.", 16));
        moveLeft = button("Move left");
        moveRight = button("Move right");
        moveUp = button("Move up");
        moveDown = button("Move down");
        click = button("Click once");
        moveLeft.setContentDescription("Move mouse left");
        moveRight.setContentDescription("Move mouse right");
        moveUp.setContentDescription("Move mouse up");
        moveDown.setContentDescription("Move mouse down");
        click.setContentDescription("Click mouse once");
        addRow(remotePage, moveLeft, moveRight);
        addRow(remotePage, moveUp, moveDown);
        add(remotePage, click);
        inputs.add(moveLeft);
        inputs.add(moveRight);
        inputs.add(moveUp);
        inputs.add(moveDown);
        inputs.add(click);

        setupPage = column();
        add(content, setupPage);
        add(setupPage, text("Projector setup", 20));
        migrationNotice = text("Scrolling update needs fresh Bluetooth pairing. Remove only the Nord–XGIMI bond "
                + "on both devices, then pair again and compare the numbers on both screens.", 16);
        migrationNotice.setVisibility(View.GONE);
        add(setupPage, migrationNotice);
        add(setupPage, text("Five-minute diagnostic. First enrollment requires matching numbers on BOTH intended devices. "
                + "A saved projector uses the current OS-managed bond; its original key cannot be pinned by this app.", 16));
        visible = button("Make visible for 120 seconds");
        add(setupPage, visible);
        add(setupPage, text("Use the physical TV remote to pair. No code is entered here. "
                + "Compare the numbers on BOTH intended screens before accepting.", 16));
        confirm = button("I compared matching codes on BOTH screens — save and connect");
        add(setupPage, confirm);
        add(setupPage, text("Keep this screen open after Connect. After confirmation, leaving the app ends the run.", 16));
        forget = button("Forget projector");
        add(setupPage, forget);
        add(setupPage, text("Forget clears the app record. Remove the old bond in OS settings before fresh comparison.", 16));

        LinearLayout footer = new LinearLayout(context);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(8), 0, 0);
        stop = button("Stop / Disconnect");
        close = button("Close");
        LinearLayout.LayoutParams stopSize = new LinearLayout.LayoutParams(0, -2, 2);
        stopSize.setMarginEnd(dp(8));
        footer.addView(stop, stopSize);
        footer.addView(close, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        for (Button input : inputs) input.setEnabled(false);
        start.setEnabled(false);
        connect.setEnabled(false);
        visible.setEnabled(false);
        confirm.setEnabled(false);
        setup.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchpad.cancelGesture();
            return false;
        });
        setup.setOnClickListener(view -> showSetup(!showingSetup));
        showSetup(false);
        setConnected(false);
    }

    public void showSetup(boolean show) {
        touchpad.cancelGesture();
        showingSetup = show;
        remotePage.setVisibility(show ? View.GONE : View.VISIBLE);
        setupPage.setVisibility(show ? View.VISIBLE : View.GONE);
        setup.setText(show ? "Remote" : "Setup");
        setup.setContentDescription(show ? "Show remote" : "Show setup");
        scroll.scrollTo(0, 0);
    }

    public void setConnected(boolean connected) {
        connection.setText(connected ? "Connected" : "Disconnected");
        connection.setTextColor(connected ? GREEN : MUTED);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String label, int size) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(TEXT);
        return view;
    }

    private Button button(String label) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        button.setBackgroundTintList(null);
        StateListDrawable background = new StateListDrawable();
        background.addState(BUTTON_STATES[0], pill(Color.rgb(56, 56, 56), Color.rgb(119, 119, 119)));
        background.addState(BUTTON_STATES[1], pill(TEXT, TEXT));
        background.addState(BUTTON_STATES[2], pill(Color.rgb(83, 83, 83), TEXT));
        background.addState(BUTTON_STATES[3], pill(CONTROL, Color.rgb(105, 105, 105)));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(BUTTON_STATES, new int[] {MUTED, CONTROL, TEXT, TEXT}));
        button.setStateListAnimator(null);
        return button;
    }

    private GradientDrawable pill(int fill, int border) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(100));
        drawable.setStroke(dp(2), border);
        return drawable;
    }

    private void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = dp(12);
        parent.addView(child, size);
    }

    private void addRow(LinearLayout parent, Button left, Button right) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams leftSize = new LinearLayout.LayoutParams(0, -2, 1);
        leftSize.setMarginEnd(dp(8));
        row.addView(left, leftSize);
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        add(parent, row);
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
