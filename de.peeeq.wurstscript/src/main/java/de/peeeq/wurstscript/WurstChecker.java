package de.peeeq.wurstscript;

import com.google.common.base.Preconditions;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.attributes.ErrorHandler;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.validation.TRVEHelper;
import de.peeeq.wurstscript.validation.WurstValidator;

import java.util.List;

public class WurstChecker {

    private final WurstGui gui;
    private ErrorHandler errorHandler;

    public WurstChecker(WurstGui gui, ErrorHandler errorHandler) {
        this.gui = gui;
        this.errorHandler = errorHandler;
    }

    public void checkProg(WurstModel root, List<CompilationUnit> toCheck) {
        Preconditions.checkNotNull(root);
        Preconditions.checkNotNull(toCheck);
        if (root.isEmpty()) {
            return;
        }
        TRVEHelper.protectedVariables.clear();
        gui.sendProgress("Checking Files");

        if (errorHandler.getErrorCount() > 0) return;

        attachErrorHandler(root);

        expandModules(root);

        if (errorHandler.getErrorCount() > 0) return;

        // compute the flow attributes
        for (CompilationUnit cu : toCheck) {
            WurstValidator.computeFlowAttributes(cu);
        }


        // validate the resource:
        WurstValidator validator = new WurstValidator(root);
        validator.validate(toCheck);
        WLogger.info("debug - finished checkProg");
    }

    private void attachErrorHandler(WurstModel root) {
        for (CompilationUnit cu : root) {
            cu.getCuInfo().setCuErrorHandler(errorHandler);
        }
    }

    private void expandModules(WurstModel root) {
        for (CompilationUnit cu : root) {
            ModuleExpander.expandModules(cu);
        }
    }

}
