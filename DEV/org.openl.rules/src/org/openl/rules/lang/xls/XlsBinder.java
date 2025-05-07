package org.openl.rules.lang.xls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ArrayUtils;

import org.openl.ICompileContext;
import org.openl.IOpenBinder;
import org.openl.OpenL;
import org.openl.binding.IBindingContext;
import org.openl.binding.IBoundCode;
import org.openl.binding.IBoundNode;
import org.openl.binding.ICastFactory;
import org.openl.binding.IMemberBoundNode;
import org.openl.binding.INameSpacedMethodFactory;
import org.openl.binding.INameSpacedTypeFactory;
import org.openl.binding.INameSpacedVarFactory;
import org.openl.binding.MethodUtil;
import org.openl.binding.impl.Binder;
import org.openl.binding.impl.BindingContext;
import org.openl.binding.impl.BindingContextDelegator;
import org.openl.binding.impl.BoundCode;
import org.openl.binding.impl.ErrorBoundNode;
import org.openl.binding.impl.module.ModuleNode;
import org.openl.conf.IOpenLConfiguration;
import org.openl.conf.IUserContext;
import org.openl.conf.LibrariesRegistry;
import org.openl.conf.OpenLConfiguration;
import org.openl.conf.OpenLConfigurationException;
import org.openl.conf.TypeResolver;
import org.openl.dependency.CompiledDependency;
import org.openl.engine.OpenLManager;
import org.openl.engine.OpenLSystemProperties;
import org.openl.exception.OpenlNotCheckedException;
import org.openl.message.OpenLMessage;
import org.openl.rules.binding.RecursiveOpenMethodPreBinder;
import org.openl.rules.binding.RulesModuleBindingContext;
import org.openl.rules.calc.CustomSpreadsheetResultOpenClass;
import org.openl.rules.calc.Spreadsheet;
import org.openl.rules.calc.SpreadsheetNodeBinder;
import org.openl.rules.calc.SpreadsheetResult;
import org.openl.rules.cmatch.ColumnMatchNodeBinder;
import org.openl.rules.constants.ConstantsTableBinder;
import org.openl.rules.data.DataBase;
import org.openl.rules.data.DataNodeBinder;
import org.openl.rules.data.IDataBase;
import org.openl.rules.datatype.binding.DatatypeNodeBinder;
import org.openl.rules.datatype.binding.DatatypeTableBoundNode;
import org.openl.rules.dt.ActionsTableBinder;
import org.openl.rules.dt.ConditionsTableBinder;
import org.openl.rules.dt.DecisionTableNodeBinder;
import org.openl.rules.dt.ReturnsTableBinder;
import org.openl.rules.fuzzy.OpenLFuzzyUtils;
import org.openl.rules.lang.xls.binding.AExecutableNodeBinder;
import org.openl.rules.lang.xls.binding.AXlsTableBinder;
import org.openl.rules.lang.xls.binding.XlsMetaInfo;
import org.openl.rules.lang.xls.binding.XlsModuleOpenClass;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.lang.xls.syntax.XlsModuleSyntaxNode;
import org.openl.rules.method.table.MethodTableNodeBinder;
import org.openl.rules.property.PropertyTableBinder;
import org.openl.rules.table.properties.PropertiesLoader;
import org.openl.rules.tbasic.AlgorithmNodeBinder;
import org.openl.rules.testmethod.TestMethodNodeBinder;
import org.openl.rules.validation.properties.dimentional.DispatcherTablesBuilder;
import org.openl.rules.vm.SimpleRulesVM;
import org.openl.source.IOpenSourceCodeModule;
import org.openl.syntax.ISyntaxNode;
import org.openl.syntax.code.IParsedCode;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.types.IMemberMetaInfo;
import org.openl.types.IMethodSignature;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenField;
import org.openl.types.IOpenMethod;
import org.openl.types.impl.OpenMethodHeader;
import org.openl.types.java.JavaOpenClass;
import org.openl.util.RuntimeExceptionWrapper;
import org.openl.validation.ValidationManager;
import org.openl.vm.IRuntimeEnv;

/**
 * Implements {@link IOpenBinder} abstraction for Excel files.
 *
 * @author snshor
 */
public class XlsBinder implements IOpenBinder {

    private static class BinderFactoryHolder {
        private static final Map<String, AXlsTableBinder> INSTANCE;

        private static final String[][] BINDERS = {
                {XlsNodeTypes.XLS_DATA.toString(), DataNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_DATATYPE.toString(), DatatypeNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_DT.toString(), DecisionTableNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_SPREADSHEET.toString(), SpreadsheetNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_METHOD.toString(), MethodTableNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_TEST_METHOD.toString(), TestMethodNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_RUN_METHOD.toString(), TestMethodNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_TBASIC.toString(), AlgorithmNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_COLUMN_MATCH.toString(), ColumnMatchNodeBinder.class.getName()},
                {XlsNodeTypes.XLS_PROPERTIES.toString(), PropertyTableBinder.class.getName()},
                {XlsNodeTypes.XLS_CONDITIONS.toString(), ConditionsTableBinder.class.getName()},
                {XlsNodeTypes.XLS_ACTIONS.toString(), ActionsTableBinder.class.getName()},
                {XlsNodeTypes.XLS_RETURNS.toString(), ReturnsTableBinder.class.getName()},
                {XlsNodeTypes.XLS_CONSTANTS.toString(), ConstantsTableBinder.class.getName()}};

        static {
            Map<String, AXlsTableBinder> binderFactory = new HashMap<>();
            for (String[] binder : BINDERS) {
                try {
                    binderFactory.put(binder[0], (AXlsTableBinder) Class.forName(binder[1]).getDeclaredConstructor().newInstance());
                } catch (Exception ex) {
                    throw RuntimeExceptionWrapper.wrap(ex);
                }
            }
            INSTANCE = Collections.unmodifiableMap(binderFactory);
        }
    }

    public Map<String, AXlsTableBinder> getBinderFactories() {
        return BinderFactoryHolder.INSTANCE;
    }

    private final IUserContext userContext;
    private final ICompileContext compileContext;

    public XlsBinder(ICompileContext compileContext, IUserContext userContext) {
        this.userContext = userContext;
        this.compileContext = compileContext;
    }

    @Override
    public ICastFactory getCastFactory() {
        return null;
    }

    @Override
    public INameSpacedMethodFactory getMethodFactory() {
        return null;
    }

    @Override
    public INameSpacedTypeFactory getTypeFactory() {
        return null;
    }

    @Override
    public INameSpacedVarFactory getVarFactory() {
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.openl.IOpenBinder#makeBindingContext()
     */
    @Override
    public IBindingContext makeBindingContext() {
        return new BindingContext(null, JavaOpenClass.VOID, null);
    }

    @Override
    public IBoundCode bind(IParsedCode parsedCode) {
        return bind(parsedCode, null);
    }

    @Override
    public IBoundCode bind(IParsedCode parsedCode, IBindingContext bindingContext) {

        XlsModuleSyntaxNode moduleNode = (XlsModuleSyntaxNode) parsedCode.getTopNode();

        String category = "XLSX::" + moduleNode.getModule().getUri();
        var openl = userContext.getOpenL(category);
        List<SyntaxNodeException> exceptions = new ArrayList<>();

        if (openl == null) {
            try {
                openl = makeOpenL(category, moduleNode, exceptions, userContext);
                userContext.registerOpenL(category, openl);
            } catch (OpenLConfigurationException ex) {
                SyntaxNodeException error = SyntaxNodeExceptionUtils.createError("Error Creating OpenL", ex, moduleNode);

                ErrorBoundNode boundNode = new ErrorBoundNode(moduleNode);

                return new BoundCode(parsedCode, boundNode, new SyntaxNodeException[]{error}, null);
            }
        }

        if (bindingContext == null) {
            IOpenBinder openlBinder = openl.getBinder();
            bindingContext = openlBinder.makeBindingContext();
        } else {
            IBindingContext bc1 = bindingContext;
            while (bc1 instanceof BindingContextDelegator) {
                bc1 = ((BindingContextDelegator) bc1).getDelegate();
            }
            if (bc1 instanceof BindingContext) {
                BindingContext bc = (BindingContext) bc1;
                if (bc.getOpenL() == null || bc.getBinder() == null) { // Workaround
                    bc.setOpenl(openl);
                    bc.setBinder(openl.getBinder());
                }
            }
        }
        // add collected exceptions
        exceptions.forEach(bindingContext::addError);

        if (parsedCode.getExternalParams() != null) {
            bindingContext.setExternalParams(parsedCode.getExternalParams());
        }

        Set<CompiledDependency> compiledDependencies = parsedCode.getCompiledDependencies();
        compiledDependencies = compiledDependencies.isEmpty() ? null : compiledDependencies; // !!! empty to null
        XlsModuleOpenClass moduleOpenClass = createModuleOpenClass(moduleNode,
                openl,
                getModuleDatabase(),
                compiledDependencies,
                selectNodes(moduleNode, getSelector(XlsNodeTypes.XLS_DATATYPE)).length > 0,
                bindingContext);
        XlsModuleOpenClass oldXlsModuleOpenClass = XlsModuleOpenClassHolder.getInstance().getXlsModuleOpenClass();
        try {
            XlsModuleOpenClassHolder.getInstance().setXlsModuleOpenClass(moduleOpenClass);
            RulesModuleBindingContext rulesModuleBindingContext = moduleOpenClass.getRulesModuleBindingContext();
            IBoundNode topNode = processBinding(moduleNode, openl, rulesModuleBindingContext, moduleOpenClass);
            ValidationManager.validate(compileContext, topNode.getType(), bindingContext);
            return new BoundCode(parsedCode, topNode, bindingContext.getErrors(), bindingContext.getMessages());
        } finally {
            XlsModuleOpenClassHolder.getInstance().setXlsModuleOpenClass(oldXlsModuleOpenClass);
            if (ValidationManager.isValidationEnabled() && bindingContext.isExecutionMode()) {
                moduleOpenClass.clearForExecutionMode();
            }
        }
    }

    protected IDataBase getModuleDatabase() {
        return new DataBase();
    }

    private IBoundNode processBinding(XlsModuleSyntaxNode moduleNode,
                                      OpenL openl,
                                      RulesModuleBindingContext rulesModuleBindingContext,
                                      XlsModuleOpenClass moduleOpenClass) {
        try {
            //
            // Selectors
            //
            Predicate<ISyntaxNode> propertiesSelector = getSelector(XlsNodeTypes.XLS_PROPERTIES);
            Predicate<ISyntaxNode> constantsSelector = getSelector(XlsNodeTypes.XLS_CONSTANTS);
            Predicate<ISyntaxNode> dataTypeSelector = getSelector(XlsNodeTypes.XLS_DATATYPE);
            Predicate<ISyntaxNode> conditionsSelector = getSelector(XlsNodeTypes.XLS_CONDITIONS);
            Predicate<ISyntaxNode> actionsSelector = getSelector(XlsNodeTypes.XLS_ACTIONS);
            Predicate<ISyntaxNode> returnsSelector = getSelector(XlsNodeTypes.XLS_RETURNS);

            Predicate<ISyntaxNode> dtDefinitionSelector = conditionsSelector.or(actionsSelector).or(returnsSelector);

            Predicate<ISyntaxNode> notPropertiesAndNotDatatypeAndNotConstantsSelector = propertiesSelector.negate()
                    .and(dataTypeSelector.negate())
                    .and(constantsSelector.negate());

            Predicate<ISyntaxNode> spreadsheetSelector = getSelector(XlsNodeTypes.XLS_SPREADSHEET);
            Predicate<ISyntaxNode> dtSelector = getSelector(XlsNodeTypes.XLS_DT);
            Predicate<ISyntaxNode> testMethodSelector = getSelector(XlsNodeTypes.XLS_TEST_METHOD);
            Predicate<ISyntaxNode> runMethodSelector = getSelector(XlsNodeTypes.XLS_RUN_METHOD);

            Predicate<ISyntaxNode> commonTablesSelector = notPropertiesAndNotDatatypeAndNotConstantsSelector
                    .and(spreadsheetSelector.negate()
                            .and(testMethodSelector.negate()
                                    .and(runMethodSelector.negate().and(dtSelector.negate().and(dtDefinitionSelector.negate())))));

            // Bind property node at first.
            //
            TableSyntaxNode[] propertiesNodes = selectNodes(moduleNode, propertiesSelector);
            bindInternal(moduleNode,
                    moduleOpenClass,
                    propertiesNodes,
                    Collections.emptyMap(),
                    openl,
                    rulesModuleBindingContext);

            bindPropertiesForAllTables(moduleNode, moduleOpenClass, openl, rulesModuleBindingContext);

            IBoundNode topNode;

            // Constants
            TableSyntaxNode[] constantNodes = selectNodes(moduleNode, constantsSelector);

            // Datatypes
            TableSyntaxNode[] datatypeNodes = selectNodes(moduleNode, dataTypeSelector);

            // Conditions && Returns && Actions
            TableSyntaxNode[] dtHeaderDefinitionsNodes = selectNodes(moduleNode, dtDefinitionSelector);

            // Select nodes excluding Properties, Datatype, Spreadsheet, Test,
            // RunMethod tables
            TableSyntaxNode[] commonTables = selectNodes(moduleNode, commonTablesSelector);

            // Select and sort Spreadsheet tables
            TableSyntaxNode[] spreadsheets = selectNodes(moduleNode, spreadsheetSelector);

            TableSyntaxNode[] dts = selectNodes(moduleNode, dtSelector);

            TableSyntaxNode[] commonAndSpreadsheetTables = ArrayUtils.addAll(
                    ArrayUtils.addAll(ArrayUtils.addAll(dtHeaderDefinitionsNodes, dts), spreadsheets),
                    commonTables);

            Map<TableSyntaxNode, CustomSpreadsheetResultOpenClass> customSpreadsheetResultOpenClassMap = registerNewCustomSpreadsheetResultTypes(
                    commonAndSpreadsheetTables,
                    rulesModuleBindingContext);

            // Bind constants
            bindInternal(moduleNode,
                    moduleOpenClass,
                    constantNodes,
                    customSpreadsheetResultOpenClassMap,
                    openl,
                    rulesModuleBindingContext);

            // Bind datatype nodes.
            bindInternal(moduleNode,
                    moduleOpenClass,
                    datatypeNodes,
                    customSpreadsheetResultOpenClassMap,
                    openl,
                    rulesModuleBindingContext);

            bindInternal(moduleNode,
                    moduleOpenClass,
                    commonAndSpreadsheetTables,
                    customSpreadsheetResultOpenClassMap,
                    openl,
                    rulesModuleBindingContext);

            // Select Test and RunMethod tables
            TableSyntaxNode[] runTables = selectNodes(moduleNode, runMethodSelector);
            bindInternal(moduleNode,
                    moduleOpenClass,
                    runTables,
                    customSpreadsheetResultOpenClassMap,
                    openl,
                    rulesModuleBindingContext);

            TableSyntaxNode[] testTables = selectNodes(moduleNode, testMethodSelector);
            topNode = bindInternal(moduleNode,
                    moduleOpenClass,
                    testTables,
                    customSpreadsheetResultOpenClassMap,
                    openl,
                    rulesModuleBindingContext);

            // After recursive compilation non initialized fields need to be initialized for CSR type in compile time
            // and meta info initialized.
            if (OpenLSystemProperties
                    .isCustomSpreadsheetTypesSupported(rulesModuleBindingContext.getExternalParams())) {
                for (IOpenClass type : moduleOpenClass.getTypes()) {
                    if (type instanceof CustomSpreadsheetResultOpenClass) {
                        type.getFields().forEach(IOpenField::getType);
                    }
                }
                moduleOpenClass.getSpreadsheetResultOpenClassWithResolvedFieldTypes()
                        .toCustomSpreadsheetResultOpenClass()
                        .getFields()
                        .forEach(IOpenField::getType);
                int combinedSpreadsheetResultOpenClassesSize = 0;
                while (combinedSpreadsheetResultOpenClassesSize != moduleOpenClass
                        .getCombinedSpreadsheetResultOpenClasses()
                        .size()) {
                    combinedSpreadsheetResultOpenClassesSize = moduleOpenClass.getCombinedSpreadsheetResultOpenClasses()
                            .size();
                    moduleOpenClass.getCombinedSpreadsheetResultOpenClasses()
                            .forEach(e -> e.getFields().forEach(IOpenField::getType));
                }
            }

            if (moduleOpenClass.isUseDecisionTableDispatcher()) {
                DispatcherTablesBuilder dispatcherTablesBuilder = new DispatcherTablesBuilder(
                        (XlsModuleOpenClass) topNode.getType());
                dispatcherTablesBuilder.build();
            }

            ((XlsModuleOpenClass) topNode.getType()).completeOpenClassBuilding();

            return topNode;
        } finally {
            OpenLFuzzyUtils.clearCaches();
        }
    }

    private Predicate<ISyntaxNode> getSelector(XlsNodeTypes selectorValue) {
        return syntaxNode -> selectorValue.toString().equals(syntaxNode.getType());
    }

    /**
     * Creates {@link XlsModuleOpenClass}
     *
     * @param moduleDependencies set of dependent modules for creating module.
     */
    protected XlsModuleOpenClass createModuleOpenClass(XlsModuleSyntaxNode moduleNode,
                                                       OpenL openl,
                                                       IDataBase dbase,
                                                       Set<CompiledDependency> moduleDependencies,
                                                       boolean appliedChangesToClasspath,
                                                       IBindingContext bindingContext) {

        return new XlsModuleOpenClass(XlsHelper.getModuleName(moduleNode),
                new XlsMetaInfo(moduleNode),
                openl,
                dbase,
                moduleDependencies,
                Thread.currentThread().getContextClassLoader(),
                appliedChangesToClasspath,
                bindingContext);
    }

    private void bindPropertiesForAllTables(XlsModuleSyntaxNode moduleNode,
                                            XlsModuleOpenClass module,
                                            OpenL openl,
                                            RulesModuleBindingContext bindingContext) {
        Predicate<ISyntaxNode> propertiesSelector = getSelector(XlsNodeTypes.XLS_PROPERTIES);
        Predicate<ISyntaxNode> otherNodesSelector = getSelector(XlsNodeTypes.XLS_OTHER);
        Predicate<ISyntaxNode> notPropertiesAndNotOtherSelector = propertiesSelector.negate()
                .and(otherNodesSelector.negate());

        TableSyntaxNode[] tableSyntaxNodes = selectNodes(moduleNode, notPropertiesAndNotOtherSelector);

        PropertiesLoader propLoader = new PropertiesLoader(openl, bindingContext, module);
        for (TableSyntaxNode tsn : tableSyntaxNodes) {
            try {
                propLoader.loadProperties(tsn);
            } catch (SyntaxNodeException error) {
                processError(error, bindingContext);
            } catch (Exception | LinkageError t) {
                SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(t, tsn);
                processError(error, bindingContext);
            }
        }
    }

    private static OpenL makeOpenL(String category, XlsModuleSyntaxNode moduleNode, List<SyntaxNodeException> exceptions, IUserContext userContext) {
        ClassLoader userClassLoader = userContext.getUserClassLoader();

        OpenLConfiguration conf = new OpenLConfiguration();

        Collection<String> packageNames = new LinkedHashSet<>();
        Collection<Class<?>> classNames = new LinkedHashSet<>();

        LibrariesRegistry thisNamespaceLibrary = new LibrariesRegistry();

        for (var anImport : moduleNode.getImports()) {
            boolean isPattern = anImport.endsWith(".*");
            if (isPattern) {
                anImport = anImport.substring(0, anImport.length() - 2);
            }
            try {
                var cls = userClassLoader.loadClass(anImport);
                if (isPattern) {
                    thisNamespaceLibrary.addJavalib(cls);
                } else {
                    classNames.add(cls);
                }
            } catch (Exception e) {
                packageNames.add(anImport);
            } catch (LinkageError e) {
                exceptions.add(SyntaxNodeExceptionUtils.createError(e, moduleNode));
            }
        }

        conf.setMethodFactory(thisNamespaceLibrary);
        var typeResolver = new TypeResolver(userClassLoader, classNames, packageNames);

        userContext.registerOpenLConfiguration(category, conf);

        OpenL op = new OpenL();
        op.setParser(new Parser());
        op.setBinder(new Binder(conf, conf, conf, typeResolver, op));
        op.setVm(new SimpleRulesVM());
        return op;
    }

    private IMemberBoundNode preBindXlsNode(ISyntaxNode syntaxNode,
                                            OpenL openl,
                                            RulesModuleBindingContext bindingContext,
                                            XlsModuleOpenClass module) throws Exception {

        String tableSyntaxNodeType = syntaxNode.getType();
        AXlsTableBinder binder = findBinder(tableSyntaxNodeType);

        if (binder == null) {
            return null;
        }

        TableSyntaxNode tableSyntaxNode = (TableSyntaxNode) syntaxNode;
        return binder.preBind(tableSyntaxNode, openl, bindingContext, module);
    }

    protected AXlsTableBinder findBinder(String tableSyntaxNodeType) {
        return getBinderFactories().get(tableSyntaxNodeType);
    }

    private TableSyntaxNode[] selectNodes(XlsModuleSyntaxNode moduleSyntaxNode, Predicate<ISyntaxNode> childSelector) {

        TableSyntaxNode[] xlsTableSyntaxNodes = moduleSyntaxNode.getXlsTableSyntaxNodes();
        return Arrays.stream(xlsTableSyntaxNodes)
                .filter(childSelector)
                .collect(Collectors.toList())
                .toArray(TableSyntaxNode.EMPTY_ARRAY);
    }

    private boolean isExecutableTableSyntaxNode(TableSyntaxNode tableSyntaxNode) {
        return XlsNodeTypes.XLS_DT.equals(tableSyntaxNode.getNodeType()) || XlsNodeTypes.XLS_TBASIC
                .equals(tableSyntaxNode.getNodeType()) || XlsNodeTypes.XLS_METHOD
                .equals(tableSyntaxNode.getNodeType()) || XlsNodeTypes.XLS_COLUMN_MATCH.equals(tableSyntaxNode
                .getNodeType()) || XlsNodeTypes.XLS_SPREADSHEET.equals(tableSyntaxNode.getNodeType());
    }

    private static String getSprResTypeNameIfCustomSpreadsheetResultTableSyntaxNode(TableSyntaxNode tableSyntaxNode) {
        if (XlsNodeTypes.XLS_SPREADSHEET.equals(tableSyntaxNode.getNodeType()) || XlsNodeTypes.XLS_DT
                .equals(tableSyntaxNode.getNodeType())) {
            String code = tableSyntaxNode.getHeader().getHeaderToken().getModule().getCode();
            code = code.replaceAll("\\s+", " ");
            int x = code.indexOf("(");
            if (x < 1) {
                return null;
            }
            String x1 = code.substring(0, x).trim();
            int y = x1.lastIndexOf(" ");
            if (y < 0 || y == x1.length() - 1) {
                return null;
            }
            String tableName = x1.substring(y + 1).trim();
            if (x1.contains("`")) {
                return null;
            }
            x1 = x1.substring(0, y).trim();
            if (XlsNodeTypes.XLS_SPREADSHEET
                    .equals(tableSyntaxNode.getNodeType()) && (x1.endsWith("[") || x1.endsWith("]"))) {
                return null;
            }
            while (x1.length() > 0 && x1.charAt(x1.length() - 1) == ' ' || x1.charAt(x1.length() - 1) == '[' || x1
                    .charAt(x1.length() - 1) == ']') {
                x1 = x1.substring(0, x1.length() - 1);
            }
            int z = x1.lastIndexOf(" ");
            if (z < 0 || z == x1.length() - 1) {
                return null;
            }
            String tableType = x1.substring(z + 1);
            if (SpreadsheetResult.class.getSimpleName().equals(tableType) || SpreadsheetResult.class.getName()
                    .equals(tableType)) {
                return Spreadsheet.SPREADSHEETRESULT_TYPE_PREFIX + tableName;
            }
        }
        return null;
    }

    private Map<TableSyntaxNode, CustomSpreadsheetResultOpenClass> registerNewCustomSpreadsheetResultTypes(
            TableSyntaxNode[] tableSyntaxNodes,
            RulesModuleBindingContext rulesModuleBindingContext) {
        if (OpenLSystemProperties.isCustomSpreadsheetTypesSupported(rulesModuleBindingContext.getExternalParams())) {
            Map<TableSyntaxNode, CustomSpreadsheetResultOpenClass> customSpreadsheetResultOpenClassTableSyntaxNodes = new HashMap<>();
            for (TableSyntaxNode tableSyntaxNode : tableSyntaxNodes) {
                String sprResTypeName = getSprResTypeNameIfCustomSpreadsheetResultTableSyntaxNode(tableSyntaxNode);
                if (sprResTypeName != null) {
                    CustomSpreadsheetResultOpenClass t = (CustomSpreadsheetResultOpenClass) rulesModuleBindingContext
                            .getModule()
                            .findType(sprResTypeName);
                    if (t == null) {
                        CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass = new CustomSpreadsheetResultOpenClass(
                                sprResTypeName,
                                rulesModuleBindingContext.getModule(),
                                rulesModuleBindingContext.isExecutionMode() ? null : tableSyntaxNode.getTableBody(),
                                XlsNodeTypes.XLS_SPREADSHEET.equals(tableSyntaxNode.getNodeType()));
                        rulesModuleBindingContext.getModule().addType(customSpreadsheetResultOpenClass);
                        t = customSpreadsheetResultOpenClass;
                    }
                    customSpreadsheetResultOpenClassTableSyntaxNodes.put(tableSyntaxNode, t);
                }
            }
            return Collections.unmodifiableMap(customSpreadsheetResultOpenClassTableSyntaxNodes);
        }
        return Collections.emptyMap();
    }

    protected IBoundNode bindInternal(XlsModuleSyntaxNode moduleSyntaxNode,
                                      XlsModuleOpenClass module,
                                      TableSyntaxNode[] tableSyntaxNodes,
                                      Map<TableSyntaxNode, CustomSpreadsheetResultOpenClass> customSpreadsheetResultOpenClassMap,
                                      OpenL openl,
                                      RulesModuleBindingContext rulesModuleBindingContext) {

        IMemberBoundNode[] childrens = new IMemberBoundNode[tableSyntaxNodes.length];
        OpenMethodHeader[] openMethodHeaders = new OpenMethodHeader[tableSyntaxNodes.length];

        SyntaxNodeExceptionHolder syntaxNodeExceptionHolder = new SyntaxNodeExceptionHolder();
        try {
            rulesModuleBindingContext.setIgnoreCustomSpreadsheetResultCompilation(true);
            for (int i = 0; i < tableSyntaxNodes.length; i++) { // Add methods that should be compiled recursively
                if (isExecutableTableSyntaxNode(tableSyntaxNodes[i])) {
                    openMethodHeaders[i] = addMethodHeaderToContext(module,
                            tableSyntaxNodes[i],
                            customSpreadsheetResultOpenClassMap.get(tableSyntaxNodes[i]),
                            openl,
                            rulesModuleBindingContext,
                            syntaxNodeExceptionHolder,
                            childrens,
                            i);
                }
            }
        } finally {
            rulesModuleBindingContext.setIgnoreCustomSpreadsheetResultCompilation(false);
        }

        for (int i = 0; i < tableSyntaxNodes.length; i++) {
            if (!isExecutableTableSyntaxNode(tableSyntaxNodes[i])) {
                IMemberBoundNode child = beginBind(tableSyntaxNodes[i], module, openl, rulesModuleBindingContext);
                childrens[i] = child;
            }
        }

        for (int i = 0; i < tableSyntaxNodes.length; i++) {
            if (!isExecutableTableSyntaxNode(tableSyntaxNodes[i])) {
                IMemberBoundNode child = childrens[i];
                if (child != null) {
                    try {
                        child.addTo(module);
                    } catch (OpenlNotCheckedException e) {
                        SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(e, tableSyntaxNodes[i]);
                        processError(error, rulesModuleBindingContext);
                    }
                }
            }
        }

        generateByteCode(childrens, tableSyntaxNodes, rulesModuleBindingContext);

        for (int i = 0; i < childrens.length; i++) {
            if (isExecutableTableSyntaxNode(tableSyntaxNodes[i])) {
                rulesModuleBindingContext.preBindMethod(openMethodHeaders[i]);
            }
        }

        for (int i = 0; i < childrens.length; i++) {
            if (childrens[i] != null) {
                finalizeBind(childrens[i], tableSyntaxNodes[i], rulesModuleBindingContext);
            }
        }

        syntaxNodeExceptionHolder.processBindingContextErrors(rulesModuleBindingContext);

        if (rulesModuleBindingContext.isExecutionMode()) {
            removeDebugInformation(childrens, tableSyntaxNodes, rulesModuleBindingContext);
        }

        return new ModuleNode(moduleSyntaxNode, rulesModuleBindingContext.getModule());
    }

    private String getParentClassName(DatatypeTableBoundNode datatypeTableBoundNode,
                                      RulesModuleBindingContext rulesModuleBindingContext) {
        if (datatypeTableBoundNode.getParentClassName() != null) {
            IOpenClass parentClass = rulesModuleBindingContext.findType(
                    datatypeTableBoundNode.getParentClassName());
            if (parentClass != null) {
                return parentClass.getJavaName();
            }
        }
        return null;
    }

    private void generateByteCode(IMemberBoundNode[] childrens,
                                  TableSyntaxNode[] tableSyntaxNodes,
                                  RulesModuleBindingContext rulesModuleBindingContext) {
        Collection<DatatypeTableBoundNode> datatypeTableBoundNodes = null;
        for (IMemberBoundNode children : childrens) {
            if (children instanceof DatatypeTableBoundNode) {
                DatatypeTableBoundNode datatypeTableBoundNode = (DatatypeTableBoundNode) children;
                if (datatypeTableBoundNodes == null) {
                    datatypeTableBoundNodes = Arrays.stream(childrens)
                            .filter(e -> e instanceof DatatypeTableBoundNode)
                            .map(DatatypeTableBoundNode.class::cast)
                            .collect(Collectors.toList());
                }
                DatatypeTableBoundNode parentDatatypeTableBoundNode = datatypeTableBoundNodes.stream()
                        .filter(d -> Objects.equals(d.getDataType().getJavaName(),
                                getParentClassName(datatypeTableBoundNode, rulesModuleBindingContext)))
                        .findFirst()
                        .orElse(null);
                datatypeTableBoundNode.setParentDatatypeTableBoundNode(parentDatatypeTableBoundNode);
            }
        }
        for (int i = 0; i < childrens.length; i++) {
            if (childrens[i] instanceof DatatypeTableBoundNode) {
                DatatypeTableBoundNode datatypeTableBoundNode = (DatatypeTableBoundNode) childrens[i];
                try {
                    datatypeTableBoundNode.generateByteCode(rulesModuleBindingContext);
                } catch (SyntaxNodeException error) {
                    processError(error, rulesModuleBindingContext);
                } catch (Exception | LinkageError t) {
                    SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(t, tableSyntaxNodes[i]);
                    processError(error, rulesModuleBindingContext);
                }
            }
        }
    }

    private OpenMethodHeader addMethodHeaderToContext(XlsModuleOpenClass module,
                                                      TableSyntaxNode tableSyntaxNode,
                                                      CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass,
                                                      OpenL openl,
                                                      RulesModuleBindingContext rulesModuleBindingContext,
                                                      SyntaxNodeExceptionHolder syntaxNodeExceptionHolder,
                                                      IMemberBoundNode[] children,
                                                      int index) {
        OpenMethodHeader openMethodHeader = null;
        SyntaxNodeException[] errors = SyntaxNodeException.EMPTY_ARRAY;
        Collection<OpenLMessage> messages = Collections.emptyList();
        try {
            AExecutableNodeBinder aExecutableNodeBinder = (AExecutableNodeBinder) getBinderFactories()
                    .get(tableSyntaxNode.getType());
            IOpenSourceCodeModule source = aExecutableNodeBinder.createHeaderSource(tableSyntaxNode,
                    rulesModuleBindingContext);
            try {
                rulesModuleBindingContext.pushErrors();
                rulesModuleBindingContext.pushMessages();
                openMethodHeader = (OpenMethodHeader) OpenLManager
                        .makeMethodHeader(openl, source, rulesModuleBindingContext);
                if (openMethodHeader != null) {
                    XlsBinderExecutableMethodBind xlsBinderExecutableMethodBind = new XlsBinderExecutableMethodBind(
                            module,
                            openl,
                            tableSyntaxNode,
                            children,
                            index,
                            openMethodHeader,
                            customSpreadsheetResultOpenClass,
                            rulesModuleBindingContext,
                            syntaxNodeExceptionHolder);
                    rulesModuleBindingContext.addBinderMethod(openMethodHeader, xlsBinderExecutableMethodBind);
                    return openMethodHeader;
                }
            } finally {
                errors = rulesModuleBindingContext.getErrors();
                messages = rulesModuleBindingContext.getMessages();
                rulesModuleBindingContext.popErrors();
                rulesModuleBindingContext.popMessages();
                if (openMethodHeader == null) {
                    rulesModuleBindingContext.addMessages(messages);
                    Arrays.stream(errors).forEach(rulesModuleBindingContext::addError);
                }
            }
        } catch (Exception | LinkageError e) {
            SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(e, tableSyntaxNode);
            processError(error, rulesModuleBindingContext);
            rulesModuleBindingContext.addMessages(messages);
            Arrays.stream(errors).forEach(rulesModuleBindingContext::addError);
        }
        return null;
    }

    protected void finalizeBind(IMemberBoundNode memberBoundNode,
                                TableSyntaxNode tableSyntaxNode,
                                RulesModuleBindingContext rulesModuleBindingContext) {
        try {
            memberBoundNode.finalizeBind(rulesModuleBindingContext);
        } catch (SyntaxNodeException error) {
            processError(error, rulesModuleBindingContext);
        } catch (Exception | LinkageError t) {
            SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(t, tableSyntaxNode);
            processError(error, rulesModuleBindingContext);
        }
    }

    protected void removeDebugInformation(IMemberBoundNode[] boundNodes,
                                          TableSyntaxNode[] tableSyntaxNodes,
                                          RulesModuleBindingContext ruleModuleBindingContext) {
        for (int i = 0; i < boundNodes.length; i++) {
            if (boundNodes[i] != null) {
                try {
                    boundNodes[i].removeDebugInformation(ruleModuleBindingContext);

                } catch (SyntaxNodeException error) {
                    processError(error, ruleModuleBindingContext);
                } catch (Exception | LinkageError t) {
                    SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(t, tableSyntaxNodes[i]);
                    processError(error, ruleModuleBindingContext);
                }
            }
        }
    }

    private IMemberBoundNode beginBind(TableSyntaxNode tableSyntaxNode,
                                       XlsModuleOpenClass module,
                                       OpenL openl,
                                       RulesModuleBindingContext rulesModuleBindingContext) {
        try {
            return preBindXlsNode(tableSyntaxNode, openl, rulesModuleBindingContext, module);
        } catch (SyntaxNodeException error) {
            processError(error, rulesModuleBindingContext);
        } catch (Exception | LinkageError t) {
            SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(t, tableSyntaxNode);
            processError(error, rulesModuleBindingContext);
        }
        return null;
    }

    protected void processError(SyntaxNodeException error, RulesModuleBindingContext rulesModuleBindingContext) {
        rulesModuleBindingContext.addError(error);
    }

    class XlsBinderExecutableMethodBind implements RecursiveOpenMethodPreBinder {
        final TableSyntaxNode tableSyntaxNode;
        final RulesModuleBindingContext rulesModuleBindingContext;
        final OpenL openl;
        final XlsModuleOpenClass module;
        final IMemberBoundNode[] childrens;
        final int index;
        final OpenMethodHeader openMethodHeader;
        boolean preBinding = false;
        final SyntaxNodeExceptionHolder syntaxNodeExceptionHolder;
        boolean completed = false;
        final CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass;

        XlsBinderExecutableMethodBind(XlsModuleOpenClass module,
                                      OpenL openl,
                                      TableSyntaxNode tableSyntaxNode,
                                      IMemberBoundNode[] childrens,
                                      int index,
                                      OpenMethodHeader openMethodHeader,
                                      CustomSpreadsheetResultOpenClass customSpreadsheetResultOpenClass,
                                      RulesModuleBindingContext rulesModuleBindingContext,
                                      SyntaxNodeExceptionHolder syntaxNodeExceptionHolder) {
            this.tableSyntaxNode = tableSyntaxNode;
            this.rulesModuleBindingContext = rulesModuleBindingContext;
            this.module = module;
            this.openl = openl;
            this.childrens = childrens;
            this.index = index;
            this.openMethodHeader = openMethodHeader;
            this.syntaxNodeExceptionHolder = syntaxNodeExceptionHolder;
            this.customSpreadsheetResultOpenClass = customSpreadsheetResultOpenClass;
        }

        @Override
        public TableSyntaxNode getTableSyntaxNode() {
            return tableSyntaxNode;
        }

        @Override
        public boolean isSpreadsheetWithCustomSpreadsheetResult() {
            return customSpreadsheetResultOpenClass != null;
        }

        public CustomSpreadsheetResultOpenClass getCustomSpreadsheetResultOpenClass() {
            return customSpreadsheetResultOpenClass;
        }

        @Override
        public OpenMethodHeader getHeader() {
            return openMethodHeader;
        }

        @Override
        public String getDisplayName(int mode) {
            return openMethodHeader.getDisplayName(mode);
        }

        @Override
        public IOpenClass getType() {
            return openMethodHeader.getType();
        }

        @Override
        public IOpenMethod getMethod() {
            return this;
        }

        @Override
        public IMethodSignature getSignature() {
            return openMethodHeader.getSignature();
        }

        @Override
        public String getName() {
            return openMethodHeader.getName();
        }

        @Override
        public Object invoke(Object target, Object[] params, IRuntimeEnv env) {
            throw new UnsupportedOperationException();
        }

        @Override
        public IMemberMetaInfo getInfo() {
            return openMethodHeader.getInfo();
        }

        @Override
        public boolean isStatic() {
            return openMethodHeader.isStatic();
        }

        @Override
        public boolean isConstructor() {
            return false;
        }

        @Override
        public IOpenClass getDeclaringClass() {
            return module;
        }

        @Override
        public void startPreBind() {
            if (completed) {
                throw new IllegalStateException(String.format("Method '%s' is already pre-compiled.",
                        MethodUtil.printMethod(getHeader().getName(), getHeader().getSignature().getParameterTypes())));
            }
            preBinding = true;
        }

        @Override
        public void finishPreBind() {
            if (!completed && preBinding) {
                throw new IllegalStateException(String.format("Method '%s' is not pre-compiled.",
                        MethodUtil.printMethod(getHeader().getName(), getHeader().getSignature().getParameterTypes())));
            }
            if (!preBinding) {
                throw new IllegalStateException(String.format("Pre-compilation is not started for method '%s'.",
                        MethodUtil.printMethod(getHeader().getName(), getHeader().getSignature().getParameterTypes())));
            }
            preBinding = false;
        }

        @Override
        public void preBind() {
            try {
                if (!completed) {
                    if (!preBinding) {
                        throw new IllegalStateException(String.format("Pre-compilation is not started for method '%s'.",
                                MethodUtil.printMethod(getHeader().getName(),
                                        getHeader().getSignature().getParameterTypes())));
                    }
                    try {
                        rulesModuleBindingContext.pushErrors();
                        IMemberBoundNode memberBoundNode = XlsBinder.this
                                .beginBind(tableSyntaxNode, module, openl, rulesModuleBindingContext);
                        childrens[index] = memberBoundNode;
                        if (memberBoundNode != null) {
                            try {
                                memberBoundNode.addTo(module);
                            } catch (Exception | LinkageError e) {
                                SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(e, tableSyntaxNode);
                                processError(error, rulesModuleBindingContext);
                            }
                        }
                    } finally {
                        rulesModuleBindingContext.popErrors()
                                .forEach(syntaxNodeExceptionHolder::addBindingContextError);
                    }
                }
            } finally {
                completed = true;
            }
        }

        @Override
        public boolean isPreBindStarted() {
            return preBinding;
        }

        @Override
        public boolean isCompleted() {
            return completed;
        }
    }

    private static class SyntaxNodeExceptionHolder {

        private final List<SyntaxNodeException> syntaxNodeExceptions = new ArrayList<>();

        private void addBindingContextError(SyntaxNodeException e) {
            syntaxNodeExceptions.add(e);
        }

        private void processBindingContextErrors(IBindingContext bindingContext) {
            for (SyntaxNodeException e : syntaxNodeExceptions) {
                bindingContext.addError(e);
            }
            syntaxNodeExceptions.clear();
        }
    }
}
