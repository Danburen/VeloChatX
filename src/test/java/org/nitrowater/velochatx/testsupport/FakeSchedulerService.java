package org.nitrowater.velochatx.testsupport;

import org.nitrowater.waterapi.domain.shared.service.SchedulerService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Recording {@link SchedulerService}. Tests decide when a scheduled task runs, so scheduler
 * behaviour is deterministic and no background thread touches shared state.
 */
public final class FakeSchedulerService implements SchedulerService {

    public final List<Task> tasks = new CopyOnWriteArrayList<>();

    @Override
    public TaskHandle schedule(Runnable task, long delay, TimeUnit timeUnit) {
        Task handle = new Task(task, delay, -1, timeUnit);
        tasks.add(handle);
        return handle;
    }

    @Override
    public TaskHandle scheduleAtFixedRate(Runnable task, long delay, long period, TimeUnit timeUnit) {
        Task handle = new Task(task, delay, period, timeUnit);
        tasks.add(handle);
        return handle;
    }

    @Override
    public void cancelAll() {
        tasks.forEach(Task::cancel);
    }

    /** Latest scheduled task, or {@code null} when nothing was scheduled. */
    public Task lastTask() {
        return tasks.isEmpty() ? null : tasks.get(tasks.size() - 1);
    }

    /** Run every non-cancelled task once. */
    public void runAll() {
        tasks.stream().filter(task -> !task.isCancelled()).forEach(Task::run);
    }

    /** A scheduled task plus its scheduling metadata. */
    public static final class Task implements TaskHandle {

        private final Runnable runnable;
        private final long delay;
        private final long period;
        private final TimeUnit timeUnit;
        private volatile boolean cancelled;

        private Task(Runnable runnable, long delay, long period, TimeUnit timeUnit) {
            this.runnable = runnable;
            this.delay = delay;
            this.period = period;
            this.timeUnit = timeUnit;
        }

        public Runnable runnable() {
            return runnable;
        }

        public long delay() {
            return delay;
        }

        public long period() {
            return period;
        }

        public TimeUnit timeUnit() {
            return timeUnit;
        }

        public void run() {
            runnable.run();
        }

        @Override
        public boolean cancel() {
            boolean wasCancelled = cancelled;
            cancelled = true;
            return !wasCancelled;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
