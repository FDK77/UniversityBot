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

    public List<String> profiles = new ArrayList<>(); // Список профилей

    private List<String> subjects = new ArrayList<>(); // Список для хранения предметов

    private int year;

    @JsonProperty("education_level")
    private String educationLevel;

    @JsonProperty("study_form")
    private String studyForm;

    private Map<String, ScoreInfo> scores;

    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

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

    public List<String> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<String> profiles) {
        this.profiles = profiles;
    }
    @Override
    public String toString() {
        return "Specialty{" +
                "id='" + id + '\'' +
                ", specialty='" + specialty + '\'' +
                ", direction='" + direction + '\'' +
                ", profiles=" + String.join(", ", profiles) + // Объединяем элементы списка в строку
                ", subjects=" + subjects +
                ", year=" + year +
                ", educationLevel='" + educationLevel + '\'' +
                ", studyForm='" + studyForm + '\'' +
                ", scores=" + scores +
                '}';
    }

}
