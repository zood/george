package io.pijun.george;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.pijun.george.api.task.PersistentQueue;

import static junit.framework.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PersistentQueueTest {

    static PersistentQueue.Converter<String> mConverter = new PersistentQueue.Converter<String>() {
        @Override
        public String deserialize(byte[] bytes) {
            return new String(bytes);
        }

        @Override
        public byte[] serialize(String t) {
            return t.getBytes();
        }
    };

    static PersistentQueue<String> mQueue;
    static final String element1 = "Hello, world";
    static final String element2 = "Goodbye, world";
    static final String element3 = "Allah-u-Abha, Abha Kingdom!";

    @Test
    public void testCreation() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getTargetContext();

        mQueue = new PersistentQueue<>(appContext, "testQueue", mConverter);
        assertNotNull(mQueue);
    }

    @Test
    public void testOffer() throws Exception {
        mQueue.offer(element1);
        assertEquals(mQueue.size(), 1);
    }

    @Test
    public void testPeek() throws Exception {
        String peeked = mQueue.peek();
        assertEquals(peeked, element1);
    }

    @Test
    public void testPoll() throws Exception {
        String polled = mQueue.poll();
        assertEquals(polled, element1);
        assertEquals(mQueue.size(), 0);
    }

    @Test
    public void testOrdering() throws Exception {
        mQueue.offer(element1);
        mQueue.offer(element2);
        mQueue.offer(element3);

        assertEquals(mQueue.size(), 3);

        assertEquals(mQueue.poll(), element1);
        assertEquals(mQueue.poll(), element2);
        assertEquals(mQueue.poll(), element3);

        assertEquals(mQueue.size(), 0);
    }

}
