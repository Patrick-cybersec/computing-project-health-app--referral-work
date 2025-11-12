package com.example.healthapp.models;

public class ActivityRecordRequest {
    private String AdminId;
    private String AdminPassword;
    private Record Record;

    public ActivityRecordRequest(String adminId, String adminPassword, Record record) {
        this.AdminId = adminId;
        this.AdminPassword = adminPassword;
        this.Record = record;
    }

    public String getAdminId() { return AdminId; }
    public void setAdminId(String adminId) { this.AdminId = adminId; }
    public String getAdminPassword() { return AdminPassword; }
    public void setAdminPassword(String adminPassword) { this.AdminPassword = adminPassword; }
    public Record getRecord() { return Record; }
    public void setRecord(Record record) { this.Record = record; }

    public static class Record {
        private int Id;
        private String UserId;
        private String ActivityType;
        private float HeartRate;
        private String Mood;
        private String Duration;
        private String Exercises;

        public Record(int id, String userId, String activityType, float heartRate, String mood, String duration, String exercises) {
            this.Id = id;
            this.UserId = userId;
            this.ActivityType = activityType;
            this.HeartRate = heartRate;
            this.Mood = mood;
            this.Duration = duration;
            this.Exercises = exercises;
        }

        public int getId() { return Id; }
        public void setId(int id) { this.Id = id; }
        public String getUserId() { return UserId; }
        public void setUserId(String userId) { this.UserId = userId; }
        public String getActivityType() { return ActivityType; }
        public void setActivityType(String activityType) { this.ActivityType = activityType; }
        public float getHeartRate() { return HeartRate; }
        public void setHeartRate(float heartRate) { this.HeartRate = heartRate; }
        public String getMood() { return Mood; }
        public void setMood(String mood) { this.Mood = mood; }
        public String getDuration() { return Duration; }
        public void setDuration(String duration) { this.Duration = duration; }
        public String getExercises() { return Exercises; }
        public void setExercises(String exercises) { this.Exercises = exercises; }
    }
}