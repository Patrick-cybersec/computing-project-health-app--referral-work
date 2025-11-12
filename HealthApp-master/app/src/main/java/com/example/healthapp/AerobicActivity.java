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

public class AerobicActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.aerobic_screen);

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Initialize buttons
        Button aerobicballButton = findViewById(R.id.aerobicballButton);
        Button runningButton = findViewById(R.id.runningButton);
        Button swimmingButton = findViewById(R.id.swimmingButton);
        Button cyclingButton = findViewById(R.id.cyclingButton);
        Button skippingButton = findViewById(R.id.skippingButton);
        Button yogaButton = findViewById(R.id.yogaButton);
        Button backButton = findViewById(R.id.backButton);

        // Set click listeners for navigation
        aerobicballButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        skippingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        yogaButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingSkippingBallYogaActivity.class);
            startActivity(intent);
        });

        runningButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingCyclingClimbingRunningActivity.class);
            startActivity(intent);
        });

        cyclingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingCyclingClimbingRunningActivity.class);
            startActivity(intent);
        });

        swimmingButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, RecordingSwimmingActivity.class);
            startActivity(intent);
        });

        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(AerobicActivity.this, YourActivity.class);
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
            Intent intent = new Intent(AerobicActivity.this, Login.class);
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