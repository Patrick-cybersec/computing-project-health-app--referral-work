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
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PASSWORD = "password";
    private static final String API_URL = "https://b0978b2ad959.ngrok-free.app/api/ActivityRecords";
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
    private ArrayList<Spinner> pushupSpinners = new ArrayList<>();
    private ArrayList<Spinner> squatSpinners = new ArrayList<>();
    private ArrayList<Spinner> situpSpinners = new ArrayList<>();

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
        setContentView(R.layout.keepfit_screen);

        client = TrustAllOkHttpClient.getClient();

        toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        addButton = findViewById(R.id.add_button); // Add this in layout if needed
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
            getSupportActionBar().setTitle("Keep Fit Record");
        }

        // Initialize spinners
        setupSpinner(findViewById(R.id.noofsets1_spinner_0), pushupSpinners); // Push-ups
        setupSpinner(findViewById(R.id.noofsets2_spinner_0), squatSpinners); // Squats
        setupSpinner(findViewById(R.id.noofsets3_spinner_0), situpSpinners); // Sit-ups
        pushupSpinners.add(findViewById(R.id.noofsets1_spinner_0));
        squatSpinners.add(findViewById(R.id.noofsets2_spinner_0));
        situpSpinners.add(findViewById(R.id.noofsets3_spinner_0));

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

        addButton.setOnClickListener(v -> addNewSpinnerSet());

        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));

        backButton.setOnClickListener(v -> finish());

        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void setupSpinner(Spinner spinner, ArrayList<Spinner> spinnerList) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.noofsets,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void addNewSpinnerSet() {
        LinearLayout spinnerContainer = new LinearLayout(this);
        spinnerContainer.setOrientation(LinearLayout.HORIZONTAL);
        spinnerContainer.setPadding(8, 8, 8, 8);

        Spinner pushupSpinner = new Spinner(this);
        Spinner squatSpinner = new Spinner(this);
        Spinner situpSpinner = new Spinner(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                50,
                1.0f
        );
        pushupSpinner.setLayoutParams(params);
        squatSpinner.setLayoutParams(params);
        situpSpinner.setLayoutParams(params);

        pushupSpinner.setId(View.generateViewId());
        squatSpinner.setId(View.generateViewId());
        situpSpinner.setId(View.generateViewId());

        setupSpinner(pushupSpinner, pushupSpinners);
        setupSpinner(squatSpinner, squatSpinners);
        setupSpinner(situpSpinner, situpSpinners);

        spinnerContainer.addView(pushupSpinner);
        spinnerContainer.addView(squatSpinner);
        spinnerContainer.addView(situpSpinner);

        workoutSetsContainer.addView(spinnerContainer);
        pushupSpinners.add(pushupSpinner);
        squatSpinners.add(squatSpinner);
        situpSpinners.add(situpSpinner);
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

    private void submitRecord() {
        if (selectedMood == null) {
            Toast.makeText(this, "Please choose mood after exercise!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (timerText.getText().toString().equals("00:00:00")) {
            Toast.makeText(this, "Please start and stop the timer!", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");
        if (userId.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "User ID or password not found. Please log in again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        try {
            List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
            boolean hasValidEntry = false;

            // Process Push-ups
            for (Spinner spinner : pushupSpinners) {
                String sets = spinner.getSelectedItem().toString();
                if (!sets.equals("Select no. of set") && !sets.equals("0")) {
                    int setCount = Integer.parseInt(sets);
                    int reps = setCount * 5; // 1 set = 5 reps
                    entries.add(new ExerciseFormatter.ExerciseEntry("Push-ups", "Reps", String.valueOf(reps), "reps"));
                    hasValidEntry = true;
                }
            }

            // Process Squats
            for (Spinner spinner : squatSpinners) {
                String sets = spinner.getSelectedItem().toString();
                if (!sets.equals("Select no. of set") && !sets.equals("0")) {
                    int setCount = Integer.parseInt(sets);
                    int reps = setCount * 5; // 1 set = 5 reps
                    entries.add(new ExerciseFormatter.ExerciseEntry("Squats", "Reps", String.valueOf(reps), "reps"));
                    hasValidEntry = true;
                }
            }

            // Process Sit-ups
            for (Spinner spinner : situpSpinners) {
                String sets = spinner.getSelectedItem().toString();
                if (!sets.equals("Select no. of set") && !sets.equals("0")) {
                    int setCount = Integer.parseInt(sets);
                    int reps = setCount * 5; // 1 set = 5 reps
                    entries.add(new ExerciseFormatter.ExerciseEntry("Sit-ups", "Reps", String.valueOf(reps), "reps"));
                    hasValidEntry = true;
                }
            }

            if (!hasValidEntry) {
                Toast.makeText(this, "Please select at least one valid set for an exercise!", Toast.LENGTH_SHORT).show();
                return;
            }

            String exercises = ExerciseFormatter.formatExercises(entries);

            // Create nested JSON payload
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "KeepFit");
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0.0f);
            record.put("Mood", selectedMood);
            record.put("Duration", timerText.getText().toString());
            record.put("Exercises", exercises);

            JSONObject requestBody = new JSONObject();
            requestBody.put("AdminId", userId);
            requestBody.put("AdminPassword", password);
            requestBody.put("Record", record);

            Log.d("RecordingKeepFit", "Payload: " + requestBody.toString());

            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingKeepFitActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(RecordingKeepFitActivity.this,
                                    "Record saved successfully!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(RecordingKeepFitActivity.this, Dashboard.class);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        Log.e("RecordingKeepFit", "Failed to save record: HTTP " + response.code() + ", Body: " + errorBody);
                        runOnUiThread(() -> Toast.makeText(RecordingKeepFitActivity.this,
                                "Failed to save record: " + errorBody, Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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