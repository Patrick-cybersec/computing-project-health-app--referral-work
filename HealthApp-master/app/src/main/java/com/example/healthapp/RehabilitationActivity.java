package com.example.healthapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.widget.Toast;

public class RehabilitationActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private Button backButton, startButton, videoButton1, videoButton2, videoButton3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.rehabilitation_screen);

        // Initialize views
        toolbar = findViewById(R.id.toolbar_menu);
        backButton = findViewById(R.id.backButton);
        startButton = findViewById(R.id.startButton);
        videoButton1 = findViewById(R.id.videoButton1);
        videoButton2 = findViewById(R.id.videoButton2);
        videoButton3 = findViewById(R.id.videoButton3);

        // Set up toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Rehabilitation");
        }

        // Back button
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, PredefinedProgrammeActivity.class);
            startActivity(intent);
            finish();
        });

        // Start button
        startButton.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, RecordingRehabilitationActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error launching Recording Rehabilitation: " + e.getMessage(), Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        });

        // Video buttons
        videoButton1.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=bBpTga3bJ7M&t=376s"));
            startActivity(intent);
        });

        videoButton2.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=pFK0xXEnv6s&t=231s"));
            startActivity(intent);
        });

        videoButton3.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=CZ9rvwtaK4Q"));
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
}