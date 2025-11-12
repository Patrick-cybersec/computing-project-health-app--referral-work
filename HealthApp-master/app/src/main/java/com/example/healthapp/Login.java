package com.example.healthapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Login extends AppCompatActivity {
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String BASE_URL = "https://a267815f7908.ngrok-free.app";
    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String TAG = "LoginActivity";
    private OkHttpClient client;
    private EditText idInput, passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);

        // Check SharedPreferences for existing login
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        Log.d(TAG, "Checking SharedPreferences: userId=" + userId);
        if (userId != null) {
            Log.d(TAG, "User already logged in, redirecting to Dashboard");
            // Clear focus before navigation
            clearFocusAndHideIME();
            startActivity(new Intent(this, Dashboard.class));
            finish();
            return;
        }

        client = TrustAllOkHttpClient.getClient();
        idInput = findViewById(R.id.idEditText);
        passwordInput = findViewById(R.id.passwordEditText);
        Button loginButton = findViewById(R.id.loginButton);
        Button registerButton = findViewById(R.id.registerButton);

        // Add focus listeners to hide IME on focus loss
        setupFocusListener(idInput);
        setupFocusListener(passwordInput);

        loginButton.setOnClickListener(v -> {
            String id = idInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            Log.d(TAG, "Login attempt with ID: " + id);
            if (id.isEmpty() || password.isEmpty()) {
                Log.w(TAG, "Empty ID or password");
                Toast.makeText(this, "Please enter ID and password", Toast.LENGTH_SHORT).show();
                return;
            }

            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("id", id);
                requestBody.put("password", password);
            } catch (Exception e) {
                Log.e(TAG, "Error creating JSON", e);
                return;
            }

            RequestBody body = RequestBody.create(requestBody.toString(), JSON);
            Request request = new Request.Builder()
                    .url(BASE_URL + "/api/Users/login")
                    .post(body)
                    .addHeader("ngrok-skip-browser-warning", "true")
                    .build();

            new Thread(() -> {
                try {
                    Log.d(TAG, "Sending login request to " + BASE_URL + "/api/Users/login");
                    Response response = client.newCall(request).execute();
                    String responseData = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Login response: HTTP " + response.code() + ", Data: " + responseData);
                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(responseData);
                        SharedPreferences prefsInner = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefsInner.edit()
                                .putString("userId", json.getString("id"))
                                .putBoolean("isAdmin", json.getBoolean("isAdmin"))
                                .putString("password", password)
                                .apply();
                        Log.d(TAG, "Login successful for ID: " + json.getString("id") + ", isAdmin: " + json.getBoolean("isAdmin"));
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                            // Clear focus before navigation
                            clearFocusAndHideIME();
                            startActivity(new Intent(this, Dashboard.class));
                            finish();
                        });
                    } else {
                        Log.e(TAG, "Login failed: HTTP " + response.code() + ", Response: " + responseData);
                        runOnUiThread(() -> Toast.makeText(this, "Login failed: " + responseData, Toast.LENGTH_LONG).show());
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Network error during login: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "Network error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected error during login: " + e.getMessage());
                    runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }).start();
        });

        registerButton.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to Register activity");
            // Clear focus before navigation
            clearFocusAndHideIME();
            startActivity(new Intent(this, Register.class));
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
}