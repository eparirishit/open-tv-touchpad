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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

/** Native presentation only; the Activity owns all session state and action binding. */
public final class RemoteScreen {
    private static final int[][] BUTTON_STATES = {
        {-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
        {android.R.attr.state_focused}, {}
    };

    public final LinearLayout root;
    public final TextView status;
    public final Button connect, stop, setup, statusSummary;
    public final SetupScreen setupView;
    public final Button remoteTab, padTab;
    public final Button lightTheme, darkTheme;
    public final EnumMap<MouseReport.Direction, Button> scrollButtons =
            new EnumMap<>(MouseReport.Direction.class);
    public final EnumMap<TvCommand, Button> tvButtons = new EnumMap<>(TvCommand.class);
    public final TextView tvStatus;
    public final TouchpadView touchpad;
    public final SeekBar sensitivity;
    public final List<Button> inputs = new ArrayList<>();

    private final Context context;
    private final ScrollView scroll;
    private final LinearLayout remotePage, padPage, setupPage, displayPage;
    private final LinearLayout keyboardPanel;
    private final EditText keyboardText;
    private final TextView keyboardError;
    private final Button keyboard, keyboardSend;
    private final LinearLayout statusCard;
    private final LinearLayout statusChips;
    private final Dpad dpad;
    private final LinearLayout header, footer;
    private final Button appearanceBack;
    private final LinearLayout displayHeader;
    private final TextView connection, brand, sensitivityLabel, sensitivityValue;
    private final View controlsDivider;
    private final List<TextView> labels = new ArrayList<>();
    private final List<Button> controls = new ArrayList<>();
    private final List<Button> navigation = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private final List<View> tracks = new ArrayList<>();
    private boolean showingSetup, showingTouchpad, dark, connected, tvConnected, imeVisible, statusExpanded, statusPainted;
    private boolean showingAppearance;
    private boolean keyboardOpen, securedKeyboardWindow, keyboardConnected, keyboardSupported;
    private TvControlProtocol.ImeField keyboardField;
    private BiPredicate<TvControlProtocol.ImeField, String> keyboardSender = (field, text) -> false;

    public RemoteScreen(Context context, BiConsumer<Integer, Integer> move, Runnable tap) {
        this.context = context;
        dark = context.getSharedPreferences("remote-appearance", Context.MODE_PRIVATE).getBoolean("dark", false);
        root = column();
        int padding = dp(12);
        root.setPadding(padding, padding, padding, padding);

        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        // Mockup spacing: content sits 20dp from the screen edges (12dp root + 8dp here).
        header.setPadding(dp(8), 0, dp(8), 0);
        brand = text("Open TV", 24);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        setup = iconButton("Setup", R.drawable.ui_setup);
        stop = button("Stop");
        stop.setContentDescription("Stop and disconnect");
        leadingIcon(stop, R.drawable.ui_stop, 14);
        stop.setEnabled(false);
        for (Button action : new Button[]{stop, setup}) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
            size.setMarginStart(dp(6));
            header.addView(action, size);
        }
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        content.setPadding(dp(8), dp(12), dp(8), dp(4));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        statusCard = column();
        statusCard.setPadding(dp(16), dp(14), dp(16), dp(2));
        add(content, statusCard);
        // Shown only while the run is fully connected; it collapses the card to this one row.
        statusSummary = button("Connected");
        statusSummary.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        statusSummary.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusSummary.setTextSize(15);
        statusSummary.setPadding(0, 0, 0, 0);
        statusSummary.setMinHeight(dp(56));
        statusSummary.setMinimumHeight(dp(56));
        add(statusCard, statusSummary);
        // Mouse and TV states are separate pill chips; they stack when there is no room side by side.
        statusChips = new ChipRow();
        connection = chip("Mouse: disconnected");
        tvStatus = chip("TV: not paired");
        statusChips.addView(connection);
        statusChips.addView(tvStatus);
        add(statusCard, statusChips);
        status = text("Connect your saved projector, or open Settings for setup.", 13);
        status.setPadding(dp(12), dp(10), dp(12), dp(10));
        status.setCompoundDrawablePadding(dp(10));
        status.setLineSpacing(dp(2), 1.1f);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        add(statusCard, status);
        connect = button("Connect");
        connect.setContentDescription("Connect saved projector");
        add(statusCard, connect);

        keyboard = iconButton("Keyboard", R.drawable.ui_keyboard);
        keyboard.setContentDescription("Open phone keyboard for the projector");
        keyboardPanel = column();
        keyboardPanel.setPadding(dp(16), dp(12), dp(16), dp(8));
        cards.add(keyboardPanel);
        add(content, keyboardPanel);
        keyboardText = new EditText(context);
        keyboardText.setHint("Text for the projector");
        keyboardText.setSingleLine(true);
        keyboardText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        keyboardText.setImeOptions(EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        keyboardText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(TvControlProtocol.ImeField.MAX_TEXT_LENGTH)});
        keyboardText.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        keyboardText.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO);
        keyboardText.setSaveEnabled(false);
        add(keyboardPanel, keyboardText);
        keyboardError = text("", 13);
        keyboardError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        keyboardError.setVisibility(View.GONE);
        add(keyboardPanel, keyboardError);
        Button keyboardCancel = button("Cancel");
        keyboardSend = button("Send to TV");
        addRow(keyboardPanel, keyboardCancel, keyboardSend);
        keyboard.setOnClickListener(view -> openKeyboard());
        keyboardCancel.setOnClickListener(view -> closeKeyboard());
        keyboardSend.setOnClickListener(view -> sendKeyboard());
        keyboardText.setOnEditorActionListener((view, action, event) ->
                action == EditorInfo.IME_ACTION_SEND && keyboardSend.performClick());

        remotePage = column();
        // Remote and Touchpad pages take the viewport space left under the status card, so
        // collapsing the card (or a tall phone) gives the D-pad and touchpad more room.
        content.addView(remotePage, fill());
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
                case NETFLIX -> "Netflix";
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
                case NETFLIX -> R.drawable.ui_netflix;
                default -> 0;
            };
            Button control = icon == 0 ? configureButton(new LabelButton(), label) : iconButton(label, icon);
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
        for (TvCommand command : new TvCommand[]{TvCommand.BACK, TvCommand.HOME, TvCommand.NETFLIX,
                TvCommand.VOLUME_DOWN, TvCommand.PLAY_PAUSE, TvCommand.VOLUME_UP}) large(tvButtons.get(command));
        large(keyboard);
        for (TvCommand command : new TvCommand[]{TvCommand.VOLUME_DOWN, TvCommand.PLAY_PAUSE, TvCommand.VOLUME_UP})
            large(tvButtons.get(command), 60);
        for (TvCommand command : new TvCommand[]{TvCommand.UP, TvCommand.DOWN, TvCommand.LEFT, TvCommand.RIGHT})
            ((IconButton) tvButtons.get(command)).iconSize = 28;
        tvButtons.get(TvCommand.OK).setTextSize(20);
        tvButtons.get(TvCommand.OK).setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        add(remotePage, row(12, tvButtons.get(TvCommand.BACK), tvButtons.get(TvCommand.HOME),
                tvButtons.get(TvCommand.NETFLIX), keyboard));
        dpad = new Dpad();
        // The D-pad takes the remaining height and centres its circle, spacing the rows evenly.
        LinearLayout.LayoutParams dpadSize = new LinearLayout.LayoutParams(-1, 0, 1);
        dpadSize.gravity = Gravity.CENTER_HORIZONTAL;
        dpadSize.bottomMargin = dp(12);
        remotePage.addView(dpad, dpadSize);
        add(remotePage, row(12, tvButtons.get(TvCommand.VOLUME_DOWN), tvButtons.get(TvCommand.PLAY_PAUSE),
                tvButtons.get(TvCommand.VOLUME_UP)));

        padPage = column();
        content.addView(padPage, fill());
        touchpad = new TouchpadView(context, move, tap);
        boolean landscape = context.getResources().getConfiguration().screenWidthDp
                > context.getResources().getConfiguration().screenHeightDp;
        // Never smaller than the established pad; grows into free space when the page fills.
        touchpad.setMinimumHeight(dp(landscape ? 180 : 240));
        padPage.addView(touchpad, new LinearLayout.LayoutParams(-1, 0, 1));
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
            Button control = large(iconButton(label, icon));
            scrollButtons.put(direction, control);
            inputs.add(control);
        }
        // One controls card in the app's card style: scroll on top, sensitivity below.
        LinearLayout controls = column();
        controls.setPadding(dp(16), dp(14), dp(16), dp(2));
        cards.add(controls);
        LinearLayout.LayoutParams controlsSize = new LinearLayout.LayoutParams(-1, -2);
        controlsSize.topMargin = dp(12);
        padPage.addView(controls, controlsSize);
        add(controls, section("Scroll"));
        addRow(controls, scrollButtons.get(MouseReport.Direction.UP), scrollButtons.get(MouseReport.Direction.DOWN),
                scrollButtons.get(MouseReport.Direction.LEFT), scrollButtons.get(MouseReport.Direction.RIGHT));
        controlsDivider = new View(context);
        add(controls, controlsDivider);
        controlsDivider.getLayoutParams().height = dp(1);
        LinearLayout speedHeader = new LinearLayout(context);
        speedHeader.setGravity(Gravity.CENTER_VERTICAL);
        sensitivityLabel = section("Touchpad sensitivity");
        speedHeader.addView(sensitivityLabel, new LinearLayout.LayoutParams(0, -2, 1));
        sensitivityValue = text("1.00×", 13);
        sensitivityValue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sensitivityValue.setPadding(dp(10), dp(4), dp(10), dp(4));
        // The slider announces its own value; the chip is the visual copy of it.
        sensitivityValue.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        speedHeader.addView(sensitivityValue, new LinearLayout.LayoutParams(-2, -2));
        controls.addView(speedHeader, new LinearLayout.LayoutParams(-1, -2));
        sensitivity = new SeekBar(context);
        sensitivity.setMax(6);
        sensitivity.setMinHeight(dp(48));
        sensitivity.setPadding(dp(10), 0, dp(10), 0);
        sensitivity.setContentDescription("Touchpad sensitivity");
        android.content.SharedPreferences touchpadPreferences = context.getSharedPreferences("touchpad-preferences", Context.MODE_PRIVATE);
        Object storedSpeed = touchpadPreferences.getAll().get("sensitivity-step");
        int speedStep = storedSpeed instanceof Integer value ? Math.max(0, Math.min(6, value)) : 2;
        sensitivity.setProgress(speedStep);
        updateSensitivity(speedStep);
        sensitivity.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchpad.cancelGesture();
            return false;
        });
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateSensitivity(progress);
                touchpadPreferences.edit().putInt("sensitivity-step", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { touchpad.cancelGesture(); }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        controls.addView(sensitivity, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout captions = new LinearLayout(context);
        TextView slower = text("Slower", 12), faster = text("Faster", 12);
        faster.setGravity(Gravity.END);
        captions.addView(slower, new LinearLayout.LayoutParams(0, -2, 1));
        captions.addView(faster, new LinearLayout.LayoutParams(0, -2, 1));
        captions.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        add(controls, captions);

        setupPage = column();
        root.addView(setupPage, new LinearLayout.LayoutParams(-1, 0, 1));
        setupView = new SetupScreen(context);
        setupPage.addView(setupView.root, new LinearLayout.LayoutParams(-1, -1));
        displayPage = column();
        add(content, displayPage);
        // Same round back control row as the wizard header; the page title names the screen.
        appearanceBack = iconButton("Back to setup", R.drawable.ui_left);
        ((IconButton) appearanceBack).icon.setAutoMirrored(true);
        displayHeader = new LinearLayout(context);
        displayHeader.setGravity(Gravity.CENTER_VERTICAL);
        displayHeader.addView(appearanceBack, new LinearLayout.LayoutParams(dp(48), dp(48)));
        displayHeader.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));
        add(displayPage, displayHeader);
        add(displayPage, text("Display", 22));
        add(displayPage, text("Choose how the app looks. It applies to every screen.", 14));
        lightTheme = button("Light");
        darkTheme = button("Dark");
        lightTheme.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ui_sun, 0, 0);
        darkTheme.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ui_moon, 0, 0);
        lightTheme.setCompoundDrawablePadding(dp(6));
        darkTheme.setCompoundDrawablePadding(dp(6));
        lightTheme.setMinHeight(dp(96));
        darkTheme.setMinHeight(dp(96));
        addRow(displayPage, lightTheme, darkTheme);

        remoteTab = button("Remote");
        padTab = button("Touchpad");
        footer = segment(remoteTab, padTab);
        LinearLayout.LayoutParams footerSize = new LinearLayout.LayoutParams(-1, -2);
        footerSize.setMarginStart(dp(8));
        footerSize.setMarginEnd(dp(8));
        root.addView(footer, footerSize);
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
        connect.setEnabled(false);
        bindNavigation(setup, () -> showSetup(!showingSetup));
        bindNavigation(statusSummary, () -> { statusExpanded = !statusExpanded; updateStatusCard(); });
        bindNavigation(remoteTab, () -> showTouchpad(false));
        bindNavigation(padTab, () -> showTouchpad(true));
        bindNavigation(appearanceBack, () -> showAppearance(false));
        bindNavigation(lightTheme, () -> setDark(false));
        bindNavigation(darkTheme, () -> setDark(true));
        updatePages();
        applyTheme();
    }

    private Runnable pageListener = () -> {};
    public void setPageListener(Runnable listener) { pageListener = java.util.Objects.requireNonNull(listener); }
    public boolean inputSurfaceVisible() { return !showingSetup; }

    void setKeyboardSender(BiPredicate<TvControlProtocol.ImeField, String> sender) {
        keyboardSender = sender;
    }

    void setKeyboardState(boolean connected, boolean supported, TvControlProtocol.ImeField field) {
        keyboardConnected = connected;
        keyboardSupported = supported;
        if (!connected || !supported) field = null;
        if (field != keyboardField) {
            keyboardField = field;
            closeKeyboard();
        }
        updateKeyboardVisibility();
    }

    private void openKeyboard() {
        if (showingSetup || keyboardOpen || !keyboardConnected) return;
        if (keyboardField == null) {
            android.widget.Toast.makeText(context, keyboardSupported
                ? "Focus a text field on the projector first."
                : "The projector didn’t offer phone keyboard input.", android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        touchpad.cancelGesture();
        if (context instanceof android.app.Activity activity) {
            android.view.Window window = activity.getWindow();
            int secure = WindowManager.LayoutParams.FLAG_SECURE;
            securedKeyboardWindow = (window.getAttributes().flags & secure) == 0;
            if (securedKeyboardWindow) window.addFlags(secure);
        }
        keyboardOpen = true;
        updatePages();
        keyboardText.requestFocus();
        WindowInsetsController ime = keyboardText.getWindowInsetsController();
        if (ime != null) ime.show(WindowInsets.Type.ime());
    }

    private void sendKeyboard() {
        if (!keyboardOpen || keyboardField == null) return;
        String draft = keyboardText.getText().toString();
        if (!TvControlProtocol.ImeField.accepts(draft)) {
            showKeyboardError("Enter a single line of text.");
        } else if (keyboardSender.test(keyboardField, draft)) {
            closeKeyboard();
        } else {
            showKeyboardError("Couldn’t send. Check the text field on the projector and try again.");
        }
    }

    private void showKeyboardError(String message) {
        keyboardError.setText(message);
        keyboardError.setVisibility(View.VISIBLE);
    }

    private void closeKeyboard() {
        if (!keyboardOpen) return;
        keyboardOpen = false;
        keyboardText.getText().clear();
        keyboardError.setVisibility(View.GONE);
        WindowInsetsController ime = keyboardText.getWindowInsetsController();
        if (ime != null) ime.hide(WindowInsets.Type.ime());
        keyboardText.clearFocus();
        updatePages();
        if (securedKeyboardWindow && context instanceof android.app.Activity activity) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            securedKeyboardWindow = false;
        }
    }

    public void showSetup(boolean show) {
        closeKeyboard();
        showingSetup = show;
        showingAppearance = false;
        if (!show) setupView.pauseControls();
        pageListener.run();
        touchpad.cancelGesture();
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void showTouchpad(boolean show) {
        closeKeyboard();
        touchpad.cancelGesture();
        showingTouchpad = show;
        showingSetup = false;
        showingAppearance = false;
        setupView.pauseControls();
        pageListener.run();
        updatePages();
        scroll.scrollTo(0, 0);
    }

    public void showAppearance() { showAppearance(true); }

    public void showAppearance(boolean show) {
        closeKeyboard();
        touchpad.cancelGesture();
        showingAppearance = show;
        showingSetup = true;
        setupView.clearCode();
        pageListener.run();
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

    private void updateSensitivity(int step) {
        double gain = 0.5 + step * 0.25;
        touchpad.setSensitivity(gain);
        String value = String.format(java.util.Locale.ROOT, "%.2f×", gain);
        sensitivityValue.setText(value);
        sensitivity.setStateDescription(value);
    }

    public void setTvConnected(boolean connected) {
        // The Activity re-renders up to ten times a second; repaint only on a real change.
        if (statusPainted && connected == tvConnected) return;
        tvConnected = connected;
        paintStatus();
    }

    public void setConnected(boolean connected) {
        if (statusPainted && connected == this.connected) return;
        // Each new connection starts collapsed; any loss expands the card again.
        if (connected != this.connected) statusExpanded = false;
        this.connected = connected;
        paintStatus();
    }

    /** Applies connection state and the current palette to the header and status card. */
    private void paintStatus() {
        statusPainted = true;
        stop.setVisibility(connected ? View.VISIBLE : View.GONE);
        updateHeader();
        int text = color("#14171A", "#F2F4F6");
        int ready = color("#1F8A4C", "#4ADE80");
        connection.setText(connected ? "Mouse: connected" : "Mouse: disconnected");
        for (TextView chip : new TextView[]{connection, tvStatus}) {
            chip.setTextColor(text);
            chip.setBackground(shape(color("#E9ECF0", "#23282E"), Color.TRANSPARENT, 16));
        }
        statusDot(connection, dot(connected ? ready : color("#8A929C", "#7C858F")), null);
        statusDot(tvStatus, dot(tvConnected ? ready : color("#C27803", "#F4C675")), null);
        // Message tone: green with a check while connected, amber with an info mark otherwise.
        int toneFill = connected ? color("#E3F4EA", "#14281C") : color("#FFF4DC", "#2A2417");
        int toneText = connected ? color("#14532D", "#86EFAC") : color("#5C3D00", "#F4C675");
        status.setTextColor(toneText);
        status.setBackground(shape(toneFill, Color.TRANSPARENT, 16));
        Drawable mark = context.getDrawable(connected ? R.drawable.ui_check_circle : R.drawable.ui_info).mutate();
        mark.setBounds(0, 0, dp(20), dp(20));
        mark.setColorFilter(new android.graphics.PorterDuffColorFilter(toneText, android.graphics.PorterDuff.Mode.SRC_IN));
        status.setCompoundDrawablesRelative(mark, null, null, null);
        updateStatusCard();
    }

    /**
     * A fully connected run collapses the card to one summary row; the owner may expand it.
     * Any loss of connection expands it again, so failures and recovery text are never hidden.
     */
    private void updateStatusCard() {
        boolean collapsed = connected && !statusExpanded;
        statusSummary.setVisibility(connected ? View.VISIBLE : View.GONE);
        statusSummary.setText(tvConnected ? "Mouse and TV connected" : "Mouse connected");
        statusSummary.setContentDescription(statusSummary.getText() + (collapsed ? ". Show details" : ". Hide details"));
        statusSummary.setStateDescription(collapsed ? "Collapsed" : "Expanded");
        int details = collapsed ? View.GONE : View.VISIBLE;
        for (View detail : new View[]{statusChips, status}) detail.setVisibility(details);
        // Collapsed it is a single 56dp pill; expanded it is the regular card.
        if (collapsed) statusCard.setPadding(dp(18), 0, dp(14), 0);
        else statusCard.setPadding(dp(16), dp(14), dp(16), dp(2));
        statusCard.setBackground(shape(color("#FFFFFF", "#181B1F"), color("#E1E4E8", "#2C323A"), collapsed ? 28 : 24));
        ((LinearLayout.LayoutParams) statusSummary.getLayoutParams()).bottomMargin = collapsed ? 0 : dp(12);
        if (dpad != null) dpad.setRoomy(collapsed);
        connect.setVisibility(collapsed || showingSetup ? View.GONE : View.VISIBLE);
        Drawable chevron = context.getDrawable(collapsed ? R.drawable.ui_down : R.drawable.ui_up).mutate();
        chevron.setBounds(0, 0, dp(20), dp(20));
        // A colour filter survives TextView re-applying its (empty) compound tint list.
        chevron.setColorFilter(new android.graphics.PorterDuffColorFilter(color("#5B636D", "#9AA3AD"),
                android.graphics.PorterDuff.Mode.SRC_IN));
        GradientDrawable live = dot(color("#1F8A4C", "#4ADE80"));
        live.setBounds(0, 0, dp(10), dp(10));
        statusDot(statusSummary, live, chevron);
        statusSummary.setCompoundDrawablePadding(dp(12));
    }

    private GradientDrawable dot(int fill) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(fill);
        dot.setBounds(0, 0, dp(9), dp(9));
        return dot;
    }

    private void statusDot(TextView label, Drawable dot, Drawable end) {
        label.setCompoundDrawablesRelative(dot, null, end, null);
        label.setCompoundDrawablePadding(dp(7));
    }

    private void updateFooter() {
        footer.setVisibility(showingSetup || imeVisible || keyboardOpen ? View.GONE : View.VISIBLE);
    }

    /**
     * Setup and Display carry their own back/title row, so the app header hides there and a
     * connected Stop moves to the end of that row instead of taking a row of its own.
     */
    private void updateHeader() {
        if (header == null) return;
        brand.setText(showingSetup ? "" : "Open TV");
        setup.setVisibility(showingSetup ? View.GONE : View.VISIBLE);
        header.setVisibility(showingSetup ? View.GONE : View.VISIBLE);
        if (!showingSetup) {
            setupView.setAccessory(null);
            moveStop(header, header.indexOfChild(setup));
        } else if (showingAppearance) {
            setupView.setAccessory(null);
            moveStop(displayHeader, displayHeader.getChildCount());
        } else setupView.setAccessory(stop);
    }

    private void moveStop(LinearLayout row, int index) {
        if (stop.getParent() == row) return;
        if (stop.getParent() instanceof android.view.ViewGroup previous) previous.removeView(stop);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-2, -2);
        size.setMarginStart(dp(6));
        row.addView(stop, Math.max(0, index), size);
    }

    private void updatePages() {
        updateFooter();
        updateHeader();
        remotePage.setVisibility(!showingSetup && !keyboardOpen && !showingTouchpad ? View.VISIBLE : View.GONE);
        padPage.setVisibility(!showingSetup && !keyboardOpen && showingTouchpad ? View.VISIBLE : View.GONE);
        updateKeyboardVisibility();
        setupPage.setVisibility(showingSetup && !showingAppearance ? View.VISIBLE : View.GONE);
        scroll.setVisibility(!showingSetup || showingAppearance ? View.VISIBLE : View.GONE);
        statusCard.setVisibility(showingSetup ? View.GONE : View.VISIBLE);
        displayPage.setVisibility(showingSetup && showingAppearance ? View.VISIBLE : View.GONE);
        setup.setContentDescription(showingSetup ? (showingTouchpad ? "Show touchpad" : "Show remote") : "Show setup");
        select(setup, showingSetup);
        select(remoteTab, !showingSetup && !showingTouchpad);
        select(padTab, !showingSetup && showingTouchpad);
        updateStatusCard();
    }

    private void updateKeyboardVisibility() {
        // Always in the top row like the other TV keys; usable only while the TV is connected.
        keyboard.setEnabled(keyboardConnected);
        keyboardPanel.setVisibility(keyboardOpen ? View.VISIBLE : View.GONE);
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
        keyboardText.setTextColor(color("#14171A", "#F2F4F6"));
        keyboardText.setHintTextColor(color("#5B636D", "#9AA3AD"));
        keyboardText.setBackgroundTintList(ColorStateList.valueOf(color("#2F6FEB", "#6AA8FF")));
        sensitivity.setProgressTintList(ColorStateList.valueOf(color("#2F6FEB", "#6AA8FF")));
        sensitivity.setThumbTintList(ColorStateList.valueOf(color("#2F6FEB", "#6AA8FF")));
        sensitivity.setProgressBackgroundTintList(ColorStateList.valueOf(color("#C9CFD6", "#3A414A")));
        sensitivityValue.setTextColor(color("#1B3E8A", "#9CC3FF"));
        sensitivityValue.setBackground(shape(color("#E8F0FE", "#16233B"), Color.TRANSPARENT, 12));
        controlsDivider.setBackgroundColor(color("#E1E4E8", "#2C323A"));
        for (View card : cards) card.setBackground(shape(color("#FFFFFF", "#181B1F"), color("#E1E4E8", "#2C323A"), 24));
        dpad.setColors(color("#FFFFFF", "#181B1F"), color("#E1E4E8", "#2C323A"), color("#2F6FEB", "#6AA8FF"));
        for (View track : tracks) track.setBackground(shape(color("#E9ECF0", "#181B1F"), Color.TRANSPARENT, 28));
        for (Button button : controls) styleButton(button);
        setupView.applyPalette(dark);
        touchpad.setPalette(color("#E9ECF0", "#181B1F"), color("#DCE0E5", "#2C323A"),
                color("#DCE0E5", "#2C323A"), color("#5B636D", "#9AA3AD"));
        select(lightTheme, !dark);
        select(darkTheme, dark);
        paintStatus();
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
        boolean destructive = button == stop;
        boolean primary = button == connect || button == keyboardSend || button == tvButtons.get(TvCommand.OK)
                || button == setup && button.isSelected();
        boolean nav = navigation.contains(button) && button != setup;
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
        // Prototype Display choices are cards. The owner's no-border-highlight rule for tabs
        // still applies, so selection is a tinted fill with a matching edge.
        boolean theme = button == lightTheme || button == darkTheme;
        if (theme) {
            fill = button.isSelected() ? color("#E8F0FE", "#16233B") : surface;
            foreground = text;
            border = fill;
        }
        if (button == statusSummary) {
            fill = Color.TRANSPARENT;
            foreground = text;
            border = Color.TRANSPARENT;
        }
        boolean roundBack = button == appearanceBack;
        if (roundBack) {
            fill = surface;
            foreground = text;
            border = color("#E1E4E8", "#2C323A");
        }
        int radius = theme ? 16 : 100;
        boolean quietFocus = nav && !roundBack;
        StateListDrawable background = new StateListDrawable();
        background.addState(BUTTON_STATES[0], shape(direction ? fill : ok ? color("#DCE8FF", "#233B60") : color("#E9ECF0", "#181B1F"), border, radius));
        background.addState(BUTTON_STATES[1], shape(direction || ok ? fill : color("#DCE8FF", "#2C4160"), direction || quietFocus ? Color.TRANSPARENT : accent, radius));
        background.addState(BUTTON_STATES[2], shape(quietFocus ? color("#DCE8FF", "#2C4160") : fill, quietFocus ? Color.TRANSPARENT : accent, direction ? 8 : radius));
        background.addState(BUTTON_STATES[3], shape(fill, border, radius));
        button.setBackground(background);
        button.setTextColor(new ColorStateList(BUTTON_STATES, new int[]{color("#6B737C", "#6F7882"), direction ? accent : ok ? foreground : text, foreground, foreground}));
        // The status summary keeps its green dot; its chevron is tinted when painted.
        button.setCompoundDrawableTintList(button == statusSummary ? null : button.getTextColors());
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

    private void leadingIcon(Button button, int resource, int size) {
        Drawable icon = context.getDrawable(resource).mutate();
        icon.setBounds(0, 0, dp(size), dp(size));
        button.setCompoundDrawablesRelative(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(6));
    }

    private Button iconButton(String label, int resource) { return configureButton(new IconButton(resource), label); }

    /** Main remote rows and scroll controls use a larger 56dp target with a 26dp icon. */
    private Button large(Button button) { return large(button, 56); }

    private Button large(Button button, int height) {
        if (button instanceof IconButton icon) { icon.target = height; icon.iconSize = 26; }
        button.setMinHeight(dp(height));
        button.setMinimumHeight(dp(height));
        return button;
    }

    /** Status chip: dot, short state text, rounded track background. */
    private TextView chip(String label) {
        TextView chip = text(label, 13);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        return chip;
    }

    /** Small uppercase heading, matching the Setup section labels. */
    private TextView section(String label) {
        TextView section = text(label, 12);
        section.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        section.setLetterSpacing(0.08f);
        section.setAllCaps(true);
        return section;
    }

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
        PressMotion.apply(button);
        controls.add(button);
        return button;
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

    private LinearLayout.LayoutParams fill() {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, 0, 1);
        size.bottomMargin = dp(12);
        return size;
    }

    private LinearLayout row(View... children) { return row(8, children); }

    private LinearLayout row(int gap, View... children) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < children.length; i++) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1);
            if (i + 1 < children.length) size.setMarginEnd(dp(gap));
            row.addView(children[i], size);
        }
        return row;
    }

    private LinearLayout segment(Button... buttons) {
        LinearLayout segment = row(buttons);
        segment.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (Button button : buttons) {
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextSize(15);
            button.setPadding(dp(6), dp(10), dp(6), dp(10));
        }
        tracks.add(segment);
        return segment;
    }

    /**
     * Text button whose label is centred on its glyphs. OEM fonts pad the line box unevenly,
     * which made "OK" sit off-centre on the Nord; the glyph box is the same on every font.
     */
    private final class LabelButton extends Button {
        private final android.graphics.Rect glyphs = new android.graphics.Rect();
        LabelButton() { super(context); }
        @Override protected void onDraw(Canvas canvas) {
            android.text.TextPaint paint = getPaint();
            paint.setColor(getCurrentTextColor());
            paint.setTextAlign(android.graphics.Paint.Align.LEFT);
            String label = getText().toString();
            paint.getTextBounds(label, 0, label.length(), glyphs);
            canvas.drawText(label, getWidth() / 2f - glyphs.exactCenterX(), getHeight() / 2f - glyphs.exactCenterY(), paint);
        }
    }

    /** Keep the accessible/test label while centering a native vector without hidden text spacing. */
    private final class IconButton extends Button {
        private final Drawable icon;
        private final int resource;
        int target = 48, iconSize = 24;
        IconButton(int resource) {
            super(context);
            this.resource = resource;
            icon = context.getDrawable(resource).mutate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(resolveSize(dp(target), widthSpec), resolveSize(dp(target), heightSpec));
        }
        @Override protected void onDraw(Canvas canvas) {
            int size = dp(iconSize);
            int left = (getWidth() - size) / 2;
            int top = (getHeight() - size) / 2;
            icon.setBounds(left, top, left + size, top + size);
            icon.setLayoutDirection(getLayoutDirection());
            if (resource == R.drawable.ui_netflix) icon.setAlpha(isEnabled() ? 255 : 90);
            else icon.setTint(getCurrentTextColor());
            icon.draw(canvas);
        }
    }

    /** A bounded circle with disjoint cardinal targets and an 80dp center at its normal 240dp size. */
    /**
     * The circle draws itself so the view can take the full remaining height while the circle
     * stays round and centred. OK is 37% of the circle and arrows 29% (the mockup's 30% would
     * overlap OK by a few pixels; targets stay disjoint). Mockup sizing: 240dp, and up to 1.3x
     * while the status card is collapsed, bounded by the free height and width.
     */
    private final class Dpad extends FrameLayout {
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint edge = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Dpad() {
            super(context);
            setWillNotDraw(false);
            edge.setStyle(android.graphics.Paint.Style.STROKE);
            edge.setStrokeWidth(dp(1));
            for (TvCommand command : new TvCommand[]{TvCommand.UP, TvCommand.DOWN, TvCommand.LEFT, TvCommand.RIGHT, TvCommand.OK}) {
                addView(tvButtons.get(command), new FrameLayout.LayoutParams(dp(64), dp(64)));
            }
        }
        private boolean roomy;
        // Press glow per arrow (UP, DOWN, LEFT, RIGHT): the whole wedge from the rim to OK lights up.
        private final float[] glow = new float[4];
        private final android.animation.ValueAnimator[] glowMotion = new android.animation.ValueAnimator[4];
        private final android.graphics.Paint glowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path wedge = new android.graphics.Path();
        void setColors(int surface, int border, int accent) {
            fill.setColor(surface); edge.setColor(border); glowPaint.setColor(accent); invalidate();
        }
        @Override public void childDrawableStateChanged(View child) {
            super.childDrawableStateChanged(child);
            int index = indexOfChild(child);
            if (index < 0 || index > 3) return;
            float target = child.isPressed() && child.isEnabled() ? 1f : 0f;
            if (glowMotion[index] != null) glowMotion[index].cancel();
            if (glow[index] == target) return;
            android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(glow[index], target);
            motion.setDuration(target > 0 ? 90 : 240);
            motion.setInterpolator(new android.view.animation.DecelerateInterpolator());
            motion.addUpdateListener(update -> { glow[index] = (float) update.getAnimatedValue(); invalidate(); });
            glowMotion[index] = motion;
            motion.start();
        }
        void setRoomy(boolean roomy) { if (this.roomy != roomy) { this.roomy = roomy; requestLayout(); } }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = Math.max(0, MeasureSpec.getSize(widthSpec) - dp(16));
            // The established 240dp size is the floor; scrolling measurement (UNSPECIFIED) keeps it,
            // and a page stretched to the viewport lets the circle grow into the free height.
            int size = Math.min(dp(240), width);
            int mode = MeasureSpec.getMode(heightSpec), available = MeasureSpec.getSize(heightSpec);
            if (roomy && mode != MeasureSpec.UNSPECIFIED) size = Math.max(size, Math.min(Math.min(width, dp(360)), available));
            setMeasuredDimension(size, mode == MeasureSpec.EXACTLY ? Math.max(size, available) : size);
            for (int i = 0; i < getChildCount(); i++) {
                int target = i == 4 ? size * 11 / 30 : size * 29 / 100;
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY));
            }
        }
        private int top() { return (getMeasuredHeight() - getMeasuredWidth()) / 2; }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float radius = getMeasuredWidth() / 2f, inset = edge.getStrokeWidth() / 2f;
            canvas.drawCircle(radius, top() + radius, radius - inset, fill);
            float okRadius = getMeasuredWidth() * 11 / 60f + dp(4);
            for (int i = 0; i < 4; i++) {
                if (glow[i] <= 0) continue;
                // Quarter wedge centred on the arrow: up, down, left, right.
                float centre = i == 0 ? 270 : i == 1 ? 90 : i == 2 ? 180 : 0;
                float outer = radius - inset, cy = top() + radius;
                wedge.reset();
                wedge.arcTo(radius - outer, cy - outer, radius + outer, cy + outer, centre - 45, 90, true);
                wedge.arcTo(radius - okRadius, cy - okRadius, radius + okRadius, cy + okRadius, centre + 45, -90, false);
                wedge.close();
                glowPaint.setAlpha(Math.round(glow[i] * 46));
                canvas.drawPath(wedge, glowPaint);
            }
            canvas.drawCircle(radius, top() + radius, radius - inset, edge);
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int size = getMeasuredWidth();
            int inset = size / 40, offset = top();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                int target = child.getMeasuredWidth();
                int x = (size - target) / 2;
                int y = x;
                if (i == 0) y = inset;
                if (i == 1) y = size - target - inset;
                if (i == 2) x = inset;
                if (i == 3) x = size - target - inset;
                child.layout(x, offset + y, x + target, offset + y + target);
            }
        }
    }

    /** Lays chips out side by side, or stacked when they do not fit the width. */
    private final class ChipRow extends LinearLayout {
        private Boolean laidOut;
        ChipRow() { super(context); }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int total = 0, visible = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() == GONE) continue;
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                total += child.getMeasuredWidth();
                visible++;
            }
            total += Math.max(0, visible - 1) * dp(8);
            boolean stack = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED && total > MeasureSpec.getSize(widthSpec);
            // Only re-flow when switching between side by side and stacked.
            if (laidOut == null || laidOut != stack) {
                laidOut = stack;
                setOrientation(stack ? VERTICAL : HORIZONTAL);
                for (int i = 0; i < getChildCount(); i++) {
                LayoutParams size = new LayoutParams(-2, -2);
                if (i + 1 < getChildCount()) { if (stack) size.bottomMargin = dp(8); else size.setMarginEnd(dp(8)); }
                getChildAt(i).setLayoutParams(size);
                }
            }
            super.onMeasure(widthSpec, heightSpec);
        }
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
