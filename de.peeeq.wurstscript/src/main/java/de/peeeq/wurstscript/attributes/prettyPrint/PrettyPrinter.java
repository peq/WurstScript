package de.peeeq.wurstscript.attributes.prettyPrint;

import de.peeeq.wurstscript.ast.*;
import org.apache.commons.lang.StringUtils;

import static de.peeeq.wurstscript.utils.Utils.escapeString;

public class PrettyPrinter {

    private static void commaSeparatedList(Element e, Spacer spacer, StringBuilder sb, int indent) {
        for (int i = 0; i < e.size(); i++) {
            if (i > 0) {
                sb.append(",");
                spacer.addSpace(sb);
            }
            e.get(i).prettyPrint(spacer, sb, indent);
        }
    }

    private static void printClassOrModuleDeclaration(ClassOrModule e, Spacer spacer, StringBuilder sb, int indent) {
        e.getModuleUses().prettyPrint(spacer, sb, indent);
        e.getVars().prettyPrint(spacer, sb, indent);
        e.getConstructors().prettyPrint(spacer, sb, indent);
        e.getMethods().prettyPrint(spacer, sb, indent);
        e.getOnDestroy().prettyPrint(spacer, sb, indent);
        e.getInnerClasses().prettyPrint(spacer, sb, indent);
    }

    private static void printFirstNewline(Element e, StringBuilder sb, int indent) {
        // Don't add first new line.
        if (e.getParent().get(0).equals(e)) {
            return;
        }
        if (sb.charAt(sb.length()-1) == '\n' && sb.charAt(sb.length()-2) == '\n') {
            return;
        }
        sb.append("\n");
    }

    private static void printNewline(Element e, StringBuilder sb, int indent) {
        // Don't add a newline unless we are directly under statements.
        // Otherwise we would add newlines inside function calls, etc.
        if (!(e.getParent() instanceof WStatements)) {
            return;
        }
        sb.append("\n");
    }

    private static void printStuff(Documentable e, Spacer spacer, StringBuilder sb, int indent) {
        printComment(sb, e, indent);
        printComment(e.getModifiers(), spacer, sb, indent);
        printIndent(sb, indent);
        e.getModifiers().prettyPrint(spacer, sb, indent);
    }

    // For some reason comments are a kind of modifier.
    private static void printComment(Modifiers e, Spacer spacer, StringBuilder sb, int indent) {
        for (Modifier modifier : e) {
            if (modifier instanceof WurstDoc) {
                modifier.prettyPrint(spacer, sb, indent);
            }
        }
    }

    private static void printComment(StringBuilder sb, Documentable d, int indent) {
        // Comments doesn't exist in the wurst parser atm.
    }

    private static void printIndent(StringBuilder sb, int indent) {
        if (sb.toString().substring(sb.length() - 1).equals("\n")) {
            for (int i = 0; i < indent; i++) {
                sb.append("\t");
            }
        }
    }

    private static String calculateIndent(int indent) {
        String s = "";
        for (int i = 0; i < indent; i++) {
            s += "\t";
        }
        return s;
    }

    public static void prettyPrint(Annotation e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getAnnotationType());
        if (e.getAnnotationMessage().length() >= 1) {
            sb.append("(");
            sb.append(e.getAnnotationMessage());
            sb.append(")");
        }
    }

    public static void prettyPrint(Arguments e, Spacer spacer, StringBuilder sb, int indent) {
        commaSeparatedList(e, spacer, sb, indent);
    }

    public static void prettyPrint(ExprList e, Spacer spacer, StringBuilder sb, int indent) {
        commaSeparatedList(e, spacer, sb, indent);
    }

    public static void prettyPrint(ArraySizes e, Spacer spacer, StringBuilder sb, int indent) {
        commaSeparatedList(e, spacer, sb, indent);
    }

    public static void prettyPrint(ClassDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("class");
        spacer.addSpace(sb);
        sb.append(e.getName());
        e.getTypeParameters().prettyPrint(spacer, sb, indent);
        if (e.getExtendedClass() instanceof TypeExpr) {
            TypeExpr te = (TypeExpr) e.getExtendedClass();
            spacer.addSpace(sb);
            sb.append("extends");
            spacer.addSpace(sb);
            te.prettyPrint(spacer, sb, indent);
        }
        if (!e.getImplementsList().isEmpty()) {
            spacer.addSpace(sb);
            sb.append("implements");
            spacer.addSpace(sb);
            commaSeparatedList(e.getImplementsList(), spacer, sb, indent);
        }
        sb.append("\n");
        printClassOrModuleDeclaration(e, spacer, sb, indent + 1);
    }

    public static void prettyPrint(CompilationUnit e, Spacer spacer, StringBuilder sb, int indent) {
        prettyPrint(e.getPackages(), spacer, sb, indent);
    }

    public static void prettyPrint(ConstructorDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("construct");
        e.getParameters().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        if (!e.getSuperArgs().isEmpty()) {
            printIndent(sb, indent + 1);
            sb.append("super(");
            e.getSuperArgs().prettyPrint(spacer, sb, indent);
            sb.append(")");
            sb.append("\n");
        }
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(ConstructorDefs e, Spacer spacer, StringBuilder sb, int indent) {
        for (ConstructorDef constructorDef : e) {
            // Remove empty constructors.
            if (constructorDef.getBody().size() < 1 && constructorDef.getSuperArgs().size() <= 0) {
                continue;
            }
            constructorDef.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(EndFunctionStatement e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(EnumDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("enum");
        spacer.addSpace(sb);
        sb.append(e.getName());
        sb.append("\n");
        e.getMembers().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(EnumMember e, Spacer spacer, StringBuilder sb, int indent) {
        printStuff(e, spacer, sb, indent);
        sb.append(e.getName());
        sb.append("\n");
    }

    public static void prettyPrint(EnumMembers e, Spacer spacer, StringBuilder sb, int indent) {
        for (EnumMember enumMember : e) {
            enumMember.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(ExprBinary e, Spacer spacer, StringBuilder sb, int indent) {
        e.getLeft().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append(e.getOp().toString());
        spacer.addSpace(sb);
        e.getRight().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(ExprBoolVal e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(String.valueOf(e.getValB()));
    }

    public static void prettyPrint(ExprCast e, Spacer spacer, StringBuilder sb, int indent) {
        e.getExpr().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append("castTo");
        spacer.addSpace(sb);
        e.getTyp().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(ExprClosure e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("(");
        e.getShortParameters().prettyPrint(spacer, sb, indent);
        sb.append(")");
        spacer.addSpace(sb);
        sb.append("->");
        spacer.addSpace(sb);
        e.getImplementation().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(ExprDestroy e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printIndent(sb, indent);
        sb.append("destroy");
        spacer.addSpace(sb);
        e.getDestroyedObj().prettyPrint(spacer, sb, indent);
        sb.append("\n");
    }

    public static void prettyPrint(ExprEmpty e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(ExprFuncRef e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("function");
        spacer.addSpace(sb);
        if (!StringUtils.isBlank(e.getScopeName())) {
            sb.append(e.getScopeName());
            sb.append(".");
        }
        sb.append(e.getFuncName());
    }

    public static void prettyPrint(ExprFunctionCall e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append(e.getFuncName());
        sb.append("(");
        e.getArgs().prettyPrint(spacer, sb, indent);
        sb.append(")");
        printNewline(e, sb, indent);
    }

    public static void prettyPrint(ExprIncomplete e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(ExprInstanceOf e, Spacer spacer, StringBuilder sb, int indent) {
        e.getExpr().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append("instanceof");
        spacer.addSpace(sb);
        e.getTyp().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(ExprIntVal e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getValIraw());
    }

    public static void prettyPrint(ExprMemberArrayVarDot e, Spacer spacer, StringBuilder sb, int indent) {
        e.getLeft().prettyPrint(spacer, sb, indent);
        sb.append(".");
        sb.append(e.getVarName());
        sb.append("[");
        e.getIndexes().prettyPrint(spacer, sb, indent);
        sb.append("]");
    }

    public static void prettyPrint(ExprMemberArrayVarDotDot e, Spacer spacer, StringBuilder sb, int indent) {
        e.getLeft().prettyPrint(spacer, sb, indent);
        sb.append("..");
        sb.append(e.getVarName());
        sb.append("[");
        e.getIndexes().prettyPrint(spacer, sb, indent);
        sb.append("]");
    }

    public static void prettyPrint(ExprMemberMethodDot e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        if (e.getLeft() instanceof ExprBinary) {
            sb.append("(");
            e.getLeft().prettyPrint(spacer, sb, indent);
            sb.append(")");
        } else {
            e.getLeft().prettyPrint(spacer, sb, indent);
        }
        sb.append(".");
        sb.append(e.getFuncName());
        sb.append("(");
        e.getArgs().prettyPrint(spacer, sb, indent);
        sb.append(")");
        printNewline(e, sb, indent);
    }

    public static void prettyPrint(ExprMemberMethodDotDot e, Spacer spacer, StringBuilder sb, int indent) {
        if (e.getLeft() instanceof ExprBinary) {
            sb.append("(");
            e.getLeft().prettyPrint(spacer, sb, indent);
            sb.append(")");
        } else {
            e.getLeft().prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
        printIndent(sb, indent+1);
        sb.append("..");
        sb.append(e.getFuncName());
        sb.append("(");
        e.getArgs().prettyPrint(spacer, sb, indent);
        sb.append(")");
        printNewline(e, sb, indent);
    }

    public static void prettyPrint(ExprMemberVarDot e, Spacer spacer, StringBuilder sb, int indent) {
        e.getLeft().prettyPrint(spacer, sb, indent);
        sb.append(".");
        sb.append(e.getVarName());
    }

    public static void prettyPrint(ExprMemberVarDotDot e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        e.getLeft().prettyPrint(spacer, sb, indent);
        sb.append("..");
        sb.append(e.getVarName());
    }

    public static void prettyPrint(ExprNewObject e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("new");
        spacer.addSpace(sb);
        sb.append(e.getTypeName());
        e.getTypeArgs().prettyPrint(spacer, sb, indent);
        if (e.getArgs().size() > 0) {
            sb.append("(");
            e.getArgs().prettyPrint(spacer, sb, indent);
            sb.append(")");
        }
        printNewline(e, sb, indent);
    }

    public static void prettyPrint(ExprNull e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("null");
    }

    public static void prettyPrint(ExprRealVal e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getValR());
    }

    public static void prettyPrint(ExprStatementsBlock e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("begin");
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
        printIndent(sb, indent);
        sb.append("end");
    }

    public static void prettyPrint(ExprStringVal e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(escapeString(e.getValS()));
    }

    public static void prettyPrint(ExprSuper e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("super");
    }

    public static void prettyPrint(ExprThis e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("this");
    }

    public static void prettyPrint(ExprTypeId e, Spacer spacer, StringBuilder sb, int indent) {
        e.getLeft().prettyPrint(spacer, sb, indent);
        sb.append(".typeId");
    }

    public static void prettyPrint(ExprUnary e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getOpU().toString());
        // Don't add space for unary minus, e.g -1.
        if (e.getOpU().toString() == "not") {
            spacer.addSpace(sb);
        }
        e.getRight().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(ExprVarAccess e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append(e.getVarName());
    }

    public static void prettyPrint(ExprVarArrayAccess e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append(e.getVarName());
        sb.append("[");
        e.getIndexes().prettyPrint(spacer, sb, indent);
        sb.append("]");
    }

    public static void prettyPrint(ExtensionFuncDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("function");
        spacer.addSpace(sb);
        e.getExtendedType().prettyPrint(spacer, sb, indent);
        sb.append(".");
        printFunctionSignature(e, spacer, sb, indent);
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }


    private static void printFunctionSignature(FunctionImplementation e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getName());
        e.getTypeParameters().prettyPrint(spacer, sb, indent);
        e.getParameters().prettyPrint(spacer, sb, indent);
        if (!(e.getReturnTyp() instanceof NoTypeExpr)) {
            spacer.addSpace(sb);
            sb.append("returns");
            spacer.addSpace(sb);
            e.getReturnTyp().prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(FuncDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("function");
        spacer.addSpace(sb);
        printFunctionSignature(e, spacer, sb, indent);
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(FuncDefs e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        for (FuncDef funcDef : e) {
            funcDef.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(GlobalVarDef e, Spacer spacer, StringBuilder sb, int indent) {
        printStuff(e, spacer, sb, indent);
        if ((e.getOptTyp() instanceof NoTypeExpr)) {
            if (!e.attrIsConstant()) {
                sb.append("var");
            }
        } else {
            e.getOptTyp().prettyPrint(spacer, sb, indent);
        }
        spacer.addSpace(sb);
        sb.append(e.getName());
        if (!(e.getInitialExpr() instanceof NoExpr)) {
            spacer.addSpace(sb);
            sb.append("=");
            spacer.addSpace(sb);
            e.getInitialExpr().prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
    }

    public static void prettyPrint(GlobalVarDefs e, Spacer spacer, StringBuilder sb, int indent) {
        if (e.size() <= 0) {
            return;
        }
        for (GlobalVarDef varDef : e) {
            varDef.prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
    }

    public static void prettyPrint(IdentifierWithTypeArgs e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(IdentifierWithTypeParamDefs e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(Indexes e, Spacer spacer, StringBuilder sb, int indent) {
        for (Expr expr : e) {
            expr.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(InitBlock e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printIndent(sb, indent);
        sb.append("init");
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(InterfaceDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("interface");
        spacer.addSpace(sb);
        sb.append(e.getName());
        e.getTypeParameters().prettyPrint(spacer, sb, indent);
        if (e.getExtendsList().size() >= 1) {
            spacer.addSpace(sb);
            sb.append("extends");
            spacer.addSpace(sb);
            e.getExtendsList().prettyPrint(spacer, sb, indent);
        }
        e.getModuleUses().prettyPrint(spacer, sb, indent + 1);
        e.getVars().prettyPrint(spacer, sb, indent + 1);
        e.getConstructors().prettyPrint(spacer, sb, indent + 1);
        e.getMethods().prettyPrint(spacer, sb, indent + 1);
        e.getOnDestroy().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(JassGlobalBlock e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(JassToplevelDeclarations e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(LocalVarDef e, Spacer spacer, StringBuilder sb, int indent) {
        printComment(sb, e, indent);
        printIndent(sb, indent);
        if ((e.getOptTyp() instanceof NoTypeExpr)) {
            if (e.attrIsConstant()) {
                sb.append("let");
            } else if (!(e.getParent() instanceof StmtForRange)) {
                sb.append("var");
            }
        } else {
            e.getOptTyp().prettyPrint(spacer, sb, indent);
        }
        spacer.addSpace(sb);
        sb.append(e.getName());
        if (!(e.getInitialExpr() instanceof NoExpr)) {
            spacer.addSpace(sb);
            sb.append("=");
            spacer.addSpace(sb);
            e.getInitialExpr().prettyPrint(spacer, sb, indent);
        }
        printNewline(e, sb, indent);
    }

    public static void prettyPrint(ModAbstract e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("abstract");
    }

    public static void prettyPrint(ModConstant e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("constant");
    }

    public static void prettyPrint(ModVararg e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("vararg");
    }

    public static void prettyPrint(Modifiers e, Spacer spacer, StringBuilder sb, int indent) {
        for (Modifier modifier : e) {
            // We handle WurstDoc separately.
            if (!(modifier instanceof WurstDoc)) {
                modifier.prettyPrint(spacer, sb, indent);
                spacer.addSpace(sb);
            }
        }
    }

    public static void prettyPrint(ModOverride e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("override");
    }

    public static void prettyPrint(ModStatic e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("static");
    }

    public static void prettyPrint(ModuleDef e, Spacer spacer, StringBuilder sb, int indent) {
        printFirstNewline(e, sb, indent);
        printStuff(e, spacer, sb, indent);
        sb.append("module");
        spacer.addSpace(sb);
        e.getNameId().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        printClassOrModuleDeclaration(e, spacer, sb, indent + 1);
    }

    public static void prettyPrint(ModuleInstanciation e, Spacer spacer, StringBuilder sb, int indent) {
        printComment(sb, e, indent);
    }

    public static void prettyPrint(ModuleInstanciations e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(ModuleUse e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("use");
        spacer.addSpace(sb);
        sb.append(e.getModuleName());
        sb.append("\n");
    }

    public static void prettyPrint(ModuleUses e, Spacer spacer, StringBuilder sb, int indent) {
        for (ModuleUse moduleUse : e) {
            moduleUse.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(NativeFunc e, Spacer spacer, StringBuilder sb, int indent) {
        printStuff(e, spacer, sb, indent);
        sb.append("native");
        spacer.addSpace(sb);
        e.getNameId().prettyPrint(spacer, sb, indent);
        e.getParameters().prettyPrint(spacer, sb, indent);
        if (!(e.getReturnTyp() instanceof NoTypeExpr)) {
            spacer.addSpace(sb);
            sb.append("returns");
            spacer.addSpace(sb);
            e.getReturnTyp().prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
    }

    public static void prettyPrint(NativeType e, Spacer spacer, StringBuilder sb, int indent) {
        printStuff(e, spacer, sb, indent);
        sb.append("nativetype");
        spacer.addSpace(sb);
        e.getNameId().prettyPrint(spacer, sb, indent);
        sb.append("\n");
    }

    public static void prettyPrint(NoDefaultCase e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(NoExpr e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(NoTypeExpr e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(OnDestroyDef e, Spacer spacer, StringBuilder sb, int indent) {
        if (e.getBody().size() <= 0) {
            return;
        }
        printFirstNewline(e, sb, indent);
        printIndent(sb, indent);
        sb.append("ondestroy");
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(StartFunctionStatement e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(StmtErr e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(StmtExitwhen e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("break");
        sb.append("\n");
    }

    public static void prettyPrint(StmtForFrom e, Spacer spacer, StringBuilder sb, int indent) {
        forIteratorLoop("from", e, e.getIn(), spacer, sb, indent);
    }

    public static void prettyPrint(StmtForIn e, Spacer spacer, StringBuilder sb, int indent) {
        forIteratorLoop("in", e, e.getIn(), spacer, sb, indent);
    }

    private static void forIteratorLoop(String method, LoopStatementWithVarDef e, Expr target, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("for");
        spacer.addSpace(sb);
        e.getLoopVar().getNameId().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append(method);
        spacer.addSpace(sb);
        target.prettyPrint(spacer, sb, indent);
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    private static void forRangeLoop(String direction, StmtForRange e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("for");
        spacer.addSpace(sb);
        e.getLoopVar().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append(direction);
        spacer.addSpace(sb);
        e.getTo().prettyPrint(spacer, sb, indent);
        if (e.getStep() instanceof ExprIntVal && ((ExprIntVal) e.getStep()).getValI() != 1) {
            spacer.addSpace(sb);
            sb.append("step");
            spacer.addSpace(sb);
            e.getStep().prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(StmtForRangeDown e, Spacer spacer, StringBuilder sb, int indent) {
        forRangeLoop("downto", e, spacer, sb, indent);
    }

    public static void prettyPrint(StmtForRangeUp e, Spacer spacer, StringBuilder sb, int indent) {
        forRangeLoop("to", e, spacer, sb, indent);
    }

    public static void prettyPrint(StmtIf e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("if");
        spacer.addSpace(sb);
        e.getCond().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        e.getThenBlock().prettyPrint(spacer, sb, indent + 1);
        if (e.getElseBlock().size() > 0) {
            printIndent(sb, indent);
            sb.append("else");
            if (e.getElseBlock().size() > 0 && e.getElseBlock().get(0) instanceof StmtIf) {
                spacer.addSpace(sb);
                e.getElseBlock().get(0).prettyPrint(spacer, sb, indent);
            } else {
                sb.append("\n");
                e.getElseBlock().prettyPrint(spacer, sb, indent + 1);
            }
        }
    }

    public static void prettyPrint(StmtLoop e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(StmtReturn e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("return");
        spacer.addSpace(sb);
        e.getReturnedObj().prettyPrint(spacer, sb, indent);
        sb.append("\n");
    }

    private static boolean printAssignmentShorthands(Expr left, Expr right, Spacer spacer, StringBuilder sb, int indent) {
        if (!(right instanceof ExprBinary)) {
            return false;
        }

        if (!(left.toString().equals(((ExprBinary) right).getLeft().toString()))) {
            return false;
        }

        String operator = ((ExprBinary) right).getOp().toString();
        Expr val = ((ExprBinary) right).getRight();

        // i++ and i--
        if (val instanceof ExprIntVal
                && ((ExprIntVal) val).getValI() == 1
                && (operator.equals("+") || operator.equals("-"))) {
            sb.append(operator);
            sb.append(operator);
            return true;
        }

        // i +=, ...
        spacer.addSpace(sb);
        sb.append(operator);
        sb.append("=");
        spacer.addSpace(sb);
        val.prettyPrint(spacer, sb, indent);
        return true;
    }

    public static void prettyPrint(StmtSet e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        e.getUpdatedExpr().prettyPrint(spacer, sb, indent);

        // Special cases for assignment shorthands i++ and i += variants.
        boolean shortened = printAssignmentShorthands(e.getUpdatedExpr(), e.getRight(), spacer, sb, indent);

        if (!shortened) {
            spacer.addSpace(sb);
            sb.append("=");
            spacer.addSpace(sb);
            e.getRight().prettyPrint(spacer, sb, indent);
        }
        sb.append("\n");
    }

    public static void prettyPrint(StmtSkip e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("skip");
        sb.append("\n");
    }

    public static void prettyPrint(StmtWhile e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("while");
        spacer.addSpace(sb);
        e.getCond().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        e.getBody().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(SwitchCase e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("case");
        spacer.addSpace(sb);
        e.getExpr().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        e.getStmts().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(SwitchCases sw, Spacer spacer, StringBuilder sb, int indent) {
        for (SwitchCase c : sw) {
            c.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(SwitchDefaultCaseStatements e, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("default");
        sb.append("\n");
        e.getStmts().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(SwitchStmt sw, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append("switch");
        spacer.addSpace(sb);
        sw.getExpr().prettyPrint(spacer, sb, indent);
        sb.append("\n");
        sw.getCases().prettyPrint(spacer, sb, indent + 1);
        sw.getSwitchDefault().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(TopLevelDeclarations e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(TupleDef e, Spacer spacer, StringBuilder sb, int indent) {
        printStuff(e, spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append("tuple");
        spacer.addSpace(sb);
        e.getNameId().prettyPrint(spacer, sb, indent);
        e.getParameters().prettyPrint(spacer, sb, indent);
        sb.append("\n");
    }

    public static void prettyPrint(TypeExprArray e, Spacer spacer, StringBuilder sb, int indent) {
        e.getBase().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append("array");
    }

    public static void prettyPrint(TypeExprList typeExprList, Spacer spacer, StringBuilder sb, int indent) {
        if (typeExprList.size() == 0) {
            return;
        }
        sb.append("<");
        commaSeparatedList(typeExprList, spacer, sb, indent);
        sb.append(">");
    }

    public static void prettyPrint(TypeExprResolved e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(TypeExprSimple e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(e.getTypeName());
        e.getTypeArgs().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(TypeExprThis e, Spacer spacer, StringBuilder sb, int indent) {
        if (!(e.getScopeType() instanceof NoTypeExpr)) {
            e.getScopeType().prettyPrint(spacer, sb, indent);
            sb.append(".");
        }
        sb.append("thistype");
    }

    public static void prettyPrint(TypeParamDef e, Spacer spacer, StringBuilder sb, int indent) {
        printComment(sb, e, indent);
        e.getNameId().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(TypeParamDefs e, Spacer spacer, StringBuilder sb, int indent) {
        if (e.size() >= 1) {
            sb.append("<");
            commaSeparatedList(e, spacer, sb, indent);
            sb.append(">");
        }
    }

    public static void prettyPrint(VisibilityDefault e, Spacer spacer, StringBuilder sb, int indent) {
    }

    public static void prettyPrint(VisibilityPrivate e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("private");
    }

    public static void prettyPrint(VisibilityProtected e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("protected");
    }

    public static void prettyPrint(VisibilityPublic e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("public");
    }

    public static void prettyPrint(VisibilityPublicread e, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("publicread");
    }

    public static void prettyPrint(WBlock wBlock, Spacer spacer, StringBuilder sb, int indent) {
        // TODO Auto-generated method stub
        throw new Error("not implemented");
    }

    public static void prettyPrint(WEntities wEntities, Spacer spacer, StringBuilder sb, int indent) {
        for (WEntity entity : wEntities) {
            entity.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(WImport wImport, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("import");
        spacer.addSpace(sb);
        sb.append(wImport.getIsInitLater() ? "initlater" : "");
        spacer.addSpace(sb);
        sb.append(wImport.getIsPublic() ? "public" : "");
        spacer.addSpace(sb);
        sb.append(wImport.getPackagename());
        sb.append("\n");
    }

    public static void prettyPrint(WurstModel wurstModel, Spacer spacer, StringBuilder sb, int indent) {
        for (CompilationUnit compilationUnit : wurstModel) {
            compilationUnit.prettyPrint(spacer, sb, indent);
        }
    }

    private static String spaces(int indentation) {
        return " "+"{"+indentation+"}";
    }

    public static void prettyPrint(WurstDoc wurstDoc, Spacer spacer, StringBuilder sb, int indent) {
        printIndent(sb, indent);
        sb.append(wurstDoc.getRawComment());
        sb.append("\n");
    }

    public static void prettyPrint(WStatements wStatements, Spacer spacer, StringBuilder sb, int indent) {
        for (WStatement wStatement : wStatements) {
            wStatement.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(WParameters wParameters, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("(");
        String prefix = "";
        for (WParameter wParameter : wParameters) {
            sb.append(prefix);
            prefix = ",";
            spacer.addSpace(sb);
            wParameter.prettyPrint(spacer, sb, indent);
        }
        sb.append(")");
    }

    public static void prettyPrint(WShortParameters wParameters, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("(");
        String prefix = "";
        for (WShortParameter wParameter : wParameters) {
            sb.append(prefix);
            prefix = ",";
            spacer.addSpace(sb);
            wParameter.prettyPrint(spacer, sb, indent);
        }
        sb.append(")");
    }

    public static void prettyPrint(WParameter wParameter, Spacer spacer, StringBuilder sb, int indent) {
        wParameter.getTyp().prettyPrint(spacer, sb, indent);
        spacer.addSpace(sb);
        sb.append(wParameter.getName());
    }

    public static void prettyPrint(WShortParameter wParameter, Spacer spacer, StringBuilder sb, int indent) {
        OptTypeExpr t = wParameter.getTypOpt();
        if (t instanceof TypeExpr) {
            t.prettyPrint(spacer, sb, indent);
            spacer.addSpace(sb);
        }
        sb.append(wParameter.getName());
    }

    public static void prettyPrint(WPackages wPackages, Spacer spacer, StringBuilder sb, int indent) {
        for (WPackage wPackage : wPackages) {
            wPackage.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(WPackage wPackage, Spacer spacer, StringBuilder sb, int indent) {
        printComment(sb, wPackage, indent);
        sb.append("package");
        spacer.addSpace(sb);
        sb.append(wPackage.getName());
        sb.append("\n\n");
        wPackage.getImports().prettyPrint(spacer, sb, indent);
        wPackage.getElements().prettyPrint(spacer, sb, indent);
    }

    public static void prettyPrint(WImports wImports, Spacer spacer, StringBuilder sb, int indent) {
        if (wImports.size() <= 0) {
            return;
        }
        for (WImport wImport : wImports) {
            if (!wImport.getPackagename().equals("Wurst")) {
                wImport.prettyPrint(spacer, sb, indent);
            }
        }
        sb.append("\n");
    }

    public static void prettyPrint(ClassDefs classDefs, Spacer spacer, StringBuilder sb, int indent) {
        for (ClassDef classDef : classDefs) {
            classDef.prettyPrint(spacer, sb, indent);
        }
    }

    public static void prettyPrint(Identifier identifier, Spacer spacer, StringBuilder sb, int indent) {
        sb.append(identifier.getName());
    }

    public static void prettyPrint(ExprIfElse e, Spacer spacer, StringBuilder sb, int indent) {
        e.getCond().prettyPrint(spacer, sb, indent + 1);
        spacer.addSpace(sb);
        sb.append("?");
        spacer.addSpace(sb);
        e.getIfTrue().prettyPrint(spacer, sb, indent + 1);
        spacer.addSpace(sb);
        sb.append(":");
        spacer.addSpace(sb);
        e.getIfFalse().prettyPrint(spacer, sb, indent + 1);
    }

    public static void prettyPrint(ArrayInitializer arrayInitializer, Spacer spacer, StringBuilder sb, int indent) {
        sb.append("[");
        arrayInitializer.getValues().prettyPrint(spacer, sb, indent);
        sb.append("]");
    }
}
