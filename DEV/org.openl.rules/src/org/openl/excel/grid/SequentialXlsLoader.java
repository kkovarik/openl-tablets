package org.openl.excel.grid;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openl.dependency.DependencyType;
import org.openl.excel.parser.ExcelReader;
import org.openl.excel.parser.ExcelReaderFactory;
import org.openl.excel.parser.SheetDescriptor;
import org.openl.exception.OpenLCompilationException;
import org.openl.message.OpenLMessage;
import org.openl.message.OpenLMessagesUtils;
import org.openl.rules.lang.xls.IXlsTableNames;
import org.openl.rules.lang.xls.IncludeSearcher;
import org.openl.rules.lang.xls.TablePart;
import org.openl.rules.lang.xls.TablePartProcessor;
import org.openl.rules.lang.xls.XlsHelper;
import org.openl.rules.lang.xls.XlsNodeTypes;
import org.openl.rules.lang.xls.XlsSheetSourceCodeModule;
import org.openl.rules.lang.xls.XlsWorkbookSourceCodeModule;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.lang.xls.syntax.WorkbookSyntaxNode;
import org.openl.rules.lang.xls.syntax.WorksheetSyntaxNode;
import org.openl.rules.lang.xls.syntax.XlsModuleSyntaxNode;
import org.openl.rules.source.impl.VirtualSourceCodeModule;
import org.openl.rules.table.IGridTable;
import org.openl.rules.table.ILogicalTable;
import org.openl.rules.table.openl.GridCellSourceCodeModule;
import org.openl.rules.table.xls.XlsSheetGridModel;
import org.openl.source.IOpenSourceCodeModule;
import org.openl.source.impl.URLSourceCodeModule;
import org.openl.syntax.code.Dependency;
import org.openl.syntax.code.IDependency;
import org.openl.syntax.code.IParsedCode;
import org.openl.syntax.code.impl.ParsedCode;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.syntax.impl.IdentifierNode;
import org.openl.util.ParserUtils;
import org.openl.util.StringTool;
import org.openl.util.StringUtils;
import org.openl.util.text.LocationUtils;

public class SequentialXlsLoader {
    private final Logger log = LoggerFactory.getLogger(SequentialXlsLoader.class);
    private final Collection<String> imports = new HashSet<>();
    private final IncludeSearcher includeSeeker;
    private final List<SyntaxNodeException> errors = new ArrayList<>();
    private final Collection<OpenLMessage> messages = new LinkedHashSet<>();
    private final Set<String> preprocessedWorkBooks = new HashSet<>();
    private final List<WorkbookSyntaxNode> workbookNodes = new ArrayList<>();
    private final Set<IDependency> dependencies = new LinkedHashSet<>();

    public SequentialXlsLoader(IncludeSearcher includeSeeker) {
        this.includeSeeker = includeSeeker;
    }

    private WorksheetSyntaxNode[] createWorksheetNodes(TablePartProcessor tablePartProcessor,
                                                       XlsWorkbookSourceCodeModule workbookSourceModule) {
        IOpenSourceCodeModule source = workbookSourceModule.getSource();

        if (VirtualSourceCodeModule.SOURCE_URI.equals(source.getUri())) {
            int nSheets = workbookSourceModule.getWorkbookLoader().getNumberOfSheets();
            WorksheetSyntaxNode[] sheetNodes = new WorksheetSyntaxNode[nSheets];

            for (int i = 0; i < nSheets; i++) {
                XlsSheetSourceCodeModule sheetSource = new XlsSheetSourceCodeModule(i, workbookSourceModule);
                IGridTable[] tables = new XlsSheetGridModel(sheetSource).getTables();
                sheetNodes[i] = createWorksheetSyntaxNode(tablePartProcessor, sheetSource, tables);
            }
            return sheetNodes;
        }

        ExcelReaderFactory factory = ExcelReaderFactory.sequentialFactory();

        // Opening the file by path is preferred because using an InputStream has a higher memory footprint than using a
        // File.
        // See POI documentation. For both: User API and SAX/Event API.
        String path;
        try {
            path = workbookSourceModule.getSourceFile().getAbsolutePath();
        } catch (Exception ex) {
            // No path found to the resource (file) on the native file system.
            // The resource can be inside jar, zip, wsjar, vfs or other virtual file system.
            // Example of such case is AlgorithmTableSpecification.xls.
            path = null;
        }
        try (ExcelReader excelReader = path == null ? factory.create(source.getByteStream()) : factory.create(path)) {
            List<? extends SheetDescriptor> sheets = excelReader.getSheets();
            boolean use1904Windowing = excelReader.isUse1904Windowing();

            int nSheets = sheets.size();
            WorksheetSyntaxNode[] sheetNodes = new WorksheetSyntaxNode[nSheets];

            for (int i = 0; i < nSheets; i++) {
                final SheetDescriptor sheet = sheets.get(i);
                XlsSheetSourceCodeModule sheetSource = new SequentialXlsSheetSourceCodeModule(workbookSourceModule,
                        sheet);
                Object[][] cells = excelReader.getCells(sheet);
                IGridTable[] tables = new ParsedGrid(path, sheetSource, sheet, cells, use1904Windowing).getTables();
                sheetNodes[i] = createWorksheetSyntaxNode(tablePartProcessor, sheetSource, tables);
            }

            return sheetNodes;
        }
    }

    private void addError(SyntaxNodeException error) {
        errors.add(error);
    }

    public IParsedCode parse(IOpenSourceCodeModule source) {

        preprocessWorkbook(source);

        WorkbookSyntaxNode[] workbooksArray = workbookNodes.toArray(new WorkbookSyntaxNode[0]);
        XlsModuleSyntaxNode syntaxNode = new XlsModuleSyntaxNode(workbooksArray,
                source,
                Collections.unmodifiableCollection(imports));

        SyntaxNodeException[] parsingErrors = errors.toArray(SyntaxNodeException.EMPTY_ARRAY);

        return new ParsedCode(syntaxNode, source, parsingErrors, messages, dependencies.toArray(new IDependency[0]));
    }

    private void preprocessEnvironmentTable(TableSyntaxNode tableSyntaxNode, XlsSheetSourceCodeModule source) {

        ILogicalTable logicalTable = tableSyntaxNode.getTable();

        int height = logicalTable.getHeight();

        for (int i = 1; i < height; i++) {
            ILogicalTable row = logicalTable.getRow(i);

            String value = row.getColumn(0).getSource().getCell(0, 0).getStringValue();
            if (StringUtils.isNotBlank(value)) {
                value = value.trim();
            }

            if (IXlsTableNames.LANG_PROPERTY.equals(value)) {
                // Drop support "language" property
            } else if (IXlsTableNames.DEPENDENCY.equals(value)) {
                // process module dependency
                //
                preprocessDependency(tableSyntaxNode, row.getSource());
            } else if (IXlsTableNames.INCLUDE_TABLE.equals(value)) {
                preprocessIncludeTable(tableSyntaxNode, row.getSource(), source);
            } else if (IXlsTableNames.IMPORT_PROPERTY.equals(value)) {
                preprocessImportTable(row.getSource());
            } else if (ParserUtils.isBlankOrCommented(value)) {
                // ignore comment
                log.debug("Comment: {}", value);
            } else {
                String message = String.format("Error in Environment table: unrecognized keyword '%s'", value);
                messages.add(OpenLMessagesUtils.newWarnMessage(message, tableSyntaxNode));
            }
        }
    }

    private void preprocessDependency(TableSyntaxNode tableSyntaxNode, IGridTable gridTable) {

        int height = gridTable.getHeight();

        for (int i = 0; i < height; i++) {
            String dependency = gridTable.getCell(1, i).getStringValue();
            if (StringUtils.isNotBlank(dependency)) {
                dependency = dependency.trim();

                IdentifierNode node = new IdentifierNode(IXlsTableNames.DEPENDENCY,
                        LocationUtils.createTextInterval(dependency),
                        dependency,
                        new GridCellSourceCodeModule(gridTable, 1, i, null));
                node.setParent(tableSyntaxNode);
                Dependency moduleDependency = new Dependency(DependencyType.MODULE, node);
                dependencies.add(moduleDependency);
            }
        }
    }

    private void preprocessImportTable(IGridTable table) {
        int height = table.getHeight();

        for (int i = 0; i < height; i++) {
            String singleImport = table.getCell(1, i).getStringValue();
            if (StringUtils.isNotBlank(singleImport)) {
                addImport(singleImport.trim());
            }
        }
    }

    private void addImport(String singleImport) {
        imports.add(singleImport);
    }

    protected static String getParentAndMergePaths(String p1, String p2) {
        p1 = p1.replaceAll("\\\\", "/");
        p2 = p2.replaceAll("\\\\", "/");

        String[] pp1 = p1.split("/");
        String[] pp2 = p2.split("/");
        List<String> result = new ArrayList<>();

        int len = p1.endsWith("/") ? pp1.length : pp1.length - 1;

        for (int i = 0; i < len; i++) {
            if (pp1[i].equals(".")) {
                continue;
            }
            if (pp1[i].equals("..")) {
                if (!result.isEmpty() && !result.get(result.size() - 1).equals("..")) {
                    result.remove(result.size() - 1);
                    continue;
                }
            }
            result.add(pp1[i]);
        }

        for (String s : pp2) {
            if (s.equals(".")) {
                continue;
            }
            if (!result.isEmpty() && s.equals("..")) {
                result.remove(result.size() - 1);
                continue;
            }
            result.add(s);
        }

        return String.join("/", result);
    }

    private void preprocessIncludeTable(TableSyntaxNode tableSyntaxNode,
                                        IGridTable table,
                                        XlsSheetSourceCodeModule sheetSource) {

        int height = table.getHeight();

        for (int i = 0; i < height; i++) {

            String include = table.getCell(1, i).getStringValue();

            if (StringUtils.isNotBlank(include)) {
                include = include.trim();
                IOpenSourceCodeModule src = null;

                if (include.startsWith("<")) {
                    try {
                        Matcher matcher = Pattern.compile("<([^<>]+)>").matcher(include);
                        matcher.find();
                        src = includeSeeker.findInclude(matcher.group(1));
                    } catch (Exception e) {
                        messages.addAll(OpenLMessagesUtils.newErrorMessages(e));

                    }
                    if (src == null) {
                        registerIncludeError(tableSyntaxNode, table, i, include, null);
                        continue;
                    }
                } else {
                    try {
                        String newURL = getParentAndMergePaths(sheetSource.getWorkbookSource().getFileUri(),
                                StringTool.encodeURL(include));
                        src = new URLSourceCodeModule(new URL(newURL));
                    } catch (Exception t) {
                        registerIncludeError(tableSyntaxNode, table, i, include, t);
                        continue;
                    }
                }

                try {
                    preprocessWorkbook(src);
                } catch (Exception t) {
                    registerIncludeError(tableSyntaxNode, table, i, include, t);
                }
            }
        }
    }

    private void registerIncludeError(TableSyntaxNode tableSyntaxNode,
                                      IGridTable table,
                                      int i,
                                      String include,
                                      Exception t) {
        SyntaxNodeException se = SyntaxNodeExceptionUtils.createError("Include '" + include + "' is not found.",
                t,
                LocationUtils.createTextInterval(include),
                new GridCellSourceCodeModule(table, 1, i, null));
        addError(se);
    }

    private TableSyntaxNode preprocessTable(IGridTable table,
                                            XlsSheetSourceCodeModule source,
                                            TablePartProcessor tablePartProcessor) throws OpenLCompilationException {

        TableSyntaxNode tsn = XlsHelper.createTableSyntaxNode(table, source);

        String type = tsn.getType();
        if (type.equals(XlsNodeTypes.XLS_ENVIRONMENT.toString())) {
            preprocessEnvironmentTable(tsn, source);
        } else if (type.equals(XlsNodeTypes.XLS_TABLEPART.toString())) {
            try {
                tablePartProcessor.register(table, source);
            } catch (Exception | LinkageError t) {
                tsn = new TableSyntaxNode(XlsNodeTypes.XLS_OTHER
                        .toString(), tsn.getGridLocation(), source, table, tsn.getHeader());
                SyntaxNodeException sne = SyntaxNodeExceptionUtils.createError(t, tsn);
                addError(sne);
            }
        }

        return tsn;
    }

    private void preprocessWorkbook(IOpenSourceCodeModule source) {

        String uri = source.getUri();

        if (preprocessedWorkBooks.contains(uri)) {
            return;
        }

        preprocessedWorkBooks.add(uri);

        TablePartProcessor tablePartProcessor = new TablePartProcessor();
        XlsWorkbookSourceCodeModule workbookSourceModule = new XlsWorkbookSourceCodeModule(source);
        WorksheetSyntaxNode[] sheetNodes = createWorksheetNodes(tablePartProcessor, workbookSourceModule);

        workbookNodes.add(createWorkbookNode(tablePartProcessor, workbookSourceModule, sheetNodes));
        messages.addAll(tablePartProcessor.getMessages());
    }

    private WorkbookSyntaxNode createWorkbookNode(TablePartProcessor tablePartProcessor,
                                                  XlsWorkbookSourceCodeModule workbookSourceModule,
                                                  WorksheetSyntaxNode[] sheetNodes) {
        TableSyntaxNode[] mergedNodes = {};
        try {
            List<TablePart> tableParts = tablePartProcessor.mergeAllNodes();
            int n = tableParts.size();
            mergedNodes = new TableSyntaxNode[n];
            for (int i = 0; i < n; i++) {
                mergedNodes[i] = preprocessTable(tableParts.get(i).getTable(),
                        tableParts.get(i).getSource(),
                        tablePartProcessor);
            }
        } catch (OpenLCompilationException e) {
            messages.add(OpenLMessagesUtils.newErrorMessage(e));
        }

        return new WorkbookSyntaxNode(sheetNodes, mergedNodes, workbookSourceModule);
    }

    private WorksheetSyntaxNode createWorksheetSyntaxNode(TablePartProcessor tablePartProcessor,
                                                          XlsSheetSourceCodeModule sheetSource,
                                                          IGridTable[] tables) {
        List<TableSyntaxNode> tableNodes = new ArrayList<>();

        for (IGridTable table : tables) {

            TableSyntaxNode tsn;

            try {
                tsn = preprocessTable(table, sheetSource, tablePartProcessor);
                tableNodes.add(tsn);
            } catch (OpenLCompilationException e) {
                messages.add(OpenLMessagesUtils.newErrorMessage(e));
            }
        }

        return new WorksheetSyntaxNode(tableNodes.toArray(TableSyntaxNode.EMPTY_ARRAY), sheetSource);
    }
}
