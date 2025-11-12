package com.example.healthapp;

import android.Manifest;
import android.content.Context;
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
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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

public class RecordingGymWeighingActivity extends AppCompatActivity implements SensorEventListener {

    /* ---------------------- CONSTANTS ---------------------- */
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://a267815f7908.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /* ---------------------- UI ---------------------- */
    private TextView timerText, totalWorkloadValue, heartRateText;
    private Button startButton, stopButton, backButton, submitButton;
    private EditText weightInput, repetitionInput;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Spinner spActivity;               // <-- ONE field for the spinner

    /* ---------------------- TIMER & SENSOR ---------------------- */
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;

    /* ---------------------- LIFECYCLE ---------------------- */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordinggymweighing_screen);

        client = TrustAllOkHttpClient.getClient();
        requestHeartRatePermission();

        /* ----- findViewById ----- */
        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        weightInput = findViewById(R.id.weight_input);
        repetitionInput = findViewById(R.id.repetition_input);
        totalWorkloadValue = findViewById(R.id.total_workload_value);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);

        /* ----- Spinner (ONE TIME) ----- */
        spActivity = findViewById(R.id.sp_activity_type);
        if (spActivity != null) {
            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                    this,
                    R.array.activity_types,
                    android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spActivity.setAdapter(adapter);
        }

        /* ----- Sensor ----- */
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) {
            heartRateText.setText("Heart rate: Not available");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart rate: Permission denied");
        }

        /* ----- Toolbar ----- */
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        /* ----- Input focus handling ----- */
        setupFocusListener(weightInput);
        setupFocusListener(repetitionInput);

        /* ----- Buttons ----- */
        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());

        /* ----- Workload calculator ----- */
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calculateWorkload(); }
        };
        weightInput.addTextChangedListener(watcher);
        repetitionInput.addTextChangedListener(watcher);

        /* ----- Mood ----- */
        moodHappy.setOnClickListener(v -> selectMood("Happy"));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral"));
        moodSad.setOnClickListener(v -> selectMood("Sad"));

        backButton.setOnClickListener(v -> { clearFocusAndHideIME(); finish(); });
        submitButton.setOnClickListener(v -> { clearFocusAndHideIME(); submitRecord(); });
    }

    /* ---------------------- UI HELPERS ---------------------- */
    private void clearFocusAndHideIME() {
        View focus = getCurrentFocus();
        if (focus != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void setupFocusListener(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });
    }

    /* ---------------------- TIMER ---------------------- */
    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            timerHandler.postDelayed(timerRunnable, 1000);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                            == PackageManager.PERMISSION_GRANTED) {
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
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    /* ---------------------- WORKLOAD ---------------------- */
    private void calculateWorkload() {
        try {
            double weight = weightInput.getText().toString().isEmpty()
                    ? 0 : Double.parseDouble(weightInput.getText().toString());
            int reps = repetitionInput.getText().toString().isEmpty()
                    ? 0 : Integer.parseInt(repetitionInput.getText().toString());
            totalWorkloadValue.setText(String.format("%.2f kg", weight * reps));
        } catch (NumberFormatException e) {
            totalWorkloadValue.setText("0 kg");
        }
    }

    /* ---------------------- MOOD ---------------------- */
    private void selectMood(String mood) {
        selectedMood = mood;
        moodHappy.setAlpha(mood.equals("Happy") ? 1.0f : 0.5f);
        moodNeutral.setAlpha(mood.equals("Neutral") ? 1.0f : 0.5f);
        moodSad.setAlpha(mood.equals("Sad") ? 1.0f : 0.5f);
    }

    /* ---------------------- SUBMIT ---------------------- */
    private void submitRecord() {
        String time = timerText.getText().toString();
        if ("00:00:00".equals(time)) {
            Toast.makeText(this, "Please start and stop the timer!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedMood == null) {
            Toast.makeText(this, "Please select your mood!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");
        boolean isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false);

        if (userId.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        /* ---------- SPINNER VALIDATION (ENFORCED) ---------- */
        if (spActivity == null) {
            Toast.makeText(this, "Activity type not loaded!", Toast.LENGTH_SHORT).show();
            return;
        }

        Object selectedItem = spActivity.getSelectedItem();
        if (selectedItem == null) {
            Toast.makeText(this, "Please select an activity type!", Toast.LENGTH_SHORT).show();
            return;
        }

        String activityType = selectedItem.toString();
        if (activityType.equals(getString(R.string.select_activity_prompt))) {
            Toast.makeText(this, "Please choose a valid activity type!", Toast.LENGTH_LONG).show();
            spActivity.requestFocus(); // Optional: highlight spinner
            return;
        }

        /* ---------- EXERCISE INPUT VALIDATION ---------- */
        String weight = weightInput.getText().toString().trim();
        String reps = repetitionInput.getText().toString().trim();
        if (weight.isEmpty() || reps.isEmpty()) {
            Toast.makeText(this, "Please enter weight and reps!", Toast.LENGTH_SHORT).show();
            return;
        }

        /* ---------- BUILD EXERCISES ---------- */
        List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
        try {
            double w = Double.parseDouble(weight);
            int r = Integer.parseInt(reps);
            if (w > 0 && r > 0) {
                entries.add(new ExerciseFormatter.ExerciseEntry(
                        "Weight Lifting", "Weight", w + "kg", r + " reps"));
            } else {
                Toast.makeText(this, "Weight and reps must be positive!", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid weight or reps!", Toast.LENGTH_SHORT).show();
            return;
        }

        String exercises = ExerciseFormatter.formatExercises(entries);

        /* ---------- SEND TO SERVER ---------- */
        try {
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", activityType);
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0);
            record.put("Mood", selectedMood);
            record.put("Duration", time);
            record.put("Exercises", exercises);

            JSONObject payload = new JSONObject();
            if (isAdmin) {
                payload.put("AdminId", userId);
                payload.put("AdminPassword", password);
            } else {
                payload.put("userId", userId);
                payload.put("userPassword", password);
            }
            payload.put("Record", record);

            Log.d("SUBMIT", "Sending: " + payload.toString());

            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingGymWeighingActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String resp = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingGymWeighingActivity.this,
                                        "Record saved as " + activityType + "!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingGymWeighingActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(RecordingGymWeighingActivity.this,
                                    "Server error: " + resp, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingGymWeighingActivity.this,
                                "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "JSON error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /* ---------------------- HEART-RATE ---------------------- */
    private void requestHeartRatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BODY_SENSORS}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Heart rate permission granted", Toast.LENGTH_SHORT).show();
                heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heartRateSensor == null) heartRateText.setText("Heart rate: Not available");
            } else {
                Toast.makeText(this, "Heart rate permission denied", Toast.LENGTH_SHORT).show();
                heartRateText.setText("Heart rate: Permission denied");
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0];
            heartRateText.setText(String.format("Heart rate: %.0f BPM", currentHeartRate));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /* ---------------------- MENU ---------------------- */
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
            prefs.edit().remove(KEY_USER_ID).apply();
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
                        (d, w) -> Toast.makeText(this, "Selected: Music " + (w + 1), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage("No new notifications.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override protected void onPause() { super.onPause(); clearFocusAndHideIME(); }
    @Override protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        sensorManager.unregisterListener(this);
    }
}