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
import java.util.Collections;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class BillboardActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://b0978b2ad959.ngrok-free.app";
    private static final String TAG = "BillboardActivity";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
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

        // Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Billboard");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize RecyclerView and No Data Text
        recyclerView = findViewById(R.id.recycler_view);
        noDataText = findViewById(R.id.no_data_text);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        userStarList = new ArrayList<>();
        adapter = new UserStarAdapter(userStarList);
        recyclerView.setAdapter(adapter);

        // Fetch User Star Counts
        fetchUserStars();
    }

    private void fetchUserStars() {
        String userId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USER_ID, "");
        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        Request request = new Request.Builder()
                .url(BASE_URL + "/api/Users/stars")
                .get()
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Response: " + responseData);
                if (response.isSuccessful()) {
                    try {
                        JSONArray jsonArray = new JSONArray(responseData);
                        List<UserStar> newList = new ArrayList<>();
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject json = jsonArray.getJSONObject(i);
                            String username = json.optString("username", "Unknown");
                            int starCount = json.optInt("starCount", 0);
                            if (starCount > 0) {
                                newList.add(new UserStar(username, starCount));
                            }
                        }
                        // Sort in descending order
                        // Note: starCount should reflect 14 distinct activity types as per activity_types array
                        Collections.sort(newList, (o1, o2) -> Integer.compare(o2.starCount, o1.starCount));
                        runOnUiThread(() -> {
                            userStarList.clear();
                            userStarList.addAll(newList);
                            adapter.notifyDataSetChanged();
                            if (newList.isEmpty()) {
                                recyclerView.setVisibility(View.GONE);
                                if (noDataText != null) {
                                    noDataText.setVisibility(View.VISIBLE);
                                }
                                Toast.makeText(BillboardActivity.this, "No leaderboard data available", Toast.LENGTH_SHORT).show();
                            } else {
                                recyclerView.setVisibility(View.VISIBLE);
                                if (noDataText != null) {
                                    noDataText.setVisibility(View.GONE);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON", e);
                        runOnUiThread(() -> {
                            recyclerView.setVisibility(View.GONE);
                            if (noDataText != null) {
                                noDataText.setVisibility(View.VISIBLE);
                            }
                            Toast.makeText(BillboardActivity.this, "Error loading leaderboard", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    Log.e(TAG, "Request failed: " + response.code());
                    runOnUiThread(() -> {
                        recyclerView.setVisibility(View.GONE);
                        if (noDataText != null) {
                            noDataText.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(BillboardActivity.this, "Failed to load leaderboard: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                runOnUiThread(() -> {
                    recyclerView.setVisibility(View.GONE);
                    if (noDataText != null) {
                        noDataText.setVisibility(View.VISIBLE);
                    }
                    Toast.makeText(BillboardActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
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
        int itemId = item.getItemId();
        if (itemId == R.id.action_music) {
            showMusicDialog();
            return true;
        } else if (itemId == R.id.action_notification) {
            showNotificationDialog();
            return true;
        } else if (itemId == R.id.action_logout) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().clear().apply();
            Intent intent = new Intent(BillboardActivity.this, Login.class);
            startActivity(intent);
            finish();
            return true;
        } else if (itemId == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMusicDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Music")
                .setItems(new String[]{"Music 1", "Music 2", "Music 3"}, (dialog, which) -> {
                    Toast.makeText(this, "Selected: Music " + (which + 1), Toast.LENGTH_SHORT).show();
                });
        builder.create().show();
    }

    private void showNotificationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notifications")
                .setMessage("You have no new notifications.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.create().show();
    }

    // Data class for user stars
    private static class UserStar {
        String username;
        int starCount;

        UserStar(String username, int starCount) {
            this.username = username;
            this.starCount = starCount;
        }
    }

    // RecyclerView Adapter
    private class UserStarAdapter extends RecyclerView.Adapter<UserStarAdapter.ViewHolder> {
        private List<UserStar> userStars;

        UserStarAdapter(List<UserStar> userStars) {
            this.userStars = userStars;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.user_star_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            UserStar userStar = userStars.get(position);
            holder.usernameText.setText(userStar.username);
            holder.starCountText.setText(userStar.starCount + " star" + (userStar.starCount != 1 ? "s" : ""));
        }

        @Override
        public int getItemCount() {
            return userStars.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView usernameText;
            TextView starCountText;

            ViewHolder(View itemView) {
                super(itemView);
                usernameText = itemView.findViewById(R.id.username_text);
                starCountText = itemView.findViewById(R.id.star_count_text);
            }
        }
    }
}