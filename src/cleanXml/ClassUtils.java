package cleanXml;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;

public class ClassUtils {

    public static LinkedHashSet<Class<?>> getAllInterfaces(final Class<?> cls) {
        LinkedHashSet<Class<?>> interfacesFound = new LinkedHashSet<>();
        getAllInterfaces(cls, interfacesFound);
        return interfacesFound;
    }

    private static void getAllInterfaces(Class<?> cls, final HashSet<Class<?>> interfacesFound) {
        while (cls != null) {
            final Class<?>[] interfaces = cls.getInterfaces();

            for (final Class<?> i : interfaces) {
                if (interfacesFound.add(i)) {
                    getAllInterfaces(i, interfacesFound);
                }
            }

            cls = cls.getSuperclass();
        }
    }

    public static Field getField(Class<?> cls, String fieldName) {
        Field f = null;
        Class<?> c = cls;
        while (f == null && c != null) // stop when we got field or reached top of class hierarchy
        {
            try {
                f = c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }

        if (f != null) {
            f.setAccessible(true);
        }
        return f;
    }
}
