package org.example.parseObjects;

import java.util.List;

public class GroupCode {
    private String code;
    private String name;
    private List<String> subjects;

    public GroupCode(String code, String name, List<String> subjects) {
        this.code = code;
        this.name = name;
        this.subjects = subjects;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}