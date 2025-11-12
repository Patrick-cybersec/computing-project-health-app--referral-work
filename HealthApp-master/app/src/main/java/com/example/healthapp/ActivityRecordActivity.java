package com.example.healthapp;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ActivityRecordActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://a267815f7908.ngrok-free.app";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String TAG = "ActivityRecord";
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private RecyclerView recyclerView;
    private RecordAdapter adapter;
    private List<Record> recordList;
    private OkHttpClient client;
    private LinearLayout adminControls;
    private TextView noRecordsText;
    private Button addButton, editButton, deleteButton;

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

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recordList = new ArrayList<>();
        adapter = new RecordAdapter(recordList, position -> {
            adapter.setSelectedPosition(position);
            updateActionButtons();
        });
        recyclerView.setAdapter(adapter);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        if (isAdmin) {
            adminControls.setVisibility(View.VISIBLE);
            addButton.setOnClickListener(v -> showAddDialog());

            editButton.setOnClickListener(v -> {
                int pos = adapter.getSelectedPosition();
                if (pos != -1) {
                    showEditDialog(recordList.get(pos));
                }
            });

            deleteButton.setOnClickListener(v -> {
                int pos = adapter.getSelectedPosition();
                if (pos != -1) {
                    showDeleteConfirmation(recordList.get(pos));
                }
            });

        } else {
            adminControls.setVisibility(View.GONE);
        }

        updateActionButtons();
        fetchRecords();
    }

    private void updateActionButtons() {
        boolean hasSelection = adapter.getSelectedPosition() != -1;
        editButton.setEnabled(hasSelection);
        deleteButton.setEnabled(hasSelection);
    }

    private void showAddDialog() {
        showAddEditDialog(null);
    }

    private void showEditDialog(Record record) {
        showAddEditDialog(record);
    }

    private void showAddEditDialog(Record existingRecord) {
        boolean isEdit = existingRecord != null;
        int layout = isEdit ? R.layout.activity_edit_record : R.layout.activity_add_record;
        View dialogView = getLayoutInflater().inflate(layout, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        EditText etUserId = dialogView.findViewById(R.id.user_id_edit_text);
        Spinner spActivity = dialogView.findViewById(R.id.activity_type_spinner);
        Spinner spExercise = dialogView.findViewById(R.id.exercise_type_spinner);
        Spinner spReps = dialogView.findViewById(R.id.repetitions_spinner);
        Spinner spSets = dialogView.findViewById(R.id.sets_spinner);
        Button btnAddExercise = dialogView.findViewById(R.id.add_exercise_button);
        EditText etExercises = dialogView.findViewById(R.id.exercises_edit_text);
        EditText etDuration = dialogView.findViewById(R.id.duration_edit_text);
        Spinner spMood = dialogView.findViewById(R.id.mood_spinner);
        EditText etHeartRate = dialogView.findViewById(R.id.heart_rate_edit_text);
        Button btnSave = dialogView.findViewById(R.id.save_button);

        List<ExerciseFormatter.ExerciseEntry> exerciseList = new ArrayList<>();

        spActivity.setAdapter(ArrayAdapter.createFromResource(this, R.array.activity_types, android.R.layout.simple_spinner_item));
        spMood.setAdapter(ArrayAdapter.createFromResource(this, R.array.mood_options, android.R.layout.simple_spinner_item));
        spReps.setAdapter(ArrayAdapter.createFromResource(this, R.array.repetitions, android.R.layout.simple_spinner_item));
        spSets.setAdapter(ArrayAdapter.createFromResource(this, R.array.noofsets, android.R.layout.simple_spinner_item));

        spActivity.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                String type = parent.getItemAtPosition(pos).toString();
                int arrayId = R.array.workout_types;
                if (type.contains("Rehabilitation")) arrayId = R.array.rehabilitation;
                else if (type.contains("Swimming")) arrayId = R.array.swimming_types;
                else if (type.contains("Cycling") || type.contains("Running") || type.contains("Climbing") || type.contains("Sprint"))
                    arrayId = R.array.cyclingclimbingrunning_types;
                else if (type.contains("Ball") || type.contains("KeepFit") || type.contains("Entertainment") || type.contains("Skipping") || type.contains("Yoga"))
                    arrayId = R.array.skippingballyoga_types;

                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                        ActivityRecordActivity.this, arrayId, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spExercise.setAdapter(adapter);
            }

            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        if (isEdit) {
            etUserId.setText(existingRecord.userId);
            setSpinnerSelection(spActivity, existingRecord.activityType);
            etDuration.setText(existingRecord.duration);
            setSpinnerSelection(spMood, existingRecord.mood);
            etHeartRate.setText(existingRecord.heartRate > 0 ? String.valueOf((int) existingRecord.heartRate) : "");
            etExercises.setText(existingRecord.exercises);
            exerciseList.addAll(ExerciseFormatter.parseExercises(existingRecord.exercises));
            btnSave.setText("Update Record");
        } else {
            btnSave.setText("Add Record");
        }

        btnAddExercise.setOnClickListener(v -> {
            String ex = spExercise.getSelectedItem().toString();
            String reps = spReps.getSelectedItem().toString();
            if (!ex.contains("Select") && !reps.contains("Select")) {
                exerciseList.add(new ExerciseFormatter.ExerciseEntry(ex, "Repetitions", reps, "repetitions"));
                etExercises.setText(ExerciseFormatter.formatExercises(exerciseList));
            }
        });

        btnSave.setOnClickListener(v -> {
            String userId = etUserId.getText().toString().trim();
            String duration = etDuration.getText().toString().trim();
            String exercises = etExercises.getText().toString().trim();
            String activityType = spActivity.getSelectedItem() != null ? spActivity.getSelectedItem().toString() : "";
            String mood = spMood.getSelectedItem() != null ? spMood.getSelectedItem().toString() : "";

            if (userId.isEmpty()) {
                Toast.makeText(this, "User ID is required!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (duration.isEmpty() || !duration.matches("\\d{2}:\\d{2}:\\d{2}")) {
                Toast.makeText(this, "Valid Duration (HH:MM:SS) is required!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (exercises.isEmpty()) {
                Toast.makeText(this, "Exercises cannot be empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (activityType.contains("Select") || mood.contains("Select")) {
                Toast.makeText(this, "Please select Activity Type and Mood!", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String adminId = prefs.getString("userId", "");
                String adminPwd = prefs.getString("password", "");

                JSONObject payload = new JSONObject();
                payload.put("AdminId", adminId);
                payload.put("AdminPassword", adminPwd);

                String url;
                Request request;

                if (isEdit) {
                    // EDIT: PUT with UpdatedRecord
                    JSONObject record = new JSONObject();
                    record.put("UserId", userId);
                    record.put("ActivityType", activityType);
                    record.put("Mood", mood);
                    record.put("Duration", duration);
                    record.put("Exercises", exercises);

                    String hrText = etHeartRate.getText().toString().trim();
                    if (!hrText.isEmpty()) {
                        try {
                            record.put("HeartRate", Float.parseFloat(hrText));
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid Heart Rate!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    payload.put("RecordId", existingRecord.id);
                    payload.put("UpdatedRecord", record);
                    url = BASE_URL + "/api/ActivityRecords/" + existingRecord.id;
                    request = new Request.Builder()
                            .url(url)
                            .put(RequestBody.create(payload.toString(), JSON))
                            .addHeader("ngrok-skip-browser-warning", "true")
                            .build();

                } else {
                    // ADD: POST with FLAT fields (NO NewRecord wrapper)
                    payload.put("UserId", userId);
                    payload.put("ActivityType", activityType);
                    payload.put("Mood", mood);
                    payload.put("Duration", duration);
                    payload.put("Exercises", exercises);

                    String hrText = etHeartRate.getText().toString().trim();
                    if (!hrText.isEmpty()) {
                        try {
                            payload.put("HeartRate", Float.parseFloat(hrText));
                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Invalid Heart Rate!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }

                    url = BASE_URL + "/api/ActivityRecords";
                    request = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(payload.toString(), JSON))
                            .addHeader("ngrok-skip-browser-warning", "true")
                            .build();
                }

                Log.d(TAG, (isEdit ? "PUT" : "POST") + " payload: " + payload.toString());

                client.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                                "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }

                    @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                        String body = "";
                        try {
                            if (response.body() != null) {
                                body = response.body().string();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to read response", e);
                        }

                        final String finalBody = body;

                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                Toast.makeText(ActivityRecordActivity.this,
                                        isEdit ? "Record updated!" : "Record added!", Toast.LENGTH_SHORT).show();
                                fetchRecords();
                                dialog.dismiss();
                            });
                        } else {
                            runOnUiThread(() -> {
                                String errorMsg = finalBody.isEmpty() ? "Unknown error" : finalBody;
                                Toast.makeText(ActivityRecordActivity.this,
                                        (isEdit ? "Update" : "Add") + " failed: " + errorMsg, Toast.LENGTH_LONG).show();
                            });
                        }
                        response.close();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "JSON build error", e);
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        dialog.show();
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        for (int i = 0; i < spinner.getCount(); i++) {
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private void showDeleteConfirmation(Record record) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Are you sure you want to delete this record?\n\n" +
                        "Activity: " + record.activityType + "\n" +
                        "Duration: " + record.duration)
                .setPositiveButton("Delete", (dialog, which) -> deleteRecord(record))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRecord(Record record) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String adminId = prefs.getString("userId", "");
        String adminPwd = prefs.getString("password", "");

        JSONObject payload = new JSONObject();
        try {
            payload.put("AdminId", adminId);
            payload.put("AdminPassword", adminPwd);
        } catch (Exception e) {
            Toast.makeText(this, "Error building delete request", Toast.LENGTH_SHORT).show();
            return;
        }

        String url = BASE_URL + "/api/ActivityRecords/" + record.id;

        Request request = new Request.Builder()
                .url(url)
                .delete(RequestBody.create(payload.toString(), JSON))
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                        "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                String body = "";
                try {
                    if (response.body() != null) {
                        body = response.body().string();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read delete response", e);
                }

                final String finalBody = body;

                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(ActivityRecordActivity.this, "Record deleted!", Toast.LENGTH_SHORT).show();
                        fetchRecords();
                        adapter.setSelectedPosition(-1);
                        updateActionButtons();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                            "Delete failed: " + finalBody, Toast.LENGTH_LONG).show());
                }
                response.close();
            }
        });
    }

    private void fetchRecords() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString("userId", "");
        String password = prefs.getString("password", "");
        boolean isAdmin = prefs.getBoolean("isAdmin", false);

        String url = isAdmin
                ? BASE_URL + "/api/ActivityRecords?adminId=" + userId + "&adminPassword=" + password
                : BASE_URL + "/api/ActivityRecords/user/" + userId + "?requestingUserId=" + userId + "&requestingUserPassword=" + password;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this, "Network error", Toast.LENGTH_SHORT).show());
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) {
                String body = "";
                try {
                    if (response.body() != null) {
                        body = response.body().string();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to read response body", e);
                }

                final String finalBody = body;

                if (response.isSuccessful()) {
                    try {
                        JSONArray array = new JSONArray(finalBody);
                        List<Record> list = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject o = array.getJSONObject(i);
                            list.add(new Record(
                                    o.optInt("id", 0),
                                    o.optString("userId", o.optString("UserId", "")),
                                    o.optString("activityType", o.optString("ActivityType", "")),
                                    (float) o.optDouble("heartRate", 0),
                                    o.optString("mood", o.optString("Mood", "")),
                                    o.optString("duration", o.optString("Duration", "")),
                                    o.optString("exercises", o.optString("Exercises", "")),
                                    o.optString("created_At", "")
                            ));
                        }
                        runOnUiThread(() -> {
                            recordList.clear();
                            recordList.addAll(list);
                            adapter.notifyDataSetChanged();
                            noRecordsText.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                            adapter.setSelectedPosition(-1);
                            updateActionButtons();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "JSON parsing error", e);
                        runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this, "Parse error", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(ActivityRecordActivity.this,
                            "Server error: " + response.code(), Toast.LENGTH_SHORT).show());
                }
                response.close();
            }
        });
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
        private final List<Record> records;
        private final OnItemClickListener listener;
        private int selectedPosition = -1;

        interface OnItemClickListener {
            void onItemClick(int position);
        }

        RecordAdapter(List<Record> records, OnItemClickListener listener) {
            this.records = records;
            this.listener = listener;
        }

        void setSelectedPosition(int position) {
            int old = selectedPosition;
            selectedPosition = position;
            if (old != -1) notifyItemChanged(old);
            if (position != -1) notifyItemChanged(position);
        }

        int getSelectedPosition() {
            return selectedPosition;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.activity_record_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Record r = records.get(position);
            holder.activityTypeText.setText("Activity: " + r.activityType);
            holder.userIdText.setText("User: " + r.userId);
            holder.durationText.setText("Duration: " + r.duration);
            holder.moodText.setText("Mood: " + r.mood);
            holder.exercisesText.setText("Exercises: " + r.exercises);

            holder.itemView.setBackgroundColor(selectedPosition == position
                    ? Color.parseColor("#E8F5E8") : Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> listener.onItemClick(position));
        }

        @Override
        public int getItemCount() {
            return records.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView activityTypeText, userIdText, durationText, moodText, exercisesText;

            ViewHolder(View v) {
                super(v);
                activityTypeText = v.findViewById(R.id.activity_type_text);
                userIdText = v.findViewById(R.id.user_id_text);
                durationText = v.findViewById(R.id.duration_text);
                moodText = v.findViewById(R.id.mood_text);
                exercisesText = v.findViewById(R.id.exercises_text);
            }
        }
    }
}