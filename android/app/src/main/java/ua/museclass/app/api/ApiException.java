package ua.museclass.app.api;

/**
 * Помилка запиту з готовим текстом для користувача.
 * Якщо сервер дав problem+json з detail — це він, українською.
 */
public class ApiException extends Exception {
    public static final String NETWORK = "Немає зв'язку з сервером. Перевір інтернет і спробуй ще раз.";
    public static final String SESSION = "Сесія закінчилась. Увійди знову.";
    public static final String GENERIC = "Щось пішло не так.";
    public static final String SERVER = "Сервер зараз не відповідає як слід. Спробуй пізніше.";

    /** HTTP-статус; 0 — до сервера не достукались. */
    public final int status;
    /** Токен більше не діє: його вже стерто, треба на вхід. */
    public final boolean unauthorized;

    ApiException(int status, String message, boolean unauthorized, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.unauthorized = unauthorized;
    }

    static ApiException network(Throwable cause) {
        return new ApiException(0, NETWORK, false, cause);
    }
}
