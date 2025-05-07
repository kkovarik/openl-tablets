package org.openl.binding.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Array;

import org.junit.jupiter.api.BeforeAll;

import org.openl.binding.ICastFactory;
import org.openl.binding.exception.AmbiguousMethodException;
import org.openl.binding.impl.cast.CastFactory;
import org.openl.binding.impl.method.MethodSearch;
import org.openl.conf.LibrariesRegistry;
import org.openl.types.IMethodCaller;
import org.openl.types.IOpenClass;
import org.openl.types.NullOpenClass;
import org.openl.types.java.JavaOpenClass;
import org.openl.util.ClassUtils;

public abstract class AbstractMethodSearchTest {
    static final String AMB = "AMBIGUOUS";
    static final String NF = "NOT FOUND";
    static CastFactory castFactory;

    @BeforeAll
    public static void init() {
        castFactory = new CastFactory();
        castFactory.setMethodFactory(new LibrariesRegistry().asMethodFactory());
    }

    final void assertInvoke(Object expected,
                            Class<?> target,
                            String methodName,
                            Class<?>... classes) throws AmbiguousMethodException {
        Object[] args = toArgs(classes);
        assertInvoke(expected, target, methodName, classes, args);
    }

    final void assertInvoke(Object expected,
                            Class<?> target,
                            String methodName,
                            Class<?>[] classes,
                            Object[] args) throws AmbiguousMethodException {
        JavaOpenClass aClass = JavaOpenClass.getOpenClass(target);

        IOpenClass[] openClasses = toOpenClasses(classes);

        IMethodCaller method = MethodSearch.findMethod(methodName, openClasses, castFactory, aClass, true);

        assertNotNull(method, "Method " + methodDescriptor(methodName, openClasses) + " has not been matched.");
        Object targetInstance = instance(target);
        Object result = method.invoke(targetInstance, args, null);
        assertEquals(expected, result, "Method " + methodDescriptor(methodName, openClasses) + " has been matched.");
    }

    private IOpenClass[] toOpenClasses(Class<?>... classes) {
        if (classes == null) {
            return IOpenClass.EMPTY;
        }
        IOpenClass[] openClasses = new IOpenClass[classes.length];
        for (int i = 0; i < classes.length; i++) {
            if (classes[i] != null) {
                openClasses[i] = JavaOpenClass.getOpenClass(classes[i]);
            } else {
                openClasses[i] = NullOpenClass.the;
            }
        }
        return openClasses;
    }

    final void assertNotFound(Class<?> target, String methodName, Class<?>... classes) throws AmbiguousMethodException {
        JavaOpenClass aClass = JavaOpenClass.getOpenClass(target);

        IOpenClass[] openClasses = toOpenClasses(classes);

        IMethodCaller method = MethodSearch.findMethod(methodName, openClasses, castFactory, aClass, true);

        assertNull(method, "Method " + methodDescriptor(methodName, openClasses) + " has been matched.");
    }

    final void assertAmbiguous(Class<?> target, String methodName, Class<?>... classes) {
        try {
            JavaOpenClass aClass = JavaOpenClass.getOpenClass(target);
            IOpenClass[] openClasses = toOpenClasses(classes);
            MethodSearch.findMethod(methodName, openClasses, castFactory, aClass, true);
            fail("AmbiguousMethodException should be thrown for " + methodDescriptor(methodName, openClasses));
        } catch (AmbiguousMethodException ex) {
            // expected
        }
    }

    final void assertMethod(Class<?> target,
                            String methodName,
                            Class<?>[] classes,
                            Object... expects) throws AmbiguousMethodException {
        assertEquals(classes.length, expects.length);
        for (int i = 0; i < classes.length; i++) {
            Object expected = expects[i];
            assertMethod(expected, target, methodName, classes[i]);
        }
    }

    final void assertMethod(Class<?> target,
                            String methodName,
                            Class<?> class1,
                            Class<?>[] classes,
                            Object... expects) throws AmbiguousMethodException {
        assertEquals(classes.length, expects.length);
        for (int i = 0; i < classes.length; i++) {
            Object expected = expects[i];
            assertMethod(expected, target, methodName, class1, classes[i]);
        }
    }

    final void assertMethod(Object expected,
                            Class<?> target,
                            String methodName,
                            Class<?>... classes) throws AmbiguousMethodException {
        if (NF.equals(expected)) {
            assertNotFound(target, methodName, classes);
        } else if (AMB.equals(expected)) {
            assertAmbiguous(target, methodName, classes);
        } else if (expected instanceof Not) {
            try {
                Object notExpected = ((Not) expected).notExpected;
                assertInvoke(notExpected, target, methodName, classes);
                IOpenClass[] openClasses = toOpenClasses(classes);
                fail("Not expected '" + notExpected + "' result for method " + methodDescriptor(methodName,
                        openClasses) + ".");
            } catch (AssertionError ex) {
                // It is expected an assertion error
            }
        } else {
            assertInvoke(expected, target, methodName, classes);
        }
    }

    final Object not(final Object expected) {
        return new Not() {
            {
                notExpected = expected;
            }
        };
    }

    private Object[] toArgs(Class<?>... classes) {
        if (classes == null) {
            return new Class<?>[0];
        }
        Object[] args = new Object[classes.length];
        for (int i = 0; i < classes.length; i++) {
            args[i] = instance(classes[i]);
        }
        return args;
    }

    private Object instance(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        Object o;
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            Object item = instance(componentType);
            o = Array.newInstance(componentType, 1);
            Array.set(o, 0, item);
            return o;
        }
        if (clazz.isPrimitive()) {
            clazz = ClassUtils.primitiveToWrapper(clazz);
        }
        try {
            o = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception exc) {
            try {
                o = clazz.getMethod("valueOf", String.class).invoke(null, "1");
            } catch (Exception exc2) {
                try {
                    o = clazz.getDeclaredConstructor(String.class).newInstance("2");
                } catch (Exception exc3) {
                    try {
                        o = clazz.getMethod("valueOf", char.class).invoke(null, 'A');
                    } catch (Exception exc4) {
                        o = null;
                    }
                }
            }
        }
        return o;
    }

    final ICastFactory getCastFactory() {
        return castFactory;
    }

    private String methodDescriptor(String name, IOpenClass[] args) {
        StringBuilder builder = new StringBuilder(100);
        builder.append(name).append('(');
        boolean flag = false;
        for (IOpenClass arg : args) {
            if (flag) {
                builder.append(", ");
            }
            flag = true;
            builder.append(arg.getName());
        }
        builder.append(')');
        return builder.toString();
    }

    private static class Not {
        Object notExpected;
    }
}
