package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/** One visible native OTP buffer; the six cells are background decoration only. */
public final class PairingCodeField extends EditText {
    private final Paint metrics = new Paint();
    private int surface = 0xfff1f5f9;
    private int border = 0xff64748b;
    private int focus = 0xff2563eb;

    public PairingCodeField(Context context) {
        super(context);
        setSingleLine(true);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(20);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        setTextDirection(TEXT_DIRECTION_LTR);
        setHint("");
        setContentDescription("Six-character hexadecimal TV pairing code");
        DigitsKeyListener hex = DigitsKeyListener.getInstance("0123456789abcdefABCDEF");
        setKeyListener(hex);
        setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setFilters(new InputFilter[]{hex, new InputFilter.LengthFilter(6)});
        // Keep sensitive IME semantics without concealing the owner-requested visible OTP.
        setTransformationMethod(null);
        setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        setSaveEnabled(false);
        setSaveFromParentEnabled(false);
        setAutofillHints((String[]) null);
        setImportantForAutofill(IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        setImportantForContentCapture(IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        setMinHeight(dp(72));
        setMinWidth(dp(48));
        setBackgroundTintList(null);
        setBackground(new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override public void draw(Canvas canvas) {
                float cell = getBounds().width() / 6f;
                float stroke = dp(isEnabled() && isFocused() ? 2 : 1);
                for (int i = 0; i < 6; i++) {
                    float left = getBounds().left + i * cell + dp(2);
                    float right = getBounds().left + (i + 1) * cell - dp(2);
                    float top = getBounds().top + stroke / 2;
                    float bottom = getBounds().bottom - stroke / 2;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(surface);
                    canvas.drawRect(left, top, right, bottom, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(stroke);
                    paint.setColor(isEnabled() && isFocused() ? focus : border);
                    canvas.drawRect(left, top, right, bottom, paint);
                }
            }

            @Override public boolean isStateful() { return true; }
            @Override protected boolean onStateChange(int[] state) { return true; }
            @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
            @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        });
        setEnabled(false);
    }

    public void setPalette(int surface, int border, int focus) {
        this.surface = surface;
        this.border = border;
        this.focus = focus;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        metrics.set(getPaint());
        metrics.setLetterSpacing(0);
        float glyph = metrics.measureText("0");
        float space = Math.max(0, getMeasuredWidth() / 6f - glyph);
        // Native letter spacing already includes the half-gap on each glyph edge.
        setPadding(0, dp(8), 0, dp(8));
        setLetterSpacing(space / getTextSize());
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override public boolean getFreezesText() { return false; }

    @Override public Parcelable onSaveInstanceState() {
        // Honor View's save-state contract, but retain neither text nor editor state.
        super.onSaveInstanceState();
        return BaseSavedState.EMPTY_STATE;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
