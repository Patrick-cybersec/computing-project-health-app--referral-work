package com.example.healthapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BillboardActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://1c05e1adb0b1.ngrok-free.app";
    private static final String TAG = "BillboardActivity";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private RecyclerView recyclerView;
    private UserStarAdapter adapter;
    private List<UserStar> userStarList;
    private OkHttpClient client;
    private TextView noDataText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.billboard_screen);

        client = TrustAllOkHttpClient.getClient();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Billboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recycler_view);
        noDataText = findViewById(R.id.no_data_text);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        userStarList = new ArrayList<>();
        adapter = new UserStarAdapter(userStarList);
        recyclerView.setAdapter(adapter);

        fetchUserStars();
    }

    private void fetchUserStars() {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/Users/stars")
                .get()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    noDataText.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                    Toast.makeText(BillboardActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Stars Response: " + body);

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        noDataText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        Toast.makeText(BillboardActivity.this, "Failed: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                try {
                    JSONArray array = new JSONArray(body);
                    List<UserStar> list = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject o = array.getJSONObject(i);
                        String username = o.optString("username", "Unknown");
                        int stars = o.optInt("starCount", 0);
                        list.add(new UserStar(username, stars)); // SHOW ALL USERS
                    }

                    runOnUiThread(() -> {
                        userStarList.clear();
                        userStarList.addAll(list);
                        adapter.notifyDataSetChanged();
                        if (list.isEmpty()) {
                            noDataText.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            noDataText.setVisibility(View.GONE);
                            recyclerView.setVisibility(View.VISIBLE);
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    runOnUiThread(() -> {
                        noDataText.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                        Toast.makeText(BillboardActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_music) {
            showMusicDialog();
            return true;
        } else if (item.getItemId() == R.id.action_notification) {
            showNotificationDialog();
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMusicDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Choose Music")
                .setItems(new String[]{"Music 1", "Music 2", "Music 3"}, (d, w) ->
                        Toast.makeText(this, "Selected: Music " + (w + 1), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage("You have no new notifications.")
                .setPositiveButton("OK", null)
                .show();
    }

    private static class UserStar {
        String username;
        int starCount;
        UserStar(String u, int s) { username = u; starCount = s; }
    }

    private class UserStarAdapter extends RecyclerView.Adapter<UserStarAdapter.ViewHolder> {
        private final List<UserStar> list;
        UserStarAdapter(List<UserStar> l) { this.list = l; }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup p, int vt) {
            return new ViewHolder(getLayoutInflater().inflate(R.layout.user_star_item, p, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder h, int pos) {
            UserStar us = list.get(pos);
            h.usernameText.setText(us.username);
            h.starCountText.setText(us.starCount + " star" + (us.starCount == 1 ? "" : "s"));
        }

        @Override
        public int getItemCount() { return list.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView usernameText, starCountText;
            ViewHolder(View v) {
                super(v);
                usernameText = v.findViewById(R.id.username_text);
                starCountText = v.findViewById(R.id.star_count_text);
            }
        }
    }
}