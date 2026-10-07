package ua.museclass.app.tabs;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ua.museclass.app.R;

/** Вкладка «Library» — наповнюється наступним кроком. */
public class LibraryFragment extends TabFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View v = inf.inflate(R.layout.fragment_page, parent, false);
        ((TextView) v.findViewById(R.id.title)).setText(R.string.tab_lib);
        return v;
    }
}
