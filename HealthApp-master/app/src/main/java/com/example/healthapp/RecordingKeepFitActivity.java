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
import androidx.core.content.ContextCompat;
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

public class RecordingKeepFitActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://1c05e1adb0b1.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private TextView timerText, heartRateText;
    private Button startButton, stopButton, addButton, backButton, submitButton;
    private LinearLayout workoutSetsContainer;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Toolbar toolbar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;

    private final ArrayList<Spinner> pushupSpinners = new ArrayList<>();
    private final ArrayList<Spinner> squatSpinners = new ArrayList<>();
    private final ArrayList<Spinner> situpSpinners = new ArrayList<>();

    private final Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = System.currentTimeMillis() - startTime;
                int h = (int) TimeUnit.MILLISECONDS.toHours(elapsed);
                int m = (int) (TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60);
                int s = (int) (TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60);
                timerText.setText(String.format("%02d:%02d:%02d", h, m, s));
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.keepfit_screen);

        client = TrustAllOkHttpClient.getClient();

        toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        addButton = findViewById(R.id.add_button);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);
        workoutSetsContainer = findViewById(R.id.workout_sets_container);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) heartRateText.setText("Heart rate: Not available");

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Keep Fit Record");

        Spinner s1 = findViewById(R.id.noofsets1_spinner_0);
        Spinner s2 = findViewById(R.id.noofsets2_spinner_0);
        Spinner s3 = findViewById(R.id.noofsets3_spinner_0);
        setupSpinner(s1); setupSpinner(s2); setupSpinner(s3);
        pushupSpinners.add(s1); squatSpinners.add(s2); situpSpinners.add(s3);

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        addButton.setOnClickListener(v -> addNewSet());
        backButton.setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> submitRecord());

        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            handler.post(updateTimer);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI);
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

    private void setupSpinner(Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.noofsets, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addNewSet() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(8, 8, 8, 8);

        Spinner pushup = createSpinner();
        Spinner squat = createSpinner();
        Spinner situp = createSpinner();

        container.addView(pushup);
        container.addView(squat);
        container.addView(situp);

        workoutSetsContainer.addView(container);
        pushupSpinners.add(pushup);
        squatSpinners.add(squat);
        situpSpinners.add(situp);
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(this);
        spinner.setId(View.generateViewId());
        spinner.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        setupSpinner(spinner);
        return spinner;
    }

    private void selectMood(String mood, ImageView selected, ImageView... others) {
        selectedMood = mood;
        selected.setAlpha(1.0f);
        for (ImageView img : others) img.setAlpha(0.5f);
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
                    runOnUiThread(() -> Toast.makeText(RecordingKeepFitActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingKeepFitActivity.this,
                                        "Record saved successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingKeepFitActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            Log.e("SUBMIT", "Failed: " + response.code() + " | " + responseBody);
                            runOnUiThread(() -> Toast.makeText(RecordingKeepFitActivity.this,
                                    "Failed: " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingKeepFitActivity.this,
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

    private boolean addEntry(Spinner spinner, String type, List<ExerciseFormatter.ExerciseEntry> list) {
        String val = spinner.getSelectedItem().toString();
        if ("Select no. of set".equals(val) || "0".equals(val)) return false;
        int reps = Integer.parseInt(val) * 5;
        list.add(new ExerciseFormatter.ExerciseEntry(type, "Reps", String.valueOf(reps), "reps"));
        return true;
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
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimer);
        sensorManager.unregisterListener(this);
    }
}