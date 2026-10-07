package ua.museclass.app.tabs;

import androidx.fragment.app.Fragment;

import ua.museclass.app.BaseActivity;
import ua.museclass.app.TabsActivity;
import ua.museclass.app.api.ApiClient;
import ua.museclass.app.ui.Toasts;

/** Спільне для вкладок: фонові запити, які мовчать, якщо вкладку вже прибрали. */
public abstract class TabFragment extends Fragment {
    protected BaseActivity host() {
        return (BaseActivity) requireActivity();
    }

    protected ApiClient api() {
        return host().api();
    }

    protected <T> void background(BaseActivity.Work<T> work, BaseActivity.Done<T> ok,
                                  BaseActivity.Done<Exception> fail) {
        host().background(work, v -> {
            if (isAdded()) ok.accept(v);
        }, e -> {
            if (isAdded()) fail.accept(e);
        });
    }

    protected void toast(CharSequence msg) {
        if (isAdded()) Toasts.show(requireActivity(), msg);
    }

    /** Перейти на іншу вкладку (наприклад, у бібліотеку після вступу в клас). */
    protected void goTab(String key) {
        if (getActivity() instanceof TabsActivity) ((TabsActivity) getActivity()).show(key, true);
    }
}
