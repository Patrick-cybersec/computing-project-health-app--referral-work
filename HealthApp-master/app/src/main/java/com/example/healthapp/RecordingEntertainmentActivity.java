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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
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
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_PASSWORD = "password";
    private static final String API_URL = "https://b0978b2ad959.ngrok-free.app/api/ActivityRecords";
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
        if (heartRateSensor == null) {
            heartRateText.setText("Heart Rate: Not Available");
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
                != PackageManager.PERMISSION_GRANTED) {
            heartRateText.setText("Heart Rate: Permission Denied");
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Entertainment Record");
        }

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
                startLocationUpdates();
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
                if (fusedLocationClient != null) {
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        });

        finishButton1.setOnClickListener(v -> {
            isRunningFinished = true;
            finishButton1.setEnabled(false);
            Toast.makeText(this, "Running task finished (15 mins)!", Toast.LENGTH_SHORT).show();
        });

        finishButton2.setOnClickListener(v -> {
            isCyclingFinished = true;
            finishButton2.setEnabled(false);
            Toast.makeText(this, "Cycling task finished (15 mins)!", Toast.LENGTH_SHORT).show();
        });

        finishButton3.setOnClickListener(v -> {
            isSwimmingFinished = true;
            finishButton3.setEnabled(false);
            Toast.makeText(this, "Swimming task finished (15 mins)!", Toast.LENGTH_SHORT).show();
        });

        runningButton.setOnClickListener(v -> {
            Toast.makeText(this, "Selected Running for entertainment", Toast.LENGTH_SHORT).show();
        });

        cyclingButton.setOnClickListener(v -> {
            Toast.makeText(this, "Selected Cycling for entertainment", Toast.LENGTH_SHORT).show();
        });

        swimmingButton.setOnClickListener(v -> {
            Toast.makeText(this, "Selected Swimming for entertainment", Toast.LENGTH_SHORT).show();
        });

        moodHappy.setOnClickListener(v -> selectMood("Happy", moodHappy, moodNeutral, moodSad));
        moodNeutral.setOnClickListener(v -> selectMood("Neutral", moodNeutral, moodHappy, moodSad));
        moodSad.setOnClickListener(v -> selectMood("Sad", moodSad, moodHappy, moodNeutral));

        backButton.setOnClickListener(v -> finish());

        submitButton.setOnClickListener(v -> submitRecord());
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create()
                .setInterval(5000)
                .setFastestInterval(2000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
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
        if (!isRunningFinished && !isCyclingFinished && !isSwimmingFinished) {
            Toast.makeText(this, "Please complete at least one task!", Toast.LENGTH_SHORT).show();
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
            if (isRunningFinished) {
                entries.add(new ExerciseFormatter.ExerciseEntry("Running", "Duration", "15", "minutes"));
            }
            if (isCyclingFinished) {
                entries.add(new ExerciseFormatter.ExerciseEntry("Cycling", "Duration", "15", "minutes"));
            }
            if (isSwimmingFinished) {
                entries.add(new ExerciseFormatter.ExerciseEntry("Swimming", "Duration", "15", "minutes"));
            }
            if (distance > 0) {
                entries.add(new ExerciseFormatter.ExerciseEntry("Total", "Distance", String.format("%.1f", distance), "meters"));
            }

            String exercises = ExerciseFormatter.formatExercises(entries);

            // Create nested JSON payload
            JSONObject record = new JSONObject();
            record.put("UserId", userId);
            record.put("ActivityType", "Entertainment");
            record.put("HeartRate", currentHeartRate > 0 ? currentHeartRate : 0.0f);
            record.put("Mood", selectedMood);
            record.put("Duration", timerText.getText().toString());
            record.put("Exercises", exercises);

            JSONObject requestBody = new JSONObject();
            requestBody.put("AdminId", userId);
            requestBody.put("AdminPassword", password);
            requestBody.put("Record", record);

            Log.d("RecordingEntertainment", "Payload: " + requestBody.toString());

            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RecordingEntertainmentActivity.this,
                            "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String errorBody = response.body() != null ? response.body().string() : "No error details";
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(RecordingEntertainmentActivity.this,
                                    "Record saved successfully!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(RecordingEntertainmentActivity.this, Dashboard.class);
                            startActivity(intent);
                            finish();
                        });
                    } else {
                        Log.e("RecordingEntertainment", "Failed to save record: HTTP " + response.code() + ", Body: " + errorBody);
                        runOnUiThread(() -> Toast.makeText(RecordingEntertainmentActivity.this,
                                "Failed to save record: " + errorBody, Toast.LENGTH_LONG).show());
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Error preparing data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.BODY_SENSORS,
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                heartRateText.setText("Heart Rate: Permission Denied");
                distanceText.setText("Distance: Permission Denied");
            } else {
                heartRateText.setText("Heart Rate: -- BPM");
                distanceText.setText("Distance: 0.0 m");
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
        handler.removeCallbacks(updateTimer);
        sensorManager.unregisterListener(this);
        if (fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}