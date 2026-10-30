package dev.opentvtouchpad.hidprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.view.Gravity;
import android.view.View;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Native, snapshot-driven presentation; never owns a run, trust, or a countdown clock. */
public final class SetupScreen {
    public final FrameLayout root;
    public final Button primary, secondary, back, repairBluetooth, repairTv, forgetBluetooth,
            forgetTv, changeAddress, appearance, batterySettings, manage;
    public final EditText address;
    public final PairingCodeField code;
    public final TextView message, diagnostic, countdown;
    public final TextView[] chips = new TextView[3];

    private final Context context;
    private final LinearLayout page, body, actions;
    private final ScrollView scroll;
    private final List<TextView> labels = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private final List<Button> buttons = new ArrayList<>();
    private final List<View> segments = new ArrayList<>();
    private SetupState.Snapshot state;
    private BiConsumer<SetupState.Action,String> listener = (a,v) -> {};
    private SetupState.Action primaryAction, secondaryAction;
    private boolean dark, paused = true;
    private FrameLayout confirmation;
    private View confirmationReturnFocus;
    private Ring ring;
    private TextView validation, timeline;

    public SetupScreen(Context context) {
        this.context = context;
        root = new FrameLayout(context);
        scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        page = column();
        page.setPadding(dp(8),dp(8),dp(8),dp(12));
        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        body = column(); actions = column();
        back = button("Back"); primary = button(""); secondary = button("");
        primary.setMinHeight(dp(56));
        repairBluetooth = button("Re-pair Bluetooth projector"); repairTv = button("Re-pair TV controls");
        forgetBluetooth = button("Forget Bluetooth projector"); forgetTv = button("Forget TV controls");
        changeAddress = button("Change address"); appearance = button("Appearance");
        batterySettings = button("Open app battery settings"); manage = button("Manage setup");
        address = new EditText(context);
        address.setHint("192.168.1.50");
        address.setContentDescription("Projector Wi-Fi IPv4 address");
        address.setTextSize(18); address.setSingleLine(true);
        address.setMinHeight(dp(56)); address.setPadding(dp(16),dp(12),dp(16),dp(12));
        address.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        address.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        address.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        address.setSaveEnabled(false); address.setSaveFromParentEnabled(false);
        address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        address.setImportantForContentCapture(View.IMPORTANT_FOR_CONTENT_CAPTURE_NO_EXCLUDE_DESCENDANTS);
        address.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        code = new PairingCodeField(context);
        message = label("",15); diagnostic = label("",12); countdown = label("",14);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        for (int i=0;i<3;i++) chips[i]=label("",12);
        back.setOnClickListener(v -> emit(SetupState.Action.BACK,""));
        primary.setOnClickListener(v -> {
            if (!primary.isEnabled() || primaryAction==null) return;
            String value=primaryAction==SetupState.Action.SAVE_ADDRESS ? address.getText().toString()
                    : primaryAction==SetupState.Action.CONFIRM_TV ? code.getText().toString() : "";
            emit(primaryAction,value);
        });
        secondary.setOnClickListener(v -> { if (secondary.isEnabled() && secondaryAction!=null) emit(secondaryAction,""); });
        bind(changeAddress,SetupState.Action.CHANGE_ADDRESS);
        bind(appearance,SetupState.Action.APPEARANCE); bind(batterySettings,SetupState.Action.BATTERY_SETTINGS);
        bind(manage,SetupState.Action.MANAGE);
        confirm(repairBluetooth,SetupState.Action.REPAIR_BT); confirm(repairTv,SetupState.Action.REPAIR_TV);
        confirm(forgetBluetooth,SetupState.Action.FORGET_BT); confirm(forgetTv,SetupState.Action.FORGET_TV);
        TextWatcher watcher=new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { updateControls(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        address.addTextChangedListener(watcher); code.addTextChangedListener(watcher);
        for (EditText field : new EditText[]{address,code}) field.setOnEditorActionListener((v,id,event) -> {
            if (id!=EditorInfo.IME_ACTION_DONE) return false;
            if (primary.isEnabled()) primary.performClick();
            return true;
        });
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) { pauseControls(); }
        });
        render(new SetupState.Snapshot(SetupState.Page.OVERVIEW,false,false,false,"","","",0,false,false,0,false));
    }

    public void setActionListener(BiConsumer<SetupState.Action,String> listener) { this.listener=Objects.requireNonNull(listener); }

    public void render(SetupState.Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        SetupState.Snapshot previous=state;
        boolean changed=previous==null || previous.page()!=snapshot.page();
        boolean generationChanged=previous!=null && previous.generation()!=snapshot.generation();
        if (changed || generationChanged || !snapshot.enabled()) cancelPendingInput();
        boolean configurationChanged=previous!=null && (previous.bluetoothSaved()!=snapshot.bluetoothSaved()
                || previous.addressSaved()!=snapshot.addressSaved() || previous.tvPaired()!=snapshot.tvPaired()
                || snapshot.page()!=SetupState.Page.ADDRESS && !previous.address().equals(snapshot.address()));
        if (changed || generationChanged || !snapshot.enabled() || snapshot.page()!=SetupState.Page.TV_CODE
                || snapshot.remainingMillis()<=0) clearCode();
        if (changed || generationChanged || configurationChanged || !snapshot.enabled()) dismissConfirmation();
        state=snapshot; paused=false;
        if (changed || configurationChanged
                || snapshot.page()==SetupState.Page.TV_ERROR && !previous.diagnostic().equals(snapshot.diagnostic())) {
            buildPage(); scroll.scrollTo(0,0);
        }
        if (changed && snapshot.page()==SetupState.Page.ADDRESS) address.setText(snapshot.address());
        message.setText(snapshot.message().isEmpty() ? defaultMessage() : snapshot.message());
        message.setVisibility(message.length()==0 ? View.GONE : View.VISIBLE);
        diagnostic.setText(snapshot.diagnostic().isEmpty() ? "" : "Report code: "+snapshot.diagnostic());
        diagnostic.setVisibility(diagnostic.length()==0 ? View.GONE : View.VISIBLE);
        long seconds=Math.max(0,snapshot.remainingMillis()/1000+(snapshot.remainingMillis()%1000>0 ? 1 : 0));
        String time=String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
        countdown.setText(snapshot.page()==SetupState.Page.BT_WAIT ? time+" visible" : "Code expires in "+time);
        countdown.setVisibility(snapshot.page()==SetupState.Page.BT_WAIT && snapshot.remainingMillis()<=0 ? View.GONE : View.VISIBLE);
        if (timeline!=null) timeline.setText(snapshot.remainingMillis()>0
                ? "Phone ready to pair\nVisible to the projector\nWaiting for OS bond observation, then explicit number comparison"
                : "Waiting for Android visibility approval. No visibility countdown is active.");
        if(ring!=null) ring.setVisibility(snapshot.remainingMillis()>0 ? View.VISIBLE : View.GONE);
        if (ring!=null) {
            if(generationChanged) ring.windowMillis=0;
            if(ring.windowMillis==0 && snapshot.remainingMillis()>0) ring.windowMillis=Math.min(120000,snapshot.remainingMillis());
            ring.remaining=snapshot.remainingMillis();ring.invalidate();
        }
        updateControls(); applyPalette(dark);
    }

    public void pauseControls() {
        paused=true; cancelPendingInput(); clearCode(); dismissConfirmation(); updateControls();
        message.setText("Setup paused. Native status is unavailable until the app is active again.");
        message.setVisibility(View.VISIBLE);
    }

    public void clearCode() { code.setText(""); }

    private void cancelPendingInput() {
        root.cancelPendingInputEvents();
        long now=android.os.SystemClock.uptimeMillis();
        MotionEvent cancel=MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0,0,0);
        for (Button button:buttons) {
            button.dispatchTouchEvent(cancel);
            button.setPressed(false);
        }
        cancel.recycle();
    }

    private void buildPage() {
        // Keep the native editor attached between deadline ticks so IME, selection and TalkBack stay stable.
        page.removeAllViews(); body.removeAllViews(); actions.removeAllViews();
        labels.clear(); cards.clear(); segments.clear(); ring=null; validation=null; timeline=null;
        labels.add(message); labels.add(diagnostic); labels.add(countdown);
        int step=switch(state.page()) {
            case BT_START,BT_WAIT,BT_COMPARE,BT_CLOSING,BT_DONE -> 1;
            case ADDRESS -> 2;
            case TV_START,TV_CONNECTING,TV_CODE,TV_CLOSING,TV_ERROR -> 3;
            default -> 0;
        };
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        add(header,back,new LinearLayout.LayoutParams(-2,-2));
        if(step>0) {
            TextView position=label("Step "+step+" of 3",13);position.setGravity(Gravity.END);
            header.addView(position,new LinearLayout.LayoutParams(0,-2,1));
        }
        add(page,header);
        if (step>0) {
            LinearLayout row=new LinearLayout(context);
            String[] names={"Bluetooth","TV address","Pair TV"};
            for(int i=0;i<3;i++) {
                LinearLayout segment=column();
                View bar=new View(context);bar.setSelected(i<step);segments.add(bar);
                segment.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
                TextView name=label(names[i],12);name.setPadding(0,dp(6),0,0);
                if(i+1==step) name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                segment.addView(name);
                LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);size.setMarginEnd(dp(6));
                row.addView(segment,size);
            }
            add(page,row);
        }
        add(page,body);
        primaryAction=null; secondaryAction=SetupState.Action.BACK;
        primary.setVisibility(View.VISIBLE); secondary.setVisibility(View.VISIBLE);secondary.setText("Back");
        switch(state.page()) {
            case OVERVIEW -> {
                title("Set up Open TV"); paragraph("Pair this phone with your projector once. After that, just tap Connect.");
                summary(true);
                LinearLayout ready=card();add(ready,label("HAVE READY",13));
                add(ready,label("• Projector on, on the same Wi-Fi as this phone",14));
                add(ready,label("• Bluetooth turned on",14));add(ready,label("• The projector's physical remote",14));
                add(body,ready);
                add(body,appearance); if(state.anyComplete()) add(body,manage);
                action(state.complete() ? "Review setup" : state.anyComplete() ? "Continue setup" : "Start setup",
                        state.complete() ? SetupState.Action.MANAGE : SetupState.Action.START);
                secondary.setVisibility(View.GONE);
            }
            case BT_START -> {
                title("Pair with the projector's Bluetooth");paragraph("This lets your phone move the cursor and click. Here's what happens:");
                instruction(1,"Tap Start pairing. Allow Android visibility for up to 120 seconds.");
                instruction(2,"On the projector, use its physical remote to add a Bluetooth device and choose this phone.");
                instruction(3,"Compare the numbers on both screens and confirm only if they match.");
                note("Android may ask to allow Nearby devices. Allow it so this phone can pair.");
                if (state.bluetoothSaved()) note("Your Bluetooth setup is saved. Retry its link test without pairing again.");
                action(state.bluetoothSaved() ? "Retry saved Bluetooth link" : "Start pairing",SetupState.Action.BT_PAIR);
            }
            case BT_WAIT -> {
                title("Waiting for the projector");paragraph("Pick this phone from the projector's Bluetooth list.");
                ring=new Ring();LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(140),dp(140));
                size.gravity=Gravity.CENTER_HORIZONTAL;body.addView(ring,size);add(body,countdown);
                LinearLayout timelineCard=card();timeline=label("",14);add(timelineCard,timeline);add(body,timelineCard);
                action("Waiting for the projector…",null);cancel();
            }
            case BT_COMPARE -> {
                title("Do the numbers match?");paragraph("Compare the number in Android's pairing prompt on this phone with the intended projector.");
                LinearLayout comparison=card();add(comparison,label("This phone — shows a number in Android's pairing prompt",15));
                add(comparison,label("The projector — shows a number on its screen",15));add(body,comparison);
                note("Accept only if both numbers are identical. If you already accepted mismatching digits, remove this bond manually on both devices.");
                action("Codes match on both screens. Save and connect",SetupState.Action.BT_CONFIRM);
                secondary.setText("They don’t match");secondaryAction=SetupState.Action.BT_MISMATCH;
            }
            case BT_CLOSING,TV_CLOSING,CLOSING -> {
                title("Closing the link safely");paragraph("Please wait for native cleanup. Continue will stay unavailable until the link is closed safely.");
                action("Continue",null);cancel();
            }
            case BT_DONE -> {
                title("Bluetooth is set up");paragraph("Your phone and projector are paired.");
                note("Projector saved on this phone\nLink closed safely");action("Continue",SetupState.Action.NEXT);
                secondary.setVisibility(View.GONE);
            }
            case ADDRESS -> {
                title("Where is your projector?");paragraph("The TV buttons need the projector's Wi-Fi address.");
                add(body,label("Projector Wi-Fi address",14));add(body,address);
                validation=label("",13);validation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);add(body,validation);
                note("How to find it\nOn the projector, open network settings and look for its Wi-Fi IPv4 address. Use the projector's own address, not this phone's or the router's. No port, public IP or IPv6.");
                paragraph("Saving doesn't test the connection. The next step does.");action("Save and continue",SetupState.Action.SAVE_ADDRESS);
            }
            case TV_START -> {
                title("Pair the TV buttons");paragraph("The projector will show a six-character hexadecimal code. You'll type it on the next screen.");
                instruction(1,"Keep the projector on and awake.");instruction(2,"Tap Pair TV. A code appears on the projector.");
                instruction(3,"Type the code here within the current window, at most 120 seconds.");
                note("Leaving the app cancels pairing and clears the code you typed.");action("Pair TV",SetupState.Action.PAIR_TV);
            }
            case TV_CONNECTING -> {
                title("Requesting the projector’s code");paragraph("Opening the pairing exchange. No TV pairing has been authenticated yet.");
                action("Requesting code…",null);cancel();
            }
            case TV_CODE -> {
                title("Enter the projector’s code");paragraph("Six characters, letters A–F and numbers.");
                add(body,code);add(body,countdown);
                note("Type the code shown on the intended projector. Leaving the app clears this field and cancels pairing.");
                action("Confirm code",SetupState.Action.CONFIRM_TV);cancel();
            }
            case TV_ERROR -> {
                title("TV pairing needs attention");
                if (state.diagnostic().equals("PAIRING_CONNECT") || state.diagnostic().equals("CONTROL_CONNECT")) {
                    instruction(1,"Check the projector is on and awake.");
                    instruction(2,"Check phone and projector are on the same Wi-Fi. Guest networks can block devices.");
                    instruction(3,"Check the address is the projector's own, not this phone's or the router's.");
                } else {
                    note(switch(state.diagnostic()) {
                        case "IDENTITY","PAIRING_TLS","CONTROL_TLS","SESSION_VALIDATION" ->
                            "Verify the intended projector and its saved identity. Never bypass TLS or identity checks. Re-pair is a separate, confirmed trust replacement in Manage.";
                        case "CODE" -> "The code was rejected, expired, or no longer eligible. Try again explicitly for a new code on the intended projector.";
                        case "PAIRING_EXCHANGE","CONTROL_EXCHANGE" -> "The pairing exchange did not complete. Check the current failure message, then retry explicitly after safe cleanup.";
                        default -> "Check the current failure message and diagnostic stage. Retry only after safe cleanup.";
                    });
                }
                if(state.bluetoothSaved()) note("Your Bluetooth pairing is safe. You don't need to redo it.");
                action("Try again",SetupState.Action.RETRY);secondary.setText("Change address");secondaryAction=SetupState.Action.CHANGE_ADDRESS;
            }
            case DONE -> {
                title("You're all set");paragraph("Open the remote and tap Connect whenever you want to use it. Saved setup does not mean a live connection.");
                summary(false);
                note("OPTIONAL\nKeep Open TV running in the background\nYou can allow background activity for Open TV only. This may use more battery. Nothing changes automatically; leave auto-launch Off.");
                add(body,batterySettings);action("Open remote",SetupState.Action.OPEN_REMOTE);secondary.setVisibility(View.GONE);
            }
            case MANAGE -> {
                title("Your setup");summary(false);
                add(body,appearance);
                add(body,label("RESET",13));add(body,forgetBluetooth);add(body,forgetTv);
                paragraph("Only use Forget to start over. For routine connection trouble, try Connect again first; pairings are kept.");
                action("Done",SetupState.Action.OPEN_REMOTE);secondary.setVisibility(View.GONE);
            }
            case RECOVERY -> {
                title("Recovery required");paragraph("Native cleanup is unconfirmed. New pairing and Continue remain blocked. Follow the current recovery instructions below.");
                action("Continue",null);cancel();
            }
        }
        add(body,message);add(body,diagnostic);
        page.addView(new View(context),new LinearLayout.LayoutParams(1,0,1));
        add(actions,primary);add(actions,secondary);add(page,actions);
        for(Button button:buttons) button.setContentDescription(button.getText());
    }

    private String defaultMessage() {
        return switch(state.page()) {
            case BT_CLOSING,TV_CLOSING,CLOSING -> "Closing the link safely. Waiting for native completion.";
            case RECOVERY -> "Native teardown is unconfirmed. Input disabled.";
            case TV_ERROR -> "Pairing did not complete. Check the diagnostic stage before retrying.";
            case TV_CODE -> state.remainingMillis()<=0 ? "The code window ended. Cancel and retry explicitly after safe cleanup." : "";
            default -> "";
        };
    }

    private void summary(boolean checklist) {
        String[] titles={"Bluetooth projector","TV address","TV buttons"};
        String[] descriptions={"Lets this phone move the cursor","Where the projector is on Wi-Fi","Home, Back, volume and more"};
        for(int i=0;i<3;i++) {
            SetupState.Status status=state.status(i+1);
            LinearLayout card=card(); card.setSelected(checklist && status==SetupState.Status.NEXT);
            LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);
            TextView badge=label(status==SetupState.Status.DONE ? "✓" : Integer.toString(i+1),14);
            badge.setGravity(Gravity.CENTER);badge.setMinHeight(dp(32));
            LinearLayout.LayoutParams badgeSize=new LinearLayout.LayoutParams(dp(32),-2);badgeSize.setMarginEnd(dp(12));row.addView(badge,badgeSize);
            LinearLayout description=column();
            TextView title=label(titles[i],15);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);description.addView(title);
            String detail=checklist ? descriptions[i] : i==1 && state.addressSaved() ? state.address()
                    : status==SetupState.Status.DONE ? "Saved; live connection not checked" : "Not configured";
            description.addView(label(detail,13));
            row.addView(description,new LinearLayout.LayoutParams(0,-2,1));
            TextView chip=chips[i];chip.setText(switch(status) {case DONE -> "Done";case NEXT -> "Next";case TO_DO -> "To do";});
            chip.setPadding(dp(8),dp(4),dp(8),dp(4));chip.setTag(status);labels.add(chip);
            LinearLayout.LayoutParams chipSize=new LinearLayout.LayoutParams(-2,-2);chipSize.setMarginStart(dp(8));
            add(row,chip,chipSize);add(card,row);
            if(state.page()==SetupState.Page.MANAGE) add(card,switch(i) {
                case 0 -> repairBluetooth; case 1 -> changeAddress; default -> repairTv;
            });
            add(body,card);
        }
    }

    private void updateControls() {
        if(state==null) return;
        boolean enabled=!paused && state.enabled();
        for(Button button:buttons) button.setEnabled(enabled);
        back.setEnabled(!paused); secondary.setEnabled(!paused && (state.enabled()
                || secondaryAction==SetupState.Action.CANCEL || secondaryAction==SetupState.Action.BACK));
        address.setEnabled(enabled && state.page()==SetupState.Page.ADDRESS);
        code.setEnabled(enabled && state.page()==SetupState.Page.TV_CODE && state.remainingMillis()>0);
        boolean valid=TvEnrollment.validAddress(address.getText().toString());
        primary.setEnabled(enabled && primaryAction!=null
                && (primaryAction!=SetupState.Action.SAVE_ADDRESS || valid)
                && (primaryAction!=SetupState.Action.CONFIRM_TV || code.isEnabled() && code.length()==6));
        if(validation!=null) validation.setText(valid ? "Looks like a private Wi-Fi address. No port needed."
                : "Enter the projector’s private IPv4 address without leading zeros or a port.");

    }

    private void emit(SetupState.Action action,String value) {
        if(paused || state==null || (!state.enabled() && action!=SetupState.Action.BACK && action!=SetupState.Action.CANCEL)) return;
        if(action==SetupState.Action.BACK || action==SetupState.Action.CANCEL || action==SetupState.Action.BT_MISMATCH) clearCode();
        listener.accept(action,value);
    }
    private void bind(Button button,SetupState.Action action) {
        button.setOnClickListener(v -> { if(button.isEnabled()) emit(action,""); });
    }
    private void confirm(Button button,SetupState.Action action) {
        button.setOnClickListener(v -> {
            if(!button.isEnabled() || state.page()!=SetupState.Page.MANAGE || paused) return;
            dismissConfirmation();
            long generation=state.generation();
            boolean bluetooth=action==SetupState.Action.REPAIR_BT || action==SetupState.Action.FORGET_BT;
            boolean repair=action==SetupState.Action.REPAIR_BT || action==SetupState.Action.REPAIR_TV;
            FrameLayout sheet=new FrameLayout(context);confirmation=sheet;
            confirmationReturnFocus=root.findFocus();
            sheet.setBackgroundColor(0x80000000);sheet.setOnClickListener(clicked -> dismissConfirmation());
            sheet.setAccessibilityPaneTitle(button.getText());
            ScrollView viewport=new ScrollView(context);viewport.setVerticalScrollBarEnabled(false);
            LinearLayout content=column();content.setClickable(true);
            content.setPadding(dp(20),dp(24),dp(20),dp(24));viewport.addView(content);
            TextView title=label(button.getText()+"?",22);title.setAccessibilityHeading(true);add(content,title);
            String explanation=bluetooth ? "This removes only this app's Bluetooth projector record. TV pairing stays saved. It cannot remove Android's OS bond. For fresh pairing, manually unpair only the intended phone and projector on both devices and compare the new numbers."
                    : "This removes only this app's TV pairing and client identity. Bluetooth pairing and the projector's other remotes stay separate. The last validated address is kept where possible. Pair TV again with a new on-projector code.";
            if(repair) explanation="Re-pair replaces this item's saved trust. "+explanation;
            TextView scope=label(explanation,15);add(content,scope);
            Button accept=button(button.getText().toString());Button keep=button("Keep pairing");add(content,accept);add(content,keep);
            accept.setOnClickListener(clicked -> {
                boolean current=confirmation==sheet && !paused && state.enabled()
                        && state.page()==SetupState.Page.MANAGE && state.generation()==generation;
                dismissConfirmation();if(current) emit(action,"");
            });
            keep.setOnClickListener(clicked -> dismissConfirmation());
            View.OnKeyListener dismiss=(view,key,event) -> {
                if(key!=KeyEvent.KEYCODE_BACK && key!=KeyEvent.KEYCODE_ESCAPE) return false;
                if(event.getAction()==KeyEvent.ACTION_UP) dismissConfirmation();
                return true;
            };
            keep.setOnKeyListener(dismiss);accept.setOnKeyListener(dismiss);
            sheet.setTag(new Object[]{title,scope,accept,keep});
            applyPalette(dark);content.setBackground(shape(color("#FFFFFF","#181B1F"),Color.TRANSPARENT,28));
            accept.setTextColor(color("#FFFFFF","#0E1012"));
            accept.setBackground(shape(color("#C4352B","#FF8A80"),Color.TRANSPARENT,28));
            FrameLayout.LayoutParams size=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
            sheet.addView(viewport,size);
            // Same-window native sheet preserves the Activity's existing focus/admission contract.
            scroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            root.addView(sheet,new FrameLayout.LayoutParams(-1,-1));keep.requestFocus();
        });
    }
    private void dismissConfirmation() {
        if(confirmation==null) return;
        FrameLayout sheet=confirmation;confirmation=null;
        sheet.cancelPendingInputEvents();
        Object[] views=(Object[])sheet.getTag();
        labels.remove(views[0]);labels.remove(views[1]);buttons.remove(views[2]);buttons.remove(views[3]);
        root.removeView(sheet);
        scroll.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        scroll.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if(confirmationReturnFocus!=null) confirmationReturnFocus.requestFocus();
        confirmationReturnFocus=null;
    }
    private void action(String title,SetupState.Action action) { primary.setText(title);primaryAction=action; }
    private void cancel() { secondary.setText("Cancel");secondaryAction=SetupState.Action.CANCEL; }
    private void title(String text) { TextView title=label(text,26);title.setAccessibilityHeading(true);add(body,title); }
    private void paragraph(String text) { add(body,label(text,15)); }
    private void note(String text) { LinearLayout card=card();add(card,label(text,14));add(body,card); }
    private void instruction(int number,String text) {
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.TOP);row.setBaselineAligned(false);
        TextView badge=label(Integer.toString(number),14);badge.setGravity(Gravity.CENTER);badge.setMinHeight(dp(32));
        LinearLayout.LayoutParams badgeSize=new LinearLayout.LayoutParams(dp(32),-2);badgeSize.setMarginEnd(dp(12));row.addView(badge,badgeSize);
        row.addView(label(text,15),new LinearLayout.LayoutParams(0,-2,1));add(body,row);
    }
    private LinearLayout card() { LinearLayout card=column();card.setPadding(dp(16),dp(14),dp(16),dp(4));cards.add(card);return card; }
    private LinearLayout column() { LinearLayout column=new LinearLayout(context);column.setOrientation(LinearLayout.VERTICAL);return column; }
    private TextView label(String text,int size) {
        TextView label=new TextView(context);label.setText(text);label.setTextSize(size);label.setLineSpacing(dp(2),1.12f);
        if(size>=20)label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);labels.add(label);return label;
    }
    private Button button(String text) {
        Button button=new Button(context);button.setText(text);button.setContentDescription(text);button.setAllCaps(false);
        button.setTextSize(16);button.setTypeface(Typeface.DEFAULT,Typeface.BOLD);button.setSingleLine(false);
        button.setMinHeight(dp(48));button.setMinimumHeight(dp(48));button.setMinWidth(dp(48));button.setMinimumWidth(dp(48));
        button.setPadding(dp(16),dp(12),dp(16),dp(12));button.setBackgroundTintList(null);buttons.add(button);return button;
    }
    private void add(LinearLayout parent,View child) {
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=dp(12);
        add(parent,child,size);
    }
    private void add(LinearLayout parent,View child,LinearLayout.LayoutParams size) {
        if(child.getParent() instanceof android.view.ViewGroup previous) previous.removeView(child);
        parent.addView(child,size);
    }
    public void applyPalette(boolean dark) {
        this.dark=dark;int text=color("#14171A","#F2F4F6"), muted=color("#5B636D","#9AA3AD");
        int surface=color("#FFFFFF","#181B1F"),border=color("#E1E4E8","#2C323A"),accent=color("#2F6FEB","#6AA8FF");
        root.setBackgroundColor(color("#F4F5F7","#0E1012"));
        for(TextView label:labels)label.setTextColor(label.getTextSize()<15*context.getResources().getDisplayMetrics().scaledDensity ? muted : text);
        for(TextView chip:chips) {
            SetupState.Status status=(SetupState.Status)chip.getTag();
            int fill=status==SetupState.Status.DONE ? color("#E3F4EA","#14281C")
                    : status==SetupState.Status.NEXT ? color("#E8F0FE","#16233B") : color("#E9ECF0","#23282E");
            chip.setTextColor(status==SetupState.Status.DONE ? color("#14532D","#86EFAC")
                    : status==SetupState.Status.NEXT ? color("#1B3E8A","#9CC3FF") : muted);
            chip.setBackground(shape(fill,Color.TRANSPARENT,12));
        }
        for(View card:cards)card.setBackground(shape(surface,card.isSelected() ? accent : border,20));
        for(View segment:segments)segment.setBackground(shape(segment.isSelected() ? accent : color("#E9ECF0","#23282E"),Color.TRANSPARENT,3));
        for(Button button:buttons) {
            boolean destructive=button==forgetBluetooth || button==forgetTv;
            int foreground=destructive ? color("#C4352B","#FF8A80") : button==primary ? color("#FFFFFF","#0E1012") : text;
            button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},{}},new int[]{muted,foreground}));
            android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
            background.addState(new int[]{-android.R.attr.state_enabled},shape(color("#E9ECF0","#181B1F"),border,28));
            background.addState(new int[]{android.R.attr.state_pressed},shape(color("#DCE8FF","#2C4160"),accent,28));
            background.addState(new int[]{android.R.attr.state_focused},shape(surface,accent,28));
            background.addState(new int[]{},shape(button==primary ? accent : surface,destructive ? foreground : border,28));button.setBackground(background);
        }
        address.setTextColor(text);address.setHintTextColor(muted);address.setBackgroundTintList(null);android.graphics.drawable.StateListDrawable fieldBackground=new android.graphics.drawable.StateListDrawable();
        fieldBackground.addState(new int[]{android.R.attr.state_focused},shape(surface,accent,16));
        fieldBackground.addState(new int[]{},shape(surface,border,16));address.setBackground(fieldBackground);
        code.setTextColor(text);code.setPalette(surface,border,accent);
        if(ring!=null)ring.invalidate();
    }
    private GradientDrawable shape(int fill,int border,int radius) {
        GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(radius));shape.setStroke(dp(1),border);return shape;
    }
    private int color(String light,String night) { return Color.parseColor(dark ? night : light); }
    private int dp(int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    private final class Ring extends View {
        long remaining,windowMillis;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Ring() { super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            float inset=dp(12);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(10));
            paint.setColor(color("#E9ECF0","#23282E"));canvas.drawOval(inset,inset,getWidth()-inset,getHeight()-inset,paint);
            paint.setColor(color("#2F6FEB","#6AA8FF"));
            canvas.drawArc(inset,inset,getWidth()-inset,getHeight()-inset,-90,360*Math.max(0,Math.min(1,remaining/(float)Math.max(1,windowMillis))),false,paint);
        }
    }
}
