package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.bot.UniversityBot;
import org.example.parseObjects.Specialty;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.InputStream;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        try {
            // Загрузка специальностей из JSON файла в ресурсах
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream inputStream = Main.class.getClassLoader().getResourceAsStream("degrees.json");
            if (inputStream == null) {
                throw new RuntimeException("Файл degrees.json не найден в ресурсах.");
            }

            List<Specialty> specialties = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<Specialty>>() {}
            );

            // Инициализация TelegramBots API
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Регистрация бота
            botsApi.registerBot(new UniversityBot(specialties));

            System.out.println("Бот успешно запущен!");
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
