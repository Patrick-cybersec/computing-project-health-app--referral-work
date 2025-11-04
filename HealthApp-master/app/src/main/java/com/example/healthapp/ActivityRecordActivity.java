package com.example.healthapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActivityRecordActivity extends AppCompatActivity {
    private static final String BASE_URL = "https://b0978b2ad959.ngrok-free.app";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String KEY_PASSWORD = "password";
    private static final String TAG = "ActivityRecord";
    private RecyclerView recyclerView;
    private RecordAdapter adapter;
    private List<Record> recordList;
    private OkHttpClient client;
    private TextView noRecordsText;
    private LinearLayout adminControls;
    private Button addButton, editButton, deleteButton;
    private int selectedRecordIndex = -1;
    private static final Pattern EXERCISE_PATTERN = Pattern.compile(
            "^([A-Za-z\\-]+:\\s*[A-Za-z]+\\s*\\d+\\s*[A-Za-z]+)(,\\s*[A-Za-z\\-]+:\\s*[A-Za-z]+\\s*\\d+\\s*[A-Za-z]+)*$"
    );
    private List<ExerciseFormatter.ExerciseEntry> exerciseEntries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activityrecord_screen);

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Activity Records");
        }

        client = TrustAllOkHttpClient.getClient();
        recyclerView = findViewById(R.id.recordsRecyclerView);
        noRecordsText = findViewById(R.id.noRecordsText);
        adminControls = findViewById(R.id.adminControls);
        addButton = findViewById(R.id.addButton);
        editButton = findViewById(R.id.editButton);
        deleteButton = findViewById(R.id.deleteButton);

        if (recyclerView == null || adminControls == null) {
            Log.e(TAG, "RecyclerView or adminControls is null");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_LONG).show();
            return;
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordList = new ArrayList<>();
        adapter = new RecordAdapter(recordList, position -> {
            if (position >= 0 && position < recordList.size()) {
                selectedRecordIndex = position;
                editButton.setEnabled(true);
                deleteButton.setEnabled(true);
                Log.i(TAG, "Record selected at position: " + position);
            } else {
                Log.e(TAG, "Invalid position selected: " + position);
            }
        });
        recyclerView.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false);
        Log.d(TAG, "isAdmin: " + isAdmin);
        if (isAdmin) {
            Log.d(TAG, "Setting adminControls to VISIBLE");
            adminControls.setVisibility(View.VISIBLE);
            adminControls.requestLayout();
            setupAdminControls();
        } else {
            adminControls.setVisibility(View.GONE);
        }

        fetchRecords();
    }

    private void setupAdminControls() {
        addButton.setOnClickListener(v -> showAddEditRecordDialog(null));
        editButton.setEnabled(false);
        editButton.setOnClickListener(v -> {
            if (selectedRecordIndex >= 0 && selectedRecordIndex < recordList.size()) {
                showAddEditRecordDialog(recordList.get(selectedRecordIndex));
            } else {
                Toast.makeText(this, "Please select a valid record to edit", Toast.LENGTH_SHORT).show();
            }
        });
        deleteButton.setEnabled(false);
        deleteButton.setOnClickListener(v -> {
            if (selectedRecordIndex >= 0 && selectedRecordIndex < recordList.size()) {
                showDeleteRecordDialog(recordList.get(selectedRecordIndex));
            } else {
                Toast.makeText(this, "Please select a valid record to delete", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchRecords() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, "");
        String password = prefs.getString(KEY_PASSWORD, "");
        boolean isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false);
        if (userId.isEmpty() || password.isEmpty()) {
            runOnUiThread(() -> {
                Toast.makeText(this, "User ID not found. Please log in again.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, Login.class));
                finish();
            });
            return;
        }

        String url = isAdmin ?
                BASE_URL + "/api/ActivityRecords?adminId=" + userId + "&adminPassword=" + password :
                BASE_URL + "/api/ActivityRecords/user/" + userId + "?requestingUserId=" + userId + "&requestingUserPassword=" + password;

        Log.d(TAG, "Fetching records from: " + url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                        "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Response code: " + response.code() + ", Body: " + responseData);
                if (response.isSuccessful()) {
                    try {
                        List<Record> newRecords = new ArrayList<>();
                        JSONArray jsonArray = new JSONArray(responseData);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject json = jsonArray.getJSONObject(i);
                            String exercises = json.optString("exercises", "");
                            newRecords.add(new Record(
                                    json.getInt("id"),
                                    json.getString("userId"),
                                    json.getString("activityType"),
                                    (float) json.optDouble("heartRate", 0.0),
                                    json.getString("mood"),
                                    json.getString("duration"),
                                    exercises,
                                    json.getString("created_At")
                            ));
                        }
                        runOnUiThread(() -> {
                            recordList.clear();
                            recordList.addAll(newRecords);
                            adapter.notifyDataSetChanged();
                            selectedRecordIndex = -1;
                            editButton.setEnabled(false);
                            deleteButton.setEnabled(false);
                            if (newRecords.isEmpty()) {
                                Toast.makeText(ActivityRecordActivity.this,
                                        "No records found", Toast.LENGTH_SHORT).show();
                                recyclerView.setVisibility(View.GONE);
                                if (noRecordsText != null) {
                                    noRecordsText.setVisibility(View.VISIBLE);
                                }
                            } else {
                                recyclerView.setVisibility(View.VISIBLE);
                                if (noRecordsText != null) {
                                    noRecordsText.setVisibility(View.GONE);
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                        runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                                "Error parsing records: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                } else {
                    Log.e(TAG, "Failed to fetch records: HTTP " + response.code() + ", Body: " + responseData);
                    runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                            "Failed to fetch records: HTTP " + response.code(), Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private boolean validateExercises(String exercises) {
        if (exercises == null || exercises.trim().isEmpty()) {
            return false;
        }
        return EXERCISE_PATTERN.matcher(exercises.trim()).matches();
    }

    private void updateExerciseInput(Spinner activityTypeSpinner, Spinner exerciseTypeSpinner, EditText exercisesInput) {
        String activityType = activityTypeSpinner.getSelectedItem().toString();
        ArrayAdapter<CharSequence> exerciseAdapter;
        switch (activityType) {
            case "Workout":
            case "Gym":
            case "Weightlifting":
                exerciseAdapter = ArrayAdapter.createFromResource(this,
                        R.array.workout_types, android.R.layout.simple_spinner_item);
                break;
            case "Rehabilitation":
                exerciseAdapter = ArrayAdapter.createFromResource(this,
                        R.array.rehabilitation, android.R.layout.simple_spinner_item);
                break;
            case "Cycling":
            case "Climbing":
            case "Running":
            case "Sprint":
                exerciseAdapter = ArrayAdapter.createFromResource(this,
                        R.array.cyclingclimbingrunning_types, android.R.layout.simple_spinner_item);
                break;
            case "Swimming":
                exerciseAdapter = ArrayAdapter.createFromResource(this,
                        R.array.swimming_types, android.R.layout.simple_spinner_item);
                break;
            case "Ball":
            case "KeepFit":
            case "Entertainment":
            case "Skipping":
            case "Yoga":
                exerciseAdapter = ArrayAdapter.createFromResource(this,
                        R.array.skippingballyoga_types, android.R.layout.simple_spinner_item);
                break;
            default:
                exerciseAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Select Exercise"});
                break;
        }
        exerciseAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        exerciseTypeSpinner.setAdapter(exerciseAdapter);
        exercisesInput.setText(ExerciseFormatter.formatExercises(exerciseEntries));
    }

    private void showAddEditRecordDialog(Record existingRecord) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existingRecord == null ? "Add Record" : "Edit Record");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        EditText userIdInput = new EditText(this);
        userIdInput.setHint("User ID");
        Spinner activityTypeSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> activityAdapter = ArrayAdapter.createFromResource(this,
                R.array.activity_types, android.R.layout.simple_spinner_item);
        activityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        activityTypeSpinner.setAdapter(activityAdapter);
        Spinner exerciseTypeSpinner = new Spinner(this);
        EditText heartRateInput = new EditText(this);
        heartRateInput.setHint("Heart Rate (optional)");
        heartRateInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner moodSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> moodAdapter = ArrayAdapter.createFromResource(this,
                R.array.mood_options, android.R.layout.simple_spinner_item);
        moodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        moodSpinner.setAdapter(moodAdapter);
        EditText durationInput = new EditText(this);
        durationInput.setHint("Duration (e.g., 00:30:00)");
        Spinner repetitionsSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> repetitionsAdapter = ArrayAdapter.createFromResource(this,
                R.array.repetitions, android.R.layout.simple_spinner_item);
        repetitionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        repetitionsSpinner.setAdapter(repetitionsAdapter);
        Spinner setsSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> setsAdapter = ArrayAdapter.createFromResource(this,
                R.array.noofsets, android.R.layout.simple_spinner_item);
        setsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        setsSpinner.setAdapter(setsAdapter);
        Button addExerciseButton = new Button(this);
        addExerciseButton.setText("Add Exercise");
        EditText exercisesInput = new EditText(this);
        exercisesInput.setHint("Exercises (e.g., Sit-up: Repetitions 15 repetitions)");
        exercisesInput.setEnabled(false);

        if (existingRecord != null) {
            userIdInput.setText(existingRecord.userId);
            for (int i = 0; i < activityAdapter.getCount(); i++) {
                if (activityAdapter.getItem(i).toString().equals(existingRecord.activityType)) {
                    activityTypeSpinner.setSelection(i);
                    break;
                }
            }
            heartRateInput.setText(existingRecord.heartRate == 0.0f ? "" : String.valueOf(existingRecord.heartRate));
            for (int i = 0; i < moodAdapter.getCount(); i++) {
                if (moodAdapter.getItem(i).toString().equals(existingRecord.mood)) {
                    moodSpinner.setSelection(i);
                    break;
                }
            }
            durationInput.setText(existingRecord.duration);
            exercisesInput.setText(existingRecord.exercises);
            exerciseEntries.clear();
            if (!existingRecord.exercises.isEmpty()) {
                String[] exercises = existingRecord.exercises.split(",\\s*");
                for (String ex : exercises) {
                    String[] parts = ex.split(":\\s*|\\s+");
                    if (parts.length >= 4) {
                        exerciseEntries.add(new ExerciseFormatter.ExerciseEntry(
                                parts[0], parts[1], parts[2], parts[3]));
                    }
                }
            }
        }

        updateExerciseInput(activityTypeSpinner, exerciseTypeSpinner, exercisesInput);
        activityTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateExerciseInput(activityTypeSpinner, exerciseTypeSpinner, exercisesInput);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        addExerciseButton.setOnClickListener(v -> {
            String exercise = exerciseTypeSpinner.getSelectedItem().toString();
            String repetitions = repetitionsSpinner.getSelectedItem().toString();
            String sets = setsSpinner.getSelectedItem().toString();
            if (!exercise.equals("Select Exercise") && !repetitions.equals("Select repetition") && !sets.equals("Select no. of set")) {
                exerciseEntries.add(new ExerciseFormatter.ExerciseEntry(
                        exercise, "Repetitions", repetitions, "repetitions"));
                exercisesInput.setText(ExerciseFormatter.formatExercises(exerciseEntries));
            } else {
                Toast.makeText(this, "Please select exercise, repetitions, and sets", Toast.LENGTH_SHORT).show();
            }
        });

        layout.addView(userIdInput);
        layout.addView(activityTypeSpinner);
        layout.addView(exerciseTypeSpinner);
        layout.addView(repetitionsSpinner);
        layout.addView(setsSpinner);
        layout.addView(addExerciseButton);
        layout.addView(exercisesInput);
        layout.addView(heartRateInput);
        layout.addView(moodSpinner);
        layout.addView(durationInput);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String userId = userIdInput.getText().toString().trim();
                String activityType = activityTypeSpinner.getSelectedItem().toString();
                String heartRateStr = heartRateInput.getText().toString().trim();
                String mood = moodSpinner.getSelectedItem().toString();
                String duration = durationInput.getText().toString().trim();
                String exercises = exercisesInput.getText().toString().trim();

                if (userId.isEmpty()) {
                    Toast.makeText(this, "User ID is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (activityType.equals("Select Activity")) {
                    Toast.makeText(this, "Please select an activity type", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mood.equals("Select Mood")) {
                    Toast.makeText(this, "Please select a mood", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (duration.isEmpty()) {
                    Toast.makeText(this, "Duration is required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!validateExercises(exercises)) {
                    Toast.makeText(this, "Invalid exercises format. Use: Exercise: Metric Value Unit, e.g., Sit-up: Repetitions 15 repetitions", Toast.LENGTH_LONG).show();
                    return;
                }

                float heartRate;
                try {
                    heartRate = heartRateStr.isEmpty() ? 0.0f : Float.parseFloat(heartRateStr);
                    if (heartRate < 0 || heartRate > 300) {
                        Toast.makeText(this, "Heart rate must be 0-300 bpm", Toast.LENGTH_SHORT).show();
                        return;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid heart rate format", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!duration.matches("\\d{2}:\\d{2}:\\d{2}")) {
                    Toast.makeText(this, "Duration must be in HH:MM:SS format", Toast.LENGTH_SHORT).show();
                    return;
                }

                JSONObject record = new JSONObject();
                record.put("id", existingRecord != null ? existingRecord.id : 0);
                record.put("userId", userId);
                record.put("activityType", activityType);
                record.put("heartRate", heartRate);
                record.put("mood", mood);
                record.put("duration", duration);
                record.put("exercises", exercises);
                if (existingRecord != null) {
                    record.put("created_At", existingRecord.createdAt);
                }

                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String adminId = prefs.getString(KEY_USER_ID, "");
                String adminPassword = prefs.getString(KEY_PASSWORD, "");

                if (adminId.isEmpty() || adminPassword.isEmpty()) {
                    Toast.makeText(this, "Admin credentials not found. Please log in again.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, Login.class));
                    finish();
                    return;
                }

                JSONObject requestBody = new JSONObject();
                requestBody.put("adminId", adminId);
                requestBody.put("adminPassword", adminPassword);
                requestBody.put("record", record);

                if (existingRecord == null) {
                    addRecord(requestBody);
                } else {
                    editRecord(requestBody);
                }
            } catch (Exception e) {
                Toast.makeText(this, "Invalid input: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            userIdInput.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
        dialog.setOnDismissListener(d -> {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            exerciseEntries.clear();
        });
        dialog.show();
    }

    private void showDeleteRecordDialog(Record record) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete Record");
        builder.setMessage("Are you sure you want to delete this record for user " + record.userId + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String adminId = prefs.getString(KEY_USER_ID, "");
            String adminPassword = prefs.getString(KEY_PASSWORD, "");

            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("adminId", adminId);
                requestBody.put("adminPassword", adminPassword);

                deleteRecord(record.id, requestBody);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void addRecord(JSONObject requestBody) {
        new Thread(() -> {
            try {
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/ActivityRecords")
                        .post(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.d(TAG, "Add record response: HTTP " + response.code() + ", Body: " + errorBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Record added successfully", Toast.LENGTH_SHORT).show();
                            fetchRecords();
                        });
                    } else {
                        runOnUiThread(() -> {
                            String message = "Failed to add record: HTTP " + response.code();
                            if (response.code() == 400) {
                                message = "Invalid record data. Please check input fields.";
                            } else if (response.code() == 401) {
                                message = "Invalid admin credentials.";
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding record: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error adding record: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void editRecord(JSONObject requestBody) {
        new Thread(() -> {
            try {
                int id = requestBody.getJSONObject("record").getInt("id");
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/ActivityRecords/" + id)
                        .put(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.d(TAG, "Edit record response: HTTP " + response.code() + ", Body: " + errorBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Record updated successfully", Toast.LENGTH_SHORT).show();
                            fetchRecords();
                        });
                    } else {
                        runOnUiThread(() -> {
                            String message = "Failed to update record: HTTP " + response.code();
                            if (response.code() == 400) {
                                message = "Invalid record data. Please check input fields.";
                            } else if (response.code() == 401) {
                                message = "Invalid admin credentials.";
                            } else if (response.code() == 404) {
                                message = "Record not found.";
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating record: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error updating record: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void deleteRecord(int id, JSONObject requestBody) {
        new Thread(() -> {
            try {
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/ActivityRecords/" + id)
                        .delete(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    Log.d(TAG, "Delete record response: HTTP " + response.code() + ", Body: " + errorBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Record deleted successfully", Toast.LENGTH_SHORT).show();
                            fetchRecords();
                        });
                    } else {
                        runOnUiThread(() -> {
                            String message = "Failed to delete record: HTTP " + response.code();
                            if (response.code() == 400) {
                                message = "Invalid request data.";
                            } else if (response.code() == 401) {
                                message = "Invalid admin credentials.";
                            } else if (response.code() == 404) {
                                message = "Record not found.";
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting record: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error deleting record: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private static class Record {
        int id;
        float heartRate;
        String userId, activityType, mood, duration, exercises, createdAt;

        Record(int id, String userId, String activityType, float heartRate, String mood, String duration, String exercises, String createdAt) {
            this.id = id;
            this.userId = userId;
            this.activityType = activityType;
            this.heartRate = heartRate;
            this.mood = mood;
            this.duration = duration;
            this.exercises = exercises;
            this.createdAt = createdAt;
        }
    }

    private static class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {
        private List<Record> records;
        private OnItemClickListener listener;

        interface OnItemClickListener {
            void onItemClick(int position);
        }

        RecordAdapter(List<Record> records, OnItemClickListener listener) {
            this.records = records;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_record_item, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Record record = records.get(position);
            holder.activityTypeText.setText("Activity: " + record.activityType);
            holder.createdAtText.setText("Date: " + record.createdAt);
            holder.userIdText.setText("User ID: " + record.userId);
            holder.heartRateText.setText(String.format("Heart Rate: %.1f bpm", record.heartRate));
            holder.moodText.setText("Mood: " + record.mood);
            holder.durationText.setText("Duration: " + record.duration);
            holder.exercisesText.setText("Exercises: " + (record.exercises.isEmpty() ? "None" : record.exercises));
            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView activityTypeText, createdAtText, userIdText, heartRateText, moodText, durationText, exercisesText;

            ViewHolder(View itemView) {
                super(itemView);
                activityTypeText = itemView.findViewById(R.id.activity_type_text);
                createdAtText = itemView.findViewById(R.id.recorded_at_text);
                userIdText = itemView.findViewById(R.id.user_id_text);
                heartRateText = itemView.findViewById(R.id.heart_rate_text);
                moodText = itemView.findViewById(R.id.mood_text);
                durationText = itemView.findViewById(R.id.duration_text);
                exercisesText = itemView.findViewById(R.id.exercises_text);
            }
        }
    }
}