package org.openl.rules.types.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.openl.exception.OpenLRuntimeException;
import org.openl.rules.context.IRulesRuntimeContext;
import org.openl.rules.lang.xls.binding.XlsModuleOpenClass;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.lang.xls.syntax.XlsModuleSyntaxNode;
import org.openl.rules.table.properties.ITableProperties;
import org.openl.rules.table.properties.PropertiesHelper;
import org.openl.rules.types.OpenMethodDispatcher;
import org.openl.rules.validation.properties.dimentional.TableSyntaxNodeDispatcherBuilder;
import org.openl.runtime.IRuntimeContext;
import org.openl.types.IMemberMetaInfo;
import org.openl.types.IOpenMethod;

/**
 * Represents group of methods(rules) overloaded by dimension properties.
 * <p>
 * TODO: refactor invoke functionality. Use {@link org.openl.rules.method.RulesMethodInvoker}.
 */
public class MatchingOpenMethodDispatcher extends OpenMethodDispatcher {
    // The fields below hold only algorithms and they don't change during
    // application lifetime. There is no need
    // to hold a new instance of that objects for every of thousands of
    // MatchingOpenMethodDispatchers. That's why
    // they were made static.
    private static final IPropertiesContextMatcher matcher = new DefaultPropertiesContextMatcher();
    private static final DefaultTablePropertiesSorter prioritySorter = new DefaultTablePropertiesSorter();
    private static final DefaultPropertiesIntersectionFinder intersectionMatcher = new DefaultPropertiesIntersectionFinder();

    private List<IOpenMethod> candidatesSorted;

    private IOpenMethod decisionTableOpenMethod;

    public IOpenMethod getDecisionTableOpenMethod() {
        return decisionTableOpenMethod;
    }

    public void setDecisionTableOpenMethod(IOpenMethod decisionTableOpenMethod) {
        this.decisionTableOpenMethod = decisionTableOpenMethod;
    }

    public MatchingOpenMethodDispatcher() {
    }

    public MatchingOpenMethodDispatcher(IOpenMethod method, XlsModuleOpenClass xlsModuleOpenClass) {
        super(method, xlsModuleOpenClass);
    }

    @Override
    public void addMethod(IOpenMethod candidate) {
        super.addMethod(candidate);
        candidatesSorted = null;
    }

    @Override
    protected IOpenMethod findMatchingMethod(List<IOpenMethod> candidates, IRuntimeContext context) {
        Set<IOpenMethod> selected = new HashSet<>(candidates);

        selectCandidates(selected, (IRulesRuntimeContext) context);
        maxMinSelectCandidates(selected, (IRulesRuntimeContext) context);

        switch (selected.size()) {
            case 0:
                IOpenMethod candidateMethod = candidates.iterator().next();
                throw new OpenLRuntimeException(String.format(
                        "No matching methods with name '%3$s' for the context. Details: \n%1$s\nContext: %2$s",
                        toString(candidates),
                        context.toString(),
                        candidateMethod.getName()));
            case 1:
                return selected.iterator().next();
            default:
                IOpenMethod method = selected.iterator().next();
                throw new OpenLRuntimeException(
                        String.format("Ambiguous dispatch for method '%3$s'. Details: \n%1$s\nContext: %2$s",
                                toString(selected),
                                context.toString(),
                                method.getName()));
        }

    }

    @Override
    public TableSyntaxNode getDispatcherTable() {
        if (decisionTableOpenMethod == null) {
            XlsModuleOpenClass moduleOpenClass = getDeclaringClass();
            TableSyntaxNode tsn = new TableSyntaxNodeDispatcherBuilder(moduleOpenClass.getRulesModuleBindingContext(),
                    moduleOpenClass,
                    this).build();
            if (tsn != null) {
                XlsModuleSyntaxNode xlsModuleNode = moduleOpenClass.getXlsMetaInfo().getXlsModuleNode();
                xlsModuleNode.getWorkbookSyntaxNodes()[0].getWorksheetSyntaxNodes()[0].addNode(tsn);
            }
        }
        if (decisionTableOpenMethod != null) {
            return (TableSyntaxNode) decisionTableOpenMethod.getInfo().getSyntaxNode();
        }
        throw new IllegalStateException(String.format("There is no dispatcher table for [%s] method.", getName()));
    }

    @Override
    public IMemberMetaInfo getInfo() {
        if (getCandidates().size() == 1) {
            return getCandidates().get(0).getInfo();
        }
        return getDispatcherTable().getMember().getInfo();
    }

    private enum MethodDispatchingPriority {
        HIGHER,
        LOWER,
        EQUAL
    }

    private MethodDispatchingPriority compareMethodProperties(ITableProperties candidateProperties,
                                                              ITableProperties mostPriorityProperties,
                                                              List<String> notNullPropertyNames) {
        boolean nested = false;
        boolean contains = false;
        propsLoop:
        for (String propName : notNullPropertyNames) {
            switch (intersectionMatcher.match(propName, candidateProperties, mostPriorityProperties)) {
                case NESTED:
                    nested = true;
                    break;
                case CONTAINS:
                    contains = true;
                    break;
                case EQUALS:
                case UNKNOWN:
                    // do nothing
                    break;
                case NO_INTERSECTION:
                case PARTLY_INTERSECTS:
                    nested = false;
                    contains = false;
                    break propsLoop;
            }
        }

        if (nested && !contains) {
            return MethodDispatchingPriority.HIGHER;
        } else if (contains && !nested) {
            return MethodDispatchingPriority.LOWER;
        } else {
            return MethodDispatchingPriority.EQUAL;
        }
    }

    private void maxMinSelectCandidates(Set<IOpenMethod> selected, IRulesRuntimeContext context) {
        // If more that one method
        if (selected.size() > 1) {
            List<IOpenMethod> notPriorMethods = new ArrayList<>();

            List<String> notNullPropertyNames = getNotNullPropertyNames(context);
            // Find the most high priority method
            List<IOpenMethod> mostPriority = new ArrayList<>();
            ITableProperties mostPriorityProperties = null;

            for (IOpenMethod candidate : selected) {
                if (mostPriority.isEmpty()) {
                    mostPriority.add(candidate);
                    mostPriorityProperties = PropertiesHelper.getTableProperties(candidate);
                } else {
                    ITableProperties candidateProperties = PropertiesHelper.getTableProperties(candidate);
                    int cmp = compareMaxMinPriorities(candidateProperties, mostPriorityProperties);
                    if (cmp < 0) {
                        notPriorMethods.addAll(mostPriority);
                        mostPriority.clear();
                        mostPriority.add(candidate);
                        mostPriorityProperties = PropertiesHelper.getTableProperties(candidate);
                    } else if (cmp == 0) {
                        mostPriority.add(candidate);
                    } else {
                        notPriorMethods.add(candidate);
                    }
                }
            }
            notPriorMethods.forEach(selected::remove);
            if (selected.size() > 1) {
                notPriorMethods.clear();
                mostPriority.clear();
                for (IOpenMethod candidate : selected) {
                    if (mostPriority.isEmpty() || notNullPropertyNames.isEmpty()) {
                        mostPriority.add(candidate);
                    } else {
                        ITableProperties candidateProperties = PropertiesHelper.getTableProperties(candidate);
                        int higherCount = 0;
                        int lowerCount = 0;
                        for (IOpenMethod m : mostPriority) {
                            ITableProperties mProperties = PropertiesHelper.getTableProperties(m);
                            MethodDispatchingPriority priority = compareMethodProperties(candidateProperties,
                                    mProperties,
                                    notNullPropertyNames);
                            if (priority == MethodDispatchingPriority.HIGHER) {
                                higherCount++;
                            } else if (priority == MethodDispatchingPriority.LOWER) {
                                lowerCount++;
                            }
                        }
                        if (higherCount == mostPriority.size()) {
                            notPriorMethods.addAll(mostPriority);
                            mostPriority.clear();
                            mostPriority.add(candidate);
                        } else if (lowerCount == mostPriority.size()) {
                            notPriorMethods.add(candidate);
                        } else {
                            mostPriority.add(candidate);
                        }
                    }
                }
            }
            notPriorMethods.forEach(selected::remove);
        }
    }

    private int compareMaxMinPriorities(ITableProperties properties1, ITableProperties properties2) {
        for (Comparator<ITableProperties> comparator : prioritySorter.getMaxMinPriorityRules()) {
            int cmp = comparator.compare(properties1, properties2);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private void selectCandidates(Set<IOpenMethod> selected, IRulesRuntimeContext context) {
        List<IOpenMethod> nomatched = new ArrayList<>();

        List<String> notNullPropertyNames = getNotNullPropertyNames(context);

        for (IOpenMethod method : selected) {
            ITableProperties props = PropertiesHelper.getTableProperties(method);

            for (String propName : notNullPropertyNames) {
                MatchingResult res = matcher.match(propName, props, context);

                if (MatchingResult.NO_MATCH.equals(res)) {
                    nomatched.add(method);
                    break;
                }
            }
        }

        nomatched.forEach(selected::remove);
    }

    private String toString(Collection<IOpenMethod> methods) {

        StringBuilder builder = new StringBuilder();
        builder.append("Candidates: {\n");

        boolean g = false;

        for (IOpenMethod method : methods) {
            if (g) {
                builder.append(",\n");
            } else {
                g = true;
            }
            builder.append("{");
            ITableProperties tableProperties = PropertiesHelper.getTableProperties(method);
            boolean f = false;
            for (Entry<String, Object> entry : tableProperties.getAllDimensionalProperties().entrySet()) {
                if (f) {
                    builder.append(", ");
                } else {
                    f = true;
                }
                builder.append(entry.getKey());
                builder.append(": ");
                builder.append(tableProperties.getPropertyValueAsString(entry.getKey()));
            }
            builder.append("}");
        }

        builder.append("\n}\n");

        return builder.toString();
    }

    @Override
    public List<IOpenMethod> getCandidates() {
        if (candidatesSorted == null) {
            candidatesSorted = prioritySorter.sort(super.getCandidates());
        }
        return candidatesSorted;
    }

    // <<< INSERT MatchingProperties >>>
    private List<String> getNotNullPropertyNames(IRulesRuntimeContext context) {
        List<String> propNames = new ArrayList<>();

        if (context.getCurrentDate() != null) {
            propNames.add("effectiveDate");
        }
        if (context.getCurrentDate() != null) {
            propNames.add("expirationDate");
        }
        if (context.getRequestDate() != null) {
            propNames.add("startRequestDate");
        }
        if (context.getRequestDate() != null) {
            propNames.add("endRequestDate");
        }
        if (context.getCaRegion() != null) {
            propNames.add("caRegions");
        }
        if (context.getCaProvince() != null) {
            propNames.add("caProvinces");
        }
        if (context.getCountry() != null) {
            propNames.add("country");
        }
        if (context.getRegion() != null) {
            propNames.add("region");
        }
        if (context.getCurrency() != null) {
            propNames.add("currency");
        }
        if (context.getLang() != null) {
            propNames.add("lang");
        }
        if (context.getLob() != null) {
            propNames.add("lob");
        }
        if (context.getUsRegion() != null) {
            propNames.add("usregion");
        }
        if (context.getUsState() != null) {
            propNames.add("state");
        }
        if (context.getNature() != null) {
            propNames.add("nature");
        }

        return propNames;
    }

    // <<< END INSERT MatchingProperties >>>
}
