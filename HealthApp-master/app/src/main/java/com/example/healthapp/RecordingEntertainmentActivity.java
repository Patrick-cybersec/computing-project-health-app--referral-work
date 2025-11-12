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
import android.location.Location;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
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

public class RecordingEntertainmentActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://1c05e1adb0b1.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private TextView timerText, heartRateText, distanceText;
    private Button startButton, stopButton, backButton, submitButton;
    private Button runningButton, cyclingButton, swimmingButton;
    private Button finishButton1, finishButton2, finishButton3;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Toolbar toolbar;
    private Handler handler = new Handler(Looper.getMainLooper());
    private long startTime = 0;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private FusedLocationProviderClient fusedLocationClient;
    private Location lastLocation;
    private double distance = 0.0;
    private boolean isRunningFinished = false;
    private boolean isCyclingFinished = false;
    private boolean isSwimmingFinished = false;
    private OkHttpClient client;

    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            long elapsedTime = System.currentTimeMillis() - startTime;
            updateTimerText(elapsedTime);
            handler.postDelayed(this, 1000);
        }
    };

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            if (location != null && lastLocation != null) {
                distance += lastLocation.distanceTo(location);
                distanceText.setText(String.format("Distance: %.1f m", distance));
            }
            lastLocation = location;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingentertainment_screen);

        client = TrustAllOkHttpClient.getClient();
        requestPermissions();

        toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        distanceText = findViewById(R.id.distance_text);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        backButton = findViewById(R.id.back_button);
        submitButton = findViewById(R.id.submit_button);
        runningButton = findViewById(R.id.runningButton2);
        cyclingButton = findViewById(R.id.cyclingButton2);
        swimmingButton = findViewById(R.id.swimmingButton2);
        finishButton1 = findViewById(R.id.finish_button1);
        finishButton2 = findViewById(R.id.finish_button2);
        finishButton3 = findViewById(R.id.finish_button3);
        moodHappy = findViewById(R.id.mood_happy);
        moodNeutral = findViewById(R.id.mood_neutral);
        moodSad = findViewById(R.id.mood_sad);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor == null) heartRateText.setText("Heart rate: Not available");
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) heartRateText.setText("Heart rate: Permission denied");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Entertainment record");

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        finishButton1.setOnClickListener(v -> { isRunningFinished = true; finishButton1.setEnabled(false); Toast.makeText(this, "Running task finished (15 mins)!", Toast.LENGTH_SHORT).show(); });
        finishButton2.setOnClickListener(v -> { isCyclingFinished = true; finishButton2.setEnabled(false); Toast.makeText(this, "Cycling task finished (15 mins)!", Toast.LENGTH_SHORT).show(); });
        finishButton3.setOnClickListener(v -> { isSwimmingFinished = true; finishButton3.setEnabled(false); Toast.makeText(this, "Swimming task finished (15 mins)!", Toast.LENGTH_SHORT).show(); });
        runningButton.setOnClickListener(v -> Toast.makeText(this, "Selected Running for entertainment", Toast.LENGTH_SHORT).show());
        cyclingButton.setOnClickListener(v -> Toast.makeText(this, "Selected Cycling for entertainment", Toast.LENGTH_SHORT).show());
        swimmingButton.setOnClickListener(v -> Toast.makeText(this, "Selected Swimming for entertainment", Toast.LENGTH_SHORT).show());
        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));
        backButton.setOnClickListener(v -> { clearFocusAndHideIME(); finish(); });
        submitButton.setOnClickListener(v -> { clearFocusAndHideIME(); submitRecord(); });
    }

    private void clearFocusAndHideIME() {
        View focus = getCurrentFocus();
        if (focus != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(), 0);
            focus.clearFocus();
        }
    }

    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            handler.post(updateTimer);
            isTimerRunning = true;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            if (heartRateSensor != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED) {
                sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_UI);
            }
            startLocationUpdates();
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
            if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private void startLocationUpdates() {
        LocationRequest request = LocationRequest.create().setInterval(5000).setFastestInterval(2000).setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        }
    }

    private void updateTimerText(long elapsed) {
        timerText.setText(String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(elapsed), TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60, TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60));
    }

    private void selectMood(String mood, ImageView selected, ImageView... others) {
        selectedMood = mood;
        selected.setAlpha(1.0f);
        for (ImageView other : others) other.setAlpha(0.5f);
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
                    runOnUiThread(() -> Toast.makeText(RecordingEntertainmentActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(RecordingEntertainmentActivity.this,
                                        "Record saved successfully!", Toast.LENGTH_SHORT).show();
                                startActivity(new Intent(RecordingEntertainmentActivity.this, Dashboard.class));
                                finish();
                            });
                        } else {
                            Log.e("SUBMIT", "Failed: " + response.code() + " | " + responseBody);
                            runOnUiThread(() -> Toast.makeText(RecordingEntertainmentActivity.this,
                                    "Failed: " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(RecordingEntertainmentActivity.this,
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
        String[] perms = {Manifest.permission.BODY_SENSORS, Manifest.permission.ACCESS_FINE_LOCATION};
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, perms, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean granted = true;
            for (int result : grantResults) if (result != PackageManager.PERMISSION_GRANTED) granted = false;
            if (!granted) {
                heartRateText.setText("Heart rate: Permission denied");
                distanceText.setText("Distance: Permission denied");
            } else {
                heartRateText.setText("Heart rate: -- BPM");
                distanceText.setText("Distance: 0.0 m");
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) { getMenuInflater().inflate(R.menu.toolbar_menu, menu); return true; }

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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose music").setItems(new String[]{"Music 1", "Music 2", "Music 3"}, (d, w) -> Toast.makeText(this, "Selected: Music " + (w + 1), Toast.LENGTH_SHORT).show());
        builder.show();
    }

    private void showNotificationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Notifications").setMessage("No new notifications.").setPositiveButton("OK", null).show();
    }

    @Override protected void onPause() { super.onPause(); clearFocusAndHideIME(); }
    @Override protected void onDestroy() { super.onDestroy(); handler.removeCallbacks(updateTimer); sensorManager.unregisterListener(this); if (fusedLocationClient != null) fusedLocationClient.removeLocationUpdates(locationCallback); }
}