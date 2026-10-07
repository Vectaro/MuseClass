package ua.museclass.app.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.R;

/**
 * Модалка прототипу (openDialog / .mbox): заголовок Serif 20, примітка, поля
 * .fld, червоний рядок помилки, «Скасувати» + дія. Enter — підтвердити.
 * Обробник може відповісти одразу (error / dismiss) або після запиту до
 * сервера — тоді спершу busy(true).
 */
public final class FormDialog {
    public interface Submit {
        void submit(String[] values, FormDialog dialog);
    }

    private static final class Field {
        String hint;
        int inputType = InputType.TYPE_CLASS_TEXT;
        String value;
        int maxLength;
        boolean upper;
    }

    private final Activity activity;
    private final String title;
    private String note;
    private String ok;
    private Submit submit;
    private final List<Field> fields = new ArrayList<>();
    private final List<EditText> inputs = new ArrayList<>();
    private Dialog dialog;
    private TextView error;
    private MaterialButton okBtn;

    public FormDialog(Activity a, String title) {
        this.activity = a;
        this.title = title;
        this.ok = a.getString(R.string.dlg_done);
    }

    public FormDialog note(String n) {
        note = n;
        return this;
    }

    public FormDialog ok(String label) {
        ok = label;
        return this;
    }

    public FormDialog field(String hint, int inputType, @Nullable String value, int maxLength, boolean upper) {
        Field f = new Field();
        f.hint = hint;
        f.inputType = inputType;
        f.value = value;
        f.maxLength = maxLength;
        f.upper = upper;
        fields.add(f);
        return this;
    }

    public FormDialog onSubmit(Submit s) {
        submit = s;
        return this;
    }

    public FormDialog show() {
        View box = LayoutInflater.from(activity).inflate(R.layout.dialog_form, null);
        ((TextView) box.findViewById(R.id.title)).setText(title);
        TextView n = box.findViewById(R.id.note);
        n.setText(note);
        n.setVisibility(note == null ? View.GONE : View.VISIBLE);
        LinearLayout holder = box.findViewById(R.id.fields);
        float d = activity.getResources().getDisplayMetrics().density;
        for (int i = 0; i < fields.size(); i++) {
            Field f = fields.get(i);
            EditText e = (EditText) LayoutInflater.from(activity).inflate(R.layout.item_field, holder, false);
            e.setHint(f.hint);
            e.setInputType(f.inputType);
            if (f.value != null) e.setText(f.value);
            List<InputFilter> fl = new ArrayList<>();
            if (f.maxLength > 0) fl.add(new InputFilter.LengthFilter(f.maxLength));
            if (f.upper) fl.add(new InputFilter.AllCaps());
            e.setFilters(fl.toArray(new InputFilter[0]));
            boolean last = i == fields.size() - 1;
            e.setImeOptions(last ? EditorInfo.IME_ACTION_DONE : EditorInfo.IME_ACTION_NEXT);
            if (last) {
                e.setOnEditorActionListener((v, action, ev) -> {
                    boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            && ev.getAction() == KeyEvent.ACTION_DOWN;
                    if (action != EditorInfo.IME_ACTION_DONE && !enter) return false;
                    fire();
                    return true;
                });
            }
            holder.addView(e);
            inputs.add(e);
        }
        error = box.findViewById(R.id.error);
        okBtn = box.findViewById(R.id.ok);
        okBtn.setText(ok);
        okBtn.setOnClickListener(v -> fire());
        box.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());

        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setDimAmount(.5f);
            int max = Math.round(340 * d), screen = activity.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(max, screen - Math.round(40 * d)), WindowManager.LayoutParams.WRAP_CONTENT);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        if (!inputs.isEmpty()) inputs.get(0).requestFocus();
        return this;
    }

    private void fire() {
        if (submit == null || !okBtn.isEnabled()) return;
        String[] v = new String[inputs.size()];
        for (int i = 0; i < v.length; i++) v[i] = inputs.get(i).getText().toString();
        error.setText(null);
        submit.submit(v, this);
    }

    public void error(String text) {
        busy(false);
        error.setText(text);
    }

    public void busy(boolean b) {
        okBtn.setEnabled(!b);
        for (EditText e : inputs) e.setEnabled(!b);
        if (!b && !inputs.isEmpty()) inputs.get(inputs.size() - 1).requestFocus();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }
}
