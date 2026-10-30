package net.rasanovum.roxy.legacy.terrain;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class CaptureQueueTest {

    @Test
    public void updatesToAlreadyCapturedSectionsAreNotLostWhileOtherSectionsWait() {
        CaptureQueue<String> queue = new CaptureQueue<String>();
        queue.offer(7, "received chunk", 7, 7);
        assertEquals(2, queue.poll().section);
        assertTrue(queue.changed(7, 4));
        assertEquals(2, queue.poll().section);
        assertEquals(1, queue.poll().section);
        assertTrue(queue.poll().last);
        assertFalse(queue.changed(7, 4));
        assertNull(queue.poll());
    }

    @Test
    public void flightKeepsEveryReceivedChunkAndCapturesOuterSurfacesFairly() {
        CaptureQueue<String> queue = new CaptureQueue<String>();
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) queue.offer(LodChunk.key(x, z), x + ":" + z, 65535, 0x1f);
        Map<Long, Integer> seen = new HashMap<Long, Integer>();
        for (int n = 0; n < 289; n++) {
            CaptureQueue.Work<String> work = queue.poll();
            assertEquals(4, work.section);
            assertNull(seen.put(work.key, 1 << work.section));
        }
        // The camera moves a long way, and the provider has unloaded the old chunks.
        queue.offer(LodChunk.key(200, 200), "new", 65535, 0x3f);
        while (queue.size() != 0) {
            CaptureQueue.Work<String> work = queue.poll();
            seen.put(work.key, seen.getOrDefault(work.key, 0) | (1 << work.section));
            assertNotNull(work.value);
        }
        assertEquals(290, seen.size());
        for (int mask : seen.values()) assertEquals(65535, mask);
    }

    @Test
    public void repeatedUpdatesCoalesceAndZeroMaskDoesNotInventSections() {
        CaptureQueue<String> queue = new CaptureQueue<String>();
        queue.offer(1, "first", 0, 0);
        assertNull(queue.poll());
        queue.offer(1, "old", 3, 3);
        queue.offer(1, "new", 6, 6);
        assertEquals(1, queue.size());
        assertEquals("new", queue.poll().value);
        assertEquals(1, queue.poll().section);
        assertTrue(queue.poll().last);
        assertNull(queue.poll());
    }
}
