package com.example.healthapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;

public class Dashboard extends AppCompatActivity {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dashboard_screen);

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Dashboard");
        }

        Button portfolioButton = findViewById(R.id.portfolioButton);
        Button quickStartButton = findViewById(R.id.quickstartButton);
        Button tutorialButton = findViewById(R.id.tutorialButton);
        Button awardButton = findViewById(R.id.awardButton);
        Button billboardButton = findViewById(R.id.billboardButton);
        Button activityRecordButton = findViewById(R.id.activityrecordButton);

        // Portfolio button
        portfolioButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, PortfolioActivity.class);
            startActivity(intent);
        });

        // Quick Start button
        quickStartButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, QuickStart.class);
            startActivity(intent);
        });

        // Tutorial button
        tutorialButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, TutorialActivity.class);
            startActivity(intent);
        });

        // Award button
        awardButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, AwardsActivity.class);
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String userId = prefs.getString(KEY_USER_ID, null);
            if (userId != null) {
                intent.putExtra("userId", userId);
                startActivity(intent);
            } else {
                Toast.makeText(Dashboard.this, "Please log in to view awards", Toast.LENGTH_SHORT).show();
            }
        });

        // Billboard button
        billboardButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, BillboardActivity.class);
            startActivity(intent);
        });

        // Activity Record button
        activityRecordButton.setOnClickListener(v -> {
            Intent intent = new Intent(Dashboard.this, ActivityRecordActivity.class);
            startActivity(intent);
        });

        // Process incoming data if any
        processIncomingData();
    }

    private void processIncomingData() {
        Intent intent = getIntent();
        if (intent == null || !intent.hasExtra("heart_rate")) {
            return;
        }

        float heartRate = intent.getFloatExtra("heart_rate", 0.0f);
        String mood = intent.getStringExtra("mood");
        String time = intent.getStringExtra("time");

        StringBuilder message = new StringBuilder();
        message.append("Heart Rate: ").append(String.format("%.0f BPM", heartRate)).append("\n")
                .append("Mood: ").append(mood).append("\n")
                .append("Time: ").append(time).append("\n");

        // Check for Keep Fit data
        if (intent.hasExtra("pushup_sets")) {
            int pushupSets = intent.getIntExtra("pushup_sets", 0);
            int squatSets = intent.getIntExtra("squat_sets", 0);
            int situpSets = intent.getIntExtra("situp_sets", 0);
            message.append("Keep Fit:\n")
                    .append("Push-ups: ").append(pushupSets).append(" sets\n")
                    .append("Squats: ").append(squatSets).append(" sets\n")
                    .append("Sit-ups: ").append(situpSets).append(" sets");
        }

        // Check for Rehabilitation data
        if (intent.hasExtra("exercises")) {
            ArrayList<String> exercises = intent.getStringArrayListExtra("exercises");
            ArrayList<Integer> sets = intent.getIntegerArrayListExtra("sets");
            if (exercises != null && sets != null && !exercises.isEmpty()) {
                message.append("Rehabilitation:\n");
                for (int i = 0; i < exercises.size(); i++) {
                    message.append(exercises.get(i)).append(": ").append(sets.get(i)).append(" sets\n");
                }
            }
        }

        Toast.makeText(this, message.toString(), Toast.LENGTH_LONG).show();
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
            Intent intent = new Intent(Dashboard.this, Login.class);
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
}