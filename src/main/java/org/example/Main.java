package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bot.UniversityBot;
import org.example.parseObjects.GroupCode;
import org.example.parseObjects.Specialty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
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
                    new TypeReference<List<Specialty>>() {}
            );

            // Загрузка кодов групп
            InputStream groupCodesStream = Main.class.getClassLoader().getResourceAsStream("codes_to_groups.json");
            if (groupCodesStream == null) {
                throw new RuntimeException("Файл codes_to_groups.json не найден в ресурсах.");
            }
            Map<String, String> groupCodesMap = objectMapper.readValue(
                    groupCodesStream,
                    new TypeReference<Map<String, String>>() {}
            );

            List<GroupCode> groupCodes = groupCodesMap.entrySet()
                    .stream()
                    .map(entry -> new GroupCode(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());

            // Инициализация бота
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new UniversityBot(specialties, groupCodes));

            System.out.println("Бот успешно запущен!");
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
