package org.openl.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Array;
import java.util.Objects;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {

    final C[] array1D = new C[]{new C("c1", "c1@email"), new C("c2", "c2@email"), new C("c3", "c3@email")};
    final A[] resultArray1D = new A[]{new C("c1", "c1@email"), new C("c2", "c2@email"), new C("c3", "c3@email")};

    final C[][] array2D = new C[][]{{new C("c11", "email"), new C("c12", "email"), new C("c13", "email")},
            {new C("c21", "email"), new C("c22", "email"), new C("c23", "email")}};

    final A[][] resultArray2D = new A[][]{{new C("c11", "email"), new C("c12", "email"), new C("c13", "email")},
            {new C("c21", "email"), new C("c22", "email"), new C("c23", "email")}};

    final C[][][] array3DWithEmptyElements = new C[][][]{
            {{new C("c111", "email"), new C("c111", "email"), new C("c111", "email")},
                    {new C("c211", "email"), new C("c211", "email")},
                    {}},
            {{}}};

    final C[][][] array3D = new C[][][]{
            {{new C("c111", "email"), new C("c121", "email"), new C("c131", "email")},
                    {new C("c211", "email"), new C("c221", "email")},
                    {new C("c311", "email"), new C("c321", "email")},},
            {{new C("c112", "email"), new C("c122", "email"), new C("c132", "email")},
                    {new C("c212", "email"), new C("c222", "email")},
                    {new C("c312", "email"), new C("c322", "email")}}};

    final C[][][] resultArray3D = new C[][][]{
            {{new C("c111", "email"), new C("c121", "email"), new C("c131", "email")},
                    {new C("c211", "email"), new C("c221", "email")},
                    {new C("c311", "email"), new C("c321", "email")},},
            {{new C("c112", "email"), new C("c122", "email"), new C("c132", "email")},
                    {new C("c212", "email"), new C("c222", "email")},
                    {new C("c312", "email"), new C("c322", "email")}}};

    final A[][][] resultArray3DWithEmptyElements = new A[][][]{
            {{new C("c111", "email"), new C("c111", "email"), new C("c111", "email")},
                    {new C("c211", "email"), new C("c211", "email")},
                    {}},
            {{}}};

    final C[][][][][] array5D = new C[][][][][]{{array3D, array3D, array3D}, {array3D,}, {{}, {}, {}, {}, {}, {}}};
    final A[][][][][] resultArray5D = new A[][][][][]{{array3D, array3D, array3D},
            {array3D,},
            {{}, {}, {}, {}, {}, {}}};

    @Test
    public void nonAssignableClass() {
        Object o = ArrayUtils.repackArray(array1D, B[].class);
        assertArrayEquals((Object[]) o, array1D);
    }

    @Test
    public void testDifferentDims() {
        Object o = ArrayUtils.repackArray(array2D, A[].class);
        assertArrayEquals((Object[]) o, array2D);
        Class<?> expectedType = o.getClass();
        while (expectedType.isArray()) {
            expectedType = expectedType.getComponentType();
        }
        assertEquals(C.class, expectedType);

    }

    @Test
    public void testEmptyArray() {
        Object o = ArrayUtils.repackArray(new C[0], A[].class);
        int length = Array.getLength(o);
        assertEquals(0, length);

        Object o1 = ArrayUtils.repackArray(new A[0], C[].class);
        int length1 = Array.getLength(o1);
        assertEquals(0, length1);
    }

    @Test
    public void test1DArray() {
        Object o = ArrayUtils.repackArray(array1D, A[].class);
        assertArrayEquals((Object[]) o, resultArray1D);
        Class<?> resultType = o.getClass();
        while (resultType.isArray()) {
            resultType = resultType.getComponentType();
        }
        assertEquals(A.class, resultType);
    }

    @Test
    public void test2DArray() {
        Object o = ArrayUtils.repackArray(array2D, A[][].class);
        assertArrayEquals((Object[][]) o, resultArray2D);
        Class<?> resultType = o.getClass();
        while (resultType.isArray()) {
            resultType = resultType.getComponentType();
        }
        assertEquals(A.class, resultType);
    }

    @Test
    public void test3DArray() {
        Object o = ArrayUtils.repackArray(array3DWithEmptyElements, A[][][].class);
        assertArrayEquals((Object[][][]) o, resultArray3DWithEmptyElements);
        Class<?> resultType = o.getClass();
        while (resultType.isArray()) {
            resultType = resultType.getComponentType();
        }
        assertEquals(A.class, resultType);

        Object oFull = ArrayUtils.repackArray(array3D, A[][][].class);
        assertArrayEquals((Object[][][]) oFull, resultArray3D);
        Class<?> resultType1 = oFull.getClass();
        while (resultType1.isArray()) {
            resultType1 = resultType1.getComponentType();
        }
        assertEquals(A.class, resultType1);
    }

    @Test
    public void test5DArray() {
        Object o = ArrayUtils.repackArray(array5D, A[][][][][].class);
        assertArrayEquals((Object[][][]) o, resultArray5D);
        Class<?> resultType = o.getClass();
        while (resultType.isArray()) {
            resultType = resultType.getComponentType();
        }
        assertEquals(A.class, resultType);
    }

    @Test
    void convertTest() {
        assertNull(ArrayUtils.convert(null, Function.identity()));
        assertEquals(10, ArrayUtils.convert(10, Function.identity()));
        assertEquals("10", ArrayUtils.convert(10, String::valueOf));
        assertConvert(new String[]{"10"}, ArrayUtils.convert(new int[]{10}, String::valueOf));
        assertConvert(new Integer[]{1, 25, 30}, ArrayUtils.convert(new int[]{1, 25, 30}, Function.identity()));
        assertConvert(new Number[]{1, 2.5, 3.0}, ArrayUtils.convert(new int[]{1, 25, 30}, x -> ((int) x) > 10 ? ((int) x) / 10.0 : x));
        assertConvert(new String[]{"null"}, ArrayUtils.convert(new Integer[]{null}, String::valueOf));
        assertConvert(new Integer[]{null, 5}, ArrayUtils.convert(new Integer[]{null, 5}, Function.identity()));
        assertConvert(new Integer[]{10, 5}, ArrayUtils.convert(new Object[]{10, 5}, Function.identity()));
        assertConvert(new Number[]{10, 5.5}, ArrayUtils.convert(new Object[]{10, 5.5}, Function.identity()));
        assertConvert(new Number[][]{null, {10, 5.5}, null}, ArrayUtils.convert(new Object[][]{null, {10, 5.5}, null}, Function.identity()));
        assertConvert(new Double[][]{null, {10.5, 5.5}, null}, ArrayUtils.convert(new Object[][]{null, {10.5, 5.5}, null}, Function.identity()));
    }

    private static void assertConvert(Object[] expected, Object actual) {
        assertEquals(expected.getClass(), actual.getClass());
        assertArrayEquals(expected, (Object[]) actual);
    }
}

class A {
    final String name;

    public A(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        A a = (A) o;

        return Objects.equals(name, a.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }
}

class B {
    final String name;

    public B(String name) {
        this.name = name;
    }
}

class C extends A {
    final String email;

    public C(String name, String email) {
        super(name);
        this.email = email;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        C c = (C) o;

        return Objects.equals(email, c.email);
    }

    @Override
    public int hashCode() {
        return email != null ? email.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "C{" + "name='" + name + '\'' + '}';
    }
}
