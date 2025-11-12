package com.example.healthapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
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
    private static final String BASE_URL = "https://1c05e1adb0b1.ngrok-free.app";
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

        // Setup focus listeners for all EditTexts
        setupFocusListener(nameEditText);
        setupFocusListener(idEditText);
        setupFocusListener(passwordEditText);
        setupFocusListener(ageEditText);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.sex_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sexSpinner.setAdapter(adapter);

        Button createAccountButton = findViewById(R.id.createAccountButton);
        createAccountButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String id = idEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String ageStr = ageEditText.getText().toString().trim();
            String sex = sexSpinner.getSelectedItem().toString();

            if (name.isEmpty() || id.isEmpty() || password.isEmpty() || ageStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int age = Integer.parseInt(ageStr);

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("Id", id);
                requestBody.put("Password", password);
                requestBody.put("Name", name);
                requestBody.put("Age", age);
                requestBody.put("Sex", sex);
            } catch (Exception e) {
                Log.e(TAG, "Error creating JSON", e);
                return;
            }

            RequestBody body = RequestBody.create(requestBody.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/Users/public-register")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String errorMsg = response.body() != null ? response.body().string() : "Unknown error";
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(Register.this, "Registration successful!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(Register.this, Login.class));
                            finish();
                        });
                    } else {
                        Log.e("Register", "Failed: HTTP " + response.code() + ", Body: " + errorMsg);
                        runOnUiThread(() -> Toast.makeText(Register.this, errorMsg, Toast.LENGTH_LONG).show());
                    }
                    response.close();
                }

                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Network error", e);
                    runOnUiThread(() -> Toast.makeText(Register.this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    // Helper: Hide IME and clear focus
    private void clearFocusAndHideIME() {
        View currentFocus = getCurrentFocus();
        if (currentFocus != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            currentFocus.clearFocus();
        }
    }

    // Helper: Setup focus listener for EditText
    private void setupFocusListener(EditText editText) {
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        clearFocusAndHideIME();
    }

    // Existing menu methods (unchanged)
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