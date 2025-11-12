package com.example.healthapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class PortfolioActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "HealthAppPrefs";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_IS_ADMIN = "isAdmin";
    private static final String KEY_PASSWORD = "password";
    private static final String BASE_URL = "https://a267815f7908.ngrok-free.app";
    private static final String TAG = "Portfolio";
    private OkHttpClient client;
    private RecyclerView usersRecyclerView;
    private UserRecordAdapter usersAdapter;
    private ArrayList<String> users;
    private ArrayList<JSONObject> userObjects;
    private LinearLayout adminControls;
    private Button addButton, editButton, deleteButton, resetPasswordButton;
    private int selectedUserIndex = -1;
    private boolean isFetchingUsers = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.portfolio_screen);

        client = TrustAllOkHttpClient.getClient();

        Toolbar toolbar = findViewById(R.id.toolbar_menu);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("User Portfolio");
        }

        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        adminControls = findViewById(R.id.adminControls);
        addButton = findViewById(R.id.addButton);
        editButton = findViewById(R.id.editButton);
        deleteButton = findViewById(R.id.deleteButton);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);

        if (usersRecyclerView == null || adminControls == null) {
            Log.e(TAG, "RecyclerView or adminControls is null");
            Toast.makeText(this, "UI initialization error", Toast.LENGTH_LONG).show();
            return;
        }

        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        users = new ArrayList<>();
        userObjects = new ArrayList<>();
        usersAdapter = new UserRecordAdapter(users, userObjects, position -> {
            selectedUserIndex = position;
            editButton.setEnabled(position >= 0 && position < userObjects.size());
            deleteButton.setEnabled(position >= 0 && position < userObjects.size());
            resetPasswordButton.setEnabled(position >= 0 && position < userObjects.size());
            Log.d(TAG, "User selected at position: " + position);
        });
        usersRecyclerView.setAdapter(usersAdapter);

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

        fetchUserRecords();
    }

    private void setupAdminControls() {
        addButton.setOnClickListener(v -> showAddEditDialog(null));
        editButton.setEnabled(false);
        editButton.setOnClickListener(v -> {
            if (selectedUserIndex >= 0 && selectedUserIndex < userObjects.size()) {
                showAddEditDialog(userObjects.get(selectedUserIndex));
            } else {
                Toast.makeText(this, "Please select a valid user to edit", Toast.LENGTH_SHORT).show();
            }
        });
        deleteButton.setEnabled(false);
        deleteButton.setOnClickListener(v -> {
            if (selectedUserIndex >= 0 && selectedUserIndex < userObjects.size()) {
                showDeleteUserDialog(userObjects.get(selectedUserIndex));
            } else {
                Toast.makeText(this, "Please select a valid user to delete", Toast.LENGTH_SHORT).show();
            }
        });
        resetPasswordButton.setEnabled(false);
        resetPasswordButton.setOnClickListener(v -> {
            if (selectedUserIndex >= 0 && selectedUserIndex < userObjects.size()) {
                showResetPasswordDialog(userObjects.get(selectedUserIndex));
            } else {
                Toast.makeText(this, "Please select a valid user to reset password", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchUserRecords() {
        if (isFetchingUsers) {
            Log.d(TAG, "fetchUserRecords already in progress, skipping");
            return;
        }
        isFetchingUsers = true;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String userId = prefs.getString(KEY_USER_ID, null);
        String password = prefs.getString(KEY_PASSWORD, null);
        boolean isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false);
        if (userId == null || password == null) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Please log in first", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, Login.class));
                finish();
            });
            isFetchingUsers = false;
            return;
        }

        String url = isAdmin ?
                BASE_URL + "/api/Users?adminId=" + userId + "&adminPassword=" + password :
                BASE_URL + "/api/Users/" + userId + "?requestingUserId=" + userId + "&requestingUserPassword=" + password;

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Accept", "*/*")
                        .addHeader("User-Agent", "Mozilla/5.0")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Fetch users response: HTTP " + response.code() + ", Data: " + responseBody);
                    if (response.isSuccessful()) {
                        ArrayList<String> newUsers = new ArrayList<>();
                        ArrayList<JSONObject> newUserObjects = new ArrayList<>();

                        if (isAdmin) {
                            JSONArray usersArray = new JSONArray(responseBody);
                            for (int i = 0; i < usersArray.length(); i++) {
                                JSONObject user = usersArray.getJSONObject(i);
                                newUserObjects.add(user);
                                newUsers.add(formatUserText(user));
                            }
                        } else {
                            JSONObject user = new JSONObject(responseBody);
                            newUserObjects.add(user);
                            newUsers.add(formatUserText(user));
                        }

                        runOnUiThread(() -> {
                            users.clear();
                            users.addAll(newUsers);
                            userObjects.clear();
                            userObjects.addAll(newUserObjects);
                            usersAdapter.updateData(newUsers, newUserObjects);
                            selectedUserIndex = -1;
                            editButton.setEnabled(false);
                            deleteButton.setEnabled(false);
                            resetPasswordButton.setEnabled(false);
                            if (newUsers.isEmpty()) {
                                Toast.makeText(this, "No users found", Toast.LENGTH_SHORT).show();
                            }
                            isFetchingUsers = false;
                        });
                    } else {
                        String errorMessage = isAdmin ? "Failed to fetch users: HTTP " + response.code() + ": " + responseBody :
                                "Failed to fetch user data: HTTP " + response.code() + ": " + responseBody;
                        Log.e(TAG, "Fetch users failed: " + errorMessage);
                        runOnUiThread(() -> {
                            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                            isFetchingUsers = false;
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error fetching users: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error fetching users: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    isFetchingUsers = false;
                });
            }
        }).start();
    }

    private String formatUserText(JSONObject user) throws Exception {
        StringBuilder userText = new StringBuilder();
        userText.append("Name: ").append(user.getString("name")).append("\n")
                .append("ID: ").append(user.getString("id")).append("\n")
                .append("Age: ").append(user.getInt("age")).append("\n")
                .append("Sex: ").append(user.getString("sex"));
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_IS_ADMIN, false)) {
            userText.append("\n").append("Admin: ").append(user.getBoolean("isAdmin") ? "Yes" : "No");
        }
        return userText.toString();
    }

    private void showAddEditDialog(JSONObject existingUser) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(existingUser == null ? "Add User" : "Edit User");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        EditText idInput = new EditText(this);
        idInput.setHint("ID");
        EditText passwordInput = new EditText(this);
        passwordInput.setHint(existingUser == null ? "Password" : "New Password (optional)");
        EditText ageInput = new EditText(this);
        ageInput.setHint("Age");
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        Spinner sexSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> sexAdapter = ArrayAdapter.createFromResource(this,
                R.array.sex_options, android.R.layout.simple_spinner_item);
        sexAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sexSpinner.setAdapter(sexAdapter);
        Spinner adminSpinner = new Spinner(this);
        ArrayAdapter<CharSequence> adminAdapter = ArrayAdapter.createFromResource(this,
                R.array.admin_options, android.R.layout.simple_spinner_item);
        adminAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adminSpinner.setAdapter(adminAdapter);

        if (existingUser != null) {
            try {
                nameInput.setText(existingUser.getString("name"));
                idInput.setText(existingUser.getString("id"));
                idInput.setEnabled(false);
                ageInput.setText(String.valueOf(existingUser.getInt("age")));
                String sex = existingUser.getString("sex");
                for (int i = 0; i < sexAdapter.getCount(); i++) {
                    if (sexAdapter.getItem(i).toString().equals(sex)) {
                        sexSpinner.setSelection(i);
                        break;
                    }
                }
                adminSpinner.setSelection(existingUser.getBoolean("isAdmin") ? 1 : 0);
            } catch (Exception e) {
                Log.e(TAG, "Error loading user data: " + e.getMessage());
                Toast.makeText(this, "Error loading user data", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        layout.addView(nameInput);
        layout.addView(idInput);
        layout.addView(passwordInput);
        layout.addView(ageInput);
        layout.addView(sexSpinner);
        layout.addView(adminSpinner);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String id = idInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String age = ageInput.getText().toString().trim();
            String sex = sexSpinner.getSelectedItem().toString();
            String adminStatus = adminSpinner.getSelectedItem().toString();

            if (name.isEmpty() || id.isEmpty() || age.isEmpty() || sex.equals("Select Sex") || adminStatus.equals("Select Admin Status")) {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show();
                return;
            }
            if (name.length() > 50 || id.length() > 50 || password.length() > 255) {
                Toast.makeText(this, "Input exceeds length limits", Toast.LENGTH_SHORT).show();
                return;
            }
            if (existingUser == null && password.isEmpty()) {
                Toast.makeText(this, "Password required for new users", Toast.LENGTH_SHORT).show();
                return;
            }
            int ageValue;
            try {
                ageValue = Integer.parseInt(age);
                if (ageValue < 0 || ageValue > 150) {
                    Toast.makeText(this, "Age must be 0-150", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid age format", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject user = new JSONObject();
                user.put("id", id);
                user.put("name", name);
                user.put("age", ageValue);
                user.put("sex", sex);
                user.put("isAdmin", adminStatus.equals("Yes"));
                if (!password.isEmpty()) {
                    user.put("password", password);
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
                requestBody.put("user", user);

                Log.d(TAG, "Submitting " + (existingUser == null ? "add" : "edit") + " request: " + requestBody.toString());
                if (existingUser == null) {
                    addUser(requestBody);
                } else {
                    editUser(requestBody);
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error creating user JSON: " + e.getMessage());
                Toast.makeText(this, "Invalid input format", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            nameInput.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
        dialog.show();
    }

    private void showResetPasswordDialog(JSONObject user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Reset Password for " + user.optString("id"));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(16, 16, 16, 16);

        EditText newPasswordInput = new EditText(this);
        newPasswordInput.setHint("New User Password");

        layout.addView(newPasswordInput);

        builder.setView(layout);
        builder.setPositiveButton("Reset", (dialog, which) -> {
            String newPassword = newPasswordInput.getText().toString().trim();
            if (newPassword.isEmpty()) {
                Toast.makeText(this, "Please enter a new password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPassword.length() > 255) {
                Toast.makeText(this, "Password must be 255 characters or less", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                String adminId = prefs.getString(KEY_USER_ID, "");
                String adminPassword = prefs.getString(KEY_PASSWORD, "");
                String userId = user.getString("id");

                if (adminId.isEmpty() || adminPassword.isEmpty()) {
                    Toast.makeText(this, "Admin credentials not found. Please log in again.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, Login.class));
                    finish();
                    return;
                }

                JSONObject requestBody = new JSONObject();
                requestBody.put("adminId", adminId);
                requestBody.put("adminPassword", adminPassword);
                requestBody.put("userId", userId);
                requestBody.put("newPassword", newPassword);

                Log.d(TAG, "Submitting reset password request: " + requestBody.toString());
                resetUserPassword(requestBody);
            } catch (Exception e) {
                Log.e(TAG, "Error creating reset password JSON: " + e.getMessage());
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            newPasswordInput.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        });
        dialog.show();
    }

    private void showDeleteUserDialog(JSONObject user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Delete User");
        builder.setMessage("Are you sure you want to delete user " + user.optString("id") + "?");
        builder.setPositiveButton("Delete", (dialog, which) -> {
            try {
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

                Log.d(TAG, "Submitting delete request: " + requestBody.toString());
                deleteUser(user.getString("id"), requestBody);
            } catch (Exception e) {
                Log.e(TAG, "Error creating delete JSON: " + e.getMessage());
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.create().show();
    }

    private void addUser(JSONObject requestBody) {
        new Thread(() -> {
            try {
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/Users/register")
                        .post(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Add user response: HTTP " + response.code() + ", Data: " + responseBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "User added successfully", Toast.LENGTH_SHORT).show();
                            fetchUserRecords();
                        });
                    } else {
                        if (response.code() == 400) {
                            Log.e(TAG, "Add user failed: Invalid user data. Please check input fields.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid user data. Please check input fields.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 401) {
                            Log.e(TAG, "Add user failed: Invalid admin credentials.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid admin credentials.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 409) {
                            Log.e(TAG, "Add user failed: User ID already exists.");
                            runOnUiThread(() -> Toast.makeText(this, "User ID already exists.", Toast.LENGTH_LONG).show());
                        } else {
                            Log.e(TAG, "Add user failed: HTTP " + response.code() + ": " + responseBody);
                            runOnUiThread(() -> Toast.makeText(this, "Failed to add user: HTTP " + response.code() + ": " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error adding user: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error adding user: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void editUser(JSONObject requestBody) {
        new Thread(() -> {
            try {
                String id = requestBody.getJSONObject("user").getString("id");
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/Users/" + id)
                        .put(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Edit user response: HTTP " + response.code() + ", Data: " + responseBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "User updated successfully", Toast.LENGTH_SHORT).show();
                            fetchUserRecords();
                        });
                    } else {
                        String errorDetail;
                        try {
                            JSONObject errorJson = new JSONObject(responseBody);
                            errorDetail = errorJson.optJSONObject("errors") != null ?
                                    errorJson.getJSONObject("errors").toString() : responseBody;
                        } catch (Exception e) {
                            errorDetail = responseBody;
                        }
                        final String message = "Failed to update user: HTTP " + response.code() + ": " + errorDetail;
                        Log.e(TAG, "Edit user failed: " + message);
                        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Invalid request format", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e(TAG, "Error updating user: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error updating user: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void resetUserPassword(JSONObject requestBody) {
        new Thread(() -> {
            try {
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/Users/reset-password")
                        .post(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Reset password response: HTTP " + response.code() + ", Data: " + responseBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> Toast.makeText(this, "Password reset successfully", Toast.LENGTH_SHORT).show());
                    } else {
                        if (response.code() == 400) {
                            Log.e(TAG, "Reset password failed: Invalid request data.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid request data.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 401) {
                            Log.e(TAG, "Reset password failed: Invalid admin credentials.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid admin credentials.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 404) {
                            Log.e(TAG, "Reset password failed: User not found.");
                            runOnUiThread(() -> Toast.makeText(this, "User not found.", Toast.LENGTH_LONG).show());
                        } else {
                            Log.e(TAG, "Reset password failed: HTTP " + response.code() + ": " + responseBody);
                            runOnUiThread(() -> Toast.makeText(this, "Failed to reset password: HTTP " + response.code() + ": " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error resetting password: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error resetting password: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void deleteUser(String id, JSONObject requestBody) {
        new Thread(() -> {
            try {
                RequestBody body = RequestBody.create(requestBody.toString(), MediaType.parse("application/json; charset=utf-8"));
                Request request = new Request.Builder()
                        .url(BASE_URL + "/api/Users/" + id)
                        .delete(body)
                        .addHeader("Accept", "*/*")
                        .addHeader("ngrok-skip-browser-warning", "true")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "Delete user response: HTTP " + response.code() + ", Data: " + responseBody);
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "User deleted successfully", Toast.LENGTH_SHORT).show();
                            fetchUserRecords();
                        });
                    } else {
                        if (response.code() == 400) {
                            Log.e(TAG, "Delete user failed: Invalid request data.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid request data.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 401) {
                            Log.e(TAG, "Delete user failed: Invalid admin credentials.");
                            runOnUiThread(() -> Toast.makeText(this, "Invalid admin credentials.", Toast.LENGTH_LONG).show());
                        } else if (response.code() == 404) {
                            Log.e(TAG, "Delete user failed: User not found.");
                            runOnUiThread(() -> Toast.makeText(this, "User not found.", Toast.LENGTH_LONG).show());
                        } else {
                            Log.e(TAG, "Delete user failed: HTTP " + response.code() + ": " + responseBody);
                            runOnUiThread(() -> Toast.makeText(this, "Failed to delete user: HTTP " + response.code() + ": " + responseBody, Toast.LENGTH_LONG).show());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting user: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Error deleting user: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
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
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply();
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