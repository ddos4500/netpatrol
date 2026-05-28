package com.netpatrol.scheduler;

import com.netpatrol.model.ScheduledTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DailyTaskScheduler {
    public interface TaskRunner {
        void runScheduledTask(ScheduledTask task);
    }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> executedToday = new HashSet<String>();
    private LocalDate currentDate = LocalDate.now();

    public void start(final List<ScheduledTask> tasks, final TaskRunner runner) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                tick(tasks, runner);
            }
        }, 5, 20, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void tick(List<ScheduledTask> tasks, TaskRunner runner) {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            currentDate = today;
            executedToday.clear();
        }
        LocalDateTime now = LocalDateTime.now();
        for (ScheduledTask task : tasks) {
            if (!task.isEnabled()) {
                continue;
            }
            if (task.isIntervalTask()) {
                tickInterval(task, now.toLocalTime(), runner);
                continue;
            }
            if (task.getRunTime() == null) continue;
            String key = task.getName() + "@" + task.getRunTime();
            LocalDateTime due = LocalDateTime.of(today, task.getRunTime());
            if (!executedToday.contains(key) && !now.isBefore(due)) {
                executedToday.add(key);
                runner.runScheduledTask(task);
            }
        }
    }

    private void tickInterval(ScheduledTask task, LocalTime now, TaskRunner runner) {
        if (!task.isRunning()) return;
        if (task.getNextRunTime() == null) {
            task.setNextRunTime(now);
        }
        if (!now.isBefore(task.getNextRunTime())) {
            task.setNextRunTime(now.plusMinutes(Math.max(1, task.getIntervalMinutes())));
            runner.runScheduledTask(task);
        }
    }
}
