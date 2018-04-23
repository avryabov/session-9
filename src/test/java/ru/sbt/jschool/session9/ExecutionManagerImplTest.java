package ru.sbt.jschool.session9;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 */
public class ExecutionManagerImplTest {

    private static ExecutionManagerImpl executionManager;
    private static Thread[] threads;

    @Before
    public void createExecutionManagerImpl() {
        executionManager = new ExecutionManagerImpl();
        threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            int finalI = i;
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(finalI * 100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }
    }

    @Test(timeout = 5000)
    public void testCompletedTaskCount() {
        Context context = executionManager.execute(null, threads);
        while (!context.isFinished()) {
        }
        int completed = context.getCompletedTaskCount();
        int failed = context.getFailedTaskCount();
        int interrupted = context.getInterruptedTaskCount();
        System.out.println(completed + " " + failed + " " + interrupted);
        assertEquals(10, completed);
        assertEquals(0, failed);
        assertEquals(0, interrupted);
    }

    @Test(timeout = 5000)
    public void testFailedTaskCount() {
        threads[3] = new Thread() {
            @Override
            public void run() {
                throw new RuntimeException("Runtime Exception");
            }
        };
        Context context = executionManager.execute(null, threads);
        while (!context.isFinished()) {
        }
        int completed = context.getCompletedTaskCount();
        int failed = context.getFailedTaskCount();
        int interrupted = context.getInterruptedTaskCount();
        System.out.println(completed + " " + failed + " " + interrupted);
        assertEquals(9, completed);
        assertEquals(1, failed);
        assertEquals(0, interrupted);
    }

    @Test(timeout = 5000)
    public void testInterruptedTaskCount() {
        Context context = executionManager.execute(null, threads);
        context.interrupt();
        while (!context.isFinished()) {
        }
        int completed = context.getCompletedTaskCount();
        int failed = context.getFailedTaskCount();
        int interrupted = context.getInterruptedTaskCount();
        System.out.println(completed + " " + failed + " " + interrupted);
        assertTrue(completed < 10);
        assertEquals(0, failed);
        assertTrue(interrupted > 0);
    }

    @Test(timeout = 5000)
    public void testCallback() throws InterruptedException {
        final String[] callbackMessage = {""};
        Thread callback = new Thread() {
            @Override
            public void run() {
                callbackMessage[0] = "Callback";
            }
        };
        Context context = executionManager.execute(callback, threads);
        while (!context.isFinished()) {
        }
        Thread.sleep(100);
        System.out.println(callbackMessage[0]);
        assertEquals("Callback", callbackMessage[0]);
    }

}
