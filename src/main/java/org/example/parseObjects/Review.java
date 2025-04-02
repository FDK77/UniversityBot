package org.example.parseObjects;

public class Review {
    private Long userId;
    private String specialtyId; // или specialtyName, или direction — смотря как удобнее
    private String text;        // текст отзыва
    private int rating;         // если нужно оценивать баллами (например, от 1 до 5). Если нет — можно убрать.

    public Review(Long userId, String specialtyId, String text, int rating) {
        this.userId = userId;
        this.specialtyId = specialtyId;
        this.text = text;
        this.rating = rating;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSpecialtyId() {
        return specialtyId;
    }

    public String getText() {
        return text;
    }

    public int getRating() {
        return rating;
    }
}
