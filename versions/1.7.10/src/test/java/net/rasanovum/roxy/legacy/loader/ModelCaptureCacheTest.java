package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

import net.rasanovum.roxy.legacy.bridge.ModelCaptureCache;

import org.junit.Test;

public class ModelCaptureCacheTest {

    @Test
    public void brokenModCallbackFallsBackOnceAndOtherModelsStillCapture() throws Exception {
        ModelCaptureCache<String> cache = new ModelCaptureCache<String>();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger reports = new AtomicInteger();
        for (int i = 0; i < 20; i++) assertEquals("cube", cache.capture("machine:0", () -> {
            calls.incrementAndGet();
            throw new NullPointerException("Tile entity unavailable");
        }, () -> "cube", (key, error) -> reports.incrementAndGet()));
        assertEquals(1, calls.get());
        assertEquals(1, reports.get());
        assertEquals("model", cache.capture("stone:0", () -> "model", () -> "wrong", (k, e) -> fail()));
        assertEquals(1, cache.fallbackCount());
        cache.clear();
        assertEquals("fixed", cache.capture("machine:0", () -> "fixed", () -> "wrong", (k, e) -> fail()));
    }

    @Test
    public void optionalModLinkageFailureIsContainedButFatalVmFailureIsNot() throws Exception {
        ModelCaptureCache<String> cache = new ModelCaptureCache<String>();
        assertEquals(
            "cube",
            cache.capture(
                "optional",
                () -> { throw new NoClassDefFoundError("optional renderer"); },
                () -> "cube",
                (k, e) -> {}));
        try {
            cache.capture(
                "fatal",
                () -> { throw new OutOfMemoryError("test sentinel"); },
                () -> "wrong",
                (k, e) -> fail());
            fail("Fatal VM errors must not be hidden");
        } catch (OutOfMemoryError expected) {
            assertEquals("test sentinel", expected.getMessage());
        }
    }
}
