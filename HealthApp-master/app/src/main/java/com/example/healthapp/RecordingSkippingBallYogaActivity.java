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

public class RecordingSkippingBallYogaActivity extends AppCompatActivity implements SensorEventListener {

    private TextView timerText, heartRateText;
    private Button startButton, stopButton, addButton, backButton, submitButton;
    private LinearLayout workoutSetsContainer;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Toolbar toolbar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private boolean isTimerRunning = false;
    private ArrayList<Spinner> spinners = new ArrayList<>();
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://a267815f7908.ngrok-free.app/api/ActivityRecords";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";

    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            long elapsedTime = System.currentTimeMillis() - startTime;
            updateTimerText(elapsedTime);
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingskippingballyoga_screen);

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
        if (heartRateSensor == null) {
            heartRateText.setText("Heart Rate: Not Available");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart Rate: Permission Denied");
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Skipping/Ball/Yoga Record");
        }

        setupSpinner(findViewById(R.id.ball_type_spinner_0));
        spinners.add(findViewById(R.id.ball_type_spinner_0));

        startButton.setOnClickListener(v -> {
            if (!isTimerRunning) {
                startTime = System.currentTimeMillis();
                handler.post(updateTimer);
                isTimerRunning = true;
                startButton.setEnabled(false);
                stopButton.setEnabled(true);
                if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                        == PackageManager.PERMISSION_GRANTED) {
                    sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI);
                }
            }
        });

        stopButton.setOnClickListener(v -> {
            if (isTimerRunning) {
                handler.removeCallbacks(updateTimer);
                isTimerRunning = false;
                startButton.setEnabled(true);
                stopButton.setEnabled(false);
                sensorManager.unregisterListener(this);
                heartRateText.setText("Heart Rate: -- BPM");
            }
        });

        addButton.setOnClickListener(v -> addNewSpinner());

        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));

        backButton.setOnClickListener(v -> finish());

        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void setupSpinner(Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.skippingballyoga_types,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addNewSpinner() {
        LinearLayout spinnerContainer = new LinearLayout(this);
        spinnerContainer.setOrientation(LinearLayout.HORIZONTAL);
        spinnerContainer.setPadding(8, 8, 8, 8);

        Spinner newSpinner = new Spinner(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                50,
                1.0f
        );
        newSpinner.setLayoutParams(params);
        newSpinner.setId(View.generateViewId());
        setupSpinner(newSpinner);

        spinnerContainer.addView(newSpinner);
        workoutSetsContainer.addView(spinnerContainer);
        spinners.add(newSpinner);
    }

    private void updateTimerText(long elapsedTime) {
        long hours = TimeUnit.MILLISECONDS.toHours(elapsedTime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) % 60;
        timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
    }

    private void selectMood(String mood, ImageView selected, ImageView... others) {
        selectedMood = mood;
        selected.setAlpha(1.0f);
        for (ImageView other : others) {
            other.setAlpha(0.5f);
        }
    }

    private String mapExerciseToActivityType(String exercise) {
        if (exercise == null || exercise.isEmpty() || exercise.equals("Select sport")) {
            return null; // Return null for invalid selections to trigger validation
        }
        switch (exercise.toLowerCase()) {
            case "basketball":
            case "football":
            case "volleyball":
            case "badminton":
            case "table tennis":
            case "tennis":
                return "Ball";
            case "skipping":
                return "Skipping";
            case "yoga":
                return "Yoga";
            default:
                Log.w("RecordingSkippingBallYoga", "Unknown exercise: " + exercise);
                return null; // Invalid exercise
        }
    }

    private void submitRecord() {
        String time = timerText.getText().toString();
        if ("00:00:00".equals(time) || selectedMood == null) {
            Toast.makeText(this, "Complete timer and mood!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (userId.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        // Collect exercises from spinners
        List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
        for (Spinner spinner : spinners) {
            String selected = spinner.getSelectedItem().toString();
            String activityType = mapExerciseToActivityType(selected);
            if (activityType != null) {
                entries.add(new ExerciseFormatter.ExerciseEntry(
                        selected, activityType, "Indoor/Outdoor", ""
                ));
            }
        }

        if (entries.isEmpty()) {
            Toast.makeText(this, "Select at least one valid sport!", Toast.LENGTH_SHORT).show();
            return;
        }

        String primaryActivity = mapExerciseToActivityType(spinners.get(0).getSelectedItem().toString());

        try {
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", primaryActivity);
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

            RequestBody body = RequestBody.create(payload.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingSkippingBallYogaActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String resp = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingSkippingBallYogaActivity.this, "Saved!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingSkippingBallYogaActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> Toast.makeText(RecordingSkippingBallYogaActivity.this,
                                    "Failed: " + resp, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingSkippingBallYogaActivity.this,
                                "Error", Toast.LENGTH_LONG).show());
                    } finally {
                        response.close();
                    }
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_music) {
            showMusicDialog();
            return true;
        } else if (id == R.id.action_notification) {
            showNotificationDialog();
            return true;
        } else if (id == R.id.action_logout) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove(KEY_USER_ID);
            editor.apply();
            Intent intent = new Intent(this, Login.class);
            startActivity(intent);
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

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            currentHeartRate = event.values[0];
            heartRateText.setText(String.format("Heart Rate: %.0f BPM", currentHeartRate));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle accuracy changes if needed
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimer);
        sensorManager.unregisterListener(this);
    }
}