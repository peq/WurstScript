package de.peeeq.wurstscript.attributes;

import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.attributes.names.NameLink;
import org.eclipse.jdt.annotation.Nullable;

public class AttrImplicitParameter {

    public static OptExpr getImplicitParameter(ExprMemberVar e) {
        Expr result = getImplicitParameterUsingLeft(e);
        if (result == null) {
            return getImplicitParamterCaseNormalVar(e);
        } else {
            return result;
        }
    }

    public static OptExpr getImplicitParameter(ExprMemberArrayVar e) {
        Expr result = getImplicitParameterUsingLeft(e);
        if (result == null) {
            return getImplicitParamterCaseNormalVar(e);
        } else {
            return result;
        }
    }

    public static OptExpr getImplicitParameter(ExprVarAccess e) {
        return getImplicitParamterCaseNormalVar(e);
    }

    public static OptExpr getImplicitParameter(ExprVarArrayAccess e) {
        return getImplicitParamterCaseNormalVar(e);
    }


    public static OptExpr getImplicitParameter(ExprFunctionCall e) {
        return getImplicitParamterCaseNormalFunctionCall(e);
    }

    public static OptExpr getImplicitParameter(ExprMemberMethod e) {
        Expr result = getImplicitParameterUsingLeft(e);
        if (result == null) {
            return getImplicitParamterCaseNormalFunctionCall(e);
        } else {
            return result;
        }
    }

    private static @Nullable Expr getImplicitParameterUsingLeft(HasReceiver e) {
        if (e.getLeft().attrTyp().isStaticRef()) {
            // we have a static ref like Math.sqrt()
            // this will be handled like if we just have sqrt()
            // if we have an implicit parameter depends on whether sqrt is static or not
            return null;
        }
        return e.getLeft();
    }

    private static OptExpr getImplicitParamterCaseNormalFunctionCall(FunctionCall e) {
        FuncLink calledFunc = e.attrFuncDef();
        if (calledFunc == null) {
            return Ast.NoExpr();
        }
        if (calledFunc.getDef().attrIsDynamicClassMember()) {
            // dynamic function call
            if (e.attrIsDynamicContext()) {
                // dynamic context means we have a 'this':
                ExprThis t = Ast.ExprThis(e.getSource());
                t.setParent(e);
                // check if 'this' has correct type
                if (!t.attrTyp().isSubtypeOf(calledFunc.getReceiverType(), e)) {
                    e.addError("Cannot access dynamic function " + e.getFuncName() + " from context of type " +
                            t.attrTyp() + ".");
                }
                return t;
            } else {
                e.addError("Cannot call dynamic function " + e.getFuncName() + " from static context.");
                return Ast.NoExpr();
            }
        } else {
            // static function:
            return Ast.NoExpr();
        }
    }

    private static OptExpr getImplicitParamterCaseNormalVar(NameRef e) {
        NameLink def = e.attrNameDef();
        if (def != null && def.getDef() instanceof VarDef) {
            VarDef varDef = (VarDef) def.getDef();
            if (varDef.attrIsDynamicClassMember()) {
                // dynamic var access
                if (e.attrIsDynamicContext()) {
                    // dynamic context means we have a 'this':
                    ExprThis t = Ast.ExprThis(e.getSource());
                    t.setParent(e);
                    // check if 'this' has correct type
                    if (!t.attrTyp().isSubtypeOf(varDef.attrNearestStructureDef().attrTyp(), e)) {
                        e.addError("Cannot access dynamic variable " + varDef.getName() + " from context of type " +
                                t.attrTyp() + ".");
                    }
                    return t;
                } else {
                    e.addError("Cannot access dynamic variable " + varDef.getName() + " from static context.");
                    return Ast.NoExpr();
                }
            }
        }
        return Ast.NoExpr();
    }


}
