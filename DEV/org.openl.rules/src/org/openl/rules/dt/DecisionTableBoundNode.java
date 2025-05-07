package org.openl.rules.dt;

import org.openl.OpenL;
import org.openl.binding.BindingDependencies;
import org.openl.binding.IBindingContext;
import org.openl.binding.impl.module.ModuleOpenClass;
import org.openl.rules.binding.RulesModuleBindingContextHelper;
import org.openl.rules.calc.CustomSpreadsheetResultOpenClass;
import org.openl.rules.calc.Spreadsheet;
import org.openl.rules.calc.SpreadsheetResultOpenClass;
import org.openl.rules.lang.xls.binding.AMethodBasedNode;
import org.openl.rules.lang.xls.syntax.TableSyntaxNode;
import org.openl.rules.method.ExecutableRulesMethod;
import org.openl.syntax.exception.SyntaxNodeException;
import org.openl.syntax.exception.SyntaxNodeExceptionUtils;
import org.openl.types.IOpenClass;
import org.openl.types.IOpenMethodHeader;

/**
 * @author snshor
 */
public class DecisionTableBoundNode extends AMethodBasedNode {

    private IBindingContext bindingContext;
    private boolean initialized = false;

    public DecisionTableBoundNode(TableSyntaxNode tableSyntaxNode,
                                  OpenL openl,
                                  IOpenMethodHeader header,
                                  ModuleOpenClass module,
                                  IBindingContext bindingContext) {

        super(tableSyntaxNode, openl, header, module);
        this.bindingContext = bindingContext;
    }

    @Override
    protected ExecutableRulesMethod createMethodShell() {
        IOpenClass type = getType();
        int dim = 0;
        while (type.isArray()) {
            type = type.getComponentClass();
            dim++;
        }
        boolean isTypeCustomSpreadsheetResult = type instanceof SpreadsheetResultOpenClass;
        DecisionTable decisionTable = new DecisionTable(getHeader(), this, isTypeCustomSpreadsheetResult);
        if (decisionTable.isTypeCustomSpreadsheetResult()) {
            decisionTable.setDim(dim);
            decisionTable.setCustomSpreadsheetResultType(
                    (CustomSpreadsheetResultOpenClass) bindingContext.findType(
                            Spreadsheet.SPREADSHEETRESULT_TYPE_PREFIX + decisionTable.getName()));
            try {
                RulesModuleBindingContextHelper.compileAllTypesInSignature(getHeader().getSignature(), bindingContext);
                new DecisionTableLoader()
                        .loadAndBind(getTableSyntaxNode(), decisionTable, getOpenl(), getModule(), bindingContext);
            } catch (Exception e) {
                SyntaxNodeException error = SyntaxNodeExceptionUtils.createError(e, getTableSyntaxNode());
                bindingContext.addError(error);
            }
            initialized = true;
        }
        return decisionTable;
    }

    @Override
    public void finalizeBind(IBindingContext bindingContext) throws Exception {
        super.finalizeBind(bindingContext);
        if (!initialized && getDecisionTable() != null) {
            RulesModuleBindingContextHelper.compileAllTypesInSignature(getHeader().getSignature(), bindingContext);
            new DecisionTableLoader()
                    .loadAndBind(getTableSyntaxNode(), getDecisionTable(), getOpenl(), getModule(), bindingContext);
        }
    }

    public final DecisionTable getDecisionTable() {
        return (DecisionTable) getMethod();
    }

    @Override
    public void updateDependency(BindingDependencies dependencies) {
        getDecisionTable().updateDependency(dependencies);
    }
}
