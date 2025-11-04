package com.example.healthapp;

import java.util.List;

public class ExerciseFormatter {
    public static String formatExercises(List<ExerciseEntry> entries) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            ExerciseEntry entry = entries.get(i);
            result.append(String.format("%s: %s %s %s",
                    entry.exercise, entry.metric, entry.value, entry.unit));
            if (i < entries.size() - 1) result.append(", ");
        }
        return result.toString();
    }

    public static class ExerciseEntry {
        public String exercise;
        public String metric;
        public String value;
        public String unit;

        public ExerciseEntry(String exercise, String metric, String value, String unit) {
            this.exercise = exercise;
            this.metric = metric;
            this.value = value;
            this.unit = unit;
        }
    }
}