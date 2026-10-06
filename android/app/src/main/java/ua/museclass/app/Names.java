package ua.museclass.app;

import java.util.Locale;

/** Підказка імені при реєстрації: ім'я обов'язкове, але вгадується з пошти. */
final class Names {
    private Names() {
    }

    /** «taras.uchen@…» → «Taras Uchen». */
    static String fromEmail(String email) {
        int at = email.indexOf('@');
        String local = at >= 0 ? email.substring(0, at) : email;
        StringBuilder sb = new StringBuilder();
        for (String w : local.split("[._+0-9-]+")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
        }
        return sb.toString();
    }
}
