package ua.museclass.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ua.museclass.app.api.Dto;

/** Вхід і реєстрація поштою. Ім'я при реєстрації підказується з адреси. */
public class LoginActivity extends BaseActivity {
    private boolean register;
    private boolean nameEdited;
    private boolean fillingName;

    private TextInputEditText email;
    private TextInputEditText password;
    private TextInputEditText name;
    private TextInputLayout nameLayout;
    private TextView error;
    private MaterialButton submit;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        askLocalNetwork();
        setContentView(R.layout.activity_login);
        padForSystemBars(findViewById(R.id.root));

        email = findViewById(R.id.email);
        password = findViewById(R.id.password);
        name = findViewById(R.id.name);
        nameLayout = findViewById(R.id.nameLayout);
        error = findViewById(R.id.error);
        submit = findViewById(R.id.submit);
        progress = findViewById(R.id.progress);

        MaterialButtonToggleGroup mode = findViewById(R.id.mode);
        mode.check(R.id.modeLogin);
        mode.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) setRegister(id == R.id.modeRegister);
        });
        email.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (register && !nameEdited) suggestName();
            }
        });
        name.addTextChangedListener(new Watcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (!fillingName) nameEdited = s.length() > 0;
            }
        });
        submit.setOnClickListener(v -> submit());
        setRegister(false);
    }

    private void setRegister(boolean on) {
        register = on;
        nameLayout.setVisibility(on ? View.VISIBLE : View.GONE);
        submit.setText(on ? R.string.login_register : R.string.login_enter);
        error.setText("");
        if (on && !nameEdited) suggestName();
    }

    private void suggestName() {
        fillingName = true;
        name.setText(Names.fromEmail(text(email)));
        fillingName = false;
    }

    private void submit() {
        String e = text(email), p = text(password), n = text(name);
        if (e.isEmpty() || p.isEmpty() || (register && n.isEmpty())) {
            error.setText(R.string.login_fill_all);
            return;
        }
        if (register && p.length() < 8) {
            error.setText(R.string.login_short_password);
            return;
        }
        busy(true);
        background(() -> register ? api().register(e, p, n) : api().login(e, p),
                this::signedIn,
                ex -> {
                    busy(false);
                    error.setText(messageOf(ex));
                });
    }

    private void signedIn(Dto.Auth auth) {
        session().save(auth);
        Intent i = new Intent(this, LibraryActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void busy(boolean on) {
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        submit.setEnabled(!on);
        if (on) error.setText("");
    }

    private static String text(TextInputEditText f) {
        return f.getText() == null ? "" : f.getText().toString().trim();
    }

    private abstract static class Watcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
