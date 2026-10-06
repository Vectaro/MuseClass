package ua.museclass.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.api.Dto;

/** «Моя бібліотека»: усе, що видали в моїх класах (GET /me/library). */
public class LibraryActivity extends BaseActivity {
    private final Adapter adapter = new Adapter();
    private SwipeRefreshLayout refresh;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_library);
        padForSystemBars(findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setSubtitle(session().displayName());

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        empty = findViewById(R.id.empty);
        refresh = findViewById(R.id.refresh);
        refresh.setOnRefreshListener(this::load);
        refresh.setRefreshing(true);
        load();
    }

    private void load() {
        background(() -> api().library(), entries -> {
            refresh.setRefreshing(false);
            adapter.set(entries);
            empty.setText(R.string.library_empty);
            empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        }, e -> {
            refresh.setRefreshing(false);
            empty.setText(messageOf(e));
            empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            if (adapter.getItemCount() > 0) {
                Snackbar.make(refresh, messageOf(e), Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.library, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.logout) {
            toLogin();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void open(Dto.LibraryEntry e) {
        Intent i = new Intent(this, ScoreActivity.class);
        i.putExtra(ScoreActivity.EXTRA_ID, e.score.id);
        i.putExtra(ScoreActivity.EXTRA_TITLE, e.score.title);
        startActivity(i);
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<Dto.LibraryEntry> items = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged")
        void set(List<Dto.LibraryEntry> entries) {
            items.clear();
            items.addAll(entries);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_score, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Dto.LibraryEntry e = items.get(position);
            h.title.setText(e.score.title);
            StringBuilder sub = new StringBuilder();
            if (e.score.composer != null) sub.append(e.score.composer);
            if (e.score.arranger != null) sub.append(sub.length() > 0 ? " · " : "").append(e.score.arranger);
            h.subtitle.setText(sub);
            h.subtitle.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);
            h.meta.setText(getString(R.string.dot_join,
                    getResources().getQuantityString(R.plurals.measures, e.score.measures, e.score.measures),
                    e.className));
            h.itemView.setOnClickListener(v -> open(e));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView meta;

        Holder(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            subtitle = v.findViewById(R.id.subtitle);
            meta = v.findViewById(R.id.meta);
        }
    }
}
