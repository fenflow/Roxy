package net.rasanovum.roxy.legacy.loader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.AccessDeniedException;

import org.junit.Test;

public class LoadDiagnosticsTest {

    @Test
    public void identifiesWrappedClassVersionFailureAndRunningJava() {
        String message = LoadDiagnostics.describe(
            new ClassNotFoundException("external class", new UnsupportedClassVersionError("class file version 65.0")));
        assertTrue(message.contains("requires Java 21+"));
        assertTrue(message.contains(System.getProperty("java.version")));
        assertTrue(message.contains("class file version 65.0"));
    }

    @Test
    public void preservesAbiFailureInsteadOfBlamingJava() {
        String message = LoadDiagnostics
            .describe(new InvocationTargetException(new NoSuchMethodError("missing adapter method")));
        assertTrue(message.contains("NoSuchMethodError: missing adapter method"));
        assertFalse(message.contains("requires Java"));
    }

    @Test
    public void doesNotMislabelCacheAccessFailure() {
        String message = LoadDiagnostics.describe(new AccessDeniedException("roxy/cache-v1/test"));
        assertTrue(message.contains("AccessDeniedException"));
        assertTrue(message.contains("roxy/cache-v1/test"));
        assertFalse(message.contains("Voxy JAR"));
        assertFalse(message.contains("Java"));
    }
}
