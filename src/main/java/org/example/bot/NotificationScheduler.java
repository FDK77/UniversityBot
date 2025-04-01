package org.example.bot;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NotificationScheduler {
    private final UniversityBot bot;
    private final ScheduledExecutorService scheduler;

    public NotificationScheduler(UniversityBot bot) {
        this.bot = bot;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public void start() {
        // Запускаем задачу каждый день в 9:00 утра
        scheduler.scheduleAtFixedRate(
                () -> bot.sendDailyNotifications(),
                0, // Начальная задержка (сразу после запуска)
                24, // Период повторения (24 часа)
                TimeUnit.HOURS
        );
    }

    public void stop() {
        scheduler.shutdown();
    }
}