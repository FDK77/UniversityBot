package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bot.NotificationScheduler;
import org.example.bot.UniversityBot;
import org.example.parseObjects.GroupCode;
import org.example.parseObjects.Specialty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {

    public static class GroupJson {
        public String group_name;
        public List<String> subjects;

        public GroupJson() {} // обязательно нужен для Jackson
    }

    public static void main(String[] args) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Загрузка специальностей
            InputStream specialtiesStream = Main.class.getClassLoader().getResourceAsStream("degrees.json");
            if (specialtiesStream == null) {
                throw new RuntimeException("Файл degrees.json не найден в ресурсах.");
            }
            List<Specialty> specialties = objectMapper.readValue(
                    specialtiesStream,
                    new TypeReference<List<Specialty>>() {
                    }
            );

            // Загрузка кодов групп
            InputStream groupCodesStream = Main.class.getClassLoader().getResourceAsStream("updated_codes_to_groups.json");
            if (groupCodesStream == null) {
                throw new RuntimeException("Файл updated_codes_to_groups.json не найден в ресурсах.");
            }

// Класс для промежуточной структуры JSON

// Преобразуем JSON в Map<String, GroupJson>
            Map<String, GroupJson> groupCodesRaw = objectMapper.readValue(
                    groupCodesStream,
                    new TypeReference<Map<String, GroupJson>>() {}
            );

// Преобразуем в List<GroupCode>
            List<GroupCode> groupCodes = groupCodesRaw.entrySet()
                    .stream()
                    .map(entry -> new GroupCode(
                            entry.getKey(),
                            entry.getValue().group_name,
                            entry.getValue().subjects
                    ))
                    .collect(Collectors.toList());

            // Обработка специальностей для добавления профилей
            List<Specialty> updatedSpecialties = processSpecialties(specialties);

            // Инициализация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            UniversityBot universityBot = new UniversityBot(updatedSpecialties, groupCodes);
            NotificationScheduler scheduler = new NotificationScheduler(universityBot);
            scheduler.start();
            botsApi.registerBot(universityBot);
            Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop));
            System.out.println("Бот успешно запущен!");

        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Метод для обработки специальностей и заполнения профилей.
     */
    public static List<Specialty> processSpecialties(List<Specialty> specialties) {
        // Карта направлений для отслеживания уже обработанных направлений
        Map<String, Specialty> directionMap = new HashMap<>();

        // Используем итератор для безопасного удаления элементов из списка
        var iterator = specialties.iterator();

        while (iterator.hasNext()) {
            Specialty specialty = iterator.next();

            // Проверяем, есть ли уже направление в карте
            if (directionMap.containsKey(specialty.getDirection())) {
                Specialty existingSpecialty = directionMap.get(specialty.getDirection());

                // Проверяем, что оценки содержат ключ "Общий конкурс" и его минимальный балл равен null
                if (specialty.getScores() != null
                        && specialty.getScores().containsKey("Общий конкурс")
                        && specialty.getScores().get("Общий конкурс") != null
                        && specialty.getScores().get("Общий конкурс").getMinScore() == null
                        && Objects.equals(existingSpecialty.getStudyForm(), specialty.getStudyForm())) {
                    // Обрезаем текст до последней точки
                    String specialtyName = specialty.getSpecialty().trim();
                    int lastDotIndex = specialtyName.lastIndexOf('.');
                    if (lastDotIndex != -1) {
                        specialtyName = specialtyName.substring(lastDotIndex + 1).trim();
                    }
                    // Добавляем обработанное название профиля
                    existingSpecialty.profiles.add(specialtyName);
                    // Удаляем текущую специальность, так как она уже добавлена
                    iterator.remove();
                }
            } else {
                // Если направления еще нет, добавляем его в карту
                directionMap.put(specialty.getDirection(), specialty);
            }
        }

        // Возвращаем обновлённый список
        return specialties;
    }

}