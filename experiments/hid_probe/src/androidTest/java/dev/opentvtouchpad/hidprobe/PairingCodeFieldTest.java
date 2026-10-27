package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Parcelable;
import android.text.InputType;
import android.util.SparseArray;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic values and native editor calls only; no transport or device gestures. */
@RunWith(AndroidJUnit4.class)
public class PairingCodeFieldTest {
    private Context context(float scale) {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = scale;
        return new ContextThemeWrapper(base.createConfigurationContext(config), R.style.RemoteTheme);
    }

    private void layout(PairingCodeField field, int widthDp) {
        int width = Math.round(widthDp * field.getResources().getDisplayMetrics().density);
        field.setLayoutParams(new android.view.ViewGroup.LayoutParams(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        field.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        field.layout(0, 0, width, field.getMeasuredHeight());
    }

    @Test public void constructorOwnsPrivacyAndNeverSavesTheBuffer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PairingCodeField field = new PairingCodeField(context(1f));
            assertTrue(field instanceof EditText);
            assertFalse(field.isEnabled());
            assertFalse(field.isSaveEnabled());
            assertFalse(field.getFreezesText());
            assertFalse(field.isSaveFromParentEnabled());
            assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS,
                    field.getImportantForAutofill());
            assertEquals(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS,
                    field.getImportantForContentCapture());
            assertNull(field.getAutofillHints());
            assertNotNull(field.getHint());
            assertTrue(field.getContentDescription().length() > 0);
            assertNull("OTP characters remain visible", field.getTransformationMethod());
            assertEquals(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    field.getInputType() & InputType.TYPE_MASK_VARIATION);
            assertTrue((field.getInputType() & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0);
            assertTrue((field.getImeOptions() & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0);
            assertTrue((field.getImeOptions() & EditorInfo.IME_FLAG_NO_EXTRACT_UI) != 0);
            field.setId(View.generateViewId());
            field.setEnabled(true);
            field.setText("aB1234");
            field.setSelection(2, 4);
            SparseArray<Parcelable> saved = new SparseArray<>();
            field.saveHierarchyState(saved);
            assertNull(saved.get(field.getId()));
            field.setSaveEnabled(true);
            field.saveHierarchyState(saved);
            assertSame(View.BaseSavedState.EMPTY_STATE, saved.get(field.getId()));
            assertEquals("aB1234", field.getText().toString());
            assertEquals(2, field.getSelectionStart());
            assertEquals(4, field.getSelectionEnd());
            field.setSaveEnabled(false);
            PairingCodeField reopened = new PairingCodeField(field.getContext());
            reopened.onRestoreInstanceState(field.onSaveInstanceState());
            assertEquals(0, reopened.length());
            assertFalse(reopened.isEnabled());
        });
    }

    @Test public void nativePasteSelectionReplacementAndBackspaceKeepOneHexBuffer() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PairingCodeField field = new PairingCodeField(context(1f));
            field.setEnabled(true);
            layout(field, 272);
            field.requestFocus();
            InputConnection editor = field.onCreateInputConnection(new EditorInfo());
            assertNotNull(editor);
            assertTrue(editor.commitText("gG0a!B1z23FFFF", 1));
            assertEquals("0aB123", field.getText().toString());
            assertEquals(6, field.getSelectionStart());
            field.setSelection(2, 4);
            assertTrue(editor.commitText("fE", 1));
            assertEquals("0afE23", field.getText().toString());
            assertEquals(4, field.getSelectionStart());
            assertTrue(editor.deleteSurroundingText(1, 0));
            assertEquals("0af23", field.getText().toString());
            assertEquals(3, field.getSelectionStart());
            field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            field.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            assertEquals("0a23", field.getText().toString());
            field.setSelection(0, field.length());
            assertTrue(editor.commitText("ABCDEF99", 1));
            assertEquals("ABCDEF", field.getText().toString());
            field.setSelection(3);
            assertTrue(editor.commitText("9", 1));
            assertEquals("ABCDEF", field.getText().toString());
            field.getText().clear();
            field.getText().append("\uFF11\uD83D\uDE00gGaB09");
            assertEquals("aB09", field.getText().toString());
            assertTrue(editor.commitText("\n", 1));
            assertEquals("aB09", field.getText().toString());
        });
    }

    @Test public void everyTypedCharacterIsVisibleInTheNativeLayout() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            PairingCodeField field = new PairingCodeField(context(1f));
            field.setEnabled(true);
            layout(field, 272);
            field.requestFocus();
            InputConnection editor = field.onCreateInputConnection(new EditorInfo());
            for (char value : "aB1234".toCharArray()) {
                assertTrue(editor.commitText(String.valueOf(value), 1));
                layout(field, 272);
                assertNull(field.getTransformationMethod());
                assertEquals(field.getText().toString(), field.getLayout().getText().toString());
            }
            assertTrue(field.isCursorVisible());
            assertTrue(field.isLongClickable());
        });
    }

    @Test public void sixNativeCellsAndVisibleGlyphsFitSmallWidthsLargeFontsAndBothPalettes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (float scale : new float[]{1f, 1.5f}) for (boolean dark : new boolean[]{false, true}) {
                PairingCodeField field = new PairingCodeField(context(scale));
                int surface = dark ? 0xff202937 : 0xffeaf0f7;
                int border = dark ? 0xffa0aabb : 0xff71829b;
                int focus = 0xff305ad0;
                field.setPalette(surface, border, focus);
                for (int width : new int[]{272, 320, 752}) {
                    field.setText("");
                    layout(field, width);
                    float density = field.getResources().getDisplayMetrics().density;
                    assertTrue("OTP cells are taller",field.getHeight() >= Math.round(72 * density));
                    Bitmap image = Bitmap.createBitmap(field.getWidth(), field.getHeight(), Bitmap.Config.ARGB_8888);
                    field.draw(new Canvas(image));
                    int runs = 0;
                    boolean inCell = false;
                    for (int x = 0; x < image.getWidth(); x++) {
                        boolean filled = image.getPixel(x, image.getHeight() / 2) == surface;
                        if (filled && !inCell) runs++;
                        inCell = filled;
                    }
                    assertEquals("six separate rectangular cell interiors", 6, runs);
                    image.recycle();
                    field.setEnabled(true);
                    field.requestFocus();
                    field.setText("aB1234");
                    field.setSelection(6);
                    layout(field, width);
                    assertEquals(View.LAYOUT_DIRECTION_LTR, field.getLayoutDirection());
                    assertEquals(1, field.getLayout().getLineCount());
                    assertEquals("aB1234", field.getLayout().getText().toString());
                    float cell = field.getWidth() / 6f;
                    for (int i = 0; i < 6; i++) {
                        float start = field.getCompoundPaddingLeft()
                                + field.getLayout().getPrimaryHorizontal(i) - field.getScrollX();
                        float end = field.getCompoundPaddingLeft()
                                + field.getLayout().getPrimaryHorizontal(i + 1) - field.getScrollX();
                        assertTrue("native glyph/caret starts in its cell", start >= i * cell - density);
                        assertTrue("native glyph/caret remains visible", end <= field.getWidth() + density);
                        assertTrue("native advances remain even", end > start);
                        if (i < 5) assertEquals(cell, end - start, density);
                    }
                    image = Bitmap.createBitmap(field.getWidth(), field.getHeight(), Bitmap.Config.ARGB_8888);
                    field.draw(new Canvas(image));
                    if (width == 272) {
                        File output = new File(field.getContext().getCacheDir(),
                                "pairing-field-320-" + scale + (dark ? "-dark.png" : "-light.png"));
                        try (FileOutputStream stream = new FileOutputStream(output)) {
                            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, stream));
                        } catch (java.io.IOException e) { throw new AssertionError(e); }
                    }
                    image.recycle();
                    field.setEnabled(false);
                }
            }
        });
    }
}
