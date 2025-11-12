// File: app/src/main/java/com/example/healthapp/utils/ExerciseFormatter.java
package com.example.healthapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ExerciseFormatter {

    public static class ExerciseEntry {
        public String name, type, location, details;
        public ExerciseEntry(String n, String t, String l, String d) {
            name = n; type = t; location = l; details = d;
        }
    }

    /** Returns JSON **string** for sending to server */
    public static String formatExercises(List<ExerciseEntry> list) {
        if (list == null || list.isEmpty()) return "[]";
        JSONArray arr = new JSONArray();
        try {
            for (ExerciseEntry e : list) {
                JSONObject o = new JSONObject();
                o.put("Name", e.name);
                o.put("Type", e.type);
                o.put("Location", e.location);
                o.put("Details", e.details);
                arr.put(o);
            }
        } catch (JSONException ignored) {}
        return arr.toString();
    }

    /** Parse JSON string from server back into List<ExerciseEntry> */
    public static List<ExerciseEntry> parseExercises(String json) {
        List<ExerciseEntry> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "[]".equals(json)) return result;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                result.add(new ExerciseEntry(
                        o.optString("Name"),
                        o.optString("Type"),
                        o.optString("Location"),
                        o.optString("Details")
                ));
            }
        } catch (JSONException ignored) {}
        return result;
    }
}