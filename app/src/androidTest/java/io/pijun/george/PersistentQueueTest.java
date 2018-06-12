package io.pijun.george;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.pijun.george.queue.PersistentQueue;

import static junit.framework.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersistentQueueTest {

    private static PersistentQueue.Converter<String> mConverter = new PersistentQueue.Converter<String>() {
        @Override
        public String deserialize(byte[] bytes) {
            return new String(bytes);
        }

        @Override
        public byte[] serialize(String t) {
            return t.getBytes();
        }
    };

    private static PersistentQueue<String> queue;
    private static final String element1 = "Hello, world";
    private static final String element2 = "Goodbye, world";
    private static final String element3 = "Allah-u-Abha, Abha Kingdom!";

    @BeforeClass
    public static void setUp() {
        Context appContext = InstrumentationRegistry.getTargetContext();

        queue = new PersistentQueue<>(appContext, null, mConverter);
    }

    @Before
    public void clearBeforeTest() {
        queue.clear();
    }

    @Test
    public void testOffer() {
        queue.offer(element1);
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testPeek() {
        queue.offer(element1);
        String peeked = queue.peek();
        assertEquals(peeked, element1);

        // the queue should still contain the element after the peek
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testPoll() {
        queue.offer(element1);

        String polled = queue.poll();
        assertEquals(polled, element1);
        assertEquals(queue.size(), 0);
    }

    @Test
    public void testOrdering() {
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
    public void testTake() throws Exception {
        queue.offer(element1);
        queue.offer(element2);

        assertEquals(queue.take(), element1);
        assertEquals(queue.take(), element2);
        assertEquals(queue.size(), 0);
    }

    @Test
    public void testBlockingPeek() throws Exception {
        queue.offer(element1);
        assertEquals(queue.blockingPeek(), element1);
        assertEquals(queue.size(), 1);
    }

    @Test
    public void testNullPoll() {
        String e = queue.poll();
        assertNull(e);
    }
}
