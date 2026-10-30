package net.rasanovum.roxy.legacy.modern;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflection across the optional, parent-loaded Angelica API. */
final class ShaderReflection {

    static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass()
                .getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(name, error);
        }
    }

    static Object call(Object owner, String name, Object... args) {
        Class<?> type = owner instanceof Class<?> ? (Class<?>) owner : owner.getClass();
        for (Method method : type.getMethods()) if (method.getName()
            .equals(name) && method.getParameterCount() == args.length) {
                boolean match = true;
                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < args.length; i++)
                    if (args[i] != null && !parameters[i].isPrimitive() && !parameters[i].isInstance(args[i]))
                        match = false;
                if (!match) continue;
                try {
                    return method.invoke(owner instanceof Class<?> ? null : owner, args);
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(type.getName() + "." + name, error);
                }
            }
        throw new IllegalStateException("Missing shader API: " + type.getName() + "." + name);
    }

    static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException error) {
            throw new IllegalStateException(name, error);
        }
    }
}
