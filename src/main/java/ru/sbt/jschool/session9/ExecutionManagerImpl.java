package ru.sbt.jschool.session9;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ExecutionManagerImpl implements ExecutionManager {

    private ExecutorService executorService;
    private AtomicInteger completedTaskCount = new AtomicInteger(0);
    private AtomicInteger failedTaskCount = new AtomicInteger(0);
    private AtomicInteger canceledTaskCount = new AtomicInteger(0);
    private AtomicInteger taskCounter = new AtomicInteger(0);
    private volatile boolean interrupt = false;
    private volatile boolean isRunning = true;

    public static void main(String[] args) {
        ExecutionManagerImpl executionManager = new ExecutionManagerImpl();
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            int finalI = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(finalI * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }
        Context context = executionManager.execute(null, threads);
        while (!context.isFinished()) {
            System.out.println(context.getCompletedTaskCount());
            System.out.println(context.getInterruptedTaskCount());
            System.out.println(context.getFailedTaskCount());
        }
    }

    public Context execute(Runnable callback, Runnable... tasks) {
        executorService = Executors.newFixedThreadPool(tasks.length);
        Queue<Runnable> taskQueue = new LinkedBlockingQueue<>();
        taskQueue.addAll(Arrays.asList(tasks));
        new Thread(new TaskRunner(taskQueue, callback)).start();
        return new ContextImpl();
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
                Future futureTask = executorService.submit(taskQueue.poll());
                new Thread(new TaskObserver(futureTask)).start();
            }
            executorService.shutdown();
            while (!executorService.isTerminated()) ;
            isRunning = false;
            new Thread(callback).start();
        }
    }

    private final class ContextImpl implements Context {


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
            return canceledTaskCount.get();
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
