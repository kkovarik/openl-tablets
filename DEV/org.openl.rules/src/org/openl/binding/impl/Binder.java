package org.openl.binding.impl;

import java.util.HashMap;
import java.util.Map;

import org.openl.IOpenBinder;
import org.openl.OpenL;
import org.openl.binding.IBindingContext;
import org.openl.binding.IBoundCode;
import org.openl.binding.IBoundNode;
import org.openl.binding.ICastFactory;
import org.openl.binding.INameSpacedMethodFactory;
import org.openl.binding.INameSpacedTypeFactory;
import org.openl.binding.INameSpacedVarFactory;
import org.openl.syntax.ISyntaxNode;
import org.openl.syntax.code.IParsedCode;
import org.openl.types.impl.MethodKey;
import org.openl.types.java.JavaOpenClass;

/**
 * Default implementation of {@link IOpenBinder}.
 *
 * @author Yury Molchan
 */
public class Binder implements IOpenBinder {

    private final Map<MethodKey, Object> methodCache = new HashMap<>();
    private final OpenL openl;
    private final ICastFactory castFactory;
    private final INameSpacedVarFactory varFactory;
    private final INameSpacedTypeFactory typeFactory;
    private final INameSpacedMethodFactory methodFactory;

    public Binder(INameSpacedMethodFactory methodFactory,
                  ICastFactory castFactory,
                  INameSpacedVarFactory varFactory,
                  INameSpacedTypeFactory typeFactory,
                  OpenL openl) {

        this.methodFactory = methodFactory;
        this.castFactory = castFactory;
        this.varFactory = varFactory;
        this.typeFactory = typeFactory;
        this.openl = openl;
    }

    @Override
    public ICastFactory getCastFactory() {
        return castFactory;
    }

    @Override
    public INameSpacedMethodFactory getMethodFactory() {
        return methodFactory;
    }

    @Override
    public INameSpacedTypeFactory getTypeFactory() {
        return typeFactory;
    }

    @Override
    public INameSpacedVarFactory getVarFactory() {
        return varFactory;
    }

    @Override
    public IBindingContext makeBindingContext() {
        return new BindingContext(this, JavaOpenClass.VOID, openl);
    }

    @Override
    public IBoundCode bind(IParsedCode parsedCode) {
        return bind(parsedCode, null);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.IOpenBinder#bind(org.openl.syntax.IParsedCode)
     */
    @Override
    public IBoundCode bind(IParsedCode parsedCode, IBindingContext bindingContext) {
        if (bindingContext == null) {
            bindingContext = makeBindingContext();
        }

        ISyntaxNode syntaxNode = parsedCode.getTopNode();
        IBoundNode boundNode;
        try {
            bindingContext.pushLocalVarContext();
            boundNode = ANodeBinder.bindChildNode(syntaxNode, bindingContext);
        } finally {
            bindingContext.popLocalVarContext();
        }
        return new BoundCode(parsedCode, boundNode, bindingContext.getErrors(), bindingContext.getMessages());
    }

    public Map<MethodKey, Object> getMethodCache() {
        return methodCache;
    }
}
