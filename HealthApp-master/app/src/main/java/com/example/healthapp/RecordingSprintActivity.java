package com.example.healthapp;

import android.Manifest;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.healthapp.utils.SharedPrefsHelper;
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

public class RecordingSprintActivity extends AppCompatActivity implements SensorEventListener {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String API_URL = "https://a267815f7908.ngrok-free.app/api/ActivityRecords";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private TextView timerText, heartRateText, distanceValueText, pacingValueText;
    private Button startButton, stopButton, backButton, submitButton;
    private EditText roundsInput;
    private ImageView moodHappy, moodNeutral, moodSad;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;
    private String selectedMood = null;
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private float currentHeartRate = 0.0f;
    private OkHttpClient client;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location lastLocation;
    private float totalDistance = 0.0f;
    private boolean isTrackingLocation = false;
    private static final int PERMISSION_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.recordingsprint_screen);

        client = TrustAllOkHttpClient.getClient();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        requestPermissions();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        timerText = findViewById(R.id.timer_text);
        heartRateText = findViewById(R.id.heart_rate_text);
        distanceValueText = findViewById(R.id.distance_value);
        pacingValueText = findViewById(R.id.pacing_value);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        roundsInput = findViewById(R.id.roundtrip_input);
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

        startButton.setOnClickListener(v -> startTimer());
        stopButton.setOnClickListener(v -> stopTimer());
        moodHappy.setOnClickListener(v -> selectMood("Happy"));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral"));
        moodSad.setOnClickListener(v -> selectMood("Sad"));
        backButton.setOnClickListener(v -> {
            startActivity(new Intent(this, Dashboard.class));
            finish();
        });
        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
        }
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied. Distance tracking unavailable.", Toast.LENGTH_SHORT).show();
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
            stopLocationUpdates();
            updatePacing();
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
                updatePacing();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(10000); // Update every 10 seconds
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;
                for (Location location : locationResult.getLocations()) {
                    if (lastLocation != null) {
                        float distance = lastLocation.distanceTo(location); // Distance in meters
                        totalDistance += distance;
                        updateDistanceDisplay();
                        updatePacing();
                    }
                    lastLocation = location;
                }
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            isTrackingLocation = true;
        }
    }

    private void stopLocationUpdates() {
        if (isTrackingLocation) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            isTrackingLocation = false;
        }
    }

    private void updateDistanceDisplay() {
        runOnUiThread(() -> distanceValueText.setText(String.format("%.0f m", totalDistance)));
    }

    private void updatePacing() {
        if (totalDistance > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            float seconds = elapsed / 1000.0f;
            float pacing = seconds / totalDistance; // seconds per meter
            runOnUiThread(() -> pacingValueText.setText(String.format("%.2f s/m", pacing)));
        } else {
            runOnUiThread(() -> pacingValueText.setText("0 s/m"));
        }
    }

    private void selectMood(String mood) {
        selectedMood = mood;
        moodHappy.setAlpha(mood.equals("Happy") ? 1.0f : 0.5f);
        moodNeutral.setAlpha(mood.equals("Neutral") ? 1.0f : 0.5f);
        moodSad.setAlpha(mood.equals("Sad") ? 1.0f : 0.5f);
    }

    private void submitRecord() {
        String time = timerText.getText().toString();
        if ("00:00:00".equals(time) || selectedMood == null) {
            Toast.makeText(this, "Complete timer and mood!", Toast.LENGTH_SHORT).show();
            return;
        }

        String rounds = roundsInput.getText().toString();
        if (rounds.isEmpty()) rounds = "1";

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId   = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");   // <-- now compiles
        boolean isAdmin = prefs.getBoolean("isAdmin", false);
        if (userId.isEmpty()) {
            startActivity(new Intent(this, Login.class));
            finish();
            return;
        }

        List<ExerciseFormatter.ExerciseEntry> entries = new ArrayList<>();
        entries.add(new ExerciseFormatter.ExerciseEntry(
                "Sprint", "Sprint", "Outdoor", rounds + " rounds"
        ));

        try {
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "Sprint");
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

            // ... same network code as above ...
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    if (permissions[i].equals(Manifest.permission.BODY_SENSORS)) {
                        Toast.makeText(this, "Heart rate permission denied", Toast.LENGTH_SHORT).show();
                        heartRateText.setText("Heart Rate: Permission Denied");
                    } else if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        Toast.makeText(this, "Location permission denied. Distance tracking unavailable.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heartRateSensor == null) {
                    heartRateText.setText("Heart Rate: Not Available");
                }
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
        stopLocationUpdates();
    }
}