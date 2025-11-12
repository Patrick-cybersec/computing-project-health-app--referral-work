package com.example.healthapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class AllActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.allactivities_screen);

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize buttons
        Button allactivitiesballButton = findViewById(R.id.allactivitiesballButton);
        Button allactivitiesrunningButton = findViewById(R.id.allactivitiesrunningButton);
        Button allactivitiesswimmingButton = findViewById(R.id.allactivitiesswimmingButton);
        Button allactivitiescyclingButton = findViewById(R.id.allactivitiescyclingButton);
        Button allactivitiesskippingButton = findViewById(R.id.allactivitiesskippingButton);
        Button allactivitiesyogaButton = findViewById(R.id.allactivitiesyogaButton);
        Button allactivitiessprintButton = findViewById(R.id.allactivitiessprintButton);
        Button allactivitiesweightliftingButton = findViewById(R.id.allactivitiesweightliftingButton);
        Button allactivitiesworkoutButton = findViewById(R.id.allactivitiesworkoutButton);
        Button allactivitiesclimbingButton = findViewById(R.id.allactivitiesclimbingButton);
        Button allactivitiesgymButton = findViewById(R.id.allactivitiesgymButton);
        Button backButton = findViewById(R.id.allactivitiesbackButton);

        // Set click listeners for navigation
        allactivitiesballButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        allactivitiesskippingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        allactivitiesyogaButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        allactivitiesrunningButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingCyclingClimbingRunningActivity.class);
            startActivity(intent);
        });

        allactivitiescyclingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingCyclingClimbingRunningActivity.class);
            startActivity(intent);
        });

        allactivitiesclimbingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingCyclingClimbingRunningActivity.class);
            startActivity(intent);
        });

        allactivitiesswimmingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingSwimmingActivity.class);
            startActivity(intent);
        });

        allactivitiessprintButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingSprintActivity.class);
            startActivity(intent);
        });

        allactivitiesweightliftingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingGymWeighingActivity.class);
            startActivity(intent);
        });

        allactivitiesgymButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingGymWeighingActivity.class);
            startActivity(intent);
        });

        allactivitiesworkoutButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, RecordingWorkoutActivity.class);
            startActivity(intent);
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(AllActivity.this, QuickStart.class);
            startActivity(intent);
        });
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
            Intent intent = new Intent(AllActivity.this, Login.class);
            startActivity(intent);
            finish();
            return true;
        } else if (itemId == android.R.id.home) {
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