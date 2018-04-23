package ru.sbt.jschool.session9;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ExecutionManagerImpl implements ExecutionManager {

    private volatile ExecutorService executorService;
    private AtomicInteger completedTaskCount = new AtomicInteger(0);
    private AtomicInteger failedTaskCount = new AtomicInteger(0);
    private AtomicInteger canceledTaskCount = new AtomicInteger(0);
    private AtomicInteger taskCounter = new AtomicInteger(0);
    private volatile boolean interrupt = false;
    private volatile boolean isRunning = true;

    public Context execute(Runnable callback, Runnable... tasks) {
        executorService = Executors.newFixedThreadPool(tasks.length);
        Queue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        taskQueue.addAll(Arrays.asList(tasks));
        new Thread(new TaskRunner(taskQueue, callback)).start();
        return new ContextImpl(taskQueue);
    }

    private final class TaskObserver implements Runnable {

        private final Future task;

        private TaskObserver(Future task) {
            this.task = task;
        }

        @Override
        public void run() {
            try {
                task.get();
                if (task.isDone())
                    completedTaskCount.incrementAndGet();
                else if (task.isCancelled())
                    canceledTaskCount.incrementAndGet();
            } catch (Exception e) {
                failedTaskCount.incrementAndGet();
            } finally {
                taskCounter.decrementAndGet();
            }
        }
    }

    private final class TaskRunner implements Runnable {

        private final Queue<Runnable> taskQueue;
        private final Runnable callback;

        private TaskRunner(Queue<Runnable> taskQueue, Runnable callback) {
            this.taskQueue = taskQueue;
            this.callback = callback;
        }

        @Override
        public void run() {
            while (!interrupt && taskQueue.size() > 0) {
                try {
                    Future futureTask = executorService.submit(taskQueue.poll());
                    new Thread(new TaskObserver(futureTask)).start();
                } catch (RejectedExecutionException e) {
                    canceledTaskCount.incrementAndGet();
                    break;
                }
            }
            executorService.shutdown();
            try {
                executorService.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                new RuntimeException(e);
            }
            isRunning = false;
            new Thread(callback).start();
        }
    }

    private final class ContextImpl implements Context {

        private final Queue<Runnable> taskQueue;

        private ContextImpl(Queue<Runnable> taskQueue) {
            this.taskQueue = taskQueue;
        }


        @Override
        public int getCompletedTaskCount() {
            return completedTaskCount.get();
        }

        @Override
        public int getFailedTaskCount() {
            return failedTaskCount.get();
        }

        @Override
        public int getInterruptedTaskCount() {
            return (canceledTaskCount.get() + taskQueue.size());
        }

        @Override
        public void interrupt() {
            interrupt = true;
            executorService.shutdown();
        }

        @Override
        public boolean isFinished() {
            return !isRunning;
        }
    }
}
