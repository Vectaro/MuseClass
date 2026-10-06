package ua.museclass.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** Учень вступає в клас за кодом (POST /classes/join). Успіх — назад з назвою класу. */
public class JoinClassActivity extends BaseActivity {
    static final String RESULT_CLASS_NAME = "className";

    private TextInputEditText code;
    private TextView error;
    private MaterialButton submit;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_class);
        padForSystemBars(findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        code = findViewById(R.id.code);
        error = findViewById(R.id.error);
        submit = findViewById(R.id.submit);
        progress = findViewById(R.id.progress);

        submit.setOnClickListener(v -> submit());
        code.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit();
            return true;
        });
        code.requestFocus();
    }

    private void submit() {
        String c = code.getText() == null ? "" : code.getText().toString().trim();
        if (c.isEmpty()) {
            error.setText(R.string.join_empty);
            return;
        }
        error.setText(null);
        busy(true);
        // формат перевіряє сервер: він прощає більше, ніж варто дублювати тут
        background(() -> api().joinClass(c), joined -> {
            setResult(RESULT_OK, new Intent().putExtra(RESULT_CLASS_NAME, joined.name));
            finish();
        }, e -> {
            busy(false);
            error.setText(messageOf(e));
            // вимкнене поле віддало фокус стрілці «назад» — Enter закрив би екран
            code.requestFocus();
            code.selectAll();
        });
    }

    private void busy(boolean b) {
        submit.setEnabled(!b);
        code.setEnabled(!b);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
    }
}
