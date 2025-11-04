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

public class TutorialActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private Button tutorialballButton, tutorialrunningButton, tutorialswimmingButton,
            tutorialcyclingButton, tutorialskippingButton, tutorialyogaButton,
            tutorialsprintButton, tutorialweightliftingButton, tutorialworkoutButton,
            tutorialclimbingButton, tutorialgymButton, tutorialbackButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tutorial_screen);

        // Initialize views
        toolbar = findViewById(R.id.toolbar_menu);
        tutorialballButton = findViewById(R.id.tutorialballButton);
        tutorialrunningButton = findViewById(R.id.tutorialrunningButton);
        tutorialswimmingButton = findViewById(R.id.tutorialswimmingButton);
        tutorialcyclingButton = findViewById(R.id.tutorialcyclingButton);
        tutorialskippingButton = findViewById(R.id.tutorialskippingButton);
        tutorialyogaButton = findViewById(R.id.tutorialyogaButton);
        tutorialsprintButton = findViewById(R.id.tutorialsprintButton);
        tutorialweightliftingButton = findViewById(R.id.tutorialweightliftingButton);
        tutorialworkoutButton = findViewById(R.id.tutorialworkoutButton);
        tutorialclimbingButton = findViewById(R.id.tutorialclimbingButton);
        tutorialgymButton = findViewById(R.id.tutorialgymButton);
        tutorialbackButton = findViewById(R.id.tutorialbackButton);

        // Set up toolbar
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Activities tutorial");
        }

        // Video button click listeners
        tutorialballButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=SxVaMcHqcoU"));
        tutorialrunningButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=_kGESn8ArrU&t=22s"));
        tutorialswimmingButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=gh5mAtmeR3Y"));
        tutorialcyclingButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=4ssLDk1eX9w"));
        tutorialskippingButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=kDOGb9C5kp0"));
        tutorialyogaButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=JqyHToMWl3E"));
        tutorialsprintButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=FYJJbwG_i8U"));
        tutorialweightliftingButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=VMaBfcRprAU"));
        tutorialworkoutButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=wIynl3at0Rs"));
        tutorialclimbingButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=b2v4brHpdxY"));
        tutorialgymButton.setOnClickListener(v -> openUrl("https://www.youtube.com/watch?v=U9ENCvFf9yQ"));

        // Back button
        tutorialbackButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, Dashboard.class);
            startActivity(intent);
            finish();
        });
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Error opening URL: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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