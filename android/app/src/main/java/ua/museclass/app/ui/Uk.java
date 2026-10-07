package ua.museclass.app.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

/**
 * Множина за українськими правилами. Android бере правила мови пристрою: на
 * англомовному телефоні вийшло б «видав 4 нот» і «2 півтона». Застосунок лише
 * український, тож рахуємо завжди по-українськи.
 */
public final class Uk {
    private static Resources res;

    private Uk() {
    }

    public static synchronized String plural(Context c, int id, int n) {
        if (res == null) {
            Configuration conf = new Configuration(c.getResources().getConfiguration());
            conf.setLocale(new Locale("uk"));
            res = c.getApplicationContext().createConfigurationContext(conf).getResources();
        }
        return res.getQuantityString(id, n, n);
    }
}
