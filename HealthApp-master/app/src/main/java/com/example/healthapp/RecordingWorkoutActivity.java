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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
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

public class RecordingWorkoutActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String API_URL = "https://b0978b2ad959.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private TextView timerText, heartRateText;
    private Button startButton, stopButton, backButton, submitButton, addButton;
    private LinearLayout workoutSetsContainer;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;
    private ArrayList<Spinner> workoutTypeSpinners = new ArrayList<>();
    private ArrayList<Spinner> repetitionSpinners = new ArrayList<>();

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

        // Initialize spinners
        Spinner workoutTypeSpinner = findViewById(R.id.workout_type_spinner_0);
        Spinner repetitionSpinner = findViewById(R.id.repetition_spinner_0);
        setupSpinner(workoutTypeSpinner, R.array.workout_types);
        setupSpinner(repetitionSpinner, R.array.repetitions);
        workoutTypeSpinners.add(workoutTypeSpinner);
        repetitionSpinners.add(repetitionSpinner);

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        addButton.setOnClickListener(v -> addNewSpinnerPair());

        moodHappy.setOnClickListener(v -> selectMood("Happy"));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral"));
        moodSad.setOnClickListener(v -> selectMood("Sad"));

        backButton.setOnClickListener(v -> finish());
        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void setupSpinner(Spinner spinner, int arrayResId) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                arrayResId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addNewSpinnerPair() {
        LinearLayout spinnerContainer = new LinearLayout(this);
        spinnerContainer.setOrientation(LinearLayout.HORIZONTAL);
        spinnerContainer.setPadding(8, 8, 8, 8);

        Spinner newWorkoutSpinner = new Spinner(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, 50, 1.0f);
        newWorkoutSpinner.setLayoutParams(params);
        newWorkoutSpinner.setId(View.generateViewId());
        setupSpinner(newWorkoutSpinner, R.array.workout_types);

        Spinner newRepetitionSpinner = new Spinner(this);
        LinearLayout.LayoutParams repParams = new LinearLayout.LayoutParams(
                0, 50, 1.0f);
        repParams.setMarginStart(8);
        newRepetitionSpinner.setLayoutParams(repParams);
        newRepetitionSpinner.setId(View.generateViewId());
        setupSpinner(newRepetitionSpinner, R.array.repetitions);

        spinnerContainer.addView(newWorkoutSpinner);
        spinnerContainer.addView(newRepetitionSpinner);
        workoutSetsContainer.addView(spinnerContainer);
        workoutTypeSpinners.add(newWorkoutSpinner);
        repetitionSpinners.add(newRepetitionSpinner);
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            timerHandler.postDelayed(timerRunnable, 1000);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
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
            heartRateText.setText("Heart Rate: -- BPM");
        }
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsed = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsed / 1000) % 60;
                int minutes = (int) ((elapsed / (1000 * 60)) % 60);
                int hours = (int) ((elapsed / (1000 * 60 * 60)) % 24);
                timerText.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private void selectMood(String mood) {
        selectedMood = mood;
        moodHappy.setAlpha(mood.equals("Happy") ? 1.0f : 0.5f);
        moodNeutral.setAlpha(mood.equals("Neutral") ? 1.0f : 0.5f);
        moodSad.setAlpha(mood.equals("Sad") ? 1.0f : 0.5f);
    }

    private void submitRecord() {
        String time = timerText.getText().toString();

        if (time.equals("00:00:00")) {
            Toast.makeText(this, "Please start and stop the timer", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedMood == null) {
            Toast.makeText(this, "Please select a mood", Toast.LENGTH_SHORT).show();
            return;
        }

        List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
        boolean hasValidEntry = false;
        for (int i = 0; i < workoutTypeSpinners.size(); i++) {
            String workoutType = workoutTypeSpinners.get(i).getSelectedItem().toString();
            String repetitions = repetitionSpinners.get(i).getSelectedItem().toString();
            if (!workoutType.equals("Select Workout Type") && !repetitions.equals("Select Repetitions")) {
                entries.add(new ExerciseFormatter.ExerciseEntry(workoutType, "Type", workoutType, "style"));
                entries.add(new ExerciseFormatter.ExerciseEntry(workoutType, "Repetitions", repetitions, "reps"));
                hasValidEntry = true;
            }
        }

        if (!hasValidEntry) {
            Toast.makeText(this, "Please select at least one valid workout type and repetitions", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_USER_ID, "");
        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        try {
            String exercises = ExerciseFormatter.formatExercises(entries);

            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "Workout");
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0.0f);
            record.put("Mood", selectedMood);
            record.put("Duration", time);
            record.put("Exercises", exercises);

            Log.d("RecordingWorkout", "Payload: " + record.toString());

            RequestBody body = RequestBody.create(record.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingWorkoutActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(RecordingWorkoutActivity.this,
                                    "Record saved successfully!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(RecordingWorkoutActivity.this, Dashboard.class);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        Log.e("RecordingWorkout", "Failed to save record: HTTP " + response.code() + ", Body: " + errorBody);
                        runOnUiThread(() -> Toast.makeText(RecordingWorkoutActivity.this,
                                "Failed to save record: " + errorBody, Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestHeartRatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BODY_SENSORS},
                    100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Heart rate permission granted", Toast.LENGTH_SHORT).show();
                heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heartRateSensor == null) {
                    heartRateText.setText("Heart Rate: Not Available");
                }
            } else {
                Toast.makeText(this, "Heart rate permission denied", Toast.LENGTH_SHORT).show();
                heartRateText.setText("Heart Rate: Permission Denied");
            }
        }
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
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerRunnable);
        sensorManager.unregisterListener(this);
    }
}