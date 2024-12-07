package org.example.parseObjects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Specialty {
    private String id;

    private String specialty;

    private String direction;

    private List<String> subjects = new ArrayList<>(); // Список для хранения предметов

    private int year;

    @JsonProperty("education_level")
    private String educationLevel;

    @JsonProperty("study_form")
    private String studyForm;

    private Map<String, ScoreInfo> scores;

    // Getters and Setters


    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    @JsonSetter("subjects")
    public void setSubjects(String subjectsString) {
        if (subjectsString != null && !subjectsString.isEmpty()) {
            this.subjects = Arrays.asList(subjectsString.split(", "));
        }
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(String educationLevel) {
        this.educationLevel = educationLevel;
    }

    public String getStudyForm() {
        return studyForm;
    }

    public void setStudyForm(String studyForm) {
        this.studyForm = studyForm;
    }

    public Map<String, ScoreInfo> getScores() {
        return scores;
    }

    public void setScores(Map<String, ScoreInfo> scores) {
        this.scores = scores;
    }
}
