package ua.museclass.app.tabs;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import ua.museclass.app.BaseActivity;
import ua.museclass.app.Instruments;
import ua.museclass.app.R;
import ua.museclass.app.TabsActivity;
import ua.museclass.app.api.Dto;
import ua.museclass.app.ui.Avatar;
import ua.museclass.app.ui.ClassDialogs;
import ua.museclass.app.ui.Page;
import ua.museclass.app.ui.Photos;
import ua.museclass.app.ui.Picks;
import ua.museclass.app.ui.ThemeMode;
import ua.museclass.app.ui.Toasts;

/**
 * Профіль (renderMe): аватар та ім'я (PATCH /me), вигляд — тема на пристрої,
 * інструменти (PUT /me/instruments), класи з діалогами вступу й створення,
 * акаунт. Фото — лише на пристрої. «Пройти онбординг заново» прототипу тут —
 * «Вийти»: справжній акаунт так і починають заново.
 */
public class ProfileFragment extends TabFragment {
    private static final String[] THEMES = {ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK};
    private static final int[] THEME_LABELS = {R.string.theme_system, R.string.theme_light, R.string.theme_dark};

    private final Handler main = new Handler(Looper.getMainLooper());
    private Avatar avatar;
    private EditText name;
    private TextView who;
    private MaterialButton photoBtn;
    private MaterialButton photoDel;
    private ChipGroup instGroup;
    private LinearLayout classes;
    private Bitmap photo;
    private final Set<String> inst = new LinkedHashSet<>();
    private boolean teacher;
    private String savedName;
    private final Runnable saveName = this::saveName;

    private final ActivityResultLauncher<PickVisualMediaRequest> pick = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri == null) return;
                background(() -> Photos.read(requireContext(), uri), b -> {
                    if (b == null) {
                        toast(getString(R.string.photo_unreadable));
                        return;
                    }
                    photo = b;
                    Photos.save(requireContext(), host().session().userId(), b);
                    showPhoto();
                }, e -> toast(getString(R.string.photo_unreadable)));
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View v = inf.inflate(R.layout.fragment_profile, parent, false);
        avatar = v.findViewById(R.id.avatar);
        name = v.findViewById(R.id.name);
        who = v.findViewById(R.id.who);
        photoBtn = v.findViewById(R.id.photo);
        photoDel = v.findViewById(R.id.photodel);
        instGroup = v.findViewById(R.id.inst);
        classes = v.findViewById(R.id.classes);

        savedName = host().session().displayName();
        name.setText(savedName);
        photo = Photos.load(requireContext(), host().session().userId());
        showPhoto();
        View.OnClickListener pickPhoto = x -> pick.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        avatar.setOnClickListener(pickPhoto);
        photoBtn.setOnClickListener(pickPhoto);
        photoDel.setOnClickListener(x -> {
            photo = null;
            Photos.save(requireContext(), host().session().userId(), null);
            showPhoto();
            toast(getString(R.string.photo_removed));
        });
        name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                avatar.set(s.toString(), photo);
                main.removeCallbacks(saveName);
                main.postDelayed(saveName, 900);
            }
        });
        name.setOnEditorActionListener((t, a, e) -> {
            if (a != EditorInfo.IME_ACTION_DONE) return false;
            main.removeCallbacks(saveName);
            saveName();
            name.clearFocus();
            return false;
        });

        bindThemes(v.findViewById(R.id.themes));
        v.findViewById(R.id.joinprompt).setOnClickListener(x ->
                ClassDialogs.join(host(), c -> goTab(TabsActivity.TAB_LIB)));
        v.findViewById(R.id.mkclass).setOnClickListener(x ->
                ClassDialogs.create(host(), c -> goTab(TabsActivity.TAB_LIB)));
        v.findViewById(R.id.logout).setOnClickListener(x -> host().toLogin());

        LinearLayout account = v.findViewById(R.id.account);
        int n = host().app().scoreFiles().count();
        Page.kv(account, getString(R.string.me_offline), getResources().getQuantityString(R.plurals.scores, n, n));
        Page.kv(account, getString(R.string.me_format), getString(R.string.me_format_value));
        Page.kv(account, getString(R.string.me_lang), getString(R.string.me_lang_value));

        background(() -> {
            Object[] r = new Object[2];
            r[0] = api().me();
            r[1] = api().classes();
            return r;
        }, r -> showData((Dto.Profile) r[0], castClasses(r[1])), e -> toast(BaseActivity.messageOf(e)));
        return v;
    }

    @SuppressWarnings("unchecked")
    private static List<Dto.ClassInfo> castClasses(Object o) {
        return (List<Dto.ClassInfo>) o;
    }

    @Override
    public void onPause() {
        // ім'я зберігаємо й тоді, коли пішли з вкладки до затримки
        main.removeCallbacks(saveName);
        saveName();
        super.onPause();
    }

    private void showPhoto() {
        avatar.set(name.getText().toString(), photo);
        photoBtn.setText(photo == null ? R.string.photo_add : R.string.photo_change);
        photoDel.setVisibility(photo == null ? View.GONE : View.VISIBLE);
    }

    private void saveName() {
        String n = name.getText().toString().trim();
        if (n.isEmpty() || n.equals(savedName)) return;
        savedName = n;
        host().session().setDisplayName(n);
        background(() -> api().updateName(n), p -> { }, e -> toast(BaseActivity.messageOf(e)));
    }

    private void bindThemes(ChipGroup g) {
        String cur = ThemeMode.get(requireContext());
        for (int i = 0; i < THEMES.length; i++) {
            TextView b = Picks.make(requireContext(), getString(THEME_LABELS[i]));
            String mode = THEMES[i];
            Picks.set(b, mode.equals(cur));
            b.setOnClickListener(x -> {
                if (mode.equals(ThemeMode.get(requireContext()))) return;
                // якщо тема справді зміниться, екран перествориться і тост покаже новий;
                // якщо ні — покажемо тут, трохи згодом
                Toasts.later(getString(ThemeMode.SYSTEM.equals(mode) ? R.string.theme_follow : R.string.theme_fixed));
                for (int k = 0; k < g.getChildCount(); k++) {
                    Picks.set((TextView) g.getChildAt(k), k == g.indexOfChild(b));
                }
                ThemeMode.set(requireContext(), mode);
                main.postDelayed(() -> {
                    if (isAdded()) Toasts.flush(requireActivity());
                }, 400);
            });
            g.addView(b);
        }
    }

    private void showData(Dto.Profile p, List<Dto.ClassInfo> cls) {
        inst.clear();
        if (p.instruments != null) inst.addAll(p.instruments);
        teacher = false;
        for (Dto.ClassInfo c : cls) teacher |= "teacher".equals(c.role);
        showWho();

        instGroup.removeAllViews();
        for (Instruments.Item it : Instruments.list(requireContext())) {
            TextView b = Picks.make(requireContext(), it.name);
            Picks.set(b, inst.contains(it.code));
            b.setOnClickListener(x -> {
                if (!inst.remove(it.code)) inst.add(it.code);
                Picks.set(b, inst.contains(it.code));
                showWho();
                List<String> codes = new ArrayList<>(inst);
                background(() -> api().setInstruments(codes), r -> { }, e -> toast(BaseActivity.messageOf(e)));
            });
            instGroup.addView(b);
        }

        classes.removeAllViews();
        for (Dto.ClassInfo c : cls) {
            if (!"teacher".equals(c.role)) Page.kv(classes, c.name, c.teacherName);
        }
        for (Dto.ClassInfo c : cls) {
            if ("teacher".equals(c.role)) Page.kv(classes, c.name, getString(R.string.lib_code, c.code));
        }
        if (cls.isEmpty()) {
            Page.kv(classes, getString(R.string.me_no_classes), null)
                    .setTextColor(ContextCompat.getColor(requireContext(), R.color.muted));
        }
    }

    private void showWho() {
        List<String> names = new ArrayList<>();
        for (Instruments.Item it : Instruments.list(requireContext())) if (inst.contains(it.code)) names.add(it.name);
        who.setText(getString(teacher ? R.string.me_teacher : R.string.me_student) + " · "
                + (names.isEmpty() ? getString(R.string.me_no_inst) : String.join(", ", names)));
    }
}
