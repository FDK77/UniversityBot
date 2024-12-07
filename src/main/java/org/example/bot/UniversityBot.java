package org.example.bot;

import org.example.parseObjects.SubjectEnum;
import org.example.parseObjects.Specialty;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

public class UniversityBot extends TelegramLongPollingBot {
    private final Map<Long, List<String>> userSubjects = new HashMap<>(); // Выбранные предметы
    private final Map<Long, Map<String, Integer>> userScores = new HashMap<>(); // Баллы по предметам
    private final Map<Long, String> userQuotas = new HashMap<>(); // Выбранная квота

    private final List<Specialty> specialties; // Список специальностей

    public UniversityBot(List<Specialty> specialties) {
        this.specialties = specialties;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleText(update);
        }
    }

    private void handleText(Update update) {
        Long userId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        if (text.equals("/start")) {
            sendQuotaSelectionMessage(userId);
        } else if (userSubjects.containsKey(userId)) {
            handleScoreInput(userId, text);
        } else {
            sendMessage(userId, "Нажмите /start для начала.");
        }
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();

        if (data.startsWith("QUOTA_")) {
            String quota = data.substring(6); // Убираем "QUOTA_"
            userQuotas.put(userId, quota);
            sendSubjectSelectionMessage(userId);
        } else if (data.equals("DONE")) {
            if (userSubjects.containsKey(userId) && !userSubjects.get(userId).isEmpty()) {
                askForScore(userId);
            } else {
                sendMessage(userId, "Выберите хотя бы один предмет.");
            }
        } else {
            userSubjects.computeIfAbsent(userId, k -> new ArrayList<>()).add(data);
            sendSubjectSelectionMessage(userId, "Вы выбрали: " + data);
        }
    }

    private void sendQuotaSelectionMessage(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Выберите квоту:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("Общий конкурс").callbackData("QUOTA_Общий конкурс").build()
        ));
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("Целевая квота").callbackData("QUOTA_Целевая квота").build()
        ));
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("Особая квота").callbackData("QUOTA_Особая квота").build()
        ));

        keyboard.setKeyboard(buttons);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendSubjectSelectionMessage(Long chatId) {
        sendSubjectSelectionMessage(chatId, "Выберите предметы (нажмите 'Готово' для завершения):");
    }

    private void sendSubjectSelectionMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(message);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        for (SubjectEnum subject : SubjectEnum.values()) {
            buttons.add(Collections.singletonList(
                    InlineKeyboardButton.builder().text(subject.getDescription()).callbackData(subject.name()).build()
            ));
        }
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("Готово").callbackData("DONE").build()
        ));

        keyboard.setKeyboard(buttons);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleScoreInput(Long userId, String text) {
        String currentSubject = userSubjects.get(userId).get(0);
        try {
            int score = Integer.parseInt(text.trim());
            userScores.computeIfAbsent(userId, k -> new HashMap<>()).put(currentSubject, score);
            userSubjects.get(userId).remove(0);

            if (userSubjects.get(userId).isEmpty()) {
                sendResult(userId);
            } else {
                askForScore(userId);
            }
        } catch (NumberFormatException e) {
            sendMessage(userId, "Введите корректный балл (число).");
        }
    }

    private void askForScore(Long userId) {
        String nextSubject = userSubjects.get(userId).get(0);
        sendMessage(userId, "Введите баллы для предмета: " + nextSubject);
    }

    private void sendResult(Long userId) {
        String quota = userQuotas.get(userId);
        if (quota == null) {
            sendMessage(userId, "Вы не выбрали квоту.");
            return;
        }

        Map<String, Integer> scores = userScores.get(userId);
        List<Specialty> availableSpecialties = filterSpecialties(scores, quota);

        if (availableSpecialties.isEmpty()) {
            sendMessage(userId, "К сожалению, вам не хватает баллов для поступления на выбранные специальности.");
        } else {
            StringBuilder result = new StringBuilder("Вы можете поступить на следующие специальности:\n");
            for (Specialty specialty : availableSpecialties) {
                result.append(specialty.getSpecialty())
                        .append(" (")
                        .append(specialty.getDirection())
                        .append(") — Минимальный балл по квоте: ")
                        .append(specialty.getScores().get(quota).getMinScore())
                        .append("\n");
            }
            sendMessage(userId, result.toString());
        }

        // Сброс состояния
        userSubjects.remove(userId);
        userScores.remove(userId);
        userQuotas.remove(userId);
    }

    private List<Specialty> filterSpecialties(Map<String, Integer> scores, String quota) {
        List<Specialty> result = new ArrayList<>();
        for (Specialty specialty : specialties) {
            int totalScore = 0;
            boolean eligible = true;

            for (String subject : specialty.getSubjects()) {
                if (subject.contains("/")) {
                    String[] alternatives = subject.split("/");
                    boolean passed = false;
                    for (String alt : alternatives) {
                        if (scores.containsKey(alt) && scores.get(alt) != null) {
                            totalScore += scores.get(alt);
                            passed = true;
                            break;
                        }
                    }
                    if (!passed) {
                        eligible = false;
                        break;
                    }
                } else {
                    if (!scores.containsKey(subject) || scores.get(subject) == null) {
                        eligible = false;
                        break;
                    }
                    totalScore += scores.get(subject);
                }
            }

            Integer minScore = specialty.getScores().get(quota) != null ? specialty.getScores().get(quota).getMinScore() : null;
            if (eligible && minScore != null && totalScore >= minScore) {
                result.add(specialty);
            }
        }
        return result;
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return "UniversityGuideBot";
    }

    @Override
    public String getBotToken() {
        return "7632414334:AAHmiUa_LBgkm6GXp5Lw1jh6ZgXt8jt3HYg";
    }
}
