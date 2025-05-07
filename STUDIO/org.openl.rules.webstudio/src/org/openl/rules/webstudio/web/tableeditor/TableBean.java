package org.openl.rules.webstudio.web.tableeditor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertyResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import org.openl.rules.lang.xls.IXlsTableNames;
import org.openl.rules.lang.xls.TableSyntaxNodeUtils;
import org.openl.rules.lang.xls.XlsNodeTypes;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.project.abstraction.RulesProject;
import org.openl.rules.service.TableServiceImpl;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.IOpenLTable;
import org.openl.rules.table.xls.XlsSheetGridModel;
import org.openl.rules.tableeditor.model.TableEditorModel;
import org.openl.rules.testmethod.ParameterWithValueDeclaration;
import org.openl.rules.testmethod.ProjectHelper;
import org.openl.rules.testmethod.TestDescription;
import org.openl.rules.testmethod.TestMethodBoundNode;
import org.openl.rules.testmethod.TestSuite;
import org.openl.rules.testmethod.TestSuiteMethod;
import org.openl.rules.testmethod.TestUtils;
import org.openl.rules.ui.ProjectModel;
import org.openl.rules.ui.RecentlyVisitedTables;
import org.openl.rules.ui.WebStudio;
import org.openl.rules.validation.properties.dimentional.DispatcherTablesBuilder;
import org.openl.rules.webstudio.util.XSSFOptimizer;
import org.openl.rules.webstudio.web.test.Utils;
import org.openl.rules.webstudio.web.util.Constants;
import org.openl.rules.webstudio.web.util.WebStudioUtils;
import org.openl.types.IOpenMethod;
import org.openl.util.CollectionUtils;
import org.openl.util.StringUtils;

/**
 * Request scope managed bean for Table page.
 */
@Service
@RequestScope
public class TableBean {
    private static final String REQUEST_ID_FORMAT = "request-id:%s;project-name:%s";
    private static final Pattern REQUEST_ID_PATTERN = Pattern.compile("request-id:(.+);project-name:(.+)");
    private static final int MAX_PROBLEMS = 100;
    private final Logger log = LoggerFactory.getLogger(TableBean.class);

    private IOpenMethod method;

    // Test in current table (only for test tables)
    private TestDescription[] runnableTestMethods = {}; // test units
    // All checks and tests for current table (including tests with no cases, run methods).
    private IOpenMethod[] allTests = {};
    private IOpenMethod[] tests = {};

    private String uri;
    private String id;
    private IOpenLTable table;
    private boolean editable;
    private boolean canBeOpenInExcel;
    private boolean copyable;

    private final PropertyResolver propertyResolver;

    public TableBean(PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        id = WebStudioUtils.getRequestParameter(Constants.REQUEST_PARAM_ID);

        WebStudio studio = WebStudioUtils.getWebStudio();
        final ProjectModel model = studio.getModel();

        table = model.getTableById(id);

        // TODO: There is should be a method to get the table by the ID without using URI which is used to generate the
        // ID.
        if (table == null) {
            table = model.getTable(studio.getTableUri());
        }

        if (table != null) {
            id = table.getId();
            uri = table.getUri();
            // Save URI because some actions don't provide table ID
            studio.setTableUri(uri);
            boolean currentOpenedModule = !model.isProjectCompilationCompleted();
            method = currentOpenedModule ? model.getOpenedModuleMethod(uri) : model.getMethod(uri);
            editable = model.isEditableTable(uri) && !isDispatcherValidationNode();
            canBeOpenInExcel = model.isEditable() && !isDispatcherValidationNode();
            copyable = editable && table
                    .isCanContainProperties() && !XlsNodeTypes.XLS_DATATYPE.toString().equals(table.getType());

            initTests(model, currentOpenedModule);

            // Save last visited table
            model.getRecentlyVisitedTables().setLastVisitedTable(table);
            // Check the save table parameter
            String saveTable1 = WebStudioUtils.getRequestParameter("saveTable");
            boolean saveTable = saveTable1 == null || Boolean.parseBoolean(saveTable1);
            if (saveTable) {
                storeTable();
            }
        }
    }

    private void storeTable() {
        ProjectModel model = WebStudioUtils.getProjectModel();
        RecentlyVisitedTables recentlyVisitedTables = model.getRecentlyVisitedTables();
        recentlyVisitedTables.add(table);
    }

    private void initTests(final ProjectModel model, boolean currentOpenedModule) {
        initRunnableTestMethods();
        allTests = model.getTestAndRunMethods(uri, currentOpenedModule);
        tests = model.getTestMethods(uri, currentOpenedModule);
    }

    private void initRunnableTestMethods() {
        if (method instanceof TestSuiteMethod) {
            try {
                runnableTestMethods = ((TestSuiteMethod) method).getTests();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                runnableTestMethods = new TestDescription[0];
            }
        }
    }

    public boolean isDispatcherValidationNode() {
        return table != null && table.getName().startsWith(DispatcherTablesBuilder.DEFAULT_DISPATCHER_TABLE_NAME);
    }

    public String getMode() {
        return isEditable() ? WebStudioUtils.getRequestParameter("mode") : null;
    }

    public IOpenLTable getTable() {
        return table;
    }

    /**
     * Return test cases for current table.
     *
     * @return array of tests for current table.
     */
    public TestDescription[] getTests() {
        return runnableTestMethods;
    }

    public ParameterWithValueDeclaration[] getTestCaseParams(TestDescription testCase) {
        ParameterWithValueDeclaration[] params;
        if (testCase != null) {
            ParameterWithValueDeclaration[] contextParams = TestUtils
                    .getContextParams(new TestSuite((TestSuiteMethod) method), testCase);
            Utils.getDb(WebStudioUtils.getProjectModel(), false);
            ParameterWithValueDeclaration[] inputParams = testCase.getExecutionParams();

            params = new ParameterWithValueDeclaration[contextParams.length + inputParams.length];
            int n = 0;
            for (ParameterWithValueDeclaration contextParam : contextParams) {
                params[n++] = contextParam;
            }
            for (ParameterWithValueDeclaration inputParam : inputParams) {
                params[n++] = inputParam;
            }
        } else {
            params = ParameterWithValueDeclaration.EMPTY_ARRAY;
        }
        return params;
    }

    public String getUri() {
        return uri;
    }

    public String getId() {
        return id;
    }

    /**
     * @return true if it is possible to create tests for current table.
     */
    public boolean isCanCreateTest() {
        return table != null && table.isExecutable() && isEditable();
    }

    public boolean isEditable() {
        return editable;
    }

    public boolean isCopyable() {
        return copyable;
    }

    public boolean isTablePart() {
        return WebStudioUtils.getProjectModel().isTablePart(uri);
    }

    /**
     * Checks if there are runnable tests for current table.
     *
     * @return true if there are runnable tests for current table.
     */
    public boolean isTestable() {
        return runnableTestMethods.length > 0;
    }

    /**
     * Checks if there are tests, including tests with test cases, runs with filled runs, tests without cases(empty),
     * runs without any parameters and tests without cases and runs.
     */
    public boolean isHasAnyTests() {
        return CollectionUtils.isNotEmpty(allTests);
    }

    public boolean isHasTests() {
        return CollectionUtils.isNotEmpty(tests);
    }

    /**
     * Gets all tests for current table.
     */
    public TableDescription[] getAllTests() {
        if (allTests == null) {
            return null;
        }
        List<TableDescription> tableDescriptions = new ArrayList<>(allTests.length);
        for (IOpenMethod test : allTests) {
            TableSyntaxNode syntaxNode = (TableSyntaxNode) test.getInfo().getSyntaxNode();
            tableDescriptions.add(new TableDescription(syntaxNode.getUri(), syntaxNode.getId(), getTestName(test)));
        }
        tableDescriptions.sort(Comparator.comparing(TableDescription::getName));
        return tableDescriptions.toArray(new TableDescription[0]);
    }

    public String getTestName(Object testMethod) {
        IOpenMethod method = (IOpenMethod) testMethod;
        String name = TableSyntaxNodeUtils.getTestName(method);
        String info = ProjectHelper.getTestInfo(method);
        return String.format("%s (%s)", name, info);
    }

    public String removeTable() throws Throwable {
        try {
            final WebStudio studio = WebStudioUtils.getWebStudio();
            IGridTable gridTable = table.getGridTable(IXlsTableNames.VIEW_DEVELOPER);

            gridTable.edit();
            new TableServiceImpl().removeTable(gridTable);
            XlsSheetGridModel sheetModel = (XlsSheetGridModel) gridTable.getGrid();
            sheetModel.getSheetSource().getWorkbookSource().save();
            gridTable.stopEditing();
            WebStudioUtils.getExternalContext()
                    .getSessionMap()
                    .remove(org.openl.rules.tableeditor.util.Constants.TABLE_EDITOR_MODEL_NAME);

            studio.compile();
            RecentlyVisitedTables visitedTables = studio.getModel().getRecentlyVisitedTables();
            visitedTables.remove(table);
        } catch (Exception e) {
            throw e.getCause() == null ? e : e.getCause();
        }
        return null;
    }

    public boolean beforeEditAction() {
        final WebStudio studio = WebStudioUtils.getWebStudio();
        RulesProject currentProject = studio.getCurrentProject();
        if (currentProject != null) {
            try {
                return currentProject.tryLock();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return false;
            }
        }

        return true;
    }

    public boolean beforeSaveAction() {
        final WebStudio studio = WebStudioUtils.getWebStudio();
        studio.freezeProject(studio.getCurrentProject().getName());
        String editorId = WebStudioUtils
                .getRequestParameter(org.openl.rules.tableeditor.util.Constants.REQUEST_PARAM_EDITOR_ID);

        Map<?, ?> editorModelMap = (Map<?, ?>) WebStudioUtils.getExternalContext()
                .getSessionMap()
                .get(org.openl.rules.tableeditor.util.Constants.TABLE_EDITOR_MODEL_NAME);

        TableEditorModel editorModel = (TableEditorModel) editorModelMap.get(editorId);

        Workbook workbook = editorModel.getSheetSource().getWorkbookSource().getWorkbook();
        if (workbook instanceof XSSFWorkbook) {
            XSSFOptimizer.removeUnusedStyles((XSSFWorkbook) workbook);
        }

        if (studio.isUpdateSystemProperties()) {
            return EditHelper.updateSystemProperties(table, editorModel, propertyResolver.getProperty("user.mode"));
        }
        return true;
    }

    public void afterSaveAction(String newId) {
        final WebStudio studio = WebStudioUtils.getWebStudio();
        studio.releaseProject(studio.getCurrentProject().getName());
        studio.compile();
    }

    public String getRequestId() {
        final WebStudio studio = WebStudioUtils.getWebStudio();
        RulesProject currentProject = studio.getCurrentProject();
        String requestId = currentProject == null ? "" : currentProject.getRepository().getId();
        String projectName = currentProject == null ? "" : currentProject.getName();
        return String.format(REQUEST_ID_FORMAT, requestId, projectName);
    }

    public static void tryUnlock(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return;
        }
        Matcher matcher = REQUEST_ID_PATTERN.matcher(requestId);
        if (!matcher.matches()) {
            return;
        }

        String repositoryId = matcher.group(1);
        String projectName = matcher.group(2);

        final WebStudio studio = WebStudioUtils.getWebStudio();
        RulesProject currentProject = studio.getProject(repositoryId, projectName);
        if (currentProject != null) {
            try {
                if (!currentProject.isModified()) {
                    currentProject.releaseMyLock();
                }
            } catch (Exception e) {
                Logger logger = LoggerFactory.getLogger(TableBean.class);
                logger.error(e.getMessage(), e);
            }
        }
    }

    public boolean isCanOpenInExcel() {
        return canBeOpenInExcel;
    }

    public boolean getCanRun() {
        WebStudio studio = WebStudioUtils.getWebStudio();
        RulesProject currentProject = studio.getCurrentProject();
        if (currentProject == null) {
            return false;
        }
        return currentProject.hasArtefact(studio.getCurrentModule().getRulesRootPath().getPath());
    }

    public boolean getCanBenchmark() {
        WebStudio studio = WebStudioUtils.getWebStudio();
        RulesProject currentProject = studio.getCurrentProject();
        if (currentProject == null) {
            return false;
        }
        return currentProject.hasArtefact(studio.getCurrentModule().getRulesRootPath().getPath());
    }

    public Integer getRowIndex() {
        if (runnableTestMethods.length > 0 && !runnableTestMethods[0].hasId()) {
            if (method instanceof TestSuiteMethod) {
                TestMethodBoundNode boundNode = ((TestSuiteMethod) method).getBoundNode();
                if (boundNode != null && !boundNode.getTable().getHeaderTable().isNormalOrientation()) {
                    // Currently row indexes aren't supported for transposed test tables
                    return null;
                }
            }

            return table.getGridTable().getHeight() - runnableTestMethods.length + 1;
        }
        return null;
    }

    public static class TableDescription {
        private final String uri;
        private final String id;
        private final String name;

        public TableDescription(String uri, String id, String name) {
            this.uri = uri;
            this.id = id;
            this.name = name;
        }

        public String getUri() {
            return uri;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
