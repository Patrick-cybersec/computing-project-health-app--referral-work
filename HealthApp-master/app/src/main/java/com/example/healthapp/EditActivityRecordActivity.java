package com.example.healthapp;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.healthapp.models.ActivityRecordRequest;
import com.example.healthapp.models.ActivityRecordResponse;
import com.example.healthapp.utils.SharedPrefsHelper;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EditActivityRecordActivity extends AppCompatActivity {

    private EditText adminIdEditText, adminPasswordEditText, userIdEditText, heartRateEditText, durationEditText, exercisesEditText;
    private Spinner activityTypeSpinner, moodSpinner, exerciseTypeSpinner, repetitionsSpinner, setsSpinner;
    private Button saveButton, deleteButton, addExerciseButton;
    private LinearLayout adminControls, adminAuthFields;
    private SharedPrefsHelper prefsHelper;
    private final OkHttpClient client = TrustAllOkHttpClient.getClient();
    private static final String TAG = "EditActivityRecord";
    private static final String API_URL = "https://b0978b2ad959.ngrok-free.app/api/ActivityRecords/%d";
    private int recordId;
    private boolean isAdmin;
    private List<String> exercisesList;
    private Map<String, String[]> activityExerciseMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_record);

        // Initialize UI elements
        adminIdEditText = findViewById(R.id.admin_id_edit_text);
        adminPasswordEditText = findViewById(R.id.admin_password_edit_text);
        userIdEditText = findViewById(R.id.user_id_edit_text);
        activityTypeSpinner = findViewById(R.id.activity_type_spinner);
        heartRateEditText = findViewById(R.id.heart_rate_edit_text);
        moodSpinner = findViewById(R.id.mood_spinner);
        durationEditText = findViewById(R.id.duration_edit_text);
        exercisesEditText = findViewById(R.id.exercises_edit_text);
        exerciseTypeSpinner = findViewById(R.id.exercise_type_spinner);
        repetitionsSpinner = findViewById(R.id.repetitions_spinner);
        setsSpinner = findViewById(R.id.sets_spinner);
        saveButton = findViewById(R.id.save_button);
        deleteButton = findViewById(R.id.delete_button);
        addExerciseButton = findViewById(R.id.add_exercise_button);
        adminControls = findViewById(R.id.admin_controls);
        adminAuthFields = findViewById(R.id.admin_auth_fields);

        // Initialize exercises list
        exercisesList = new ArrayList<>();

        // Map activity types to exercise arrays
        activityExerciseMap = new HashMap<>();
        activityExerciseMap.put("Workout", getResources().getStringArray(R.array.workout_types));
        activityExerciseMap.put("Rehabilitation", getResources().getStringArray(R.array.rehabilitation));
        activityExerciseMap.put("Cycling", getResources().getStringArray(R.array.cyclingclimbingrunning_types));
        activityExerciseMap.put("Swimming", getResources().getStringArray(R.array.swimming_types));
        activityExerciseMap.put("KeepFit", getResources().getStringArray(R.array.workout_types));
        activityExerciseMap.put("Gym", getResources().getStringArray(R.array.workout_types));
        activityExerciseMap.put("Entertainment", getResources().getStringArray(R.array.skippingballyoga_types));
        activityExerciseMap.put("Skipping", getResources().getStringArray(R.array.skippingballyoga_types));
        activityExerciseMap.put("Sprint", getResources().getStringArray(R.array.cyclingclimbingrunning_types));
        activityExerciseMap.put("Running", getResources().getStringArray(R.array.cyclingclimbingrunning_types));
        activityExerciseMap.put("Ball", getResources().getStringArray(R.array.skippingballyoga_types));
        activityExerciseMap.put("Climbing", getResources().getStringArray(R.array.cyclingclimbingrunning_types));
        activityExerciseMap.put("Yoga", getResources().getStringArray(R.array.skippingballyoga_types));
        activityExerciseMap.put("Weighlifting", getResources().getStringArray(R.array.workout_types));

        // Initialize SharedPrefsHelper
        try {
            prefsHelper = new SharedPrefsHelper(this);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SharedPrefsHelper: ", e);
            Toast.makeText(this, "Error initializing storage", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Get intent data
        recordId = getIntent().getIntExtra("RECORD_ID", -1);
        isAdmin = getIntent().getBooleanExtra("IS_ADMIN", false);
        String loggedInUserId = getIntent().getStringExtra("USER_ID");

        if (recordId == -1) {
            Toast.makeText(this, "Invalid record ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Restrict access to admins
        if (!isAdmin) {
            adminControls.setVisibility(View.GONE);
            adminAuthFields.setVisibility(View.GONE);
            Toast.makeText(this, "Only admins can edit or delete records", Toast.LENGTH_LONG).show();
            saveButton.setEnabled(false);
            deleteButton.setEnabled(false);
            addExerciseButton.setEnabled(false);
            return;
        } else {
            adminControls.setVisibility(View.VISIBLE);
            String storedAdminId = prefsHelper.getAdminId();
            String storedAdminPassword = prefsHelper.getAdminPassword();
            if (!storedAdminId.isEmpty() && !storedAdminPassword.isEmpty()) {
                adminIdEditText.setText(storedAdminId);
                adminPasswordEditText.setText(storedAdminPassword);
                adminAuthFields.setVisibility(View.GONE);
            }
        }

        // Set up activity type spinner listener
        activityTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateExerciseSpinner();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Set up buttons
        addExerciseButton.setOnClickListener(v -> addExercise());
        saveButton.setOnClickListener(v -> saveActivityRecord());
        deleteButton.setOnClickListener(v -> deleteActivityRecord());

        // Fetch record data
        fetchRecordData(recordId, loggedInUserId);
    }

    private void updateExerciseSpinner() {
        String selectedActivity = activityTypeSpinner.getSelectedItem().toString();
        String[] exerciseOptions = activityExerciseMap.getOrDefault(selectedActivity, new String[]{"Select Exercise"});
        ArrayAdapter<String> exerciseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, exerciseOptions);
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseTypeSpinner.setAdapter(exerciseAdapter);
    }

    private void addExercise() {
        String exerciseType = exerciseTypeSpinner.getSelectedItem().toString();
        String repetitions = repetitionsSpinner.getSelectedItem().toString();
        String sets = setsSpinner.getSelectedItem().toString();

        if (exerciseType.equals("Select Exercise") || repetitions.equals("Select Repetitions") || sets.equals("Select no. of set")) {
            Toast.makeText(this, "Please select exercise, repetitions, and sets", Toast.LENGTH_SHORT).show();
            return;
        }

        String exerciseEntry = String.format("%s: Repetitions %s repetitions, %s: Sets %s sets", exerciseType, repetitions, exerciseType, sets);
        exercisesList.add(exerciseEntry);
        updateExercisesText();
    }

    private void updateExercisesText() {
        exercisesEditText.setText(String.join(", ", exercisesList));
    }

    private void fetchRecordData(int recordId, String requestingUserId) {
        String url = String.format(API_URL, recordId) + "?requestingUserId=" + requestingUserId + "&requestingUserPassword=" + prefsHelper.getAdminPassword();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Ngrok-Skip-Browser-Warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Fetch error: ", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    Gson gson = new Gson();
                    ActivityRecordResponse record = gson.fromJson(responseBody, ActivityRecordResponse.class);
                    runOnUiThread(() -> populateForm(record));
                    Log.d(TAG, "Fetch response: HTTP " + response.code() + ", Body: " + responseBody);
                } else {
                    runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Failed to fetch record: " + responseBody, Toast.LENGTH_LONG).show());
                    Log.e(TAG, "Fetch failed: HTTP " + response.code() + ", Body: " + responseBody);
                }
            }
        });
    }

    private void populateForm(ActivityRecordResponse record) {
        userIdEditText.setText(record.getUserId());

        ArrayAdapter<CharSequence> activityAdapter = (ArrayAdapter<CharSequence>) activityTypeSpinner.getAdapter();
        int activityPosition = activityAdapter.getPosition(record.getActivityType());
        activityTypeSpinner.setSelection(activityPosition >= 0 ? activityPosition : 0);

        ArrayAdapter<CharSequence> moodAdapter = (ArrayAdapter<CharSequence>) moodSpinner.getAdapter();
        int moodPosition = moodAdapter.getPosition(record.getMood());
        moodSpinner.setSelection(moodPosition >= 0 ? moodPosition : 0);

        heartRateEditText.setText(String.valueOf(record.getHeartRate()));
        durationEditText.setText(record.getDuration());

        exercisesList.clear();
        if (record.getExercises() != null && !record.getExercises().isEmpty()) {
            exercisesList.addAll(Arrays.asList(record.getExercises().split(", ")));
        }
        updateExercisesText();

        updateExerciseSpinner();
    }

    private void saveActivityRecord() {
        String adminId = adminIdEditText.getText().toString().trim();
        String adminPassword = adminPasswordEditText.getText().toString().trim();
        String userId = userIdEditText.getText().toString().trim();
        String activityType = activityTypeSpinner.getSelectedItem().toString();
        String heartRateStr = heartRateEditText.getText().toString().trim();
        String mood = moodSpinner.getSelectedItem().toString();
        String duration = durationEditText.getText().toString().trim();
        String exercises = exercisesEditText.getText().toString().trim();

        // Validate inputs
        if (adminId.isEmpty() || adminPassword.isEmpty()) {
            Toast.makeText(this, "Admin ID and Password are required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (userId.isEmpty() || activityType.equals("Select Activity") || mood.equals("Select Mood") || duration.isEmpty()) {
            Toast.makeText(this, "User ID, Activity Type, Mood, and Duration are required", Toast.LENGTH_SHORT).show();
            return;
        }

        float heartRate = 0f;
        if (!heartRateStr.isEmpty()) {
            try {
                heartRate = Float.parseFloat(heartRateStr);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid heart rate", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (!duration.matches("\\d{2}:\\d{2}:\\d{2}")) {
            Toast.makeText(this, "Duration must be in HH:mm:ss format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (exercises.isEmpty()) {
            Toast.makeText(this, "At least one exercise is required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save admin credentials
        prefsHelper.saveAdminCredentials(adminId, adminPassword);

        // Create payload (cast HeartRate to int for backend)
        ActivityRecordRequest.Record record = new ActivityRecordRequest.Record(
                recordId, userId, activityType, (float) Math.round(heartRate), mood, duration, exercises
        );
        ActivityRecordRequest request = new ActivityRecordRequest(adminId, adminPassword, record);

        // Convert to JSON
        Gson gson = new Gson();
        String json = gson.toJson(request);
        Log.d(TAG, "Payload: " + json);

        // Send request
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request httpRequest = new Request.Builder()
                .url(String.format(API_URL, recordId))
                .put(body)
                .addHeader("Ngrok-Skip-Browser-Warning", "true")
                .build();

        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Network error: ", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(EditActivityRecordActivity.this, "Record updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    Log.d(TAG, "Save response: HTTP " + response.code() + ", Body: " + responseBody);
                } else {
                    runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Failed to update: " + responseBody, Toast.LENGTH_LONG).show());
                    Log.e(TAG, "Failed to update: HTTP " + response.code() + ", Body: " + responseBody);
                }
            }
        });
    }

    private void deleteActivityRecord() {
        String adminId = adminIdEditText.getText().toString().trim();
        String adminPassword = adminPasswordEditText.getText().toString().trim();

        if (adminId.isEmpty() || adminPassword.isEmpty()) {
            Toast.makeText(this, "Admin ID and Password are required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create payload
        Map<String, String> payload = new HashMap<>();
        payload.put("AdminId", adminId);
        payload.put("AdminPassword", adminPassword);
        Gson gson = new Gson();
        String json = gson.toJson(payload);

        // Send request
        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(json, JSON);
        Request httpRequest = new Request.Builder()
                .url(String.format(API_URL, recordId))
                .delete(body)
                .addHeader("Ngrok-Skip-Browser-Warning", "true")
                .build();

        client.newCall(httpRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                Log.e(TAG, "Network error: ", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(EditActivityRecordActivity.this, "Record deleted successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    Log.d(TAG, "Delete response: HTTP " + response.code() + ", Body: " + responseBody);
                } else {
                    runOnUiThread(() -> Toast.makeText(EditActivityRecordActivity.this, "Failed to delete: " + responseBody, Toast.LENGTH_LONG).show());
                    Log.e(TAG, "Failed to delete: HTTP " + response.code() + ", Body: " + responseBody);
                }
            }
        });
    }
}