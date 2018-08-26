package io.pijun.george;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.SecureRandom;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import io.pijun.george.queue.PersistentQueue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class PersistentQueueTest {

    private static PersistentQueue.Converter<String> stringConverter = new PersistentQueue.Converter<String>() {
        @Override
        public String deserialize(@NonNull byte[] bytes) {
            return new String(bytes, Constants.utf8);
        }

        @NonNull
        @Override
        public byte[] serialize(@NonNull String t) {
            return t.getBytes(Constants.utf8);
        }
    };

    private static PersistentQueue.Converter<byte[]> bytesConverter = new PersistentQueue.Converter<byte[]>() {
        @Override
        public byte[] deserialize(@NonNull byte[] bytes) {
            return bytes;
        }

        @NonNull
        @Override
        public byte[] serialize(@NonNull byte[] item) {
            return item;
        }
    };

    private static final String element1 = "Hello, world";
    private static final String element2 = "Goodbye, world";
    private static final String element3 = "Allah-u-Abha, Abha Kingdom!";

    public static PersistentQueue<String> newStringQueue() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        return new PersistentQueue<>(ctx, null, stringConverter);
    }

    @Test
    public void testOffer() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testPeek() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);
        String peeked = queue.peek();
        assertEquals(peeked, element1);

        // the queue should still contain the element after the peek
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testPoll() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);

        String polled = queue.poll();
        assertEquals(polled, element1);
        assertEquals(queue.size(), 0);
    }

    @Test
    public void testOrdering() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);
        queue.offer(element2);
        queue.offer(element3);

        assertEquals(queue.size(), 3);

        assertEquals(queue.poll(), element1);
        assertEquals(queue.poll(), element2);
        assertEquals(queue.poll(), element3);

        assertEquals(queue.size(), 0);
    }

    @Test
    public void testTake() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);
        queue.offer(element2);

        assertEquals(queue.take(), element1);
        assertEquals(queue.take(), element2);
        assertEquals(queue.size(), 0);
    }

    @Test
    public void testBlockingPeek() {
        PersistentQueue<String> queue = newStringQueue();
        queue.offer(element1);
        assertEquals(queue.blockingPeek(), element1);
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testNullPoll() {
        PersistentQueue<String> queue = newStringQueue();
        String e = queue.poll();
        assertNull(e);
    }

    @Test
    public void testChunkSizedObject() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        PersistentQueue<byte[]> queue = new PersistentQueue<>(ctx, null, bytesConverter);
        byte[] bytes = new byte[768 * 1024];
        new SecureRandom().nextBytes(bytes);
        queue.offer(bytes);
        assertEquals(queue.size(), 1);
        byte[] copy = queue.poll();
        assertArrayEquals(copy, bytes);
    }

    @Test
    public void testChunkSizedPlus1() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        PersistentQueue<byte[]> queue = new PersistentQueue<>(ctx, null, bytesConverter);
        byte[] bytes = new byte[(768 * 1024) + 1];
        new SecureRandom().nextBytes(bytes);
        queue.offer(bytes);
        assertEquals(queue.size(), 1);
        byte[] copy = queue.poll();
        assertArrayEquals(copy, bytes);
    }

    @Test
    public void test2xChunkSizeObject() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        PersistentQueue<byte[]> queue = new PersistentQueue<>(ctx, null, bytesConverter);
        byte[] bytes = new byte[768 * 1024 * 2];
        new SecureRandom().nextBytes(bytes);
        queue.offer(bytes);
        assertEquals(queue.size(), 1);
        byte[] copy = queue.poll();
        assertArrayEquals(copy, bytes);
    }

    @Test
    public void test1ByteObject() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        PersistentQueue<byte[]> queue = new PersistentQueue<>(ctx, null, bytesConverter);
        byte[] bytes = new byte[1];
        new SecureRandom().nextBytes(bytes);
        queue.offer(bytes);
        assertEquals(queue.size(), 1);
        byte[] copy = queue.poll();
        assertArrayEquals(copy, bytes);
    }

    @Test
    public void testZeroByteObject() {
        Context ctx = InstrumentationRegistry.getTargetContext();
        PersistentQueue<byte[]> queue = new PersistentQueue<>(ctx, null, bytesConverter);
        byte[] bytes = new byte[0];
        queue.offer(bytes);
        assertEquals(queue.size(), 1);
        byte[] copy = queue.poll();
        assertArrayEquals(copy, bytes);
    }
}
