package com.example.healthapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class AwardsActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://b0978b2ad959.ngrok-free.app";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PASSWORD = "password";
    private static final String TAG = "AwardsActivity";
    private TextView starCount;
    private Map<String, Button> activityButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.award_screen);

        // Initialize Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Awards");
        }

        // Initialize Views
        starCount = findViewById(R.id.star_count);
        activityButtons = new HashMap<>();
        activityButtons.put("Ball", findViewById(R.id.starballButton));
        activityButtons.put("Running", findViewById(R.id.starrunningButton));
        activityButtons.put("Swimming", findViewById(R.id.starswimmingButton));
        activityButtons.put("Cycling", findViewById(R.id.starcyclingButton));
        activityButtons.put("Skipping", findViewById(R.id.starskippingButton));
        activityButtons.put("Yoga", findViewById(R.id.staryogaButton));
        activityButtons.put("Sprint", findViewById(R.id.starsprintButton));
        activityButtons.put("Weightlifting", findViewById(R.id.starweightliftingButton));
        activityButtons.put("Workout", findViewById(R.id.starworkoutButton));
        activityButtons.put("Climbing", findViewById(R.id.starclimbingButton));
        activityButtons.put("Gym", findViewById(R.id.stargymButton));
        activityButtons.put("Rehabilitation", findViewById(R.id.starrehabilitationButton));
        activityButtons.put("Entertainment", findViewById(R.id.starentertainmentButton));
        activityButtons.put("KeepFit", findViewById(R.id.starkeepfitButton));

        Button backButton = findViewById(R.id.starbackButton);
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(AwardsActivity.this, Dashboard.class);
            startActivity(intent);
            finish();
        });

        // Fetch Activity Counts
        fetchActivityCounts();
    }

    private void fetchActivityCounts() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");
        if (userId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "User ID or password not found. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        OkHttpClient client = TrustAllOkHttpClient.getClient();

        String url = BASE_URL + "/api/ActivityRecords/user/" + userId +
                "?requestingUserId=" + userId + "&requestingUserPassword=" + password;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Response: " + responseData);
                if (response.isSuccessful()) {
                    try {
                        // Parse records and compute distinct activity types
                        JSONArray records = new JSONArray(responseData);
                        Set<String> activitiesWithRecords = new HashSet<>();
                        Set<String> uniqueActivityTypes = new HashSet<>();

                        for (int i = 0; i < records.length(); i++) {
                            JSONObject record = records.getJSONObject(i);
                            String activityType = record.optString("activityType", "");
                            if (!activityType.isEmpty()) {
                                activitiesWithRecords.add(activityType);
                                uniqueActivityTypes.add(activityType);
                            } else {
                                Log.w(TAG, "Empty activity type in record");
                            }
                        }

                        // Prepare activityCounts array for UI update
                        JSONArray activityCountsArray = new JSONArray();
                        for (String activity : activityButtons.keySet()) {
                            JSONObject activityObj = new JSONObject();
                            activityObj.put("activity", activity);
                            activityObj.put("hasRecord", activitiesWithRecords.contains(activity) ? 1 : 0);
                            activityCountsArray.put(activityObj);
                        }

                        int distinctActivityCount = uniqueActivityTypes.size();
                        runOnUiThread(() -> {
                            starCount.setText(String.valueOf(distinctActivityCount));
                            for (int i = 0; i < activityCountsArray.length(); i++) {
                                try {
                                    JSONObject activity = activityCountsArray.getJSONObject(i);
                                    String activityName = activity.optString("activity", "");
                                    int hasRecord = activity.optInt("hasRecord", 0);
                                    Button button = activityButtons.get(activityName);
                                    if (button != null) {
                                        button.setAlpha(hasRecord == 1 ? 1.0f : 0.5f);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error parsing activity at index " + i, e);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON", e);
                        runOnUiThread(() -> {
                            Toast.makeText(AwardsActivity.this, "Error loading awards", Toast.LENGTH_SHORT).show();
                            starCount.setText("0");
                        });
                    }
                } else {
                    Log.e(TAG, "Request failed: " + response.code() + ", Body: " + responseData);
                    runOnUiThread(() -> {
                        Toast.makeText(AwardsActivity.this, "Failed to load awards: HTTP " + response.code(), Toast.LENGTH_SHORT).show();
                        starCount.setText("0");
                    });
                }
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(AwardsActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    starCount.setText("0");
                });
            }
        });
    }
}