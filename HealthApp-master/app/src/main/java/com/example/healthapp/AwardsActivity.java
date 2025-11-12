package com.example.healthapp;

import static com.example.healthapp.TrustAllOkHttpClient.getClient;

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
    private static final String BASE_URL = "https://1c05e1adb0b1.ngrok-free.app";
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
            Toast.makeText(this, "Login required", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        String url = BASE_URL + "/api/ActivityRecords/user/" + userId +
                "?requestingUserId=" + userId + "&requestingUserPassword=" + password;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        TrustAllOkHttpClient.getClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(AwardsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                    starCount.setText("0");
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Response: " + body);

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(AwardsActivity.this, "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                        starCount.setText("0");
                    });
                    return;
                }

                try {
                    JSONArray records = new JSONArray(body);
                    Set<String> uniqueActivities = new HashSet<>();

                    for (int i = 0; i < records.length(); i++) {
                        JSONObject r = records.getJSONObject(i);
                        String type = r.optString("activityType", r.optString("ActivityType", ""));
                        if (!type.isEmpty()) {
                            uniqueActivities.add(type);
                        }
                    }

                    int starCountValue = uniqueActivities.size();

                    runOnUiThread(() -> {
                        starCount.setText(String.valueOf(starCountValue));
                        for (Map.Entry<String, Button> entry : activityButtons.entrySet()) {
                            Button btn = entry.getValue();
                            if (btn != null) {
                                btn.setAlpha(uniqueActivities.contains(entry.getKey()) ? 1.0f : 0.5f);
                            }
                        }
                    });

                } catch (Exception e) {
                    Log.e(TAG, "Parse error", e);
                    runOnUiThread(() -> {
                        Toast.makeText(AwardsActivity.this, "Parse error", Toast.LENGTH_SHORT).show();
                        starCount.setText("0");
                    });
                }
            }
        });
    }
}