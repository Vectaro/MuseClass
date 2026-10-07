package ua.museclass.app.ui;

import android.text.InputType;

import ua.museclass.app.BaseActivity;
import ua.museclass.app.R;
import ua.museclass.app.api.Dto;

/** Діалоги прототипу «Приєднатися до класу» (joinprompt) і «Новий клас» (mkclass) — через наш API. */
public final class ClassDialogs {
    public interface Done {
        void accept(Dto.ClassInfo c);
    }

    private ClassDialogs() {
    }

    public static void join(BaseActivity a, Done done) {
        new FormDialog(a, a.getString(R.string.dlg_join_title))
                .note(a.getString(R.string.dlg_join_note))
                .field(a.getString(R.string.code_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, null, 7, true)
                .ok(a.getString(R.string.join))
                .onSubmit((v, d) -> {
                    String code = v[0].trim();
                    if (code.isEmpty()) {
                        d.error(a.getString(R.string.code_needed));
                        return;
                    }
                    d.busy(true);
                    a.background(() -> a.api().joinClass(code), c -> {
                        d.dismiss();
                        Toasts.show(a, a.getString(R.string.joined, c.name));
                        done.accept(c);
                    }, e -> d.error(BaseActivity.messageOf(e)));
                })
                .show();
    }

    public static void create(BaseActivity a, Done done) {
        new FormDialog(a, a.getString(R.string.dlg_mkclass_title))
                .note(a.getString(R.string.dlg_mkclass_note))
                .field(a.getString(R.string.dlg_mkclass_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, null, 40, false)
                .ok(a.getString(R.string.dlg_mkclass_ok))
                .onSubmit((v, d) -> {
                    String name = v[0].trim();
                    if (name.isEmpty()) {
                        d.error(a.getString(R.string.class_name_needed));
                        return;
                    }
                    d.busy(true);
                    a.background(() -> a.api().createClass(name), c -> {
                        d.dismiss();
                        Toasts.show(a, a.getString(R.string.class_created, c.name, c.code));
                        done.accept(c);
                    }, e -> d.error(BaseActivity.messageOf(e)));
                })
                .show();
    }
}
