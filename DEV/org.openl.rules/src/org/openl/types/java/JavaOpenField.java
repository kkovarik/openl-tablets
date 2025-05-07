/*
 * Created on May 21, 2003
 *
 * Developed by Intelligent ChoicePoint Inc. 2003
 */

package org.openl.types.java;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.openl.rules.annotations.ContextProperty;
import org.openl.types.IMemberMetaInfo;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenField;
import org.openl.util.RuntimeExceptionWrapper;
import org.openl.vm.IRuntimeEnv;

/**
 * @author snshor
 */
public class JavaOpenField implements IOpenField {

    private final Field field;
    private String contextProperty;

    JavaOpenField(Field field) {
        this.field = field;
        ContextProperty contextProperty = field.getAnnotation(ContextProperty.class);
        if (contextProperty != null) {
            this.contextProperty = contextProperty.value();
        }
    }

    @Override
    public Object get(Object target, IRuntimeEnv env) {
        if (target == null && !Modifier.isStatic(field.getModifiers())) {
            return getType().nullObject();
        }
        try {
            return field.get(target);
        } catch (Exception t) {
            throw RuntimeExceptionWrapper.wrap(t);
        }
    }

    @Override
    public IOpenClass getDeclaringClass() {
        return JavaOpenClass.getOpenClass(field.getDeclaringClass());
    }

    @Override
    public String getDisplayName(int mode) {
        return getName();
    }

    @Override
    public boolean isTransient() {
        return Modifier.isTransient(field.getModifiers());
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.types.IOpenMember#getInfo()
     */
    @Override
    public IMemberMetaInfo getInfo() {
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.base.INamedThing#getName()
     */
    @Override
    public String getName() {
        return field.getName();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.types.IOpenMember#getType()
     */
    @Override
    public IOpenClass getType() {
        return JavaOpenClass.getOpenClass(field.getType());
    }

    @Override
    public boolean isConst() {
        return Modifier.isFinal(field.getModifiers());
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.types.IOpenMember#isStatic()
     */
    @Override
    public boolean isStatic() {
        return Modifier.isStatic(field.getModifiers());
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public void set(Object target, Object value, IRuntimeEnv env) {
        if (target != null) {
            try {
                field.set(target, value);
            } catch (Exception t) {
                throw RuntimeExceptionWrapper.wrap(t);
            }
        }
    }

    @Override
    public String toString() {
        return getName();
    }

    public Field getJavaField() {
        return field;
    }

    @Override
    public boolean isContextProperty() {
        return contextProperty != null;
    }

    @Override
    public String getContextProperty() {
        return contextProperty;
    }
}
