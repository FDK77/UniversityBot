package org.example.parseObjects;


public enum SubjectEnum {
    MATH("Математика"),
    RUSSIAN("Русский язык"),
    IT("Информатика и ИКТ"),
    PHYSICS("Физика"),
    SOCIAL_SCIENCE("Обществознание"),
    HISTORY("История"),
    FOREIGN_LANGUAGE("Иностранный язык"),
    PROF_TEST_PUBLIC_ADMIN("Профессиональное испытание \"Государственное и муниципальное управление\""),
    PROF_TEST_CUSTOMS("Профессиональное испытание \"Таможенное дело\"");

    private final String description;

    SubjectEnum(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
