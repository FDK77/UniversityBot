package org.example.parseObjects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScoreInfo {
    @JsonProperty("min_score")
    private Integer minScore;

    @JsonProperty("avg_score")
    private Integer avgScore;

    public Integer getMinScore() {
        return minScore;
    }

    public void setMinScore(Integer minScore) {
        this.minScore = minScore;
    }

    public Integer getAvgScore() {
        return avgScore;
    }

    public void setAvgScore(Integer avgScore) {
        this.avgScore = avgScore;
    }
}
