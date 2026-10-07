package ua.museclass.app;

import android.content.Intent;
import android.os.Bundle;

/** Точка входу без власного екрана: увійшов — бібліотека, ні — вхід. */
public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Class<?> next = session().isActive() ? TabsActivity.class : LoginActivity.class;
        startActivity(new Intent(this, next));
        finish();
    }
}
