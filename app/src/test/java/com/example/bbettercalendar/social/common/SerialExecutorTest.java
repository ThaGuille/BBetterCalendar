package com.example.bbettercalendar.social.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SerialExecutorTest {

    @Test
    public void thousandTasksOverAFourThreadPoolRunOneAtATimeInOrder() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            int n = 1000;
            List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
            AtomicInteger running = new AtomicInteger();
            AtomicInteger maxRunning = new AtomicInteger();
            CountDownLatch done = new CountDownLatch(n);
            for (int i = 0; i < n; i++) {
                int id = i;
                serial.execute(() -> {
                    int now = running.incrementAndGet();
                    maxRunning.accumulateAndGet(now, Math::max);
                    if (id % 97 == 0) {
                        Thread.yield(); // da ocasión a que otro hilo del pool se cuele si pudiera
                    }
                    seen.add(id);
                    running.decrementAndGet();
                    done.countDown();
                });
            }
            assertTrue("tasks did not finish", done.await(20, TimeUnit.SECONDS));
            assertEquals(1, maxRunning.get());
            assertEquals(n, seen.size());
            for (int i = 0; i < n; i++) {
                assertEquals(Integer.valueOf(i), seen.get(i));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void aThrowingTaskDoesNotStopTheQueue() throws Exception {
        List<Runnable> backing = new ArrayList<>();
        SerialExecutor serial = new SerialExecutor(backing::add);
        List<String> seen = new ArrayList<>();
        serial.execute(() -> {
            throw new IllegalStateException("boom");
        });
        serial.execute(() -> seen.add("second"));
        // Sin hilo propio: solo la primera está entregada al ejecutor de debajo.
        assertEquals(1, backing.size());
        try {
            backing.remove(0).run();
        } catch (IllegalStateException expected) {
            // la excepción sale por el hilo del pool, pero la cola sigue
        }
        assertEquals(1, backing.size());
        backing.remove(0).run();
        assertEquals(Collections.singletonList("second"), seen);
    }
}
