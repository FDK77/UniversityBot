package org.example.bot;

import org.example.parseObjects.GroupCode;
import org.example.parseObjects.Review;
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
import java.util.stream.Collectors;

public class UniversityBot extends TelegramLongPollingBot {
    // Хранилище всех отзывов: ключ — specialtyId (или direction),
// значение — список отзывов, оставленных пользователями
    // Новое поле
    private final Map<Long, String> userReviewStage = new HashMap<>();

    private final Map<String, List<Review>> reviewsBySpecialty = new HashMap<>();

    // Состояние пользователя, чтобы понимать, что он сейчас вводит
    private final Map<Long, String> userReviewSpecialty = new HashMap<>();
// Будем использовать это для хранения ID специальности, когда пользователь оставляет отзыв
    private static final int PAGE_SIZE = 10; // по сколько специальностей на странице
    private final Map<Long, Boolean> userIsWritingReview = new HashMap<>();
// Показывает, что пользователь выбрал специальность и теперь бот ждёт от него текста отзыва

    // Если хотим оценку (рейтинги), можно добавить еще один map для временного хранения рейтинга
    private final Map<Long, Integer> userReviewRating = new HashMap<>();
    private static final String CONTACTS_CALLBACK = "CONTACTS";
    private final Map<Long, List<String>> userSubjects = new HashMap<>(); // Выбранные предметы
    private final Map<Long, List<String>> userAllSubjects = new HashMap<>();

    private final Map<Long, Map<String, Integer>> userScores = new HashMap<>(); // Баллы по предметам
    private final Map<Long, String> userQuotas = new HashMap<>(); // Выбранная квота
    private final Map<Long, Integer> userMessageId = new HashMap<>(); // Хранилище ID сообщений
    private final List<Specialty> specialties; // Список специальностей
    private final List<GroupCode> groupCodes; // Список групп
    private final Map<Long, List<String>> userPreferredGroups = new HashMap<>(); // Выбранные группы

    private final Map<Long, Boolean> userNotificationPreferences = new HashMap<>();
    private final Map<Long, String> userNotificationTexts = new HashMap<>();
    private final Set<Long> adminIds = Set.of(983195539L); // ID администраторов
    private boolean isAdminMode = false;

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
        Integer messageId = update.getMessage().getMessageId();
        // 1. Проверка команд отзывов
        if (text.equalsIgnoreCase("/review")) {
            // Пользователь хочет оставить отзыв о специальности
            // Предложим ему выбрать специальность
            sendSpecialtyListForReview(userId);
            return;
        } else if (text.equalsIgnoreCase("/reviews")) {
            // Пользователь хочет посмотреть отзывы
            // Предложим ему выбрать специальность, отзывы о которой хочет посмотреть
            sendSpecialtyListForReadingReviews(userId);
            return;
        }
        String stage = userReviewStage.get(userId);
        if (stage != null) {
            switch (stage) {
                case "AWAITING_RATING":
                    try {
                        int rating = Integer.parseInt(text.trim());
                        if (rating < 1 || rating > 5) {
                            sendMessage(userId, "Оценка должна быть от 1 до 5. Повторите ввод.");
                            return;
                        }
                        userReviewRating.put(userId, rating);
                        userReviewStage.put(userId, "AWAITING_TEXT");

                        sendMessage(userId, "Спасибо! Оценка принята.\nТеперь введите текст отзыва (или пустое сообщение).");
                    } catch (NumberFormatException e) {
                        sendMessage(userId, "Ошибка. Введите число от 1 до 5.");
                    }
                    return;

                case "AWAITING_TEXT":
                    String specialtyId = userReviewSpecialty.get(userId);
                    String specialtyName = getSpecialtyNameById(specialtyId);
                    if (specialtyId == null) {
                        // безопасная обработка
                        sendMessage(userId, "Ошибка: не найдена выбранная специальность.");
                        userReviewStage.remove(userId);
                        return;
                    }

                    int rating = userReviewRating.getOrDefault(userId, 5);
                    String reviewText = text.trim();

                    // Сохраняем
                    reviewsBySpecialty.computeIfAbsent(specialtyId, k -> new ArrayList<>())
                            .add(new Review(userId, specialtyId, reviewText, rating));

                    sendMessage(userId, "Спасибо! Ваш отзыв о «" + specialtyName + "» сохранён.\n" +
                            "Оценка: " + rating + "\n" +
                            (reviewText.isEmpty() ? "(без комментариев)" : reviewText));

                    // Сброс
                    sendQuotaSelectionMessage(userId);
                    userReviewStage.remove(userId);
                    userReviewSpecialty.remove(userId);
                    userReviewRating.remove(userId);
                    return;
            }
        }

        // Проверяем, является ли пользователь администратором
        if (adminIds.contains(userId) && text.equalsIgnoreCase("/admin")) {
            isAdminMode = true;
            sendMessage(userId, "Режим администратора активирован. Введите текст для рассылки:");
            return;
        }

        // Если администратор в режиме рассылки
        if (adminIds.contains(userId) && isAdminMode) {
            sendAdminBroadcast(userId, text);
            isAdminMode = false;
            return;
        }
        if (text.equalsIgnoreCase("/notifications")) {
            handleNotificationSetup(userId, text);
        } else if (userNotificationPreferences.containsKey(userId) &&
                userNotificationPreferences.get(userId) &&
                userNotificationTexts.containsKey(userId) &&
                userNotificationTexts.get(userId) == null) {
            handleNotificationSetup(userId, text);
        } else {
        if (text.equals("/start")) {
            sendQuotaSelectionMessage(userId);
        } else if (userSubjects.containsKey(userId)) {
            handleScoreInput(userId, text, messageId); // Передаем ID сообщения
        } else {
            sendMessage(userId, "Нажмите /start для начала.");
        }
        }
    }

    private void sendAdminBroadcast(Long adminId, String text) {
        int successCount = 0;
        int failCount = 0;

        // Рассылаем только пользователям с включенными уведомлениями
        for (Map.Entry<Long, Boolean> entry : userNotificationPreferences.entrySet()) {
            if (entry.getValue()) { // Если уведомления включены
                try {
                    sendMessage(entry.getKey(), "🔔 Сообщение от администратора:\n\n" + text);
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                }
            }
        }

        // Отправляем отчет администратору
        String report = String.format(
                "Рассылка завершена!\n\n" +
                        "✅ Успешно отправлено: %d\n" +
                        "❌ Не удалось отправить: %d",
                successCount, failCount
        );

        sendMessage(adminId, report);
    }

    private void sendSpecialtyListForReview(Long userId) {
        // Отправляем новое сообщение (только один раз), для страницы 0:
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(userId);
        sendMessage.setText("Специальности (страница 1)...");

        InlineKeyboardMarkup keyboard = buildReviewKeyboardPage(userId, 0);
        sendMessage.setReplyMarkup(keyboard);

        try {
            Message msg = execute(sendMessage);
            // msg.getMessageId() — это ID отправленного сообщения
            // Можем сохранить его в userMessageId.put(userId, msg.getMessageId()) если нужно
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup buildReviewKeyboardPage(Long userId, int pageIndex) {
        // Считаем общее кол-во и кол-во страниц
        int total = specialties.size();
        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);

        // Корректируем pageIndex, если вышли за границы
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex > totalPages - 1) pageIndex = totalPages - 1;

        // Индексы "среза" списка
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<Specialty> pageItems = specialties.subList(from, to);

        // Готовим список рядов (rows) для InlineKeyboard
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1) Кнопки специальностей
        for (Specialty specialty : pageItems) {
            // например: REVIEW_SPECIALTY_XXX
            String callbackData = "REVIEW_SPECIALTY_" + specialty.getId();
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(specialty.getSpecialty())
                    .callbackData(callbackData)
                    .build();
            rows.add(Collections.singletonList(button));
        }

        // 2) Кнопки "Назад" / "Вперёд"
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (pageIndex > 0) {
            navRow.add(InlineKeyboardButton.builder()
                    .text("← Назад")
                    // при нажатии придёт REVIEW_PAGE_(pageIndex - 1)
                    .callbackData("REVIEW_PAGE_" + (pageIndex - 1))
                    .build());
        }
        if (pageIndex < totalPages - 1) {
            navRow.add(InlineKeyboardButton.builder()
                    .text("Вперёд →")
                    .callbackData("REVIEW_PAGE_" + (pageIndex + 1))
                    .build());
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        // Создаём саму клавиатуру
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }
    private void sendSpecialtyListForReadingReviews(Long userId) {
        // отправляем новое сообщение, страница 0
        SendMessage message = new SendMessage();
        message.setChatId(userId);
        message.setText("Специальности (страница 1 / X). Выберите, о какой посмотреть отзывы:");

        InlineKeyboardMarkup keyboard = buildReadReviewsKeyboardPage(0);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private InlineKeyboardMarkup buildReadReviewsKeyboardPage(int pageIndex) {
        int total = specialties.size();
        int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);

        // Корректируем pageIndex, если вышли за границы
        if (pageIndex < 0) pageIndex = 0;
        if (pageIndex >= totalPages) pageIndex = totalPages - 1;

        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, total);
        List<Specialty> pageItems = specialties.subList(from, to);

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // 1) Кнопки специальностей для чтения отзывов
        for (Specialty specialty : pageItems) {
            Double avgRating = getAverageRating(specialty.getId());
            String ratingText = (avgRating != null)
                    ? String.format(" (%.1f/5 ★)", avgRating)
                    : "";
            String buttonLabel = specialty.getSpecialty() + ratingText;

            String callbackData = "READ_REVIEWS_" + specialty.getId();

            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(buttonLabel)
                    .callbackData(callbackData)
                    .build();
            rows.add(Collections.singletonList(button));
        }


        // 2) Навигационные кнопки
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (pageIndex > 0) {
            navRow.add(InlineKeyboardButton.builder()
                    .text("← Назад")
                    .callbackData("READREV_PAGE_" + (pageIndex - 1))
                    .build());
        }
        if (pageIndex < totalPages - 1) {
            navRow.add(InlineKeyboardButton.builder()
                    .text("Вперёд →")
                    .callbackData("READREV_PAGE_" + (pageIndex + 1))
                    .build());
        }
        if (!navRow.isEmpty()) {
            rows.add(navRow);
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    private void handleCallback(CallbackQuery callbackQuery) {
        Long userId = callbackQuery.getMessage().getChatId();
        Integer messageId = callbackQuery.getMessage().getMessageId();
        String data = callbackQuery.getData();

        // Обработка новых callback'ов для работы со списком специальностей
        if (data.equals("SPECIALTIES_LIST")) {
            sendSpecialtiesList(userId, 0);
            return;
        } else if (data.startsWith("SPECIALTIES_PAGE_")) {
            int page = Integer.parseInt(data.substring("SPECIALTIES_PAGE_".length()));
            sendSpecialtiesList(userId, page);
            return;
        } else if (data.startsWith("SPECIALTY_DETAIL_")) {
            String specialtyId = data.substring("SPECIALTY_DETAIL_".length());
            showSpecialtyDetail(userId, specialtyId);
            return;
        }
        if (data.equals("REVIEWS_MENU")) {
            sendReviewsMenu(userId);
            return;
        }
        if (data.startsWith("REVIEW_PAGE_")) {
            int pageIndex = Integer.parseInt(data.substring("REVIEW_PAGE_".length()));

            // соберём новый текст и новую клавиатуру
            int total = specialties.size();
            int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);
            // Корректируем pageIndex, чтобы не вылететь
            if (pageIndex < 0) pageIndex = 0;
            if (pageIndex > totalPages - 1) pageIndex = totalPages - 1;

            // Формируем текст
            String newText = String.format(
                    "Специальности (страница %d / %d):\n" +
                            "Выберите специальность, о которой хотите оставить отзыв:",
                    pageIndex + 1, totalPages
            );

            // Будет новая клавиатура
            InlineKeyboardMarkup newKeyboard = buildReviewKeyboardPage(userId, pageIndex);

            // Редактируем **существующее** сообщение, используя EditMessageText
            EditMessageText edit = new EditMessageText();
            edit.setChatId(userId.toString());
            edit.setMessageId(messageId);  // тот же ID, что в callback
            edit.setText(newText);
            edit.setReplyMarkup(newKeyboard);

            try {
                execute(edit);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (data.startsWith("REVIEW_SPECIALTY_")) {
            String specialtyId = data.substring("REVIEW_SPECIALTY_".length());
            String specialtyName = getSpecialtyNameById(specialtyId);

            // Запоминаем, что этот пользователь выбрал specialtyId
            userReviewSpecialty.put(userId, specialtyId);

            // Ставим стадию: ждём рейтинг
            userReviewStage.put(userId, "AWAITING_RATING");

            // Просим ввести рейтинг от 1 до 5
            sendMessage(userId, "Вы выбрали специальность: " + specialtyName +
                    "\nПожалуйста, введите вашу ОЦЕНКУ (число от 1 до 5).");
            return;
        }


        if (data.startsWith("READREV_PAGE_")) {
            int pageIndex = Integer.parseInt(data.substring("READREV_PAGE_".length()));
            int total = specialties.size();
            int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);

            if (pageIndex < 0) pageIndex = 0;
            if (pageIndex >= totalPages) pageIndex = totalPages - 1;

            String newText = String.format(
                    "Специальности (страница %d / %d). Выберите, о какой посмотреть отзывы:",
                    pageIndex + 1,
                    totalPages
            );
            InlineKeyboardMarkup newKeyboard = buildReadReviewsKeyboardPage(pageIndex);

            // Уже есть userId и messageId
            EditMessageText edit = new EditMessageText();
            edit.setChatId(userId.toString());
            edit.setMessageId(messageId);
            edit.setText(newText);
            edit.setReplyMarkup(newKeyboard);

            try {
                execute(edit);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        if (data.startsWith("READ_REVIEWS_")) {
            String specialtyId = data.substring("READ_REVIEWS_".length());
            showReviewsForSpecialty(userId, specialtyId);
            return;
        }

        // Например, если пользователь нажал "Оставить отзыв" в меню отзывов
        if (data.equals("LEAVE_REVIEW")) {
            // Переходим к логике выбора специальности для отзыва
            sendSpecialtyListForReview(userId);
            return;
        }

        if (data.equals("READ_REVIEWS")) {
            sendSpecialtyListForReadingReviews(userId);
            return;
        }
        if (data.equals("SHOW_QUOTAS")) {
            showQuotaSelection(userId);
        }
        if (data.equals("CONTACTS")) {
            showContactsMenu(userId);
        }
        else if (data.equals("BACK_TO_MAIN")) {
            sendQuotaSelectionMessage(userId);
        }
        if (data.equals("NOTIFY_SETUP")) {
            handleNotificationSetup(userId, "/notifications");
        }
        if (data.equals("BACK_TO_START")) {
            sendQuotaSelectionMessage(userId);
        }
        if (data.equals("ADMIN_PANEL")) {
            if (adminIds.contains(userId)) {
                isAdminMode = true;
                sendMessage(userId, "Режим администратора активирован. Введите текст для рассылки:");
            }
        }
        if (data.equals("NOTIFY_ON")) {
            userNotificationPreferences.put(userId, true);
            userNotificationTexts.put(userId, null); // Пока без текста
            sendMessage(userId, "Уведомления включены! Введите текст, который вы хотите получать ежедневно:");
        } else if (data.equals("NOTIFY_OFF")) {
            userNotificationPreferences.put(userId, false);
            sendMessage(userId, "Уведомления выключены.");
        }
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
            String subject = data.substring(8); // Убираем префикс "SUBJECT_"
            List<String> selectedSubjects = userSubjects.computeIfAbsent(userId, k -> new ArrayList<>());
            List<String> allSubjects = userAllSubjects.computeIfAbsent(userId, k -> new ArrayList<>());
            if (selectedSubjects.contains(subject)) {
                selectedSubjects.remove(subject);
                allSubjects.remove(subject);
            } else {
                selectedSubjects.add(subject);
                allSubjects.add(subject);
            }
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
    private void sendReviewsMenu(Long userId) {
        SendMessage message = new SendMessage();
        message.setChatId(userId);
        message.setText("Отзывы: выберите действие");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка «Оставить отзыв» с эмодзи ✍
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("✍ Оставить отзыв")
                        .callbackData("LEAVE_REVIEW")
                        .build()
        ));

        // Кнопка «Посмотреть отзывы» с эмодзи 👁
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("👁 Посмотреть отзывы")
                        .callbackData("READ_REVIEWS")
                        .build()
        ));

        // Кнопка «Назад» с эмодзи ◀️
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("◀️ Назад")
                        .callbackData("BACK_TO_MAIN")
                        .build()
        ));

        keyboard.setKeyboard(rows);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void sendSafeMessage(Long chatId, String message) {
        if (message.length() > 4096) {
            sendMessageInParts(chatId, message);
        } else {
            sendMessage(chatId, message);
        }
    }

    private void showReviewsForSpecialty(Long userId, String specialtyId) {
        String specialtyName = getSpecialtyNameById(specialtyId);
        List<Review> reviews = reviewsBySpecialty.getOrDefault(specialtyId, new ArrayList<>());

        if (reviews.isEmpty()) {
            sendMessage(userId, "Пока никто не оставил отзыв о «" + specialtyName + "».");
            sendQuotaSelectionMessage(userId);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("👁‍🗨 Отзывы о «").append(specialtyName).append("»:\n\n");

        // Добавляем среднюю оценку:
        Double avgRating = getAverageRating(specialtyId);
        if (avgRating != null) {
            sb.append("⭐ Средняя оценка: ")
                    .append(String.format("%.1f", avgRating))
                    .append("/5\n\n");  // Ещё можно добавить эмодзи
        }

        int counter = 1;
        for (Review review : reviews) {
            sb.append("— — — — — — —\n");
            sb.append("[").append(counter++).append("]\n");
            sb.append("Оценка: ").append(review.getRating()).append("/5\n");
            // Если хотите больше смайликов
            sb.append("✍ Отзыв: ").append(review.getText()).append("\n\n");
        }

        sendSafeMessage(userId, sb.toString());
        sendQuotaSelectionMessage(userId);
    }



    private InlineKeyboardMarkup generateGroupSelectionKeyboard(Long userId) {
        List<String> selectedGroups = userPreferredGroups.getOrDefault(userId, new ArrayList<>());
        // Вместо userSubjects берем оригинальный список выбранных предметов
        List<String> userSelectedSubjects = userAllSubjects.getOrDefault(userId, new ArrayList<>());

        Set<String> normalizedUserSubjects = userSelectedSubjects.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        for (GroupCode group : groupCodes) {
            List<String> groupSubjects = group.getSubjects();
            if (groupSubjects == null || groupSubjects.isEmpty()) continue;

            List<String> normalizedGroupSubjects = groupSubjects.stream()
                    .map(String::toUpperCase)
                    .collect(Collectors.toList());

            long matchCount = normalizedGroupSubjects.stream()
                    .filter(normalizedUserSubjects::contains)
                    .count();

            if (matchCount < 3) continue;

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
        sendMessage.setText("Главное меню:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        // Первая группа - выбор квоты
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🎓 Выбрать квоту")
                        .callbackData("SHOW_QUOTAS")
                        .build()
        ));

        // Вторая группа - сервисные кнопки
        buttons.add(Arrays.asList(
                InlineKeyboardButton.builder()
                        .text("🔔 Уведомления")
                        .callbackData("NOTIFY_SETUP")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("📞 Контакты")
                        .callbackData("CONTACTS")
                        .build(),
                InlineKeyboardButton.builder()
                        .text("💬 Отзывы")
                        .callbackData("REVIEWS_MENU")
                        .build()
        ));
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("📋 Описание специальностей")
                        .callbackData("SPECIALTIES_LIST")
                        .build()
        ));

        // Третья группа - админ-панель (только для админов)
        if (adminIds.contains(chatId)) {
            buttons.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text("👨‍💻 Админ-панель")
                            .callbackData("ADMIN_PANEL")
                            .build()
            ));
        }

        keyboard.setKeyboard(buttons);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Добавляем новый метод для отображения списка специальностей
    private void sendSpecialtiesList(Long chatId, int page) {
        int totalPages = (int) Math.ceil((double) specialties.size() / PAGE_SIZE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        int from = page * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, specialties.size());
        List<Specialty> pageSpecialties = specialties.subList(from, to);

        StringBuilder messageText = new StringBuilder();
        messageText.append("📋 Список специальностей (страница ").append(page + 1).append(" из ").append(totalPages).append("):\n\n");

        for (int i = 0; i < pageSpecialties.size(); i++) {
            Specialty s = pageSpecialties.get(i);
            messageText.append(i + 1).append(". ").append(s.getDirection()).append(" - ").append(s.getSpecialty()).append("\n");
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопки для выбора специальности
        for (int i = 0; i < pageSpecialties.size(); i++) {
            Specialty s = pageSpecialties.get(i);
            rows.add(Collections.singletonList(
                    InlineKeyboardButton.builder()
                            .text((i + 1) + ". " + s.getDirection())
                            .callbackData("SPECIALTY_DETAIL_" + s.getId())
                            .build()
            ));
        }

        // Кнопки навигации
        List<InlineKeyboardButton> navButtons = new ArrayList<>();
        if (page > 0) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("◀️ Назад")
                    .callbackData("SPECIALTIES_PAGE_" + (page - 1))
                    .build());
        }
        if (page < totalPages - 1) {
            navButtons.add(InlineKeyboardButton.builder()
                    .text("Вперёд ▶️")
                    .callbackData("SPECIALTIES_PAGE_" + (page + 1))
                    .build());
        }
        if (!navButtons.isEmpty()) {
            rows.add(navButtons);
        }

        // Кнопка возврата в главное меню
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🔙 В главное меню")
                        .callbackData("BACK_TO_MAIN")
                        .build()
        ));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText.toString());
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Метод для отображения детальной информации о специальности
    private void showSpecialtyDetail(Long chatId, String specialtyId) {
        Specialty specialty = specialties.stream()
                .filter(s -> s.getId().equals(specialtyId))
                .findFirst()
                .orElse(null);

        if (specialty == null) {
            sendMessage(chatId, "Специальность не найдена.");
            return;
        }

        String messageText = "📌 Информация о специальности:\n\n" +
                "🔹 Код: " + specialty.getDirection() + "\n" +
                "🔹 Название: " + specialty.getSpecialty() + "\n" +
                "🔹 Форма обучения: " + specialty.getStudyForm() + "\n" +
                "🔹 Описание: " + specialty.getDescription() + "\n\n" +
                "📚 Профили:\n" + String.join("\n", specialty.getProfiles());

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка возврата к списку
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🔙 К списку специальностей")
                        .callbackData("SPECIALTIES_LIST")
                        .build()
        ));

        // Кнопка возврата в главное меню
        rows.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("🔙 В главное меню")
                        .callbackData("BACK_TO_MAIN")
                        .build()
        ));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(messageText);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void showQuotaSelection(Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Выберите квоту:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        // Кнопки выбора квоты
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

        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("◀️ Назад")
                        .callbackData("BACK_TO_MAIN")
                        .build()
        ));


        // ВАЖНО: теперь свяжем buttons и keyboard
        keyboard.setKeyboard(buttons);
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showContactsMenu(Long chatId) {
        // Здесь можно позже добавить любой текст контактов
        String contactsText = "📌 Контактная информация:\n\n" +
                "📞 Телефон: +7 495 260-23-32\n" +
                "📧 Email: pk@rut-miit.ru\n" +
                "🌐 Сайт: https://www.miit.ru/admissions/office\n\n" +
                "📍 Адрес: г. Москва, ул. Образцова, д. 9, стр. 9";

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(contactsText);

        // Добавляем кнопку "Назад"
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<InlineKeyboardButton> buttons = Collections.singletonList(
                InlineKeyboardButton.builder()
                        .text("◀️ Назад")
                        .callbackData("BACK_TO_START")
                        .build()
        );
        keyboard.setKeyboard(Collections.singletonList(buttons));
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
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
        message.setText("🔄 Хотите попробовать снова или посмотреть контакты?");
        message.enableMarkdown(true);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("🔄 Начать заново").callbackData("RESET").build()
        ));
        buttons.add(Collections.singletonList(
                InlineKeyboardButton.builder().text("📞 Контакты").callbackData(CONTACTS_CALLBACK).build()
        ));

        keyboard.setKeyboard(buttons);
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
            // Преобразуем список профилей в строку с более читабельным форматом
            String profiles = specialty.getProfiles().stream()
                    .map(profile -> "    • " + profile)
                    .collect(Collectors.joining("\n"));

            String specialtyInfo = String.format(
                    "🔸 #%d\n" +
                            "🏫 Специальность: %s\n" +
                            "🎓 Направление: %s\n" +
                            "💡 Профили:\n%s\n" +
                            "📚 Форма обучения: %s\n" +
                            "📚 Описание: %s\n" +
                            (quota != null
                                    ? "🎯 Минимальный балл: %d\n\n"
                                    : "🎯 Минимальный балл: неизвестно\n\n"),
                    counter++,
                    specialty.getSpecialty(),
                    specialty.getDirection(),
                    profiles.isEmpty() ? "нет данных" : profiles,
                    specialty.getStudyForm(),
                    specialty.getDescription(),
                    quota != null && specialty.getScores().get(quota) != null
                            ? specialty.getScores().get(quota).getMinScore()
                            : 0
            );

            builder.append(specialtyInfo);
        }
    }

    private void handleNotificationSetup(Long userId, String text) {
        if (text.equalsIgnoreCase("/notifications") || text.equals("NOTIFY_SETUP")) {
            // Запрос на настройку уведомлений
            SendMessage message = new SendMessage();
            message.setChatId(userId);
            message.setText("Хотите получать текстовые уведомления?");

            InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

            buttons.add(Arrays.asList(
                    InlineKeyboardButton.builder().text("✅ Включить").callbackData("NOTIFY_ON").build(),
                    InlineKeyboardButton.builder().text("❌ Выключить").callbackData("NOTIFY_OFF").build()
            ));

            keyboard.setKeyboard(buttons);
            message.setReplyMarkup(keyboard);

            try {
                execute(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (userNotificationPreferences.containsKey(userId) &&
                userNotificationPreferences.get(userId) &&
                userNotificationTexts.containsKey(userId) &&
                userNotificationTexts.get(userId) == null) {
            // Сохранение текста уведомления
            userNotificationTexts.put(userId, text);
            sendMessage(userId, "Текст уведомления сохранён! Вы будете получать его ежедневно.");
            // После сохранения текста уведомления показываем изначальные кнопки
            sendQuotaSelectionMessage(userId);
        }
    }
    public void sendDailyNotifications() {
        for (Map.Entry<Long, Boolean> entry : userNotificationPreferences.entrySet()) {
            if (entry.getValue()) { // Если уведомления включены
                boolean hasActiveAdmins = adminIds.stream()
                        .anyMatch(id -> userNotificationPreferences.getOrDefault(id, false));

                if (!hasActiveAdmins) {
                    return; // Не отправляем уведомления, если нет активных администраторов
                }
                Long userId = entry.getKey();
                String notificationText = userNotificationTexts.getOrDefault(userId,
                        "⏰ Ваше ежедневное уведомление от UniversityBot!");

                sendMessage(userId, notificationText);
            }
        }
    }
    private Double getAverageRating(String specialtyId) {
        List<Review> reviews = reviewsBySpecialty.getOrDefault(specialtyId, new ArrayList<>());
        if (reviews.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Review r : reviews) {
            sum += r.getRating();
        }
        return sum / reviews.size();
    }
    private String getSpecialtyNameById(String specialtyId) {
        for (Specialty s : specialties) {
            if (s.getId() != null && s.getId().equals(specialtyId)) {
                return s.getSpecialty(); // Возвращаем "полное название"
            }
        }
        return specialtyId; // Если почему-то не нашли, вернём сам ID
    }

    public List<Specialty> getSpecialties() {
        return specialties;
    }
    public void addReview(String specialtyId, Review review) {
        reviewsBySpecialty.computeIfAbsent(specialtyId, k -> new ArrayList<>()).add(review);
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
