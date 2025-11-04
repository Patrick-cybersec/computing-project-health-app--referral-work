package com.example.healthapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Register extends AppCompatActivity {

    private EditText nameEditText, idEditText, passwordEditText, ageEditText;
    private Spinner sexSpinner;
    private OkHttpClient client;
    private static final String BASE_URL = "https://b0978b2ad959.ngrok-free.app";
    private static final String TAG = "RegisterActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register_screen);

        client = TrustAllOkHttpClient.getClient();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Register");
        }

        nameEditText = findViewById(R.id.nameEditText);
        idEditText = findViewById(R.id.idEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        ageEditText = findViewById(R.id.ageEditText);
        sexSpinner = findViewById(R.id.sexSpinner);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sex_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sexSpinner.setAdapter(adapter);

        Button createAccountButton = findViewById(R.id.createAccountButton);
        createAccountButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String id = idEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String age = ageEditText.getText().toString().trim();
            String sex = sexSpinner.getSelectedItem().toString();

            if (!validateInput(name, id, password, age, sex)) {
                Toast.makeText(this, "Please fill all required fields correctly", Toast.LENGTH_SHORT).show();
                return;
            }

            registerUser(name, id, password, age, sex);
        });

        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            startActivity(new Intent(this, Login.class));
            finish();
        });
    }

    private boolean validateInput(String name, String id, String password, String age, String sex) {
        // Required fields
        if (name.isEmpty() || id.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Name, ID, and password are required", Toast.LENGTH_SHORT).show();
            return false;
        }
        // String length constraints (based on backend User model)
        if (name.length() > 100) {
            Toast.makeText(this, "Name must be 100 characters or less", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (id.length() > 50) {
            Toast.makeText(this, "ID must be 50 characters or less", Toast.LENGTH_SHORT).show();
            return false;
        }
        if (password.length() > 100) {
            Toast.makeText(this, "Password must be 100 characters or less", Toast.LENGTH_SHORT).show();
            return false;
        }
        // Age validation
        if (!age.isEmpty()) {
            try {
                int ageValue = Integer.parseInt(age);
                if (ageValue < 0 || ageValue > 150) {
                    Toast.makeText(this, "Age must be between 0 and 150", Toast.LENGTH_SHORT).show();
                    return false;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Age must be a valid number", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        // Sex validation
        if (sex.equals("Select Sex")) {
            Toast.makeText(this, "Please select a sex", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void registerUser(String name, String id, String password, String age, String sex) {
        JSONObject json = new JSONObject();
        try {
            json.put("Id", id);
            json.put("Password", password);
            json.put("Name", name);
            json.put("Age", age.isEmpty() ? 0 : Integer.parseInt(age));
            json.put("Sex", sex);
            json.put("IsAdmin", false);
        } catch (Exception e) {
            Log.e(TAG, "Error creating JSON", e);
            runOnUiThread(() -> Toast.makeText(this, "Error creating request: " + e.getMessage(), Toast.LENGTH_LONG).show());
            return;
        }

        RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
        );
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/Users/public-register")
                .post(body)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Register response: " + responseData);
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(Register.this, "Registration successful. Please log in.", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(Register.this, Login.class));
                        finish();
                    } else {
                        String errorMsg = "Registration failed: HTTP " + response.code();
                        if (response.code() == 400) {
                            errorMsg = "Invalid input. Please check your details.";
                        } else if (response.code() == 409) {
                            errorMsg = "User ID already exists.";
                        } else if (response.code() == 500) {
                            errorMsg = "Server error. Please try again later.";
                        }
                        try {
                            JSONObject errorJson = new JSONObject(responseData);
                            String serverMsg = errorJson.optString("errors.message", "");
                            if (!serverMsg.isEmpty()) {
                                errorMsg = serverMsg;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing error response: " + e.getMessage());
                        }
                        Toast.makeText(Register.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error", e);
                runOnUiThread(() -> Toast.makeText(Register.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
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
            startActivity(new Intent(this, Login.class));
            finish();
            return true;
        } else if (itemId == android.R.id.home) {
            startActivity(new Intent(this, Login.class));
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