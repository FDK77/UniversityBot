package org.example.bot;

import org.example.parseObjects.GroupCode;
import org.example.parseObjects.SubjectEnum;
import org.example.parseObjects.Specialty;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;

public class UniversityBot extends TelegramLongPollingBot {

    private final Map<Long, List<String>> userSubjects = new HashMap<>(); // Выбранные предметы
    private final Map<Long, Map<String, Integer>> userScores = new HashMap<>(); // Баллы по предметам
    private final Map<Long, String> userQuotas = new HashMap<>(); // Выбранная квота
    private final Map<Long, Integer> userMessageId = new HashMap<>(); // Хранилище ID сообщений
    private final List<Specialty> specialties; // Список специальностей
    private final List<GroupCode> groupCodes; // Список групп
    private final Map<Long, List<String>> userPreferredGroups = new HashMap<>(); // Выбранные группы

    public UniversityBot(List<Specialty> specialties, List<GroupCode> groupCodes) {
        this.specialties = specialties;
        this.groupCodes = groupCodes;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleText(update);
        }
    }

    private void handleScoreInput(Long userId, String text, Integer messageId) {
        try {
            int score = Integer.parseInt(text.trim()); // Преобразуем текст в число

            // Проверяем диапазон значений
            if (score < 0 || score > 100) {
                sendMessage(userId, "Пожалуйста, введите число от 0 до 100.");
                return;
            }

            List<String> subjects = userSubjects.get(userId);
            if (subjects == null || subjects.isEmpty()) {
                sendMessage(userId, "Сначала выберите предметы.");
                return;
            }

            String currentSubject = subjects.remove(0); // Удаляем текущий предмет из списка
            userScores.computeIfAbsent(userId, k -> new HashMap<>()).put(currentSubject, score);

            if (subjects.isEmpty()) {
                // Если все предметы обработаны, создаём сообщение для выбора групп
                sendMessage(userId, "Баллы успешно сохранены! Теперь выберите группы.");
                int groupMessageId = sendGroupSelectionMessage(userId); // Создаём новое сообщение для групп
                userMessageId.put(userId, groupMessageId); // Сохраняем ID сообщения для обновления
            } else {
                // Запрашиваем баллы для следующего предмета
                sendMessage(userId, "Введите баллы для предмета: " + SubjectEnum.valueOf(subjects.get(0)).getDescription());
            }
        } catch (NumberFormatException e) {
            sendMessage(userId, "Пожалуйста, введите корректное число.");
        }
    }




    private void updateMessage(Long chatId, String text, Integer messageId) {
        EditMessageText editMessageText = new EditMessageText();
        editMessageText.setChatId(chatId.toString());
        editMessageText.setMessageId(messageId);
        editMessageText.setText(text);

        try {
            execute(editMessageText);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void handleText(Update update) {
        Long userId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        Integer messageId = update.getMessage().getMessageId(); // Получение ID сообщения

        if (text.equals("/start")) {
            sendQuotaSelectionMessage(userId);
        } else if (userSubjects.containsKey(userId)) {
            handleScoreInput(userId, text, messageId); // Передаем ID сообщения
        } else {
            sendMessage(userId, "Нажмите /start для начала.");
        }
    }



    private void handleCallback(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();

        if (data.startsWith("QUOTA_")) {
            // Обработка выбора квоты
            String quota = data.substring(6);
            userQuotas.put(userId, quota);

            // Подтверждаем выбор квоты
            sendMessage(userId, "Вы выбрали квоту: " + quota);

            // Создаем новое сообщение для выбора предметов
            sendSubjectSelectionForm(userId);
        }
        else if (data.startsWith("SUBJECT_")) {
            // Обработка выбора предмета
            String subject = data.substring(8); // Убираем префикс "SUBJECT_"
            List<String> selectedSubjects = userSubjects.computeIfAbsent(userId, k -> new ArrayList<>());

            if (selectedSubjects.contains(subject)) {
                selectedSubjects.remove(subject); // Убираем предмет, если он уже выбран
            } else {
                selectedSubjects.add(subject); // Добавляем предмет, если он не выбран
            }

            // Обновляем кнопки в одном сообщении
            sendSubjectSelectionMessage(userId, messageId);
        } else if (data.equals("DONE_SUBJECTS")) {
            // Завершение выбора предметов
            if (userSubjects.containsKey(userId) && !userSubjects.get(userId).isEmpty()) {
                askForScore(userId); // Переход к вводу баллов
            } else {
                sendMessage(userId, "Пожалуйста, выберите хотя бы один предмет.");
            }
        } else if (data.startsWith("GROUP_")) {
            String groupCode = data.substring(6);
            List<String> selectedGroups = userPreferredGroups.computeIfAbsent(userId, k -> new ArrayList<>());

            if (selectedGroups.contains(groupCode)) {
                selectedGroups.remove(groupCode); // Убираем группу
            } else {
                selectedGroups.add(groupCode); // Добавляем группу
            }

            // Обновляем сообщение с кнопками
            updateGroupSelectionMessage(userId);
        } else if (data.equals("DONE_GROUPS")) {
            sendMessage(userId, "Вы успешно завершили выбор групп!");
            sendAvailableSpecialties(userId); // Добавляем вызов метода для показа доступных специальностей
        }

        else if (data.equals("RESET")) {
            // Сброс всех данных
            resetUserData(userId);
            sendQuotaSelectionMessage(userId);
        }
    }

    private void sendMessageInParts(Long chatId, String fullMessage) {
        int maxLength = 4096; // Максимальная длина сообщения Telegram
        int start = 0;
        while (start < fullMessage.length()) {
            int end = Math.min(fullMessage.length(), start + maxLength);
            String part = fullMessage.substring(start, end);
            sendMessage(chatId, part);
            start = end;
        }
    }


    private void sendSafeMessage(Long chatId, String message) {
        if (message.length() > 4096) {
            sendMessageInParts(chatId, message);
        } else {
            sendMessage(chatId, message);
        }
    }


    private InlineKeyboardMarkup generateGroupSelectionKeyboard(Long userId) {
        List<String> selectedGroups = userPreferredGroups.getOrDefault(userId, new ArrayList<>());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (GroupCode group : groupCodes) {
            String buttonText = selectedGroups.contains(group.getCode())
                    ? "✅ " + group.getName()
                    : "☑️ " + group.getName();

            buttons.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(buttonText)
                            .callbackData("GROUP_" + group.getCode())
                            .build()
            ));
        }

        // Кнопка завершения выбора
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("✅ Готово")
                        .callbackData("DONE_GROUPS")
                        .build()
        ));

        keyboard.setKeyboard(buttons);
        return keyboard;
    }


    private void sendSubjectSelectionForm(Long userId) {
        SendMessage newMessage = new SendMessage();
        newMessage.setChatId(userId);
        newMessage.setText("Выберите дисциплины:");

        InlineKeyboardMarkup keyboard = generateSubjectSelectionKeyboard(userId);
        newMessage.setReplyMarkup(keyboard);

        try {
            Message message = execute(newMessage);
            userMessageId.put(userId, message.getMessageId()); // Сохраняем ID сообщения
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(userId, "Ошибка при отображении формы выбора дисциплин.");
        }
    }

    private int sendGroupSelectionMessage(Long userId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(userId.toString());
        sendMessage.setText("Выберите предпочтительные группы специальностей:");

        InlineKeyboardMarkup keyboard = generateGroupSelectionKeyboard(userId);
        sendMessage.setReplyMarkup(keyboard);

        try {
            Message sentMessage = execute(sendMessage); // Отправляем сообщение и получаем его ID
            return sentMessage.getMessageId();
        } catch (Exception e) {
            e.printStackTrace();
            return -1; // Возвращаем -1 в случае ошибки
        }
    }

    private void updateGroupSelectionMessage(Long userId) {
        Integer messageId = userMessageId.get(userId);
        if (messageId == null || messageId == -1) {
            sendMessage(userId, "Ошибка при обновлении формы выбора групп.");
            return;
        }

        EditMessageReplyMarkup editMessage = new EditMessageReplyMarkup();
        editMessage.setChatId(userId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setReplyMarkup(generateGroupSelectionKeyboard(userId));

        try {
            execute(editMessage); // Обновляем сообщение
        } catch (Exception e) {
            e.printStackTrace();
            sendMessage(userId, "Ошибка при обновлении формы выбора групп.");
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
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("Места по договорам").callbackData("QUOTA_Места по договорам").build()
        ));

        keyboard.setKeyboard(buttons);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void sendSubjectSelectionMessage(Long chatId, Integer messageId) {
        EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
        editMessageReplyMarkup.setChatId(chatId.toString());
        editMessageReplyMarkup.setMessageId(messageId);

        List<String> selectedSubjects = userSubjects.getOrDefault(chatId, new ArrayList<>());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (SubjectEnum subject : SubjectEnum.values()) {
            String buttonText = selectedSubjects.contains(subject.name())
                    ? "✅ " + subject.getDescription()
                    : "☑️ " + subject.getDescription();
            buttons.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(buttonText)
                            .callbackData("SUBJECT_" + subject.name())
                            .build()
            ));
        }

        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("✅ Готово")
                        .callbackData("DONE_SUBJECTS")
                        .build()
        ));

        keyboard.setKeyboard(buttons);
        editMessageReplyMarkup.setReplyMarkup(keyboard);

        try {
            execute(editMessageReplyMarkup);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private InlineKeyboardMarkup generateSubjectSelectionKeyboard(Long chatId) {
        List<String> selectedSubjects = userSubjects.getOrDefault(chatId, new ArrayList<>());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (SubjectEnum subject : SubjectEnum.values()) {
            String buttonText = selectedSubjects.contains(subject.name())
                    ? "✅ " + subject.getDescription()
                    : "☑️ " + subject.getDescription();
            buttons.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text(buttonText)
                            .callbackData("SUBJECT_" + subject.name())
                            .build()
            ));
        }

        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("✅ Готово")
                        .callbackData("DONE_SUBJECTS")
                        .build()
        ));

        keyboard.setKeyboard(buttons);
        return keyboard;
    }

    private void askForScore(Long userId) {
        String nextSubject = userSubjects.get(userId).get(0);
        String description = SubjectEnum.valueOf(nextSubject).getDescription();
        sendMessage(userId, "Введите баллы для предмета: " + description);
    }
    private void sendMessage(Long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendAvailableSpecialties(Long userId) {
        String quota = userQuotas.get(userId);
        Map<String, Integer> scores = userScores.get(userId);
        List<String> preferredGroups = userPreferredGroups.getOrDefault(userId, new ArrayList<>());

        if (quota == null) {
            sendMessage(userId, "❗ Вы не выбрали квоту.");
            sendRestartButton(userId);
            return;
        }

        if (scores == null || scores.isEmpty()) {
            sendMessage(userId, "❗ Вы не ввели баллы.");
            sendRestartButton(userId);
            return;
        }

        if (preferredGroups.isEmpty()) {
            sendMessage(userId, "❗ Вы не выбрали предпочтительные группы.");
            sendRestartButton(userId);
            return;
        }

        List<Specialty> availableSpecialties = new ArrayList<>();
        List<Specialty> unknownScoreSpecialties = new ArrayList<>();

        for (Specialty specialty : specialties) {
            String specialtyGroupCode = specialty.getDirection().substring(0, 2); // Код группы - первые 2 символа
            if (!preferredGroups.contains(specialtyGroupCode)) {
                continue; // Пропускаем, если группа не выбрана
            }

            Integer minScore = specialty.getScores().get(quota) != null
                    ? specialty.getScores().get(quota).getMinScore()
                    : null;

            if (minScore == null) {
                unknownScoreSpecialties.add(specialty);
            } else if (isEligibleForSpecialty(scores, specialty, minScore)) {
                availableSpecialties.add(specialty);
            }
        }

        StringBuilder result = new StringBuilder("📊 Ваши результаты:\n");
        scores.forEach((subject, score) ->
                result.append("🔹 ").append(SubjectEnum.valueOf(subject).getDescription())
                        .append(": ").append(score).append(" баллов\n"));
        result.append("📈 Общий балл: ")
                .append(scores.values().stream().mapToInt(Integer::intValue).sum())
                .append("\n\n");

        if (availableSpecialties.isEmpty()) {
            result.append("❌ К сожалению, вам не хватает баллов для поступления на выбранные специальности.\n");
        } else {
            result.append("🎓 Доступные специальности:\n\n");
            appendSpecialtyList(result, availableSpecialties, quota);
        }

        if (!unknownScoreSpecialties.isEmpty()) {
            result.append("\n❓ Специальности без информации о минимальных баллах:\n\n");
            appendSpecialtyList(result, unknownScoreSpecialties, null);
        }

        // Используем безопасную отправку сообщений
        sendSafeMessage(userId, result.toString());
        sendRestartButton(userId); // Предлагаем начать заново
        resetUserData(userId); // Сбрасываем данные пользователя
    }


    private void resetUserData(Long userId) {
        userSubjects.remove(userId);
        userScores.remove(userId);
        userQuotas.remove(userId);
        userPreferredGroups.remove(userId);
        userMessageId.remove(userId); // Сбрасываем ID сообщения
    }


    private void sendRestartButton(Long userId) {
        SendMessage message = new SendMessage();
        message.setChatId(userId);
        message.setText("🔄 Хотите попробовать снова?");
        message.enableMarkdown(true);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> buttons = Collections.singletonList(
                InlineKeyboardButton.builder().text("Начать заново").callbackData("RESET").build()
        );
        keyboard.setKeyboard(Collections.singletonList(buttons));
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isEligibleForSpecialty(Map<String, Integer> scores, Specialty specialty, Integer minScore) {
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

        return eligible && totalScore >= minScore;
    }
    private void appendSpecialtyList(StringBuilder builder, List<Specialty> specialties, String quota) {
        int counter = 1;
        for (Specialty specialty : specialties) {
            // Преобразуем список профилей в строку с переносами строк
            String profiles = String.join("\n", specialty.getProfiles());

            String specialtyInfo = String.format(
                    "🔸 #%d\n" +
                            "🏫 Специальность: %s\n" +
                            "📖 Направление: %s\n" +
                            "📖 Профили:\n%s\n" + // Профили с переносом строки
                            "📖 Форма обучения: %s\n" +
                            (quota != null
                                    ? "🎯 Минимальный балл: %d\n"
                                    : "🎯 Минимальный балл: неизвестно\n") +
                            "📅 Год: %d\n\n",
                    counter++,
                    specialty.getSpecialty(),
                    specialty.getDirection(),
                    profiles.isEmpty() ? "нет данных" : profiles, // Если профилей нет, пишем "нет данных"
                    specialty.getStudyForm(),
                    quota != null && specialty.getScores().get(quota) != null
                            ? specialty.getScores().get(quota).getMinScore()
                            : 0,
                    specialty.getYear()
            );
            builder.append(specialtyInfo);
        }
    }


    @Override
    public String getBotUsername() {
        return "MyMIITAdmissionBot";
    }

    @Override
    public String getBotToken() {
        return "7872273967:AAECXYzgpVqceffU0zcOvQZUZk7eB5-EJqc";
    }
}
