package ua.museclass.app;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import ua.museclass.app.tabs.HomeFragment;
import ua.museclass.app.tabs.LibraryFragment;
import ua.museclass.app.tabs.ProfileFragment;
import ua.museclass.app.ui.Toasts;

/**
 * Головний екран після входу: три вкладки, як #tabs прототипу. Кожен перехід
 * на вкладку малює її заново (у прототипі render() теж перемальовує, і пошук
 * скидається) — так бібліотека бачить клас, щойно доданий у профілі.
 */
public class TabsActivity extends BaseActivity {
    public static final String TAB_HOME = "home";
    public static final String TAB_LIB = "lib";
    public static final String TAB_ME = "me";
    private static final String STATE_TAB = "tab";

    private String tab = TAB_HOME;
    private View home;
    private View lib;
    private View me;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askLocalNetwork();
        setContentView(R.layout.activity_tabs);
        View root = findViewById(R.id.root);
        View tabs = findViewById(R.id.tabs);
        // вміст — під статус-бар, таби — над смугою навігації (calc(11px + safe-area))
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, b.top, b.right, 0);
            tabs.setPadding(0, 0, 0, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // значки — вектори з тими самими символами (♪ 𝄃𝄃 ◍): системні гліфи різного розміру
        home = setupTab(R.id.tab_home, R.drawable.ic_tab_home, R.string.tab_home, TAB_HOME);
        lib = setupTab(R.id.tab_lib, R.drawable.ic_tab_lib, R.string.tab_lib, TAB_LIB);
        me = setupTab(R.id.tab_me, R.drawable.ic_tab_me, R.string.tab_me, TAB_ME);

        if (savedInstanceState != null) tab = savedInstanceState.getString(STATE_TAB, TAB_HOME);
        else if (getIntent().getStringExtra(STATE_TAB) != null) tab = getIntent().getStringExtra(STATE_TAB);
        show(tab, savedInstanceState == null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Toasts.flush(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_TAB, tab);
    }

    private View setupTab(int id, int icon, int label, String key) {
        View v = findViewById(id);
        ((ImageView) v.findViewById(R.id.ic)).setImageResource(icon);
        ((TextView) v.findViewById(R.id.label)).setText(label);
        v.setOnClickListener(x -> show(key, true));
        return v;
    }

    /** Перейти на вкладку і намалювати її заново. */
    public void show(String key, boolean fresh) {
        tab = key;
        ColorStateList on = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_ink));
        ColorStateList off = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.muted));
        for (View v : new View[]{home, lib, me}) {
            boolean cur = (v == home && TAB_HOME.equals(key)) || (v == lib && TAB_LIB.equals(key))
                    || (v == me && TAB_ME.equals(key));
            v.setSelected(cur);
            ImageViewCompat.setImageTintList(v.findViewById(R.id.ic), cur ? on : off);
            ((TextView) v.findViewById(R.id.label)).setTextColor(cur ? on : off);
        }
        if (!fresh && getSupportFragmentManager().findFragmentById(R.id.content) != null) return;
        Fragment f = TAB_LIB.equals(key) ? new LibraryFragment() : TAB_ME.equals(key) ? new ProfileFragment()
                : new HomeFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.content, f).commit();
    }
}
