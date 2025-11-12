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

public class RecordingWorkoutActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://1c05e1adb0b1.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // UI
    private TextView timerText, heartRateText;
    private Button startButton, stopButton, addButton, backButton, submitButton;
    private LinearLayout workoutSetsContainer;
    private ImageView moodHappy, moodNeutral, moodSad;

    // Timer / Sensors
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;

    // Network
    private OkHttpClient client;

    // Spinners (type + reps)
    private final ArrayList<Spinner> typeSpinners = new ArrayList<>();
    private final ArrayList<Spinner> repSpinners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingworkout_screen);

        client = TrustAllOkHttpClient.getClient();
        requestHeartRatePermission();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        addButton = findViewById(R.id.add_button);
        workoutSetsContainer = findViewById(R.id.workout_sets_container);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) {
            heartRateText.setText("Heart Rate: Not Available");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart Rate: Permission Denied");
        }

        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // initial pair (layout already contains them)
        Spinner type0 = findViewById(R.id.workout_type_spinner_0);
        Spinner rep0  = findViewById(R.id.repetition_spinner_0);
        setupSpinner(type0, R.array.workout_types);
        setupSpinner(rep0,  R.array.repetitions);
        typeSpinners.add(type0);
        repSpinners.add(rep0);

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        addButton.setOnClickListener(v -> addNewSpinnerPair());

        moodHappy.setOnClickListener(v -> selectMood("Happy"));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral"));
        moodSad.setOnClickListener(v -> selectMood("Sad"));

        backButton.setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> submitRecord());
    }

    /* ------------------------------------------------- PERMISSIONS ------------------------------------------------- */
    private void requestHeartRatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BODY_SENSORS}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Heart-rate permission granted", Toast.LENGTH_SHORT).show();
        } else if (requestCode == 100) {
            Toast.makeText(this, "Heart-rate permission denied", Toast.LENGTH_SHORT).show();
            heartRateText.setText("Heart Rate: Permission Denied");
        }
    }

    /* ------------------------------------------------- SPINNER ------------------------------------------------- */
    private void setupSpinner(Spinner s, int arrayRes) {
        ArrayAdapter<CharSequence> a = ArrayAdapter.createFromResource(this,
                arrayRes, android.R.layout.simple_spinner_item);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
    }

    private void addNewSpinnerPair() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 8, 8, 8);

        Spinner type = new Spinner(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 50, 1f);
        type.setLayoutParams(lp);
        type.setId(View.generateViewId());
        setupSpinner(type, R.array.workout_types);

        Spinner rep = new Spinner(this);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, 50, 1f);
        lp2.setMarginStart(8);
        rep.setLayoutParams(lp2);
        rep.setId(View.generateViewId());
        setupSpinner(rep, R.array.repetitions);

        row.addView(type);
        row.addView(rep);
        workoutSetsContainer.addView(row);
        typeSpinners.add(type);
        repSpinners.add(rep);
    }

    /* ------------------------------------------------- TIMER ------------------------------------------------- */
    private void startTimer() {
        if (isTimerRunning) return;
        startTime = System.currentTimeMillis();
        isTimerRunning = true;
        timerHandler.postDelayed(timerRunnable, 1000);
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        if (heartRateSensor != null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private void stopTimer() {
        if (!isTimerRunning) return;
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        sensorManager.unregisterListener(this);
        heartRateText.setText("Heart Rate: -- BPM");
    }

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTimerRunning) return;
            long elapsed = System.currentTimeMillis() - startTime;
            int s = (int) (elapsed / 1000) % 60;
            int m = (int) (elapsed / 60000) % 60;
            int h = (int) (elapsed / 3600000) % 24;
            timerText.setText(String.format("%02d:%02d:%02d", h, m, s));
            timerHandler.postDelayed(this, 1000);
        }
    };

    /* ------------------------------------------------- MOOD ------------------------------------------------- */
    private void selectMood(String mood) {
        selectedMood = mood;
        moodHappy.setAlpha(mood.equals("Happy") ? 1f : 0.5f);
        moodNeutral.setAlpha(mood.equals("Neutral") ? 1f : 0.5f);
        moodSad.setAlpha(mood.equals("Sad") ? 1f : 0.5f);
    }

    /* ------------------------------------------------- SUBMIT ------------------------------------------------- */
    private void submitRecord() {
        String time = timerText.getText().toString();
        if ("00:00:00".equals(time) || selectedMood == null) {
            Toast.makeText(this, "Complete timer and mood!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString("password", "");
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (userId.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // ---- Build Exercises ----
        List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
        for (int i = 0; i < typeSpinners.size(); i++) {
            String type = typeSpinners.get(i).getSelectedItem().toString();
            String reps = repSpinners.get(i).getSelectedItem().toString();
            if (!type.equals("Select exercise") && !reps.equals("Select repetition")) {
                entries.add(new ExerciseFormatter.ExerciseEntry(
                        type, "Workout", "Gym", reps + " reps"));
            }
        }
        if (entries.isEmpty()) {
            Toast.makeText(this, "Add at least one workout!", Toast.LENGTH_SHORT).show();
            return;
        }

        // ---- JSON payload ----
        try {
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "Workout");
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0);
            record.put("Mood", selectedMood);
            record.put("Duration", time);
            record.put("Exercises", ExerciseFormatter.formatExercises(entries));

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
                    runOnUiThread(() -> Toast.makeText(RecordingWorkoutActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String resp = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingWorkoutActivity.this,
                                        "Record saved successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingWorkoutActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            Log.e("SUBMIT", "Failed: " + response.code() + " | " + resp);
                            runOnUiThread(() -> Toast.makeText(RecordingWorkoutActivity.this,
                                    "Failed: " + resp, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception ex) {
                        runOnUiThread(() -> Toast.makeText(RecordingWorkoutActivity.this,
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

    /* ------------------------------------------------- HEART RATE ------------------------------------------------- */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0];
            heartRateText.setText(String.format("Heart Rate: %.0f BPM", currentHeartRate));
        }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /* ------------------------------------------------- MENU ------------------------------------------------- */
    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_music) { showMusicDialog(); return true; }
        if (id == R.id.action_notification) { showNotificationDialog(); return true; }
        if (id == R.id.action_logout) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .remove(KEY_USER_ID).apply();
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMusicDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Choose Music")
                .setItems(new String[]{"Music 1", "Music 2", "Music 3"},
                        (d, w) -> Toast.makeText(this, "Selected: Music " + (w + 1), Toast.LENGTH_SHORT).show())
                .show();
    }

    private void showNotificationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Notifications")
                .setMessage("You have no new notifications.")
                .setPositiveButton("OK", null)
                .show();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        sensorManager.unregisterListener(this);
    }
}