/*
 * Created on May 19, 2003 Developed by Intelligent ChoicePoint Inc. 2003
 */

package org.openl.binding.impl;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Objects;

import org.openl.binding.IBindingContext;
import org.openl.binding.IBoundNode;
import org.openl.binding.impl.method.MethodSearch;
import org.openl.rules.operator.Comparison;
import org.openl.domain.IDomain;
import org.openl.syntax.ISyntaxNode;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.syntax.impl.ISyntaxConstants;
import org.openl.types.IMethodCaller;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenMethod;
import org.openl.types.impl.CastingMethodCaller;

/**
 * @author snshor
 */
public class BinaryOperatorNodeBinder extends ANodeBinder {
    private static final Map<String, String> INVERSE_METHOD;

    static {
        INVERSE_METHOD = Map.of("le", "ge", "lt", "gt", "ge", "le", "gt", "lt", "eq", "eq", "add", "add");
    }

    public static IBoundNode bindOperator(ISyntaxNode node,
                                          String operatorName,
                                          IBoundNode b1,
                                          IBoundNode b2,
                                          IBindingContext bindingContext) {

        IOpenClass[] types = {b1.getType(), b2.getType()};
        IMethodCaller methodCaller = findBinaryOperatorMethodCaller(operatorName, types, bindingContext);
        if (methodCaller == null) {
            String message = errorMsg(operatorName, types[0], types[1]);
            return makeErrorNode(message, node, bindingContext);
        }

        if (methodCaller instanceof CastingMethodCaller) {
            IOpenMethod method = methodCaller.getMethod();
            if (("eq".equals(method.getName()) || "ne".equals(method.getName())) && method.getDeclaringClass()
                    .getInstanceClass() == Comparison.class) {
                IOpenClass[] parameterTypes = method.getSignature().getParameterTypes();
                if (parameterTypes.length == 2) {
                    if (parameterTypes[0].getInstanceClass() == Object.class && parameterTypes[1]
                            .getInstanceClass() == Object.class) {
                        validateComparisonWithObjectIncluded(b1, b2, method, node, bindingContext);
                    }
                    validateComparisonLiteralWithDomainType(b1, b2, method, node, bindingContext);
                }
            }
        }

        return new BinaryOpNode(node, b1, b2, methodCaller);
    }

    private static void validateComparisonLiteralWithDomainType(IBoundNode b1,
                                                                IBoundNode b2,
                                                                IOpenMethod method,
                                                                ISyntaxNode node,
                                                                IBindingContext bindingContext) {
        validateComparisonLiteralWithDomainTypeInternal(b1, b2, method, node, bindingContext);
        validateComparisonLiteralWithDomainTypeInternal(b2, b1, method, node, bindingContext);
    }

    @SuppressWarnings("unchecked")
    private static void validateComparisonLiteralWithDomainTypeInternal(IBoundNode b1,
                                                                        IBoundNode b2,
                                                                        IOpenMethod method,
                                                                        ISyntaxNode node,
                                                                        IBindingContext bindingContext) {
        if (b1.getType().getDomain() != null && b2 instanceof LiteralBoundNode) {
            IDomain<Object> domain = (IDomain<Object>) b1.getType().getDomain();
            LiteralBoundNode literalBoundNode = (LiteralBoundNode) b2;
            if (literalBoundNode.getValue() != null && !domain.selectObject(literalBoundNode.getValue())) {
                BindHelper.processWarn(String.format(
                        "Warning: Object '%s' is outside of valid domain '%s'. The comparison always returns %s.",
                        ((LiteralBoundNode) b2).getValue(),
                        b1.getType().getName(),
                        "ne".equals(method.getName())), node, bindingContext);
            }
        }
    }

    private static void validateComparisonWithObjectIncluded(IBoundNode b1,
                                                             IBoundNode b2,
                                                             IOpenMethod method,
                                                             ISyntaxNode node,
                                                             IBindingContext bindingContext) {
        IOpenClass b1Type = b1.getType();
        IOpenClass b2Type = b2.getType();
        if (!Objects.equals(b1Type, b2Type)) {
            Class<?> b1InstanceClass = b1Type.getInstanceClass();
            Class<?> b2InstanceClass = b2Type.getInstanceClass();
            if (b1InstanceClass == null || b2InstanceClass == null) {
                return;
            }
            boolean b1Final = Modifier.isFinal(b1InstanceClass.getModifiers());
            boolean b2Final = Modifier.isFinal(b2InstanceClass.getModifiers());
            if (!b1Final && !b2Final) {
                return;
            }
            if (b1Final && b2InstanceClass.isAssignableFrom(b1InstanceClass)) {
                return;
            }
            if (b2Final && b1InstanceClass.isAssignableFrom(b2InstanceClass)) {
                return;
            }
            BindHelper.processWarn(String.format(
                    "Warning: Compared elements have different types ('%s', '%s'). Comparing these types always returns %s.",
                    b1.getType().getName(),
                    b2.getType().getName(),
                    "ne".equals(method.getName())), node, bindingContext);
        }
    }

    public static String errorMsg(String methodName, IOpenClass t1, IOpenClass t2) {
        return String.format("Operator '%s(%s, %s)' is not found.", methodName, t1.getName(), t2.getName());
    }

    public static IMethodCaller findBinaryOperatorMethodCaller(String methodName,
                                                               IOpenClass[] types,
                                                               IBindingContext bindingContext) {

        IMethodCaller methodCaller = findSingleBinaryOperatorMethodCaller(methodName, types, bindingContext);

        if (methodCaller != null) {
            return methodCaller;
        }

        String inverse = INVERSE_METHOD.get(methodName);

        if (inverse != null) {

            IOpenClass[] invTypes = new IOpenClass[]{types[1], types[0]};
            methodCaller = findSingleBinaryOperatorMethodCaller(inverse, invTypes, bindingContext);

            if (methodCaller != null) {
                return new BinaryMethodCallerSwapParams(methodCaller);
            }
        }

        return null;
    }

    private static IMethodCaller findSingleBinaryOperatorMethodCaller(String methodName,
                                                                      IOpenClass[] argumentTypes,
                                                                      IBindingContext bindingContext) {

        // An attempt to find the method <namespace>.<methodName>(argumentTypes) in the binding context.
        // This is the most privileged place for searching.
        // @author DLiauchuk
        //
        IMethodCaller methodCaller = bindingContext
                .findMethodCaller(ISyntaxConstants.OPERATORS_NAMESPACE, methodName, argumentTypes);
        if (methodCaller != null) {
            return methodCaller;
        }

        IOpenClass[] types2 = {argumentTypes[1]};

        // An attempt to find method <methodName>(argumentTypes[1]), using the first argument type as a possible
        // collection of suitable methods.
        //
        // TODO: Investigate which case covers this branch. How the method, e.g. foo(Type2) may be suitable
        // for foo(Type1, Type2). Why it has more priority than next items for search?
        // @author DLiauchuk
        //
        methodCaller = MethodSearch.findMethod(methodName, types2, bindingContext, argumentTypes[0], false);
        if (methodCaller != null) {
            return methodCaller;
        }

        // An attempt to find method <methodName>(argumentTypes), using the first argument type as a possible
        // collection of suitable methods, e.g. {@link DoubleValue#add(DoubleValue value1, DoubleValue value2).
        //
        methodCaller = MethodSearch.findMethod(methodName, argumentTypes, bindingContext, argumentTypes[0], false);
        if (methodCaller != null) {
            return methodCaller;
        }

        // An attempt to find method <methodName>(argumentTypes), using the second argument type as a possible
        // collection of suitable methods.
        //
        return MethodSearch.findMethod(methodName, argumentTypes, bindingContext, argumentTypes[1], false);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.binding.INodeBinder#bind(org.openl.parser.ISyntaxNode, org.openl.env.IOpenEnv,
     * org.openl.binding.IBindingContext)
     */
    @Override
    public IBoundNode bind(ISyntaxNode node, IBindingContext bindingContext) throws Exception {

        if (node.getNumberOfChildren() != 2) {
            throw SyntaxNodeExceptionUtils.createError("Binary node must have 2 subnodes.", node);
        }

        int index = node.getType().lastIndexOf('.');

        String methodName = node.getType().substring(index + 1);
        IBoundNode[] children = bindChildren(node, bindingContext);

        return bindOperator(node, methodName, children[0], children[1], bindingContext);
    }

}
