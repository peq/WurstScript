package de.peeeq.wurstscript.translation.lua.translation;

import de.peeeq.datastructures.UnionFind;
import de.peeeq.wurstio.TimeTaker;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.luaAst.*;
import de.peeeq.wurstscript.translation.imoptimizer.ImOptimizer;
import de.peeeq.wurstscript.translation.imtranslation.FunctionFlagEnum;
import de.peeeq.wurstscript.translation.imtranslation.GetAForB;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.translation.imtranslation.NormalizeNames;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LuaTranslator {

    final ImProg prog;
    final LuaCompilationUnit luaModel;
    private final Set<String> usedNames = new HashSet<>(Arrays.asList(
        // reserved function names
        "print", "tostring",
        // keywords:
        "and",
        "break",
        "do",
        "else",
        "elseif",
        "end",
        "false",
        "for",
        "function",
        "if",
        "in",
        "local",
        "nil",
        "not",
        "or",
        "repeat",
        "return",
        "then",
        "true",
        "until",
        "while"
    ));

    List<ExprTranslation.TupleFunc> tupleEqualsFuncs = new ArrayList<>();
    List<ExprTranslation.TupleFunc> tupleCopyFuncs = new ArrayList<>();

    GetAForB<ImVar, LuaVariable> luaVar = new GetAForB<ImVar, LuaVariable>() {
        @Override
        public LuaVariable initFor(ImVar a) {
            return LuaAst.LuaVariable(uniqueName(a.getName()), LuaAst.LuaNoExpr());
        }
    };

    GetAForB<ImFunction, LuaFunction> luaFunc = new GetAForB<ImFunction, LuaFunction>() {

        @Override
        public LuaFunction initFor(ImFunction a) {
            return LuaAst.LuaFunction(uniqueName(a.getName()), LuaAst.LuaParams(), LuaAst.LuaStatements());
        }
    };
    public GetAForB<ImMethod, LuaMethod> luaMethod = new GetAForB<ImMethod, LuaMethod>() {

        @Override
        public LuaMethod initFor(ImMethod a) {
            LuaExpr receiver = LuaAst.LuaExprVarAccess(luaClassVar.getFor(a.attrClass()));
            return LuaAst.LuaMethod(receiver, a.getName(), LuaAst.LuaParams(), LuaAst.LuaStatements());
        }
    };


    GetAForB<ImClass, LuaVariable> luaClassVar = new GetAForB<ImClass, LuaVariable>() {
        @Override
        public LuaVariable initFor(ImClass a) {
            return LuaAst.LuaVariable(uniqueName(a.getName()), LuaAst.LuaNoExpr());
        }
    };

    GetAForB<ImClass, LuaVariable> luaClassMetaTableVar = new GetAForB<ImClass, LuaVariable>() {
        @Override
        public LuaVariable initFor(ImClass a) {
            return LuaAst.LuaVariable(uniqueName(a.getName() + "_mt"), LuaAst.LuaNoExpr());
        }
    };

    GetAForB<ImClass, LuaMethod> luaClassInitMethod = new GetAForB<ImClass, LuaMethod>() {
        @Override
        public LuaMethod initFor(ImClass a) {
            LuaExprVarAccess receiver = LuaAst.LuaExprVarAccess(luaClassVar.getFor(a));
            return LuaAst.LuaMethod(receiver, uniqueName("create"), LuaAst.LuaParams(), LuaAst.LuaStatements());
        }
    };

    LuaFunction arrayInitFunction = LuaAst.LuaFunction(uniqueName("defaultArray"), LuaAst.LuaParams(), LuaAst.LuaStatements());

    LuaFunction stringConcatFunction = LuaAst.LuaFunction(uniqueName("stringConcat"), LuaAst.LuaParams(), LuaAst.LuaStatements());
    private final ImTranslator imTr;

    public LuaTranslator(ImProg prog, ImTranslator imTr) {
        this.prog = prog;
        this.imTr = imTr;
        luaModel = LuaAst.LuaCompilationUnit();
    }

    protected String uniqueName(String name) {
        int i = 0;
        String rname = name;
        while (usedNames.contains(rname)) {
            rname = name + ++i;
        }
        usedNames.add(rname);
        return rname;
    }

    public LuaCompilationUnit translate() {
        prog.flatten(imTr);

        normalizeMethodNames();

//        NormalizeNames.normalizeNames(prog);

        createArrayInitFunction();
        createStringConcatFunction();

        for (ImVar v : prog.getGlobals()) {
            translateGlobal(v);
        }

        // first add class variables
        for (ImClass c : prog.getClasses()) {
            LuaVariable classVar = luaClassVar.getFor(c);
            luaModel.add(classVar);
        }

        for (ImClass c : prog.getClasses()) {
            translateClass(c);
        }

        for (ImFunction f : prog.getFunctions()) {
            translateFunc(f);
        }

        for (ImClass c : prog.getClasses()) {
            initClassTables(c);
        }

        cleanStatements();

        return luaModel;
    }

    private void normalizeMethodNames() {
        // group related methods
        UnionFind<ImMethod> methodUnions = new UnionFind<>();
        for (ImClass c : prog.getClasses()) {
            for (ImMethod m : c.getMethods()) {
                methodUnions.find(m);
                for (ImMethod subMethod : m.getSubMethods()) {
                    methodUnions.union(m, subMethod);
                }
            }
        }

        // give all related methods the same name
        for (Map.Entry<ImMethod, Set<ImMethod>> entry : methodUnions.groups().entrySet()) {
            String name = uniqueName(entry.getKey().getName());
            for (ImMethod method : entry.getValue()) {
                method.setName(name);
            }
        }
    }

    private void createStringConcatFunction() {
        String[] code = {
            "if x then",
            "    if y then return x .. y else return x end",
            "else",
            "    return y",
            "end"
        };

        stringConcatFunction.getParams().add(LuaAst.LuaVariable("x", LuaAst.LuaNoExpr()));
        stringConcatFunction.getParams().add(LuaAst.LuaVariable("y", LuaAst.LuaNoExpr()));
        for (String c : code) {
            stringConcatFunction.getBody().add(LuaAst.LuaLiteral(c));
        }
        luaModel.add(stringConcatFunction);
    }

    private void createArrayInitFunction() {
        /*
        function defaultArray(d)
            local t = {}
            local mt = {__index = function (table, key)
                local v = d()
                table[key] = v
                return v
            end}
            setmetatable(t, mt)
            return t
        end
         */
        String[] code = {
            "local t = {}",
            "local mt = {__index = function (table, key)",
            "    local v = d()",
            "    table[key] = v",
            "    return v",
            "end}",
            "setmetatable(t, mt)",
            "return t"
        };

        arrayInitFunction.getParams().add(LuaAst.LuaVariable("d", LuaAst.LuaNoExpr()));
        for (String c : code) {
            arrayInitFunction.getBody().add(LuaAst.LuaLiteral(c));
        }
        luaModel.add(arrayInitFunction);
    }

    private void cleanStatements() {
        luaModel.accept(new LuaModel.DefaultVisitor() {
            @Override
            public void visit(LuaStatements stmts) {
                super.visit(stmts);
                cleanStatements(stmts);
            }

        });
    }

    private void cleanStatements(LuaStatements stmts) {
        ListIterator<LuaStatement> it = stmts.listIterator();
        while (it.hasNext()) {
            LuaStatement s = it.next();
            if (s instanceof LuaExprNull) {
                it.remove();
            } else if (s instanceof LuaExpr) {
                LuaExpr e = (LuaExpr) s;
                if (!(e instanceof LuaCallExpr || e instanceof LuaLiteral) || e instanceof LuaExprFunctionCallE) {
                    e.setParent(null);
                    LuaVariable exprTemp = LuaAst.LuaVariable("wurstExpr", e);
                    it.set(exprTemp);
                }
            }
        }
    }

    private void translateFunc(ImFunction f) {
        LuaFunction lf = luaFunc.getFor(f);
        if (f.isNative()) {
            LuaNatives.get(lf);
        } else {


            // translate parameters
            for (ImVar p : f.getParameters()) {
                LuaVariable pv = luaVar.getFor(p);
                lf.getParams().add(pv);
            }

            if (f.hasFlag(FunctionFlagEnum.IS_VARARG)) {
                LuaVariable lastParam = luaVar.getFor(Utils.getLast(f.getParameters()));
                lastParam.setName("...");
            }

            // translate local variables
            for (ImVar local : f.getLocals()) {
                LuaVariable luaLocal = luaVar.getFor(local);
                luaLocal.setInitialValue(defaultValue(local.getType()));
                lf.getBody().add(luaLocal);
            }

            // translate body:
            translateStatements(lf.getBody(), f.getBody());
        }

        if (f.isExtern()) {
            // only add the function if it is not yet defined:
            String name = lf.getName();
            luaModel.add(LuaAst.LuaIf(
                LuaAst.LuaExprFuncRef(lf),
                LuaAst.LuaStatements(),
                LuaAst.LuaStatements(
                    LuaAst.LuaAssignment(LuaAst.LuaLiteral(name), LuaAst.LuaExprFunctionAbstraction(
                        lf.getParams().copy(),
                        lf.getBody().copy()
                    ))
                )
            ));
        } else {
            luaModel.add(lf);
        }
    }

    void translateStatements(List<LuaStatement> res, ImStmts stmts) {
        for (ImStmt s : stmts) {
            s.translateStmtToLua(res, this);
        }
    }

    public LuaStatements translateStatements(ImStmts stmts) {
        LuaStatements r = LuaAst.LuaStatements();
        translateStatements(r, stmts);
        return r;
    }


    private void translateClass(ImClass c) {

        // following the code at http://lua-users.org/wiki/InheritanceTutorial
        LuaVariable classVar = luaClassVar.getFor(c);
        LuaMethod initMethod = luaClassInitMethod.getFor(c);

        luaModel.add(initMethod);

        classVar.setInitialValue(emptyTable());

        // translate functions
        for (ImFunction f : c.getFunctions()) {
            translateFunc(f);
            luaFunc.getFor(f).setName(uniqueName(c.getName() + "_" + f.getName()));
        }

        createClassInitFunction(c, classVar, initMethod);
    }

    private void createClassInitFunction(ImClass c, LuaVariable classVar, LuaMethod initMethod) {
        // create init function:
        LuaStatements body = initMethod.getBody();
        // local new_inst = { ... }
        LuaTableFields initialFieldValues = LuaAst.LuaTableFields();
        LuaVariable newInst = LuaAst.LuaVariable("new_inst", LuaAst.LuaTableConstructor(initialFieldValues));
        for (ImVar field : c.getFields()) {
            initialFieldValues.add(
                LuaAst.LuaTableNamedField(field.getName(), defaultValue(field.getType()))
            );
        }


        body.add(newInst);
        // setmetatable(new_inst, {__index = classVar})
        body.add(LuaAst.LuaExprFunctionCallByName("setmetatable", LuaAst.LuaExprlist(
            LuaAst.LuaExprVarAccess(newInst),
            LuaAst.LuaTableConstructor(LuaAst.LuaTableFields(
                LuaAst.LuaTableNamedField("__index", LuaAst.LuaExprVarAccess(classVar))
            ))
        )));
        body.add(LuaAst.LuaReturn(LuaAst.LuaExprVarAccess(newInst)));
    }

    private void initClassTables(ImClass c) {
        LuaVariable classVar = luaClassVar.getFor(c);
        // create methods:
        Set<String> methods = new HashSet<>();
        createMethods(c, classVar, methods);

        // set supertype metadata:
        LuaTableFields superClasses = LuaAst.LuaTableFields();
        collectSuperClasses(superClasses, c, new HashSet<>());
        luaModel.add(LuaAst.LuaAssignment(LuaAst.LuaExprFieldAccess(
            LuaAst.LuaExprVarAccess(classVar),
            ExprTranslation.WURST_SUPERTYPES),
            LuaAst.LuaTableConstructor(superClasses)
        ));

        // set typeid metadata:
        luaModel.add(LuaAst.LuaAssignment(LuaAst.LuaExprFieldAccess(
            LuaAst.LuaExprVarAccess(classVar),
            ExprTranslation.TYPE_ID),
            LuaAst.LuaExprIntVal("" + prog.attrTypeId().get(c))
        ));


    }

    private void createMethods(ImClass c, LuaVariable classVar, Set<String> methods) {
        for (ImMethod method : c.getMethods()) {
            if (methods.contains(method.getName())) {
                continue;
            }
            methods.add(method.getName());
            if (method.getIsAbstract()) {
                continue;
            }
            luaModel.add(LuaAst.LuaAssignment(LuaAst.LuaExprFieldAccess(
                LuaAst.LuaExprVarAccess(classVar),
                method.getName()),
                LuaAst.LuaExprFuncRef(luaFunc.getFor(method.getImplementation()))
            ));
        }
        // also create links for inherited methods
        for (ImClassType sc : c.getSuperClasses()) {
            createMethods(sc.getClassDef(), classVar, methods);
        }
    }

    @NotNull
    private LuaTableConstructor emptyTable() {
        return LuaAst.LuaTableConstructor(LuaAst.LuaTableFields());
    }

    private void collectSuperClasses(LuaTableFields superClasses, ImClass c, Set<ImClass> visited) {
        if (visited.contains(c)) {
            return;
        }
        superClasses.add(LuaAst.LuaTableExprField(LuaAst.LuaExprVarAccess(luaClassVar.getFor(c)), LuaAst.LuaExprBoolVal(true)));
        visited.add(c);
        for (ImClassType sc : c.getSuperClasses()) {
            collectSuperClasses(superClasses, sc.getClassDef(), visited);
        }
    }


    private void translateGlobal(ImVar v) {
        LuaVariable lv = luaVar.getFor(v);
        lv.setInitialValue(defaultValue(v.getType()));
        luaModel.add(lv);
    }

    private LuaExpr defaultValue(ImType type) {
        return type.match(new ImType.Matcher<LuaExpr>() {
            @Override
            public LuaExpr case_ImTupleType(ImTupleType tt) {
                LuaTableFields tableFields = LuaAst.LuaTableFields();
                for (int i = 0; i < tt.getNames().size(); i++) {
                    tableFields.add(LuaAst.LuaTableSingleField(defaultValue(tt.getTypes().get(i))));
                }
                return LuaAst.LuaTableConstructor(
                    tableFields
                );
            }

            @Override
            public LuaExpr case_ImVoid(ImVoid imVoid) {
                return LuaAst.LuaExprNull();
            }

            @Override
            public LuaExpr case_ImClassType(ImClassType imClassType) {
                return LuaAst.LuaExprNull();
            }

            @Override
            public LuaExpr case_ImArrayTypeMulti(ImArrayTypeMulti at) {
                ImType baseType;
                if (at.getArraySize().size() <= 1) {
                    baseType = at.getEntryType();
                } else {
                    List<Integer> arraySizes = new ArrayList<>(at.getArraySize());
                    arraySizes.remove(0);
                    baseType = JassIm.ImArrayTypeMulti(at.getEntryType(), arraySizes);
                }
                return LuaAst.LuaExprFunctionCall(arrayInitFunction,
                    LuaAst.LuaExprlist(
                        LuaAst.LuaExprFunctionAbstraction(LuaAst.LuaParams(),
                            LuaAst.LuaStatements(
                                LuaAst.LuaReturn(defaultValue(baseType))
                            )
                        )
                    ));
            }

            @Override
            public LuaExpr case_ImSimpleType(ImSimpleType st) {
                if (TypesHelper.isIntType(st)) {
                    return LuaAst.LuaExprIntVal("0");
                } else if (TypesHelper.isBoolType(st)) {
                    return LuaAst.LuaExprBoolVal(false);
                } else if (TypesHelper.isRealType(st)) {
                    return LuaAst.LuaExprRealVal("0.");
                }
                return LuaAst.LuaExprNull();
            }

            @Override
            public LuaExpr case_ImArrayType(ImArrayType imArrayType) {
                return emptyTable();
            }

            @Override
            public LuaExpr case_ImTypeVarRef(ImTypeVarRef imTypeVarRef) {
                return LuaAst.LuaExprNull();
            }
        });
    }

    public LuaExprOpt translateOptional(ImExprOpt e) {
        if (e instanceof ImExpr) {
            ImExpr imExpr = (ImExpr) e;
            return imExpr.translateToLua(this);
        }
        return LuaAst.LuaNoExpr();
    }

    public LuaExprlist translateExprList(ImExprs exprs) {
        LuaExprlist r = LuaAst.LuaExprlist();
        for (ImExpr e : exprs) {
            r.add(e.translateToLua(this));
        }
        return r;
    }


    public int getTypeId(ImClass classDef) {
        return prog.attrTypeId().get(classDef);
    }
}
