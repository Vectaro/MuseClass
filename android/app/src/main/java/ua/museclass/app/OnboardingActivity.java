package ua.museclass.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ua.museclass.app.api.Dto;
import ua.museclass.app.ui.Avatar;
import ua.museclass.app.ui.FormDialog;
import ua.museclass.app.ui.Photos;
import ua.museclass.app.ui.Picks;
import ua.museclass.app.ui.Toasts;

/**
 * Онбординг за прототипом (renderOnb): вітання → ім'я і фото → сценарій → код
 * класу або перший клас → інструменти. Замість Google — пошта нашого API:
 * реєстрація збирає пошту й пароль у діалозі, а акаунт створюється на кроці
 * імені (сервер вимагає displayName одразу). Вхід у наявний акаунт пропускає
 * решту кроків. Сценарій — лише на пристрої: від нього залежать кроки.
 */
public class OnboardingActivity extends BaseActivity {
    private static final String AUTH = "auth", NAME = "name", ROLE = "role", CODE = "code", CLASS = "class",
            INST = "inst";
    private static final String STUDENT = "student", TEACHER = "teacher", SOLO = "solo";

    private String step = AUTH;
    private String email = "";
    private String password = "";
    private String name = "";
    private Bitmap photo;
    private String role;
    private boolean registered;
    private Dto.ClassInfo created;
    private final Set<String> inst = new LinkedHashSet<>();

    private LinearLayout steps;
    private FrameLayout body;
    private Avatar avatar;
    private MaterialButton photoBtn;
    private MaterialButton photoDel;

    private final ActivityResultLauncher<PickVisualMediaRequest> pick = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                background(() -> Photos.read(this, uri), b -> {
                    if (b == null) {
                        Toasts.show(this, getString(R.string.photo_unreadable));
                        return;
                    }
                    photo = b;
                    if (registered) Photos.save(this, session().userId(), b);
                    showPhoto();
                }, e -> Toasts.show(this, getString(R.string.photo_unreadable)));
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askLocalNetwork();
        setContentView(R.layout.activity_onboarding);
        padForSystemBars(findViewById(R.id.root));
        steps = findViewById(R.id.steps);
        body = findViewById(R.id.body);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                back();
            }
        });
        render();
    }

    // ---- кроки ----

    private List<String> stepList() {
        List<String> s = new ArrayList<>();
        s.add(NAME);
        s.add(ROLE);
        if (STUDENT.equals(role)) s.add(CODE);
        else if (TEACHER.equals(role)) s.add(CLASS);
        s.add(INST);
        return s;
    }

    private void next() {
        List<String> s = stepList();
        int i = s.indexOf(step);
        step = s.get(Math.min(i + 1, s.size() - 1));
        render();
    }

    private void back() {
        List<String> s = stepList();
        int i = s.indexOf(step);
        if (AUTH.equals(step)) {
            finish();
        } else if (i <= 0) {
            // до вітання — лише поки акаунт не створено
            if (!registered) {
                step = AUTH;
                render();
            } else {
                finish();
            }
        } else {
            step = s.get(i - 1);
            render();
        }
    }

    private void render() {
        steps.removeAllViews();
        List<String> s = stepList();
        int ix = s.indexOf(step);
        steps.setVisibility(AUTH.equals(step) ? View.GONE : View.VISIBLE);
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < s.size(); i++) {
            View seg = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(2 * d);
            g.setColor(ContextCompat.getColor(this, i <= ix ? R.color.amber : R.color.line));
            seg.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) lp.setMarginStart(Math.round(5 * d));
            steps.addView(seg, lp);
        }
        body.removeAllViews();
        int layout = AUTH.equals(step) ? R.layout.onb_auth : NAME.equals(step) ? R.layout.onb_name
                : ROLE.equals(step) ? R.layout.onb_role : CODE.equals(step) ? R.layout.onb_code
                : CLASS.equals(step) ? R.layout.onb_class : R.layout.onb_inst;
        View v = LayoutInflater.from(this).inflate(layout, body, false);
        body.addView(v);
        switch (step) {
            case AUTH:
                bindAuth(v);
                break;
            case NAME:
                bindName(v);
                break;
            case ROLE:
                bindRole(v);
                break;
            case CODE:
                bindCode(v);
                break;
            case CLASS:
                bindClass(v);
                break;
            default:
                bindInst(v);
        }
    }

    // ---- вітання і діалоги пошти ----

    private void bindAuth(View v) {
        v.findViewById(R.id.login).setOnClickListener(x -> new FormDialog(this, getString(R.string.dlg_login_title))
                .field(getString(R.string.email_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, email, 0, false)
                .field(getString(R.string.password_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD, null, 0, false)
                .ok(getString(R.string.dlg_login_ok))
                .onSubmit((vals, dlg) -> {
                    String m = vals[0].trim(), p = vals[1];
                    if (m.isEmpty() || p.isEmpty()) {
                        dlg.error(getString(R.string.err_fill));
                        return;
                    }
                    email = m;
                    dlg.busy(true);
                    background(() -> api().login(m, p), auth -> {
                        session().save(auth);
                        dlg.dismiss();
                        toTabs();
                    }, e -> dlg.error(messageOf(e)));
                })
                .show());
        v.findViewById(R.id.register).setOnClickListener(x -> new FormDialog(this, getString(R.string.dlg_register_title))
                .note(getString(R.string.dlg_register_note))
                .field(getString(R.string.email_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, email, 0, false)
                .field(getString(R.string.password_hint), InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD, null, 0, false)
                .ok(getString(R.string.dlg_register_ok))
                .onSubmit((vals, dlg) -> {
                    String m = vals[0].trim();
                    if (!Patterns.EMAIL_ADDRESS.matcher(m).matches()) {
                        dlg.error(getString(R.string.err_email));
                        return;
                    }
                    if (vals[1].length() < 8) {
                        dlg.error(getString(R.string.err_password));
                        return;
                    }
                    email = m;
                    password = vals[1];
                    if (name.isEmpty()) name = Names.fromEmail(m);
                    dlg.dismiss();
                    step = NAME;
                    render();
                })
                .show());
    }

    // ---- ім'я і фото ----

    private void bindName(View v) {
        avatar = v.findViewById(R.id.avatar);
        photoBtn = v.findViewById(R.id.photo);
        photoDel = v.findViewById(R.id.photodel);
        EditText field = v.findViewById(R.id.name);
        field.setText(name);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                name = s.toString();
                avatar.set(name, photo);
            }
        });
        View.OnClickListener pickPhoto = x -> pick.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        avatar.setOnClickListener(pickPhoto);
        photoBtn.setOnClickListener(pickPhoto);
        photoDel.setOnClickListener(x -> {
            photo = null;
            if (registered) Photos.save(this, session().userId(), null);
            showPhoto();
        });
        showPhoto();
        MaterialButton next = v.findViewById(R.id.next);
        next.setOnClickListener(x -> {
            String n = name.trim();
            if (n.isEmpty()) {
                Toasts.show(this, getString(R.string.name_needed));
                return;
            }
            next.setEnabled(false);
            // акаунт створюється тут: серверу потрібне ім'я разом з поштою й паролем
            background(() -> registered ? toAuthName(api().updateName(n)) : api().register(email, password, n),
                    auth -> {
                        if (!registered) session().save(auth);
                        session().setDisplayName(n);
                        registered = true;
                        password = "";
                        Photos.save(this, session().userId(), photo);
                        next();
                    }, e -> {
                        next.setEnabled(true);
                        Toasts.show(this, messageOf(e));
                    });
        });
    }

    private static Dto.Auth toAuthName(Dto.Profile p) {
        Dto.Auth a = new Dto.Auth();
        a.displayName = p.displayName;
        return a;
    }

    private void showPhoto() {
        avatar.set(name, photo);
        photoBtn.setText(photo == null ? R.string.photo_add : R.string.photo_change);
        photoDel.setVisibility(photo == null ? View.GONE : View.VISIBLE);
    }

    // ---- сценарій ----

    private void bindRole(View v) {
        MaterialButton next = v.findViewById(R.id.next);
        String[][] opts = {{STUDENT, getString(R.string.role_student), getString(R.string.role_student_sub)},
                {TEACHER, getString(R.string.role_teacher), getString(R.string.role_teacher_sub)},
                {SOLO, getString(R.string.role_solo), getString(R.string.role_solo_sub)}};
        int[] ids = {R.id.student, R.id.teacher, R.id.solo};
        List<View> all = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            View o = v.findViewById(ids[i]);
            ((TextView) o.findViewById(R.id.strong)).setText(opts[i][1]);
            ((TextView) o.findViewById(R.id.span)).setText(opts[i][2]);
            o.setSelected(opts[i][0].equals(role));
            all.add(o);
            final String key = opts[i][0];
            o.setOnClickListener(x -> {
                role = key;
                for (View a : all) a.setSelected(a == o);
                next.setEnabled(true);
                render();
            });
        }
        next.setEnabled(role != null);
        next.setOnClickListener(x -> next());
    }

    // ---- код класу (учень) ----

    private void bindCode(View v) {
        EditText code = v.findViewById(R.id.code);
        TextView err = v.findViewById(R.id.error);
        MaterialButton join = v.findViewById(R.id.join);
        Runnable go = () -> {
            String c = code.getText().toString().trim();
            if (c.isEmpty()) {
                err.setText(R.string.code_needed);
                return;
            }
            err.setText(null);
            join.setEnabled(false);
            background(() -> api().joinClass(c), cl -> {
                Toasts.show(this, getString(R.string.joined, cl.name));
                next();
            }, e -> {
                join.setEnabled(true);
                err.setText(messageOf(e));
            });
        };
        join.setOnClickListener(x -> go.run());
        code.setOnEditorActionListener((t, a, e) -> {
            if (a != EditorInfo.IME_ACTION_DONE) return false;
            go.run();
            return true;
        });
        v.findViewById(R.id.skip).setOnClickListener(x -> next());
    }

    // ---- перший клас (викладач) ----

    private void bindClass(View v) {
        EditText nm = v.findViewById(R.id.cname);
        TextView box = v.findViewById(R.id.codebox);
        TextView err = v.findViewById(R.id.error);
        MaterialButton create = v.findViewById(R.id.create);
        View skip = v.findViewById(R.id.skip);
        Runnable show = () -> {
            boolean done = created != null;
            box.setText(done ? created.code : getString(R.string.code_hint));
            box.setAlpha(done ? 1f : .35f);
            create.setText(done ? R.string.next : R.string.class_create);
            skip.setVisibility(done ? View.GONE : View.VISIBLE);
            nm.setEnabled(!done);
            if (done) nm.setText(created.name);
        };
        show.run();
        create.setOnClickListener(x -> {
            if (created != null) {
                next();
                return;
            }
            String n = nm.getText().toString().trim();
            if (n.isEmpty()) {
                err.setText(R.string.class_name_needed);
                return;
            }
            err.setText(null);
            create.setEnabled(false);
            background(() -> api().createClass(n), cl -> {
                created = cl;
                create.setEnabled(true);
                show.run();
            }, e -> {
                create.setEnabled(true);
                err.setText(messageOf(e));
            });
        });
        skip.setOnClickListener(x -> next());
    }

    // ---- інструменти ----

    private void bindInst(View v) {
        ChipGroup g = v.findViewById(R.id.pick);
        MaterialButton done = v.findViewById(R.id.done);
        for (Instruments.Item it : Instruments.list(this)) {
            TextView b = Picks.make(this, it.name);
            Picks.set(b, inst.contains(it.code));
            b.setOnClickListener(x -> {
                if (!inst.remove(it.code)) inst.add(it.code);
                Picks.set(b, inst.contains(it.code));
                done.setEnabled(!inst.isEmpty());
            });
            g.addView(b);
        }
        done.setEnabled(!inst.isEmpty());
        done.setOnClickListener(x -> {
            done.setEnabled(false);
            List<String> codes = new ArrayList<>();
            codes.addAll(inst);
            background(() -> api().setInstruments(codes), p -> toTabs(), e -> {
                done.setEnabled(true);
                Toasts.show(this, messageOf(e));
            });
        });
    }

    private void toTabs() {
        Intent i = new Intent(this, TabsActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
