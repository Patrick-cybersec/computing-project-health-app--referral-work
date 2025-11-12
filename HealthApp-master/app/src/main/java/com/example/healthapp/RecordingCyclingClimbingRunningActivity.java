package com.example.healthapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecordingCyclingClimbingRunningActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://a267815f7908.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private TextView timerText, heartRateText;
    private Button startButton, stopButton, backButton, submitButton;
    private Spinner activitySpinner;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingcyclingclimbingrunning_screen);

        client = TrustAllOkHttpClient.getClient();
        requestHeartRatePermission();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        activitySpinner = findViewById(R.id.cyclingclimbingrunning_spinner_0);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) {
            heartRateText.setText("Heart rate: Not available");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart rate: Permission denied");
        }

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        startButton.setOnClickListener(this::startTimer);
        stopButton.setOnClickListener(v -> stopTimer());
        moodHappy.setOnClickListener(v -> selectMood("Happy"));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral"));
        moodSad.setOnClickListener(v -> selectMood("Sad"));
        backButton.setOnClickListener(v -> { clearFocusAndHideIME(); finish(); });
        submitButton.setOnClickListener(v -> { clearFocusAndHideIME(); submitRecord(); });
    }

    private void clearFocusAndHideIME() {
        View focus = getCurrentFocus();
        if (focus != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void startTimer(View v) {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            timerHandler.postDelayed(timerRunnable, 1000);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    private void stopTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            timerHandler.removeCallbacks(timerRunnable);
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            sensorManager.unregisterListener(this);
            heartRateText.setText("Heart rate: -- BPM");
        }
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = System.currentTimeMillis() - startTime;
                int h = (int) (elapsed / 3600000) % 24;
                int m = (int) (elapsed / 60000) % 60;
                int s = (int) (elapsed / 1000) % 60;
                timerText.setText(String.format("%02d:%02d:%02d", h, m, s));
                timerHandler.postDelayed(this, 1000);  // 'this' refers to Runnable
            }
        }
    };

    private void selectMood(String mood) {
        selectedMood = mood;
        moodHappy.setAlpha("Happy".equals(mood) ? 1.0f : 0.5f);
        moodNeutral.setAlpha("Neutral".equals(mood) ? 1.0f : 0.5f);
        moodSad.setAlpha("Sad".equals(mood) ? 1.0f : 0.5f);
    }

    private void submitRecord() {
        String time = timerText.getText().toString();
        if ("00:00:00".equals(time) || selectedMood == null) {
            Toast.makeText(this, "Complete timer and mood!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("HealthAppPrefs", MODE_PRIVATE);
        String userId = prefs.getString("userId", "");
        String password = prefs.getString("password", "");
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (userId.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        try {
            // Build your exercises here (example)
            List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
            entries.add(new ExerciseFormatter.ExerciseEntry("Running", "Type", "Outdoor", "style"));
            String exercises = ExerciseFormatter.formatExercises(entries);

            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "Running");
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0);
            record.put("Mood", selectedMood);
            record.put("Duration", time);
            record.put("Exercises", exercises);

            JSONObject payload = new JSONObject();

            // THIS IS THE KEY FIX:
            if (isAdmin) {
                payload.put("AdminId", userId);
                payload.put("AdminPassword", password);
            } else {
                payload.put("userId", userId);
                payload.put("userPassword", password);
            }
            payload.put("Record", record);

            Log.d("SUBMIT", "Sending: " + payload.toString());

            RequestBody body = RequestBody.create(payload.toString(), MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url("https://a267815f7908.ngrok-free.app/api/ActivityRecords")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingCyclingClimbingRunningActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingCyclingClimbingRunningActivity.this,
                                        "Record saved successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingCyclingClimbingRunningActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            Log.e("SUBMIT", "Failed: " + response.code() + " | " + responseBody);
                            runOnUiThread(() -> Toast.makeText(RecordingCyclingClimbingRunningActivity.this,
                                    "Failed: " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingCyclingClimbingRunningActivity.this,
                                "Error reading response", Toast.LENGTH_LONG).show());
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void requestHeartRatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Heart rate permission granted", Toast.LENGTH_SHORT).show();
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
            if (heartRateSensor == null) heartRateText.setText("Heart rate: Not available");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE && event.accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) {
            currentHeartRate = event.values[0];
            heartRateText.setText(String.format("Heart rate: %.0f BPM", currentHeartRate));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_music) { showMusicDialog(); return true; }
        if (id == R.id.action_notification) { showNotificationDialog(); return true; }
        if (id == R.id.action_logout) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            Editor editor = prefs.edit();
            editor.remove(KEY_USER_ID);
            editor.apply();
            clearFocusAndHideIME();
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMusicDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Choose music")
                .setItems(new String[]{"Music 1", "Music 2", "Music 3"},
                        (dialog, which) -> Toast.makeText(this, "Selected: Music " + (which + 1), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage("No new notifications.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearFocusAndHideIME();
        if (isTimerRunning) stopTimer();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        sensorManager.unregisterListener(this);
    }
}