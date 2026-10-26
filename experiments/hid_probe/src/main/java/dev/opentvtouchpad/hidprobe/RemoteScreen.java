package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;

/** Native presentation only; the Activity owns all session state and action binding. */
public final class RemoteScreen {
    private static final int[][] BUTTON_STATES = {
        {-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
        {android.R.attr.state_focused}, {}
    };

    public final LinearLayout root;
    public final TextView status, migrationNotice;
    public final Button start, connect, visible, confirm, forget, stop, setup;
    public final Button moveLeft, moveRight, moveUp, moveDown, click;
    public final Button remoteTab, padTab, projectorTab, tvTab, displayTab;
    public final Button surfaceTab, buttonsTab, lightTheme, darkTheme;
    public final EnumMap<MouseReport.Direction, Button> scrollButtons =
            new EnumMap<>(MouseReport.Direction.class);
    public final EnumMap<TvCommand, Button> tvButtons = new EnumMap<>(TvCommand.class);
    public final TextView tvStatus;
    public final EditText tvAddress, tvCode;
    public final Button tvSaveAddress, tvPair, tvConfirm, tvForget;
    public final TouchpadView touchpad;
    public final List<Button> inputs = new ArrayList<>();

    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout remotePage, padPage, setupPage, projectorPage, tvPage, displayPage;
    private final LinearLayout surfacePage, mousePage, footer;
    private final TextView connection, brand;
    private final List<TextView> labels = new ArrayList<>();
    private final List<Button> controls = new ArrayList<>();
    private final List<Button> navigation = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private final List<View> tracks = new ArrayList<>();
    private boolean showingSetup, showingTouchpad, padButtons, dark, connected, imeVisible;
    private int setupSection;

    public RemoteScreen(Context context, BiConsumer<Integer, Integer> move, Runnable tap) {
        this.context = context;
        dark = context.getSharedPreferences("remote-appearance", Context.MODE_PRIVATE).getBoolean("dark", false);
        root = column();
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        brand = text("Open TV", 22);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        setup = iconButton("Setup", R.drawable.ui_setup);
        stop = button("Stop");
        stop.setContentDescription("Stop and disconnect");
        Drawable stopIcon = context.getDrawable(R.drawable.ui_stop).mutate();
        stopIcon.setBounds(0, 0, dp(14), dp(14));
        stop.setCompoundDrawablesRelative(stopIcon, null, null, null);
        stop.setCompoundDrawablePadding(dp(6));
        for (Button action : new Button[]{setup, stop}) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginStart(dp(6));
            header.addView(action, size);
        }
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(0, dp(12), 0, dp(4));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout statusCard = column();
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(2));
        cards.add(statusCard);
        add(content, statusCard);
        connection = text("Mouse: disconnected", 14);
        connection.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(statusCard, connection);
        tvStatus = text("TV: not paired", 14);
        tvStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(statusCard, tvStatus);
        status = text("Start a five-minute run, then connect your projector.", 14);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(statusCard, status);
        start = button("Start");
        start.setContentDescription("Start five-minute remote run");
        connect = button("Connect");
        connect.setContentDescription("Connect saved projector");
        addRow(statusCard, start, connect);

        remotePage = column();
        add(content, remotePage);
        for (TvCommand command : TvCommand.values()) {
            String label = switch (command) {
                case UP -> "Up";
                case DOWN -> "Down";
                case LEFT -> "Left";
                case RIGHT -> "Right";
                case OK -> "OK";
                case HOME -> "Home";
                case BACK -> "Back";
                case VOLUME_UP -> "Volume +";
                case VOLUME_DOWN -> "Volume −";
                case PLAY_PAUSE -> "Play / Pause";
                case NETFLIX -> "N";
            };
            int icon = switch (command) {
                case UP -> R.drawable.ui_up;
                case DOWN -> R.drawable.ui_down;
                case LEFT -> R.drawable.ui_left;
                case RIGHT -> R.drawable.ui_right;
                case HOME -> R.drawable.ui_home;
                case BACK -> R.drawable.ui_back;
                case VOLUME_UP -> R.drawable.ui_volume_up;
                case VOLUME_DOWN -> R.drawable.ui_volume_down;
                case PLAY_PAUSE -> R.drawable.ui_play_pause;
                default -> 0;
            };
            Button control = icon == 0 ? button(label) : iconButton(label, icon);
            control.setContentDescription(switch (command) {
                case UP, DOWN, LEFT, RIGHT -> "TV D-pad " + label.toLowerCase(java.util.Locale.ROOT);
                case VOLUME_UP -> "TV volume up";
                case VOLUME_DOWN -> "TV volume down";
                case NETFLIX -> "Open Netflix";
                default -> "TV " + label;
            });
            control.setEnabled(false);
            tvButtons.put(command, control);
        }
        addRow(remotePage, tvButtons.get(TvCommand.BACK), tvButtons.get(TvCommand.HOME), tvButtons.get(TvCommand.NETFLIX));
        Dpad dpad = new Dpad();
        cards.add(dpad);
        LinearLayout.LayoutParams dpadSize = new LinearLayout.LayoutParams(-1, -2);
        dpadSize.gravity = Gravity.CENTER_HORIZONTAL;
        dpadSize.bottomMargin = dp(20);
        dpadSize.topMargin = dp(8);
        remotePage.addView(dpad, dpadSize);
        addRow(remotePage, tvButtons.get(TvCommand.VOLUME_DOWN), tvButtons.get(TvCommand.PLAY_PAUSE), tvButtons.get(TvCommand.VOLUME_UP));

        padPage = column();
        add(content, padPage);
        surfaceTab = button("Touchpad");
        buttonsTab = button("Buttons instead");
        add(padPage, segment(surfaceTab, buttonsTab));
        surfacePage = column();
        add(padPage, surfacePage);
        touchpad = new TouchpadView(context, move, tap);
        boolean landscape = context.getResources().getConfiguration().screenWidthDp
                > context.getResources().getConfiguration().screenHeightDp;
        surfacePage.addView(touchpad, new LinearLayout.LayoutParams(-1, dp(landscape ? 180 : 240)));
        mousePage = column();
        add(padPage, mousePage);
        moveLeft = iconButton("Move left", R.drawable.ui_left);
        moveRight = iconButton("Move right", R.drawable.ui_right);
        moveUp = iconButton("Move up", R.drawable.ui_up);
        moveDown = iconButton("Move down", R.drawable.ui_down);
        click = button("Click once");
        moveLeft.setContentDescription("Move mouse left");
        moveRight.setContentDescription("Move mouse right");
        moveUp.setContentDescription("Move mouse up");
        moveDown.setContentDescription("Move mouse down");
        click.setContentDescription("Click mouse once");
        addRow(mousePage, new Space(context), moveUp, new Space(context));
        addRow(mousePage, moveLeft, click, moveRight);
        addRow(mousePage, new Space(context), moveDown, new Space(context));
        inputs.add(moveLeft);
        inputs.add(moveRight);
        inputs.add(moveUp);
        inputs.add(moveDown);
        inputs.add(click);
        for (MouseReport.Direction direction : MouseReport.Direction.values()) {
            String label = switch (direction) {
                case UP -> "Scroll up";
                case DOWN -> "Scroll down";
                case LEFT -> "Scroll left";
                case RIGHT -> "Scroll right";
            };
            int icon = switch (direction) {
                case UP -> R.drawable.ui_scroll_up;
                case DOWN -> R.drawable.ui_scroll_down;
                case LEFT -> R.drawable.ui_scroll_left;
                case RIGHT -> R.drawable.ui_scroll_right;
            };
            Button control = iconButton(label, icon);
            scrollButtons.put(direction, control);
            inputs.add(control);
        }
        add(padPage, text("Scroll", 16));
        addRow(padPage, scrollButtons.get(MouseReport.Direction.UP), scrollButtons.get(MouseReport.Direction.DOWN),
                scrollButtons.get(MouseReport.Direction.LEFT), scrollButtons.get(MouseReport.Direction.RIGHT));

        setupPage = column();
        add(content, setupPage);
        projectorTab = button("Projector");
        tvTab = button("TV controls");
        displayTab = button("Display");
        add(setupPage, segment(projectorTab, tvTab, displayTab));
        projectorPage = column();
        tvPage = column();
        displayPage = column();
        add(setupPage, projectorPage);
        add(setupPage, tvPage);
        add(setupPage, displayPage);
        add(projectorPage, text("Bluetooth projector setup", 20));
        migrationNotice = text("Scrolling update needs fresh Bluetooth pairing. Remove only the Nord–XGIMI bond "
                + "on both devices, then pair again and compare the numbers on both screens.", 14);
        migrationNotice.setVisibility(View.GONE);
        add(projectorPage, migrationNotice);
        add(projectorPage, text("Five-minute diagnostic. First enrollment requires matching numbers on BOTH intended devices. "
                + "A saved projector uses the current OS-managed bond; its original key cannot be pinned by this app.", 14));
        visible = button("Make visible for 120 seconds");
        add(projectorPage, visible);
        add(projectorPage, text("Use the physical TV remote for Bluetooth pairing; no Bluetooth code is entered here. "
                + "Compare the numbers on BOTH intended screens before accepting.", 14));
        confirm = button("I compared matching codes on BOTH screens — save and connect");
        add(projectorPage, confirm);
        add(projectorPage, text("Keep this screen open after Connect. After confirmation, leaving the app ends the run.", 14));
        forget = button("Forget Bluetooth projector");
        add(projectorPage, forget);
        add(projectorPage, text("Forget clears the app record. Remove the old bond in OS settings before fresh comparison.", 14));

        add(tvPage, text("TV controls setup", 20));
        add(tvPage, text("First enroll Bluetooth, then Stop. Start a NEW run without connecting or sending input, "
                + "and choose Pair TV. Enter only the six-character hexadecimal code shown on the intended TV. "
                + "After pairing finishes, use a new Start / Connect to connect both Mouse and TV controls.", 14));
        tvAddress = entry("Projector Wi-Fi IPv4 address");
        tvAddress.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        tvAddress.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        tvAddress.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        add(tvPage, tvAddress);
        tvSaveAddress = button("Save TV address");
        add(tvPage, tvSaveAddress);
        add(tvPage, text("Edit the Wi-Fi address while stopped. An IP change preserves the saved TV identity. "
                + "A changed certificate requires explicit Forget TV controls and fresh pairing on the intended TV.", 14));
        tvPair = button("Pair TV");
        add(tvPage, tvPair);
        tvCode = entry("Six-character TV pairing code");
        tvCode.setKeyListener(DigitsKeyListener.getInstance("0123456789abcdefABCDEF"));
        tvCode.setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        tvCode.setTransformationMethod(PasswordTransformationMethod.getInstance());
        tvCode.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
        tvCode.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        add(tvPage, tvCode);
        tvConfirm = button("Confirm TV code");
        add(tvPage, tvConfirm);
        tvForget = button("Forget TV controls");
        add(tvPage, tvForget);
        add(tvPage, text("Forget TV controls removes only this app's TV enrollment and client identity. "
                + "Bluetooth pairing and the TV's other remotes stay separate.", 14));
        for (Button control : new Button[]{tvSaveAddress, tvPair, tvConfirm, tvForget}) control.setEnabled(false);

        add(displayPage, text("Display", 20));
        add(displayPage, text("Choose how the app looks. It applies to every screen.", 14));
        lightTheme = button("Light");
        darkTheme = button("Dark");
        lightTheme.setMinHeight(dp(96));
        darkTheme.setMinHeight(dp(96));
        addRow(displayPage, lightTheme, darkTheme);

        remoteTab = button("Remote");
        padTab = button("Touchpad");
        footer = segment(remoteTab, padTab);
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            root.setPadding(padding + bars.left, padding + bars.top,
                    padding + bars.right, padding + bars.bottom);
            imeVisible = insets.getInsets(WindowInsets.Type.ime()).bottom > 0;
            updateFooter();
            return insets;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { applySystemBars(); }
            @Override public void onViewDetachedFromWindow(View view) { }
        });

        for (Button input : inputs) input.setEnabled(false);
        start.setEnabled(false);
        connect.setEnabled(false);
        visible.setEnabled(false);
        confirm.setEnabled(false);
        bindNavigation(setup, () -> showSetup(!showingSetup));
        bindNavigation(remoteTab, () -> showTouchpad(false));
        bindNavigation(padTab, () -> showTouchpad(true));
        bindNavigation(projectorTab, () -> showSetupSection(0));
        bindNavigation(tvTab, () -> showSetupSection(1));
        bindNavigation(displayTab, () -> showSetupSection(2));
        bindNavigation(surfaceTab, () -> showPadButtons(false));
        bindNavigation(buttonsTab, () -> showPadButtons(true));
        bindNavigation(lightTheme, () -> setDark(false));
        bindNavigation(darkTheme, () -> setDark(true));
        updatePages();
        applyTheme();
    }

    public void showSetup(boolean show) {
        touchpad.cancelGesture();
        showingSetup = show;
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void showTouchpad(boolean show) {
        touchpad.cancelGesture();
        showingTouchpad = show;
        showingSetup = false;
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void showSetupSection(int section) {
        if (section < 0 || section > 2) throw new IllegalArgumentException("Unknown setup section");
        touchpad.cancelGesture();
        setupSection = section;
        showingSetup = true;
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void showPadButtons(boolean show) {
        touchpad.cancelGesture();
        padButtons = show;
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void setDark(boolean dark) {
        touchpad.cancelGesture();
        this.dark = dark;
        context.getSharedPreferences("remote-appearance", Context.MODE_PRIVATE).edit().putBoolean("dark", dark).apply();
        applyTheme();
    }

    public boolean isDark() { return dark; }

    public void setConnected(boolean connected) {
        this.connected = connected;
        connection.setText(connected ? "Mouse: connected" : "Mouse: disconnected");
        connection.setTextColor(connected ? color("#1F8A4C", "#4ADE80") : color("#5B636D", "#9AA3AD"));
    }

    private void updateFooter() {
        footer.setVisibility(showingSetup || imeVisible ? View.GONE : View.VISIBLE);
    }

    private void updatePages() {
        updateFooter();
        remotePage.setVisibility(!showingSetup && !showingTouchpad ? View.VISIBLE : View.GONE);
        padPage.setVisibility(!showingSetup && showingTouchpad ? View.VISIBLE : View.GONE);
        setupPage.setVisibility(showingSetup ? View.VISIBLE : View.GONE);
        projectorPage.setVisibility(setupSection == 0 ? View.VISIBLE : View.GONE);
        tvPage.setVisibility(setupSection == 1 ? View.VISIBLE : View.GONE);
        displayPage.setVisibility(setupSection == 2 ? View.VISIBLE : View.GONE);
        surfacePage.setVisibility(padButtons ? View.GONE : View.VISIBLE);
        mousePage.setVisibility(padButtons ? View.VISIBLE : View.GONE);
        brand.setText(showingSetup ? "Setup" : "Open TV");
        setup.setContentDescription(showingSetup ? (showingTouchpad ? "Show touchpad" : "Show remote") : "Show setup");
        select(setup, showingSetup);
        select(remoteTab, !showingSetup && !showingTouchpad);
        select(padTab, !showingSetup && showingTouchpad);
        select(projectorTab, setupSection == 0);
        select(tvTab, setupSection == 1);
        select(displayTab, setupSection == 2);
        select(surfaceTab, !padButtons);
        select(buttonsTab, padButtons);
    }

    private void select(Button button, boolean selected) {
        button.setSelected(selected);
        button.setStateDescription(selected ? "Selected" : "Not selected");
        styleButton(button);
    }

    private void bindNavigation(Button button, Runnable action) {
        navigation.add(button);
        button.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchpad.cancelGesture();
            return false;
        });
        button.setOnClickListener(view -> { touchpad.cancelGesture(); action.run(); });
    }

    private void applyTheme() {
        root.setBackgroundColor(color("#F4F5F7", "#0E1012"));
        for (TextView label : labels) label.setTextColor(label.getTextSize() <= 14 * context.getResources().getDisplayMetrics().scaledDensity
                ? color("#5B636D", "#9AA3AD") : color("#14171A", "#F2F4F6"));
        status.setTextColor(color("#5B636D", "#9AA3AD"));
        for (View card : cards) card.setBackground(shape(color("#FFFFFF", "#181B1F"), color("#E1E4E8", "#2C323A"), card instanceof Dpad ? 120 : 24));
        for (View track : tracks) track.setBackground(shape(color("#E9ECF0", "#181B1F"), Color.TRANSPARENT, 28));
        for (Button button : controls) styleButton(button);
        for (EditText field : new EditText[]{tvAddress, tvCode}) {
            field.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                    new int[]{color("#6B737C", "#9AA3AD"), color("#14171A", "#F2F4F6")}));
            field.setHintTextColor(color("#5B636D", "#9AA3AD"));
            field.setBackgroundTintList(null);
            StateListDrawable fieldBackground = new StateListDrawable();
            fieldBackground.addState(new int[]{android.R.attr.state_focused},
                    shape(color("#FFFFFF", "#181B1F"), color("#2F6FEB", "#6AA8FF"), 16));
            fieldBackground.addState(new int[]{},
                    shape(color("#FFFFFF", "#181B1F"), color("#E1E4E8", "#2C323A"), 16));
            field.setBackground(fieldBackground);
        }
        migrationNotice.setTextColor(color("#5C3D00", "#F4C675"));
        migrationNotice.setBackground(shape(color("#FFF4DC", "#2A2417"), Color.TRANSPARENT, 16));
        touchpad.setPalette(color("#E9ECF0", "#181B1F"), color("#DCE0E5", "#2C323A"),
                color("#DCE0E5", "#2C323A"), color("#5B636D", "#9AA3AD"));
        select(lightTheme, !dark);
        select(darkTheme, dark);
        setConnected(connected);
        applySystemBars();
    }

    private void applySystemBars() {
        // The Activity has no decor during construction; attachment reapplies the palette.
        if (!root.isAttachedToWindow()) return;
        Context owner = context;
        java.util.Set<Context> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while (!(owner instanceof android.app.Activity)
                && owner instanceof android.content.ContextWrapper wrapper && visited.add(owner)) {
            owner = wrapper.getBaseContext();
        }
        WindowInsetsController controller = root.getWindowInsetsController();
        boolean barColorsApplied = false;
        if (owner instanceof android.app.Activity activity) {
            android.view.Window window = activity.getWindow();
            int background = color("#F4F5F7", "#0E1012");
            // API31 retains the XML bar colors unless the Window is updated too.
            window.setStatusBarColor(background);
            window.setNavigationBarColor(background);
            controller = window.getInsetsController();
            barColorsApplied = true;
        }
        if (controller != null) {
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            controller.setSystemBarsAppearance(dark && barColorsApplied ? 0 : mask, mask);
        }
    }

    private void styleButton(Button button) {
        int accent = color("#2F6FEB", "#6AA8FF");
        int text = color("#14171A", "#F2F4F6");
        int surface = color("#FFFFFF", "#181B1F");
        int border = color("#E1E4E8", "#2C323A");
        boolean destructive = button == stop || button == forget || button == tvForget;
        boolean primary = button == start || button == connect || button == visible || button == confirm || button == tvPair
                || button == tvConfirm || button == click || button == tvButtons.get(TvCommand.OK)
                || button == setup && button.isSelected();
        boolean nav = navigation.contains(button) && button != setup && button != lightTheme && button != darkTheme;
        int fill = primary ? accent : surface;
        int foreground = primary ? color("#FFFFFF", "#0E1012") : text;
        if (destructive) { fill = Color.TRANSPARENT; foreground = color("#C4352B", "#FF8A80"); border = foreground; }
        boolean direction = button == tvButtons.get(TvCommand.UP) || button == tvButtons.get(TvCommand.DOWN)
                || button == tvButtons.get(TvCommand.LEFT) || button == tvButtons.get(TvCommand.RIGHT);
        boolean ok = button == tvButtons.get(TvCommand.OK);
        if (direction) {
            fill = Color.TRANSPARENT;
            border = Color.TRANSPARENT;
        }
        if (nav) {
            fill = button.isSelected() ? color("#FFFFFF", "#2C323A") : Color.TRANSPARENT;
            foreground = button.isSelected() ? text : color("#5B636D", "#9AA3AD");
            border = Color.TRANSPARENT;
        }
        if ((button == lightTheme || button == darkTheme) && button.isSelected()) border = accent;
        StateListDrawable background = new StateListDrawable();
        background.addState(BUTTON_STATES[0], shape(direction || ok ? fill : color("#E9ECF0", "#181B1F"), border, 100));
        background.addState(BUTTON_STATES[1], shape(direction || ok ? fill : color("#DCE8FF", "#2C4160"), direction ? Color.TRANSPARENT : accent, 100));
        background.addState(BUTTON_STATES[2], shape(fill, accent, direction ? 8 : 100));
        background.addState(BUTTON_STATES[3], shape(fill, border, 100));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(BUTTON_STATES, new int[]{ok ? foreground : color("#6B737C", "#6F7882"), direction ? accent : ok ? foreground : text, foreground, foreground}));
        if (button == stop) button.setCompoundDrawableTintList(button.getTextColors());
    }

    private int color(String light, String night) { return Color.parseColor(dark ? night : light); }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String label, int size) {
        TextView view = new TextView(context);
        view.setText(label);
        view.setTextSize(size);
        if (size >= 20) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        labels.add(view);
        return view;
    }

    private Button button(String label) { return configureButton(new Button(context), label); }

    private Button iconButton(String label, int resource) { return configureButton(new IconButton(resource), label); }

    private Button configureButton(Button button, String label) {
        button.setText(label);
        button.setContentDescription(label);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        controls.add(button);
        return button;
    }

    private EditText entry(String hint) {
        EditText field = new EditText(context);
        field.setHint(hint);
        field.setContentDescription(hint);
        field.setTextSize(16);
        field.setPadding(dp(16), dp(12), dp(16), dp(12));
        field.setSingleLine(true);
        field.setMinHeight(dp(48));
        field.setMinimumHeight(dp(48));
        field.setSaveEnabled(false);
        field.setSaveFromParentEnabled(false);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        field.setEnabled(false);
        return field;
    }

    private GradientDrawable shape(int fill, int border, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), border);
        return drawable;
    }

    private void add(LinearLayout parent, View child) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = dp(12);
        parent.addView(child, size);
    }

    private void addRow(LinearLayout parent, View... children) { add(parent, row(children)); }

    private LinearLayout row(View... children) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < children.length; i++) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (i + 1 < children.length) size.setMarginEnd(dp(8));
            row.addView(children[i], size);
        }
        return row;
    }

    private LinearLayout segment(Button... buttons) {
        LinearLayout segment = row(buttons);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (Button button : buttons) {
            button.setTextSize(14);
            button.setPadding(dp(6), dp(10), dp(6), dp(10));
        }
        tracks.add(segment);
        return segment;
    }

    /** Keep the accessible/test label while centering a native vector without hidden text spacing. */
    private final class IconButton extends Button {
        private final Drawable icon;
        IconButton(int resource) {
            super(context);
            icon = context.getDrawable(resource).mutate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(dp(48), widthSpec), resolveSize(dp(48), heightSpec));
        }
        @Override protected void onDraw(Canvas canvas) {
            int size = dp(24);
            int left = (getWidth() - size) / 2;
            int top = (getHeight() - size) / 2;
            icon.setBounds(left, top, left + size, top + size);
            icon.setTint(getCurrentTextColor());
            icon.draw(canvas);
        }
    }

    /** A bounded circle with disjoint cardinal targets and an 80dp center at its normal 240dp size. */
    private final class Dpad extends FrameLayout {
        Dpad() {
            super(context);
            for (TvCommand command : new TvCommand[]{TvCommand.UP, TvCommand.DOWN, TvCommand.LEFT, TvCommand.RIGHT, TvCommand.OK}) {
                addView(tvButtons.get(command), new FrameLayout.LayoutParams(dp(64), dp(64)));
            }
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int size = Math.min(dp(240), Math.max(0, MeasureSpec.getSize(widthSpec) - dp(40)));
            setMeasuredDimension(size, size);
            for (int i = 0; i < getChildCount(); i++) {
                int target = i == 4 ? size / 3 : size * 4 / 15;
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY));
            }
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int size = getMeasuredWidth();
            int inset = size / 30;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int target = child.getMeasuredWidth();
                int x = (size - target) / 2;
                int y = x;
                if (i == 0) y = inset;
                if (i == 1) y = size - target - inset;
                if (i == 2) x = inset;
                if (i == 3) x = size - target - inset;
                child.layout(x, y, x + target, y + target);
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
