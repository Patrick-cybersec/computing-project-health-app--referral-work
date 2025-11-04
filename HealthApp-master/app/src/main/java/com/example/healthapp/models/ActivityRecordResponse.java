package com.example.healthapp.models;

public class ActivityRecordResponse {
    private int Id;
    private String UserId;
    private String ActivityType;
    private float HeartRate; // Changed to float
    private String Mood;
    private String Duration;
    private String Exercises;
    private String Created_At;

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
    public String getCreated_At() { return Created_At; }
    public void setCreated_At(String created_At) { this.Created_At = created_At; }
}