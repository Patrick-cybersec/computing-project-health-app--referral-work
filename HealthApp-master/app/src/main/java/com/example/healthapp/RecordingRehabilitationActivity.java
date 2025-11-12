package com.example.healthapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.healthapp.utils.SharedPrefsHelper;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RecordingRehabilitationActivity extends AppCompatActivity implements SensorEventListener {

    private TextView timerText, heartRateText;
    private Button startButton, stopButton, backButton, submitButton, addButton;
    private ImageView moodHappy, moodNeutral, moodSad;
    private LinearLayout rehabilitationContainer;
    private Toolbar toolbar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private ArrayList<Spinner> exerciseSpinners = new ArrayList<>();
    private OkHttpClient client;

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://1c05e1adb0b1.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = System.currentTimeMillis() - startTime;
                int hours = (int) TimeUnit.MILLISECONDS.toHours(elapsed);
                int minutes = (int) (TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60);
                int seconds = (int) (TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60);
                timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingrehabilitation_screen);

        client = TrustAllOkHttpClient.getClient();
        requestPermissions();

        toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);
        addButton = findViewById(R.id.add_button);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);
        rehabilitationContainer = findViewById(R.id.rehabilitation_container);

        Spinner firstSpinner = findViewById(R.id.rehabilitation_spinner_0);
        setupSpinner(firstSpinner);
        exerciseSpinners.add(firstSpinner);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) {
            heartRateText.setText("Heart rate: Not available");
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Rehabilitation Record");
        }

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        addButton.setOnClickListener(v -> addNewSpinner());
        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));
        backButton.setOnClickListener(v -> finishActivity());
        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            handler.post(updateTimer);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }
    }

    private void stopTimer() {
        if (isTimerRunning) {
            handler.removeCallbacks(updateTimer);
            isTimerRunning = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            sensorManager.unregisterListener(this);
            heartRateText.setText("Heart rate: -- BPM");
        }
    }

    private void finishActivity() {
        clearFocusAndHideIME();
        startActivity(new Intent(this, Dashboard.class));
        finish();
    }

    private void clearFocusAndHideIME() {
        View current = getCurrentFocus();
        if (current != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(current.getWindowToken(), 0);
            current.clearFocus();
        }
    }

    private void setupSpinner(Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.rehabilitation, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addNewSpinner() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(8, 8, 8, 8);

        Spinner newSpinner = new Spinner(this);
        newSpinner.setId(View.generateViewId());
        newSpinner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        setupSpinner(newSpinner);

        layout.addView(newSpinner);
        rehabilitationContainer.addView(layout);
        exerciseSpinners.add(newSpinner);
    }

    private void selectMood(String mood, ImageView selected, ImageView... others) {
        selectedMood = mood;
        selected.setAlpha(1.0f);
        for (ImageView img : others) {
            img.setAlpha(0.5f);
        }
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
                    .url("https://1c05e1adb0b1.ngrok-free.app/api/ActivityRecords")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingRehabilitationActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingRehabilitationActivity.this,
                                        "Record saved successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingRehabilitationActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            Log.e("SUBMIT", "Failed: " + response.code() + " | " + responseBody);
                            runOnUiThread(() -> Toast.makeText(RecordingRehabilitationActivity.this,
                                    "Failed: " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingRehabilitationActivity.this,
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

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BODY_SENSORS}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart rate: -- BPM");
        }
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE && event.values[0] > 0) {
            currentHeartRate = event.values[0];
            heartRateText.setText(String.format("Heart rate: %.0f BPM", currentHeartRate));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_USER_ID).apply();
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMusicDialog() {
        new AlertDialog.Builder(this).setTitle("Choose music")
                .setItems(new String[]{"Music 1", "Music 2", "Music 3"}, (d, w) ->
                        Toast.makeText(this, "Selected: Music " + (w + 1), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(this).setTitle("Notifications")
                .setMessage("No new notifications.").setPositiveButton("OK", null).show();
    }

    @Override protected void onPause() { super.onPause(); clearFocusAndHideIME(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimer);
        if (sensorManager != null) sensorManager.unregisterListener(this);
    }
}