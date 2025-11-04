package com.example.healthapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SharedPrefsHelper {
    private static final String PREF_NAME = "HealthAppPrefs";
    private static final String KEY_ADMIN_ID = "admin_id";
    private static final String KEY_ADMIN_PASSWORD = "admin_password";
    private final SharedPreferences prefs;

    public SharedPrefsHelper(Context context) throws Exception {
        MasterKey masterKey = new MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build();
        prefs = EncryptedSharedPreferences.create(
                context,
                PREF_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        );
    }

    public void saveAdminCredentials(String adminId, String adminPassword) {
        prefs.edit()
                .putString(KEY_ADMIN_ID, adminId)
                .putString(KEY_ADMIN_PASSWORD, adminPassword)
                .apply();
    }

    public String getAdminId() {
        return prefs.getString(KEY_ADMIN_ID, "");
    }

    public String getAdminPassword() {
        return prefs.getString(KEY_ADMIN_PASSWORD, "");
    }
}