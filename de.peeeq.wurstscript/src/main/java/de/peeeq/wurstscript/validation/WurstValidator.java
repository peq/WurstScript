package de.peeeq.wurstscript.validation;

import com.google.common.collect.*;
import de.peeeq.wurstio.utils.FileUtils;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.CofigOverridePackages;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.attributes.ImplicitFuncs;
import de.peeeq.wurstscript.attributes.names.DefLink;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.attributes.names.VarLink;
import de.peeeq.wurstscript.gui.ProgressHelper;
import de.peeeq.wurstscript.types.*;
import de.peeeq.wurstscript.utils.Utils;
import de.peeeq.wurstscript.validation.controlflow.DataflowAnomalyAnalysis;
import de.peeeq.wurstscript.validation.controlflow.ReturnsAnalysis;
import io.vavr.Tuple2;
import org.eclipse.jdt.annotation.Nullable;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static de.peeeq.wurstscript.attributes.SmallHelpers.superArgs;

/**
 * this class validates a wurstscript program
 * <p>
 * it has visit methods for different elements in the AST and checks whether
 * these are correct
 * <p>
 * the validation phase might not find all errors, code transformation and
 * optimization phases might detect other errors because they do a more
 * sophisticated analysis of the program
 * <p>
 * also note that many cases are already caught by the calculation of the
 * attributes
 */
public class WurstValidator {

    private WurstModel prog;
    private int functionCount;
    private int visitedFunctions;
    private Multimap<WScope, WScope> calledFunctions = HashMultimap.create();
    private @Nullable Element lastElement = null;
    private HashSet<String> trveWrapperFuncs = new HashSet<>();
    private HashMap<String, HashSet<FunctionCall>> wrapperCalls = new HashMap<>();

    public WurstValidator(WurstModel root) {
        this.prog = root;
    }

    public void validate(List<CompilationUnit> toCheck) {
        try {
            functionCount = countFunctions();
            visitedFunctions = 0;

            prog.getErrorHandler().setProgress("Checking wurst types",
                    ProgressHelper.getValidatorPercent(visitedFunctions, functionCount));
            for (CompilationUnit cu : toCheck) {
                walkTree(cu);
            }
            prog.getErrorHandler().setProgress("Post checks", 0.55);
            postChecks(toCheck);
        } catch (RuntimeException e) {
            WLogger.severe(e);
            Element le = lastElement;
            if (le != null) {
                le.addError("Encountered compiler bug near element " + Utils.printElement(le) + ":\n"
                        + Utils.printException(e));
            } else {
                // rethrow
                throw e;
            }
        }
    }

    /**
     * checks done after walking the tree
     */
    private void postChecks(List<CompilationUnit> toCheck) {
        checkUnusedImports(toCheck);
        ValidateGlobalsUsage.checkGlobalsUsage(toCheck);
        ValidateClassMemberUsage.checkClassMembers(toCheck);
        ValidateLocalUsage.checkLocalsUsage(toCheck);

        trveWrapperFuncs.forEach(wrapper -> {
            if (wrapperCalls.containsKey(wrapper)) {
                wrapperCalls.get(wrapper).forEach(call -> {
                    if (call.getArgs().size() > 1 && call.getArgs().get(1) instanceof ExprStringVal) {
                        ExprStringVal varName = (ExprStringVal) call.getArgs().get(1);
                        TRVEHelper.protectedVariables.add(varName.getValS());
                        WLogger.info("keep: " + varName.getValS());
                    } else {
                        call.addError("Map contains TriggerRegisterVariableEvent with non-constant arguments. Can't be optimized.");
                    }
                });
            }
        });
    }

    private void checkUnusedImports(List<CompilationUnit> toCheck) {
        for (CompilationUnit cu : toCheck) {
            for (WPackage p : cu.getPackages()) {
                checkUnusedImports(p);
            }
        }
    }

    private void checkUnusedImports(WPackage p) {
        Set<PackageOrGlobal> used = Sets.newLinkedHashSet();

        collectUsedPackages(used, p.getElements());

        // String usedToStr =
        // used.stream().map(Utils::printElement).sorted().collect(Collectors.joining(",
        // "));
        // System.out.println("used = " + usedToStr);

        // contributed packages for each import
        Map<WImport, Set<WPackage>> contributions = new HashMap<>();
        for (WImport imp : p.getImports()) {
            Set<WPackage> contributedPackages = contributedPackages(imp.attrImportedPackage(), used, new HashSet<>());
            contributions.put(imp, contributedPackages);
            // System.out.println( imp.getPackagename() + " contributes = " +
            // contributedPackages.stream().map(Utils::printElement).sorted().collect(Collectors.joining(",
            // ")));
        }

        // check for imports, which only contribute a subset of some other
        // import
        for (WImport imp : p.getImports()) {
            if (imp.attrImportedPackage() == null || imp.getIsPublic() || imp.getPackagename().equals("Wurst")) {
                continue;
            }
            Set<WPackage> impContributions = contributions.get(imp);
            if (impContributions.isEmpty()) {
                imp.addWarning("The import " + imp.getPackagename() + " is never used");
            } else {
                for (WImport imp2 : p.getImports()) {
                    if (imp == imp2) {
                        continue;
                    }
                    if (contributions.get(imp2).containsAll(impContributions)) {
                        imp.addWarning("The import " + imp.getPackagename()
                                + " can be removed, because it is already included in " + imp2.getPackagename() + ".");
                        break;
                    }

                }
            }
        }
    }

    private Set<WPackage> contributedPackages(WPackage p, Set<PackageOrGlobal> used, Set<WPackage> visited) {
        if (p == null) {
            return Collections.emptySet();
        }
        visited.add(p);
        Set<WPackage> result = new HashSet<>();
        if (used.contains(p)) {
            result.add(p);
        }
        for (WImport imp : p.getImports()) {
            WPackage imported = imp.attrImportedPackage();
            if (imp.getPackagename().equals("Wurst") || visited.contains(imported)) {
                continue;
            }
            if (imp.getIsPublic()) {
                result.addAll(contributedPackages(imported, used, visited));
            }
        }
        return result;
    }

    private void collectUsedPackages(Set<PackageOrGlobal> used, Element e) {
        for (int i = 0; i < e.size(); i++) {
            collectUsedPackages(used, e.get(i));
        }

        if (e instanceof FuncRef) {
            FuncRef fr = (FuncRef) e;
            FuncLink link = fr.attrFuncLink();
            if (link != null) {
                used.add(link.getDef().attrNearestPackage());
            }
        }
        if (e instanceof NameRef) {
            NameRef nr = (NameRef) e;
            NameLink def = nr.attrNameLink();
            if (def != null) {
                used.add(def.getDef().attrNearestPackage());
            }
        }
        if (e instanceof TypeRef) {
            TypeRef t = (TypeRef) e;
            TypeDef def = t.attrTypeDef();
            if (def != null) {
                used.add(def.attrNearestPackage());
            }
        }
        if (e instanceof ExprBinary) {
            ExprBinary binop = (ExprBinary) e;
            FuncLink def = binop.attrFuncLink();
            if (def != null) {
                used.add(def.getDef().attrNearestPackage());
            }
        }
        if (e instanceof Expr) {
            WurstType typ = ((Expr) e).attrTyp();
            if (typ instanceof WurstTypeNamedScope) {
                WurstTypeNamedScope ns = (WurstTypeNamedScope) typ;
                NamedScope def = ns.getDef();
                if (def != null) {
                    used.add(def.attrNearestPackage());
                }
            } else if (typ instanceof WurstTypeTuple) {
                TupleDef def = ((WurstTypeTuple) typ).getTupleDef();
                used.add(def.attrNearestPackage());
            }
        }
    }

    private void walkTree(Element e) {
        lastElement = e;
        check(e);
        lastElement = null;
        for (int i = 0; i < e.size(); i++) {
            walkTree(e.get(i));
        }
    }

    private void check(Element e) {
        try {
            if (e instanceof Annotation)
                checkAnnotation((Annotation) e);
            if (e instanceof AstElementWithTypeParameters)
                checkTypeParameters((AstElementWithTypeParameters) e);
            if (e instanceof AstElementWithNameId)
                checkName((AstElementWithNameId) e);
            if (e instanceof ClassDef) {
                checkAbstractMethods((ClassDef) e);
                visit((ClassDef) e);
            }
            if (e instanceof ClassOrModule)
                checkConstructorsUnique((ClassOrModule) e);
            if (e instanceof CompilationUnit)
                checkPackageName((CompilationUnit) e);
            if (e instanceof ConstructorDef)
                checkConstructor((ConstructorDef) e);
            if (e instanceof ConstructorDef)
                checkConstructorSuperCall((ConstructorDef) e);
            if (e instanceof ExprBinary)
                visit((ExprBinary) e);
            if (e instanceof ExprClosure)
                checkClosure((ExprClosure) e);
            if (e instanceof ExprEmpty)
                checkExprEmpty((ExprEmpty) e);
            if (e instanceof ExprIntVal)
                checkIntVal((ExprIntVal) e);
            if (e instanceof ExprFuncRef)
                checkFuncRef((ExprFuncRef) e);
            if (e instanceof ExprFunctionCall)
                checkBannedFunctions((ExprFunctionCall) e);
            if (e instanceof ExprFunctionCall)
                visit((ExprFunctionCall) e);
            if (e instanceof ExprMemberMethod)
                visit((ExprMemberMethod) e);
            if (e instanceof ExprMemberVar)
                checkMemberVar((ExprMemberVar) e);
            if (e instanceof ExprMemberArrayVar)
                checkMemberArrayVar((ExprMemberArrayVar) e);
            if (e instanceof ExprNewObject)
                checkNewObj((ExprNewObject) e);
            if (e instanceof ExprNewObject)
                visit((ExprNewObject) e);
            if (e instanceof ExprNull)
                checkExprNull((ExprNull) e);
            if (e instanceof ExprVarAccess)
                visit((ExprVarAccess) e);
            if (e instanceof ExprVarArrayAccess)
                checkArrayAccess((ExprVarArrayAccess) e);
            if (e instanceof ExtensionFuncDef)
                visit((ExtensionFuncDef) e);
            if (e instanceof FuncDef)
                visit((FuncDef) e);
            if (e instanceof FuncRef)
                checkFuncRef((FuncRef) e);
            if (e instanceof FunctionLike)
                checkUninitializedVars((FunctionLike) e);
            if (e instanceof GlobalVarDef)
                visit((GlobalVarDef) e);
            if (e instanceof HasModifier)
                checkModifiers((HasModifier) e);
            if (e instanceof HasTypeArgs)
                checkTypeBinding((HasTypeArgs) e);
            if (e instanceof InterfaceDef)
                checkInterfaceDef((InterfaceDef) e);
            if (e instanceof LocalVarDef)
                checkLocalShadowing((LocalVarDef) e);
            if (e instanceof LocalVarDef)
                visit((LocalVarDef) e);
            if (e instanceof Modifiers)
                visit((Modifiers) e);
            if (e instanceof ModuleDef)
                visit((ModuleDef) e);
            if (e instanceof NameDef)
                nameDefsMustNotBeNamedAfterJassNativeTypes((NameDef) e);
            if (e instanceof NameDef)
                checkConfigOverride((NameDef) e);
            if (e instanceof NameRef)
                checkImplicitParameter((NameRef) e);
            if (e instanceof NameRef)
                checkNameRef((NameRef) e);
            if (e instanceof StmtCall)
                checkCall((StmtCall) e);
            if (e instanceof ExprDestroy)
                visit((ExprDestroy) e);
            if (e instanceof StmtForRange)
                checkForRange((StmtForRange) e);
            if (e instanceof StmtIf)
                visit((StmtIf) e);
            if (e instanceof StmtReturn)
                visit((StmtReturn) e);
            if (e instanceof StmtSet)
                checkStmtSet((StmtSet) e);
            if (e instanceof StmtWhile)
                visit((StmtWhile) e);
            if (e instanceof SwitchStmt)
                checkSwitch((SwitchStmt) e);
            if (e instanceof TypeExpr)
                checkTypeExpr((TypeExpr) e);
            if (e instanceof TypeExprArray)
                checkCodeArrays((TypeExprArray) e);
            if (e instanceof TupleDef)
                checkTupleDef((TupleDef) e);
            if (e instanceof VarDef)
                checkVarDef((VarDef) e);
            if (e instanceof WImport)
                visit((WImport) e);
            if (e instanceof WPackage)
                checkPackage((WPackage) e);
            if (e instanceof WParameter)
                checkParameter((WParameter) e);
            if (e instanceof WParameter)
                visit((WParameter) e);
            if (e instanceof WScope)
                checkForDuplicateNames((WScope) e);
            if (e instanceof WStatement)
                checkReachability((WStatement) e);
            if (e instanceof WurstModel)
                checkForDuplicatePackages((WurstModel) e);
            if (e instanceof WStatements)
                checkForInvalidStmts((WStatements) e);
            if (e instanceof StmtExitwhen)
                visit((StmtExitwhen) e);
        } catch (CyclicDependencyError cde) {
            cde.printStackTrace();
            Element element = cde.getElement();
            String attr = cde.getAttributeName().replaceFirst("^attr", "");
            WLogger.info(Utils.printElementWithSource(Optional.of(element))
                + " depends on itself when evaluating attribute "
                + attr);
            WLogger.info(cde);
            throw new CompileError(element.attrSource(),
                    Utils.printElement(element) + " depends on itself when evaluating attribute " + attr);
        }
    }

    private void checkAbstractMethods(ClassDef c) {
        ImmutableMultimap<String, DefLink> nameLinks = c.attrNameLinks();
        if (!c.attrIsAbstract()) {
            StringBuilder toImplement = new StringBuilder();
            // should have no abstract methods
            for (DefLink link : nameLinks.values()) {
                NameDef f = link.getDef();
                if (f.attrIsAbstract()) {
                    if (f.attrNearestStructureDef() == c) {
                        Element loc = f.getModifiers().stream()
                                .filter(m -> m instanceof ModAbstract)
                                .<Element>map(x -> x)
                                .findFirst()
                                .orElse(f);
                        loc.addError("Non-abstract class " + c.getName() + " cannot have abstract functions like " + f.getName());
                    } else if (link instanceof FuncLink) {
                        toImplement.append("\n    ");
                        toImplement.append(((FuncLink) link).printFunctionTemplate());
                    }
                }
            }
            if (toImplement.length() > 0) {
                c.addError("Non-abstract class " + c.getName() + " must implement the following functions:" + toImplement);
            }
        }
    }

    private void visit(StmtExitwhen exitwhen) {
        Element parent = exitwhen.getParent();
        while (!(parent instanceof FunctionDefinition)) {
            if (parent instanceof StmtForEach) {
                StmtForEach forEach = (StmtForEach) parent;
                if (forEach.getIn().tryGetNameDef().attrIsVararg()) {
                    exitwhen.addError("Cannot use break in vararg for each loops.");
                }
                return;
            } else if (parent instanceof LoopStatement) {
                return;
            }
            parent = parent.getParent();
        }
        exitwhen.addError("Break is not allowed outside of loop statements.");
    }

    private void checkTupleDef(TupleDef e) {
        checkTupleDefCycle(e, new ArrayList<>());

    }

    private boolean checkTupleDefCycle(TupleDef e, ArrayList<TupleDef> tuples) {
        if (tuples.contains(e)) {
            return true;
        }
        tuples.add(e);
        try {
            for (WParameter param : e.getParameters()) {
                WurstType t = param.getTyp().attrTyp();
                if (t instanceof WurstTypeTuple) {
                    WurstTypeTuple tt = (WurstTypeTuple) t;
                    TupleDef tDef = tt.getTupleDef();
                    if (checkTupleDefCycle(tDef, tuples)) {
                        param.addError("Parameter " + param.getName() + " is recursive. This is not allowed for tuples.");
                        return true;
                    }
                }
            }
            return false;
        } finally {
            tuples.remove(e);
        }
    }

    private void checkForInvalidStmts(WStatements stmts) {
        for (WStatement s : stmts) {
            if (s instanceof ExprVarAccess) {
                ExprVarAccess ev = (ExprVarAccess) s;
                s.addError("Use of variable " + ev.getVarName() + " is an incomplete statement.");
            }
        }
    }

    private void checkName(AstElementWithNameId e) {
        String name = e.getNameId().getName();
        TypeDef def = e.lookupType(name, false);

        if (def != e && def instanceof NativeType) {
            e.addError(
                    "The name '" + name + "' is already used as a native type in " + Utils.printPos(def.getSource()));
        } else if (!e.attrSource().getFile().endsWith(".j")) {
            switch (name) {
                case "int":
                case "integer":
                case "real":
                case "code":
                case "boolean":
                case "string":
                case "handle":
                    e.addError("The name '" + name + "' is a built-in type and cannot be used here.");
            }
        }
    }

    private void checkConfigOverride(NameDef e) {
        if (!e.hasAnnotation("@config")) {
            return;
        }
        PackageOrGlobal nearestPackage = e.attrNearestPackage();
        if (!(nearestPackage instanceof WPackage)) {
            e.addError("Annotation @config can only be used in packages.");
            return;
        }
        WPackage configPackage = (WPackage) nearestPackage;
        if (!configPackage.getName().endsWith(CofigOverridePackages.CONFIG_POSTFIX)) {
            e.addError(
                    "Annotation @config can only be used in config packages (package name has to end with '_config').");
            return;
        }

        WPackage origPackage = CofigOverridePackages.getOriginalPackage(configPackage);
        if (origPackage == null) {
            return;
        }

        if (e instanceof GlobalVarDef) {
            GlobalVarDef v = (GlobalVarDef) e;
            NameLink origVar = origPackage.getElements().lookupVarNoConfig(v.getName(), false);
            if (origVar == null) {
                e.addError("Could not find var " + v.getName() + " in configured package.");
                return;
            }

            if (!v.attrTyp().equalsType(origVar.getTyp(), v)) {
                e.addError("Configured variable must have type " + origVar.getTyp() + " but the found type is "
                        + v.attrTyp() + ".");
                return;
            }

            if (!origVar.getDef().hasAnnotation("@configurable")) {
                e.addWarning("The configured variable " + v.getName() + " is not marked with @configurable.\n"
                        + "It is still possible to configure this var but it is not recommended.");
            }

        } else if (e instanceof FuncDef) {
            FuncDef funcDef = (FuncDef) e;
            Collection<FuncLink> funcs = origPackage.getElements().lookupFuncsNoConfig(funcDef.getName(), false);
            FuncDef configuredFunc = null;
            for (NameLink nameLink : funcs) {
                if (nameLink.getDef() instanceof FuncDef) {
                    FuncDef f = (FuncDef) nameLink.getDef();
                    if (equalSignatures(funcDef, f)) {
                        configuredFunc = f;
                        break;
                    }
                }
            }
            if (configuredFunc == null) {
                funcDef.addError("Could not find a function " + funcDef.getName()
                        + " with the same signature in the configured package.");
            } else {
                if (!configuredFunc.hasAnnotation("@configurable")) {
                    e.addWarning("The configured function " + funcDef.getName() + " is not marked with @configurable.\n"
                            + "It is still possible to configure this function but it is not recommended.");
                }
            }

        } else {
            e.addError("Configuring " + Utils.printElement(e) + " is not supported by Wurst.");
        }
    }

    private boolean equalSignatures(FuncDef f, FuncDef g) {
        if (f.getParameters().size() != g.getParameters().size()) {
            return false;
        }
        if (!f.attrReturnTyp().equalsType(g.attrReturnTyp(), f)) {
            return false;
        }
        for (int i = 0; i < f.getParameters().size(); i++) {
            if (!f.getParameters().get(i).attrTyp().equalsType(g.getParameters().get(i).attrTyp(), f)) {
                return false;
            }
        }

        return true;
    }

    private void checkExprEmpty(ExprEmpty e) {
        e.addError("Incomplete expression...");

    }

    private void checkMemberArrayVar(ExprMemberArrayVar e) {
        // TODO Auto-generated method stub

    }

    private void checkNameRef(NameRef e) {
        if (e.getVarName().isEmpty()) {
            e.addError("Missing variable name.");
        }
    }

    private void checkPackage(WPackage p) {
        checkForDuplicateImports(p);
        p.attrInitDependencies();
    }

    private void checkTypeExpr(TypeExpr e) {
        if (e instanceof TypeExprResolved) {
            return;
        }
        if (e.isModuleUseTypeArg()) {
            return;
        }

        TypeDef typeDef = e.attrTypeDef();
        // check that modules are not used as normal types
        if (e.attrTypeDef() instanceof ModuleDef) {
            ModuleDef md = (ModuleDef) e.attrTypeDef();
            checkModuleTypeUsedCorrectly(e, md);
        }

        if (typeDef instanceof TypeParamDef) { // references a type parameter
            TypeParamDef tp = (TypeParamDef) typeDef;
            checkTypeparamsUsedCorrectly(e, tp);
        }

    }

    /**
     * Checks that module types are only used in valid places
     */
    private void checkModuleTypeUsedCorrectly(TypeExpr e, ModuleDef md) {
        if (e instanceof TypeExprThis) {
            // thistype is allowed, because it is translated to a real type when used
            return;
        }
        if (e.getParent() instanceof TypeExprThis) {
            TypeExprThis parent = (TypeExprThis) e.getParent();
            if (parent.getScopeType() == e) {
                // ModuleName.thistype is allowed
                // TODO (maybe check here that it is a parent)
                return;
            }
        }
        if (e instanceof TypeExprSimple) {
            TypeExprSimple tes = (TypeExprSimple) e;
            if (tes.getScopeType() instanceof TypeExpr) {
                TypeExpr scopeType = (TypeExpr) tes.getScopeType();
                if (scopeType instanceof TypeExprThis
                        || scopeType.attrTypeDef() instanceof ModuleDef) {
                    // thistype.A etc. is allowed
                    return;
                }
            }
        }
        e.addError("Cannot use module type " + md.getName() + " in this context.");
    }

    /**
     * check that type parameters are used in correct contexts:
     */
    private void checkTypeparamsUsedCorrectly(TypeExpr e, TypeParamDef tp) {
        if (tp.isStructureDefTypeParam()) { // typeParamDef is for
            // structureDef
            if (tp.attrNearestStructureDef() instanceof ModuleDef) {
                // in modules we can also type-params in static contexts
                return;
            }

            if (!e.attrIsDynamicContext()) {
                e.addError("Type variables must not be used in static contexts.");
            }
        }
    }

    private void checkClosure(ExprClosure e) {
        WurstType expectedTyp = e.attrExpectedTypAfterOverloading();
        if (expectedTyp instanceof WurstTypeCode) {
            // TODO check if no vars are captured
            if (!e.attrCapturedVariables().isEmpty()) {
                for (Entry<Element, VarDef> elem : e.attrCapturedVariables().entries()) {
                    elem.getKey().addError("Cannot capture local variable '" + elem.getValue().getName()
                            + "' in anonymous function. This is only possible with closures.");
                }
            }
        } else if (expectedTyp instanceof WurstTypeUnknown || expectedTyp instanceof WurstTypeClosure) {
            e.addError("Closures can only be used when a interface or class type is given.");

        } else if (!(expectedTyp instanceof WurstTypeClass
                || expectedTyp instanceof WurstTypeInterface)) {
            e.addError("Closures can only be used when a interface or class type is given, " + "but at this position a "
                    + expectedTyp + " is expected.");
        }
        e.attrCapturedVariables();

        if (e.getImplementation() instanceof ExprStatementsBlock) {
            ExprStatementsBlock block = (ExprStatementsBlock) e.getImplementation();
            new DataflowAnomalyAnalysis(false).execute(block);
        }

        if (expectedTyp instanceof WurstTypeClass) {
            WurstTypeClass ct = (WurstTypeClass) expectedTyp;

            ClassDef cd = ct.getClassDef();
            if (cd.getConstructors().stream().noneMatch(constr -> constr.getParameters().isEmpty())) {
                e.addError("No default constructor for class " + ct
                        + " found, so it cannot be instantiated using an anonymous function.");
            }
        }

    }

    private void checkConstructorsUnique(ClassOrModule c) {
        List<ConstructorDef> constrs = c.getConstructors();

        for (int i = 0; i < constrs.size() - 1; i++) {
            ConstructorDef c1 = constrs.get(i);
            for (int j = i + 1; i < constrs.size(); i++) {
                ConstructorDef c2 = constrs.get(j);
                if (c1.getParameters().size() != c2.getParameters().size()) {
                    continue;
                }

                if (!parametersTypeDisjunct(c1.getParameters(), c2.getParameters())) {
                    c2.addError(
                            "Duplicate constructor, an other constructor with similar types is already defined in line "
                                    + c1.attrSource().getLine());
                }
            }
        }

    }

    private boolean parametersTypeDisjunct(WParameters params1, WParameters params2) {
        for (int i = 0; i < params1.size(); i++) {
            WurstType t1 = params1.get(i).attrTyp();
            WurstType t2 = params2.get(i).attrTyp();
            if (!t1.isSubtypeOf(t2, params1) && !t2.isSubtypeOf(t1, params2)) {
                return true;
            }
        }
        return false;
    }

    private void checkImplicitParameter(NameRef e) {
        e.attrImplicitParameter();
    }

    private void checkTypeParameters(AstElementWithTypeParameters e) {
        for (TypeParamDef ta : e.getTypeParameters()) {
            if (ta.getName().contains("<") || ta.getName().startsWith("#")) {
                ta.addError("Type parameter must be a simple name ");
            } else {
                checkTypeName(ta, ta.getName());
            }
            ta.attrTyp();
        }
    }

    private void checkExprNull(ExprNull e) {
        if (!Utils.isJassCode(Optional.of(e)) && e.attrExpectedTyp() instanceof WurstTypeUnknown) {
            e.addError(
                    "Cannot use 'null' constant here because " + "the compiler cannot infer which kind of null it is.");
        }

    }

    private void checkForRange(StmtForRange e) {
        if (!(e.getLoopVar().attrTyp().isSubtypeOf(WurstTypeInt.instance(), e))) {
            e.getLoopVar().addError("For-loop variable must be int.");
        }
        if (!(e.getTo().attrTyp().isSubtypeOf(WurstTypeInt.instance(), e))) {
            e.getLoopVar().addError("For-loop target must be int.");
        }
        if (!(e.getStep().attrTyp().isSubtypeOf(WurstTypeInt.instance(), e))) {
            e.getLoopVar().addError("For-loop step must be int.");
        }
    }

    private void checkIntVal(ExprIntVal e) {
        // check range? ...
    }

    private int countFunctions() {
        final int functionCount[] = new int[1];
        prog.accept(new WurstModel.DefaultVisitor() {

            @Override
            public void visit(FuncDef f) {
                super.visit(f);
                functionCount[0]++;
            }
        });
        return functionCount[0];
    }

    private void checkStmtSet(StmtSet s) {
        NameLink nameLink = s.getUpdatedExpr().attrNameLink();
        if (nameLink == null) {
            s.getUpdatedExpr().addError("Could not find variable " + s.getUpdatedExpr().getVarName() + ".");
            return;
        }

        if (!(nameLink instanceof VarLink)) {
            s.getUpdatedExpr()
                    .addError("Invalid assignment. This is not a variable, this is a " + nameLink);
            return;
        }

        WurstType leftType = s.getUpdatedExpr().attrTyp();
        WurstType rightType = s.getRight().attrTyp();

        checkAssignment(Utils.isJassCode(Optional.of(s)), s, leftType, rightType);

        checkIfAssigningToConstant(s.getUpdatedExpr());

        checkIfNoEffectAssignment(s);
    }

    private void checkIfNoEffectAssignment(StmtSet s) {
        if (refersToSameVar(s.getUpdatedExpr(), s.getRight())) {
            s.addWarning("The assignment to " + Utils.printElement(s.getUpdatedExpr().attrNameDef())
                    + " probably has no effect.");
        }

    }

    private boolean refersToSameVar(OptExpr a, OptExpr b) {
        if (a instanceof NoExpr && b instanceof NoExpr) {
            return true;
        }
        if (a instanceof ExprThis && b instanceof ExprThis) {
            return true;
        }
        if (a instanceof NameRef && b instanceof NameRef) {
            NameRef va = (NameRef) a;
            NameRef vb = (NameRef) b;
            NameLink nla = va.attrNameLink();
            NameLink nlb = vb.attrNameLink();
            if (nla != null && nlb != null && nla.getDef() == nlb.getDef()
                    && refersToSameVar(va.attrImplicitParameter(), vb.attrImplicitParameter())) {
                if (va instanceof AstElementWithIndexes && vb instanceof AstElementWithIndexes) {
                    AstElementWithIndexes vai = (AstElementWithIndexes) va;
                    AstElementWithIndexes vbi = (AstElementWithIndexes) vb;

                    for (int i = 0; i < vai.getIndexes().size() && i < vbi.getIndexes().size(); i++) {
                        if (!refersToSameVar(vai.getIndexes().get(i), vbi.getIndexes().get(i))) {
                            return false;
                        }
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void checkIfAssigningToConstant(final LExpr left) {
        left.match(new LExpr.MatcherVoid() {

            @Override
            public void case_ExprVarArrayAccess(ExprVarArrayAccess e) {

            }

            @Override
            public void case_ExprVarAccess(ExprVarAccess e) {
                checkVarNotConstant(e, e.attrNameLink());
            }

            @Override
            public void case_ExprMemberVarDot(ExprMemberVarDot e) {
                if (e.attrNameDef() instanceof WParameter) {
                    // we have an assignment to a tuple variable
                    // check whether left side is 'this' or a constant variable
                    if (e.getLeft() instanceof ExprThis) {
                        e.addError("Cannot change 'this'. Tuples are not classes.");
                    } else if (e.getLeft() instanceof NameRef) {
                        checkIfAssigningToConstant((NameRef) e.getLeft());
                    } else {
                        e.addError(
                                "Ok, so you are trying to assign something to the return value of a function. This wont do nothing. Tuples are not classes.");
                    }
                }
                checkVarNotConstant(e, e.attrNameLink());
            }

            @Override
            public void case_ExprMemberArrayVarDot(ExprMemberArrayVarDot e) {

            }

            @Override
            public void case_ExprMemberArrayVarDotDot(ExprMemberArrayVarDotDot e) {
                e.addError("Cannot assign to dot-dot-expression.");
            }

            @Override
            public void case_ExprMemberVarDotDot(ExprMemberVarDotDot e) {
                e.addError("Cannot assign to dot-dot-expression.");
            }
        });
    }

    private void checkVarNotConstant(NameRef left, @Nullable NameLink link) {
        if (link == null) {
            return;
        }
        NameDef var = link.getDef();
        if (var != null && var.attrIsConstant()) {
            if (var instanceof GlobalVarDef) {
                GlobalVarDef glob = (GlobalVarDef) var;
                if (glob.attrIsDynamicClassMember() && isInConstructor(left)) {
                    // allow to assign constant members in constructor
                    return;
                }
            }
            left.addError("Cannot assign a new value to constant " + Utils.printElement(var));
        }
    }

    private boolean isInConstructor(Element e) {
        while (e != null) {
            if (e instanceof ConstructorDef) {
                return true;
            }
            e = e.getParent();
        }
        return false;
    }

    private void checkAssignment(boolean isJassCode, Element pos, WurstType leftType, WurstType rightType) {
        if (!rightType.isSubtypeOf(leftType, pos)) {
            if (isJassCode) {
                if (leftType.isSubtypeOf(WurstTypeReal.instance(), pos)
                        && rightType.isSubtypeOf(WurstTypeInt.instance(), pos)) {
                    // special case: jass allows to assign an integer to a real
                    // variable
                    return;
                }
            }
            pos.addError("Cannot assign " + rightType + " to " + leftType);
        }
        if (leftType instanceof WurstTypeNamedScope) {
            WurstTypeNamedScope ns = (WurstTypeNamedScope) leftType;
            if (ns.isStaticRef()) {
                pos.addError("Missing variable name in variable declaration.\n" + "Cannot assign to " + leftType);
            }
        }
        if (leftType instanceof WurstTypeArray) {
            pos.addError("Missing array index for assignment to array variable.s");
        }
        if (rightType instanceof WurstTypeVoid) {
            if (pos.attrNearestPackage() instanceof WPackage) {
                WPackage pack = (WPackage) pos.attrNearestPackage();
                if (pack != null && !pack.getName().equals("WurstREPL")) { // allow
                    // assigning
                    // nothing
                    // to
                    // a
                    // variable
                    // in
                    // the
                    // Repl
                    pos.addError("Function or expression returns nothing. Cannot assign nothing to a variable.");
                }
            }
        }
    }

    private void visit(LocalVarDef s) {
        checkVarName(s, false);
        if (s.getInitialExpr() instanceof Expr) {
            Expr initial = (Expr) s.getInitialExpr();
            if ((s.getOptTyp() instanceof NoTypeExpr)) {
                // TODO
            } else {
                if (initial instanceof ExprNewObject) {
                    s.addWarning("Duplicated type information. Use 'var' or 'let' instead.");
                }
            }
            WurstType leftType = s.attrTyp();
            WurstType rightType = initial.attrTyp();

            checkAssignment(Utils.isJassCode(Optional.of(s)), s, leftType, rightType);
        } else if (s.getInitialExpr() instanceof ArrayInitializer) {
            ArrayInitializer arInit = (ArrayInitializer) s.getInitialExpr();
            checkArrayInit(s, arInit);
        }
        checkIfRead(s);
    }

    private void checkArrayInit(VarDef def, ArrayInitializer arInit) {
        WurstType leftType = def.attrTyp();
        if (leftType instanceof WurstTypeArray) {
            WurstTypeArray arT = (WurstTypeArray) leftType;
            if (arT.getDimensions() > 1) {
                def.addError("Array initializer can only be used with one-dimensional arrays.");
            }
            if (arT.getDimensions() == 1) {
                int initialValues = arInit.getValues().size();
                int size = arT.getSize(0);
                if (size >= 0 && size != initialValues) {
                    def.addError("Array variable " + def.getName() + " is an array of size " + size + ", but is initialized with " + initialValues + " values here.");
                }

            }
            WurstType baseType = arT.getBaseType();
            for (Expr expr : arInit.getValues()) {
                if (!expr.attrTyp().isSubtypeOf(baseType, expr)) {
                    expr.addError("Expected expression of type " + baseType + " in array initialization, but found " + expr.attrTyp());
                }
            }
        } else {
            def.addError("Array initializer can only be used with array-variables, but " + Utils.printElement(def) + " has type " + leftType);
        }

    }

    private void checkIfRead(VarDef s) {
        if (s.getName().startsWith("_")) {
            // variables starting with an underscore are not read
            // (same convention as in Erlang)
            return;
        }
        if (Utils.isJassCode(Optional.of(s))) {
            return;
        }
        if (s.getParent() instanceof StmtForRange) {
            // it is ok, when the variable of a for-statement is not used
            return;
        }
        WScope f = s.attrNearestScope();
        if (f != null && !f.attrReadVariables().contains(s)) {
            s.addWarning("The " + Utils.printElement(s) + " is never read. If intentional, prefix with \"_\" to suppress this warning.");
        }
    }

    private void checkVarName(VarDef s, boolean isConstant) {
        String varName = s.getName();

        if (!isValidVarnameStart(varName) // first letter not lower case
                && !Utils.isJassCode(Optional.of(s)) // not in jass code
                && !varName.matches("[A-Z0-9_]+") // not a constant
        ) {
            s.addWarning("Variable names should start with a lower case character. (" + varName + ")");
        }
        if (varName.equals("handle")) {
            s.addError("\"handle\" is not a valid variable name");
        } else if (varName.equals("code")) {
            s.addError("\"code\" is not a valid variable name");
        }

    }

    private boolean isValidVarnameStart(String varName) {
        return varName.length() > 0 && Character.isLowerCase(varName.charAt(0)) || varName.startsWith("_");
    }

    private void visit(WParameter p) {
        checkVarName(p, false);
        if (p.attrIsVararg()) {
            if (p.attrNearestFuncDef().getParameters().size() != 1) {
                p.addError("Vararg functions may only have one parameter");
            }
        }
        checkIfParameterIsRead(p);
    }

    private void checkIfParameterIsRead(WParameter p) {
        FunctionImplementation f = p.attrNearestFuncDef();
        if (f != null) {
            if (p.getParent().getParent() instanceof ExprClosure) {
                // closures can ignore parameters
                return;
            }
            if (f.attrIsOverride()) {
                // if a function is overridden it is ok to ignore parameters
                return;
            }
            if (f.attrIsAbstract()) {
                // if a function is abstract, then parameter vars are not used
                return;
            }
            if (f.attrHasAnnotation("compiletimenative")) {
                return;
            }
        } else {
            if (p.getParent().getParent() instanceof TupleDef) {
                // ignore tuples
                return;
            }
            if (p.getParent().getParent() instanceof NativeFunc) {
                // ignore native functions
                return;
            }
        }

        checkIfRead(p);
    }

    private void visit(GlobalVarDef s) {
        checkVarName(s, s.attrIsConstant());
        if (s.getInitialExpr() instanceof Expr) {
            Expr initial = (Expr) s.getInitialExpr();
            WurstType leftType = s.attrTyp();
            WurstType rightType = initial.attrTyp();
            checkAssignment(Utils.isJassCode(Optional.of(s)), s, leftType, rightType);
        } else if (s.getInitialExpr() instanceof ArrayInitializer) {
            checkArrayInit(s, (ArrayInitializer) s.getInitialExpr());
        }

        if (s.attrTyp() instanceof WurstTypeArray && !s.attrIsStatic() && s.attrIsDynamicClassMember()) {
            // s.addError("Array variables must be static.\n" +
            // "Hint: use Lists for dynamic stuff.");
        }
    }

    private void visit(StmtIf stmtIf) {
        WurstType condType = stmtIf.getCond().attrTyp();
        if (!(condType instanceof WurstTypeBool)) {
            stmtIf.getCond().addError("If condition must be a boolean but found " + condType);
        }
    }

    private void visit(StmtWhile stmtWhile) {
        WurstType condType = stmtWhile.getCond().attrTyp();
        if (!(condType instanceof WurstTypeBool)) {
            stmtWhile.getCond().addError("While condition must be a boolean but found " + condType);
        }
    }

    private void visit(ExtensionFuncDef func) {
        checkFunctionName(func);
        func.getExtendedType().attrTyp();
    }

    private void checkFunctionName(FunctionDefinition f) {
        if (!Utils.isJassCode(Optional.of(f))) {
            if (!isValidVarnameStart(f.getName())) {
                f.addWarning("Function names should start with an lower case character.");
            }
        }
    }

    private void checkReturn(FunctionLike func) {
        if (!func.attrHasEmptyBody()) {
            new ReturnsAnalysis().execute(func);
        } else { // no body, check if in interface:
            if (func instanceof FunctionImplementation) {
                FunctionImplementation funcDef = (FunctionImplementation) func;
                if (funcDef.getReturnTyp() instanceof TypeExpr
                        && !(func.attrNearestStructureDef() instanceof InterfaceDef)) {
                    func.addError("Function " + funcDef.getName()
                            + " is missing a body. Use the 'skip' statement to define an empty body.");
                }
            }
        }
    }

    private void checkReachability(WStatement s) {
        if (s.getParent() instanceof WStatements) {
            WStatements stmts = (WStatements) s.getParent();
            if (s.attrPreviousStatements().isEmpty()) {
                if (s.attrListIndex() > 0 || !(stmts.getParent() instanceof TranslatedToImFunction
                        || stmts.getParent() instanceof ExprStatementsBlock)) {
                    if (Utils.isJassCode(Optional.of(s))) {
                        // in jass this is just a warning, because
                        // the shitty code emitted by jasshelper sometimes
                        // contains unreachable code
                        s.addWarning("Unreachable code");
                    } else {
                        if (mightBeAffectedBySwitchThatCoversAllCases(s)) {
                            // fow backwards compatibility just use a warning when
                            // switch statements that handle all cases are involved:
                            s.addWarning("Unreachable code");
                        } else {
                            s.addError("Unreachable code");
                        }
                    }
                }
            }
        }
    }

    private boolean mightBeAffectedBySwitchThatCoversAllCases(WStatement s) {
        boolean[] containsSwitchAr = { false };
        s.attrNearestNamedScope().accept(new Element.DefaultVisitor() {
            @Override
            public void visit(SwitchStmt switchStmt) {
                if (switchStmt.calculateHandlesAllCases()) {
                    containsSwitchAr[0] = true;
                }
            }
        });
        return containsSwitchAr[0];
    }

    private void visit(FuncDef func) {
        visitedFunctions++;
        func.getErrorHandler().setProgress(null, ProgressHelper.getValidatorPercent(visitedFunctions, functionCount));

        checkFunctionName(func);
        if (func.attrIsAbstract()) {
            if (!func.attrHasEmptyBody()) {
                func.addError("Abstract function " + func.getName() + " must not have a body.");
            }
            if (func.attrIsPrivate()) {
                func.addError("Abstract functions must not be private.");
            }
        }
    }

    private void checkUninitializedVars(FunctionLike f) {
        boolean isAbstract = false;
        if (f instanceof FuncDef) {
            FuncDef func = (FuncDef) f;
            if (func.attrIsAbstract()) {
                isAbstract = true;
                if (!func.attrHasEmptyBody()) {
                    func.getBody().get(0)
                            .addError("The abstract function " + func.getName() + " must not have any statements.");
                }
            }
        }
        if (!isAbstract) { // not abstract
            checkReturn(f);

            if (!f.getSource().getFile().endsWith("common.j")
                    && !f.getSource().getFile().endsWith("blizzard.j")
                    && !f.getSource().getFile().endsWith("war3map.j")) {
                new DataflowAnomalyAnalysis(Utils.isJassCode(Optional.of(f))).execute(f);
            }
        }
    }

    private void checkCall(StmtCall call) {
        String funcName;
        if (call instanceof FunctionCall) {
            FunctionCall fcall = (FunctionCall) call;
            funcName = fcall.getFuncName();
            HashSet<FunctionCall> fcalls = wrapperCalls.computeIfAbsent(funcName, (String s) -> new HashSet<>());
            fcalls.add(fcall);
        } else if (call instanceof ExprNewObject) {
            funcName = "constructor";
        } else {
            throw new Error("unhandled case: " + Utils.printElement(call));
        }

        call.attrCallSignature().checkSignatureCompatibility(call.attrFunctionSignature(), funcName, call);
    }

    private void checkAnnotation(Annotation a) {
        FuncLink fl = a.attrFuncLink();
        if (fl != null) {
            if (a.getArgs().size() < fl.getParameterTypes().size()) {
                a.addWarning("not enough arguments");
            } else if (a.getArgs().size() > fl.getParameterTypes().size()) {
                a.addWarning("too many enough arguments");
            } else {
                for (int i = 0; i < a.getArgs().size(); i++) {
                    WurstType actual = a.getArgs().get(i).attrTyp();
                    WurstType expected = fl.getParameterType(i);
                    if (!actual.isSubtypeOf(expected, a)) {
                        a.getArgs().get(i).addWarning("Expected " + expected + " but found " + actual + ".");
                    }
                }
            }
        }
    }

    private void visit(ExprFunctionCall stmtCall) {
        String funcName = stmtCall.getFuncName();
        // calculating the exprType should reveal most errors:
        stmtCall.attrTyp();

        checkFuncDefDeprecated(stmtCall);

        if (stmtCall.attrFuncLink() != null) {
            FuncLink calledFunc = stmtCall.attrFuncLink();
            if (calledFunc.getDef().attrIsDynamicClassMember()) {
                if (!stmtCall.attrIsDynamicContext()) {
                    stmtCall.addError("Cannot call dynamic function " + funcName + " from static context.");
                }
            }
            if (calledFunc.getDef() instanceof ExtensionFuncDef) {
                stmtCall.addError("Extension function " + funcName + " must be called with an explicit receiver.\n"
                        + "Try to write this." + funcName + "(...) .");
            }
        }

        // special check for filter & condition:
        if (Utils.oneOf(funcName, "Condition", "Filter") && !stmtCall.getArgs().isEmpty()) {
            Expr firstArg = stmtCall.getArgs().get(0);
            if (firstArg instanceof ExprFuncRef) {
                ExprFuncRef exprFuncRef = (ExprFuncRef) firstArg;
                FuncLink f = exprFuncRef.attrFuncLink();
                if (f != null) {
                    if (!(f.getReturnType() instanceof WurstTypeBool) && !(f.getReturnType() instanceof WurstTypeVoid)) {
                        firstArg.addError("Functions passed to Filter or Condition must return boolean or nothing.");
                    }
                }
            }
        }

    }

    // private void checkParams(Element where, List<Expr> args,
    // FunctionDefinition calledFunc) {
    // if (calledFunc == null) {
    // return;
    // }
    // List<PscriptType> parameterTypes = calledFunc.attrParameterTypes();
    // checkParams(where, args, parameterTypes);
    // }

    @Deprecated
    private void checkParams(Element where, String preMsg, List<Expr> args, FunctionSignature sig) {
        checkParams(where, preMsg, args, sig.getParamTypes());
    }

    @Deprecated
    private void checkParams(Element where, String preMsg, List<Expr> args, List<WurstType> parameterTypes) {
        if (args.size() > parameterTypes.size()) {
            where.addError(preMsg + "Too many parameters.");

        } else if (args.size() < parameterTypes.size()) {
            where.addError(preMsg + "Missing parameters.");
        } else {
            for (int i = 0; i < args.size(); i++) {

                WurstType actual = args.get(i).attrTyp();
                WurstType expected = parameterTypes.get(i);
                // if (expected instanceof AstElementWithTypeArgs)
                if (!actual.isSubtypeOf(expected, where)) {
                    args.get(i).addError(
                            preMsg + "Expected " + expected + " as parameter " + (i + 1) + " but  found " + actual);
                }
            }
        }
    }

    private void visit(ExprBinary expr) {
        FuncLink def = expr.attrFuncLink();
        if (def != null) {
            FunctionSignature sig = FunctionSignature.fromNameLink(def);
            CallSignature callSig = new CallSignature(expr.getLeft(), Collections.singletonList(expr.getRight()));
            callSig.checkSignatureCompatibility(sig, "" + expr.getOp(), expr);
        }
    }

    private void visit(ExprMemberMethod stmtCall) {
        // calculating the exprType should reveal all errors:
        stmtCall.attrTyp();
    }

    private void visit(ExprNewObject stmtCall) {
        stmtCall.attrTyp();
        stmtCall.attrConstructorDef();
    }

    private void visit(Modifiers modifiers) {
        boolean hasVis = false;
        boolean isStatic = false;
        for (Modifier m : modifiers) {
            if (m instanceof VisibilityModifier) {
                if (hasVis) {
                    m.addError("Each element can only have one visibility modifier (public, private, ...)");
                }
                hasVis = true;
            } else if (m instanceof ModStatic) {
                if (isStatic) {
                    m.addError("double static? - what r u trying to do?");
                }
                isStatic = true;
            }
        }
    }

    private void visit(StmtReturn s) {
        if (s.attrNearestExprStatementsBlock() != null) {
            ExprStatementsBlock e = s.attrNearestExprStatementsBlock();
            if (e.getReturnStmt() != s) {
                s.addError("Return in a statements block can only be at the end.");
                return;
            }
            if (s.getReturnedObj() instanceof Expr) {
                Expr expr = (Expr) s.getReturnedObj();
                if (expr.attrTyp().isVoid()) {
                    s.addError("Cannot return void from statements block.");
                }
            } else {
                s.addError("Cannot have empty return statement in statements block.");
            }
        } else {
            FunctionImplementation func = s.attrNearestFuncDef();
            if (func == null) {
                s.addError("return statements can only be used inside functions");
                return;
            }
            checkReturnInFunc(s, func);
        }
    }

    private void checkReturnInFunc(StmtReturn s, FunctionImplementation func) {
        WurstType returnType = func.attrReturnTyp();
        if (s.getReturnedObj() instanceof Expr) {
            Expr returned = (Expr) s.getReturnedObj();
            if (returnType.isSubtypeOf(WurstTypeVoid.instance(), s)) {
                s.addError("Cannot return a value from a function which returns nothing");
            } else {
                WurstType returnedType = returned.attrTyp();
                if (!returnedType.isSubtypeOf(returnType, s)) {
                    s.addError("Cannot return " + returnedType + ", expected expression of type " + returnType);
                }
            }
        } else { // empty return
            if (!returnType.isSubtypeOf(WurstTypeVoid.instance(), s)) {
                s.addError("Missing return value");
            }
        }
    }

    private void visit(ClassDef classDef) {
        checkTypeName(classDef, classDef.getName());
        if (!(classDef.getExtendedClass() instanceof NoTypeExpr) && !(classDef.getExtendedClass().attrTyp() instanceof WurstTypeClass)) {
            classDef.getExtendedClass().addError("Classes may only extend other classes.");
        }
        if (classDef.isInnerClass() && !classDef.attrIsStatic()) {
            classDef.addError("At the moment only static inner classes are supported.");
        }
    }

    private void checkTypeName(Element source, String name) {
        if (!Character.isUpperCase(name.charAt(0))) {
            source.addWarning("Type names should start with upper case characters.");
        }
    }

    private void visit(ModuleDef moduleDef) {
        checkTypeName(moduleDef, moduleDef.getName());
        // calculate all functions to find possible errors
        moduleDef.attrNameLinks();
    }

    private void visit(ExprDestroy stmtDestroy) {
        WurstType typ = stmtDestroy.getDestroyedObj().attrTyp();
        if (typ instanceof WurstTypeModule) {

        } else if (typ instanceof WurstTypeClass) {
            WurstTypeClass c = (WurstTypeClass) typ;
            checkDestroyClass(stmtDestroy, c);
        } else if (typ instanceof WurstTypeInterface) {
            WurstTypeInterface i = (WurstTypeInterface) typ;
            checkDestroyInterface(stmtDestroy, i);
        } else {
            stmtDestroy.addError("Cannot destroy objects of type " + typ);
        }
    }

    private void checkDestroyInterface(ExprDestroy stmtDestroy, WurstTypeInterface i) {
        if (i.isStaticRef()) {
            stmtDestroy.addError("Cannot destroy interface " + i);
        }
    }

    private void checkDestroyClass(ExprDestroy stmtDestroy, WurstTypeClass c) {
        if (c.isStaticRef()) {
            stmtDestroy.addError("Cannot destroy class " + c);
        }
        calledFunctions.put(stmtDestroy.attrNearestScope(), c.getClassDef().getOnDestroy());
    }

    private void visit(ExprVarAccess e) {
        checkVarRef(e, e.attrIsDynamicContext());
    }

    private void visit(WImport wImport) {
        if (wImport.attrImportedPackage() == null) {
            if (!wImport.getPackagename().equals("NoWurst")) {
                wImport.addError("Could not find imported package " + wImport.getPackagename());
            }
            return;
        }
        if (!wImport.attrImportedPackage().getName().equals("Wurst")
                && wImport.attrImportedPackage().getName().equals(wImport.attrNearestNamedScope().getName())) {
            wImport.addError("Packages cannot import themselves");
        }
    }

    /**
     * check if the nameRef e is accessed correctly i.e. not using a dynamic
     * variable from a static context
     *
     * @param e
     * @param dynamicContext
     */
    private void checkVarRef(NameRef e, boolean dynamicContext) {
        NameLink link = e.attrNameLink();
        if (link == null) {
            return;
        }
        NameDef def = link.getDef();
        if (def instanceof GlobalVarDef) {
            GlobalVarDef g = (GlobalVarDef) def;
            if (g.attrIsDynamicClassMember() && !dynamicContext) {
                e.addError("Cannot reference dynamic variable " + e.getVarName() + " from static context.");
            }
        }
        checkNameRefDeprecated(e, def);
        if (e.attrTyp() instanceof WurstTypeNamedScope) {
            WurstTypeNamedScope wtns = (WurstTypeNamedScope) e.attrTyp();
            if (wtns.isStaticRef()) {
                if (!isUsedAsReceiverInExprMember(e)) {
                    e.addError("Reference to " + e.getVarName() + " cannot be used as an expression.");
                } else if (e.getParent() instanceof ExprMemberMethodDotDot) {
                    e.addError("Reference to " + e.getVarName()
                            + " cannot be used with the cascade operator. Only dynamic objects are allowed.");
                } else if (e.getParent() instanceof ExprMemberMethod) {
                    ExprMemberMethod em = (ExprMemberMethod) e.getParent();
                    if (em.attrFuncDef() instanceof ExtensionFuncDef) {
                        e.addError("Reference to " + e.getVarName()
                                + " can only be used for calling static methods, but not for calling extension method method '" + em.getFuncName() + "'.");
                    }


                }
            }
        }

    }

    private boolean isUsedAsReceiverInExprMember(Expr e) {
        if (e.getParent() instanceof ExprMember) {
            ExprMember em = (ExprMember) e.getParent();
            return em.getLeft() == e;
        } else if (e.getParent() instanceof StmtForIn) {
            // if we write for x in E, then it actually calls E.iterator(), so it is used in an ExprMember
            StmtForIn parent = (StmtForIn) e.getParent();
            return parent.getIn() == e;
        } else if (e.getParent() instanceof StmtForFrom) {
            StmtForFrom parent = (StmtForFrom) e.getParent();
            return parent.getIn() == e;
        }
        return false;
    }

    private void checkTypeBinding(HasTypeArgs e) {
        VariableBinding mapping = e.match(new HasTypeArgs.Matcher<VariableBinding>() {

            @Override
            public VariableBinding case_ExprNewObject(ExprNewObject e) {
                return e.attrTyp().getTypeArgBinding();
            }

            @Override
            public VariableBinding case_ModuleUse(ModuleUse moduleUse) {
                return null;
            }

            @Override
            public VariableBinding case_TypeExprSimple(TypeExprSimple e) {
                return e.attrTyp().getTypeArgBinding();
            }

            @Override
            public VariableBinding case_ExprFunctionCall(ExprFunctionCall e) {
                return e.attrTyp().getTypeArgBinding();
            }

            @Override
            public VariableBinding case_ExprMemberMethodDot(ExprMemberMethodDot e) {
                return e.attrTyp().getTypeArgBinding();
            }

            @Override
            public VariableBinding case_ExprMemberMethodDotDot(ExprMemberMethodDotDot e) {
                return e.attrTyp().getTypeArgBinding();
            }
        });
        if (mapping == null) {
            return;
        }

        for (Tuple2<TypeParamDef, WurstTypeBoundTypeParam> t : mapping) {
            WurstTypeBoundTypeParam boundTyp = t._2();
            WurstType typ = boundTyp.getBaseType();

            TypeParamDef tp = t._1();
            if (tp.getTypeParamConstraints() instanceof TypeExprList) {
                // new style generics
            } else { // old style generics

                if (!typ.isTranslatedToInt() && !(e instanceof ModuleUse)) {
                    String toIndexFuncName = ImplicitFuncs.toIndexFuncName(typ);
                    String fromIndexFuncName = ImplicitFuncs.fromIndexFuncName(typ);
                    Collection<FuncLink> toIndexFuncs = ImplicitFuncs.findToIndexFuncs(typ, e);
                    Collection<FuncLink> fromIndexFuncs = ImplicitFuncs.findFromIndexFuncs(typ, e);
                    if (toIndexFuncs.isEmpty()) {
                        e.addError("Type parameters can only be bound to ints and class types, but " + "not to " + typ
                                + ".\n" + "You can provide functions " + toIndexFuncName + " and " + fromIndexFuncName
                                + " to use this type " + "with generics.");
                    } else if (fromIndexFuncs.isEmpty()) {
                        e.addError("Could not find function " + fromIndexFuncName + " which is required to use " + typ
                                + " with generics.");
                    } else {
                        if (toIndexFuncs.size() > 1) {
                            e.addError("There is more than one function named " + toIndexFuncName);
                        }
                        if (fromIndexFuncs.size() > 1) {
                            e.addError("There is more than one function named " + fromIndexFuncName);
                        }
                        NameDef toIndex = Utils.getFirst(toIndexFuncs).getDef();
                        if (toIndex instanceof FuncDef) {
                            FuncDef toIndexF = (FuncDef) toIndex;

                            if (toIndexF.getParameters().size() != 1) {
                                toIndexF.addError("Must have exactly one parameter");

                            } else if (!toIndexF.getParameters().get(0).attrTyp().equalsType(typ, e)) {
                                toIndexF.addError("Parameter must be of type " + typ);
                            }

                            WurstType returnType = toIndexF.attrReturnTyp();
                            if (!returnType.equalsType(WurstTypeInt.instance(), e)) {
                                toIndexF.addError("Return type must be of type int " + " but was " + returnType);
                            }
                        } else {
                            toIndex.addError("This should be a function.");
                        }

                        NameDef fromIndex = Utils.getFirst(fromIndexFuncs).getDef();
                        if (fromIndex instanceof FuncDef) {
                            FuncDef fromIndexF = (FuncDef) fromIndex;

                            if (fromIndexF.getParameters().size() != 1) {
                                fromIndexF.addError("Must have exactly one parameter");

                            } else if (!fromIndexF.getParameters().get(0).attrTyp()
                                    .equalsType(WurstTypeInt.instance(), e)) {
                                fromIndexF.addError("Parameter must be of type int");
                            }

                            WurstType returnType = fromIndexF.attrReturnTyp();
                            if (!returnType.equalsType(typ, e)) {
                                fromIndexF.addError("Return type must be of type " + typ + " but was " + returnType);
                            }

                        } else {
                            fromIndex.addError("This should be a function.");
                        }
                    }
                }
            }
        }
    }

    private void checkFuncRef(FuncRef ref) {
        if (ref.getFuncName().isEmpty()) {
            ref.addError("Missing function name.");
        }
        checkFuncDefDeprecated(ref);
        FuncLink called = ref.attrFuncLink();
        if (called == null) {
            return;
        }
        WScope scope = ref.attrNearestFuncDef();
        if (scope == null) {
            scope = ref.attrNearestScope();
        }
        if (!(ref instanceof ExprFuncRef)) { // ExprFuncRef is not a direct call
            calledFunctions.put(scope, called.getDef());
        }
    }

    private void checkNameRefDeprecated(Element trace, NameLink link) {
        if (link != null) {
            checkNameRefDeprecated(trace, link.getDef());
        }

    }

    private void checkNameRefDeprecated(Element trace, NameDef def) {
        if (def != null && def.hasAnnotation("@deprecated")) {
            Annotation annotation = def.getAnnotation("@deprecated");
            String msg = annotation.getAnnotationMessage();
            msg = (msg == null || msg.isEmpty()) ? "It shouldn't be used and will be removed in the future." : msg;
            trace.addWarning("<" + def.getName() + "> is deprecated. " + msg);
        }
    }

    private void checkFuncDefDeprecated(FuncRef ref) {
        checkNameRefDeprecated(ref, ref.attrFuncLink());
    }

    private void checkFuncRef(ExprFuncRef ref) {
        FuncLink called = ref.attrFuncLink();
        if (called == null) {
            return;
        }
        if (ref.attrTyp() instanceof WurstTypeCode) {
            if (called.getDef().attrParameterTypesIncludingReceiver().size() > 0) {
                String msg = "Can only use functions without parameters in 'code' function references.";
                if (called.getDef().attrIsDynamicClassMember()) {
                    msg += "\nNote that " + called.getName()
                            + " is a dynamic function and thus has an implicit parameter 'this'.";
                }
                ref.addError(msg);
            }
        }
    }

    private void checkModifiers(final HasModifier e) {
        for (final Modifier m : e.getModifiers()) {
            final StringBuilder error = new StringBuilder();

            e.match(new HasModifier.MatcherVoid() {

                @Override
                public void case_WParameter(WParameter wParameter) {
                    check(ModConstant.class);
                }

                @Override
                public void case_WShortParameter(WShortParameter wShortParameter) {
                    check(ModConstant.class);
                }

                @Override
                public void case_TypeParamDef(TypeParamDef typeParamDef) {
                    error.append("Type Parameters must not have modifiers");
                }

                @Override
                public void case_NativeType(NativeType nativeType) {
                    check(VisibilityPublic.class);
                }

                @SafeVarargs
                private final void check(Class<? extends Modifier>... allowed) {
                    if (m instanceof WurstDoc) {
                        // wurstdoc always allowed
                        return;
                    }
                    if (m instanceof ModVararg && e.getParent() instanceof WParameters) {
                        return;
                    }
                    boolean isAllowed = false;
                    for (Class<? extends Modifier> a : allowed) {
                        String modName = m.getClass().getName();
                        String allowedName = a.getName();
                        if (modName.startsWith(allowedName)) {
                            isAllowed = true;
                            break;
                        }
                    }
                    if (!isAllowed) {
                        error.append("Modifier ").append(printMod(m)).append(" not allowed for ").append(Utils.printElement(e)).append(".\n Allowed are the " +
                                "following modifiers: ");
                        boolean first = true;
                        for (Class<? extends Modifier> c : allowed) {
                            if (!first) {
                                error.append(", ");
                            }
                            error.append(printMod(c));
                            first = false;
                        }
                    }
                }

                @Override
                public void case_NativeFunc(NativeFunc nativeFunc) {
                    check(VisibilityPublic.class, Annotation.class);
                }

                @Override
                public void case_ModuleInstanciation(ModuleInstanciation moduleInstanciation) {
                    check(VisibilityPrivate.class, VisibilityProtected.class);
                }

                @Override
                public void case_ModuleDef(ModuleDef moduleDef) {
                    check(VisibilityPublic.class);
                }

                @Override
                public void case_LocalVarDef(LocalVarDef localVarDef) {
                    check(ModConstant.class);
                    if (localVarDef.hasAnnotation("@compiletime")) {
                        localVarDef.getAnnotation("@compiletime").addWarning("The annotation '@compiletime' has no effect on variables.");
                    }
                }

                @Override
                public void case_GlobalVarDef(GlobalVarDef g) {
                    if (g.attrNearestClassOrModule() != null) {
                        check(VisibilityPrivate.class, VisibilityProtected.class, ModStatic.class, ModConstant.class,
                                Annotation.class);
                    } else {
                        check(VisibilityPublic.class, ModConstant.class, Annotation.class);
                    }
                    if (g.hasAnnotation("@compiletime")) {
                        g.getAnnotation("@compiletime").addWarning("The annotation '@compiletime' has no effect on variables.");
                    }
                }

                @Override
                public void case_FuncDef(FuncDef f) {
                    if (f.attrNearestStructureDef() != null) {
                        if (f.attrNearestStructureDef() instanceof InterfaceDef) {
                            check(VisibilityPrivate.class, VisibilityProtected.class, ModAbstract.class,
                                    ModOverride.class, Annotation.class);
                        } else {
                            check(VisibilityPrivate.class, VisibilityProtected.class, ModAbstract.class,
                                    ModOverride.class, ModStatic.class, Annotation.class);
                            if (f.attrNearestStructureDef() instanceof ClassDef) {
                                if (f.attrIsStatic() && f.attrIsAbstract()) {
                                    f.addError("Static functions cannot be abstract.");
                                }
                            }
                        }
                    } else {
                        check(VisibilityPublic.class, Annotation.class);
                    }
                    if (f.attrIsCompiletime()) {
                        if (f.getParameters().size() > 0) {
                            f.addError("Functions annotated '@compiletime' may not take parameters." +
                                    "\nNote: The annotation marks functions to be executed by wurst at compiletime.");
                        } else if (f.attrIsDynamicClassMember()) {
                            f.addError("Functions annotated '@compiletime' must be static." +
                                    "\nNote: The annotation marks functions to be executed by wurst at compiletime.");
                        }
                    }
                }

                @Override
                public void case_ExtensionFuncDef(ExtensionFuncDef extensionFuncDef) {
                    check(VisibilityPublic.class, Annotation.class);
                }

                @Override
                public void case_ConstructorDef(ConstructorDef constructorDef) {
                    check(VisibilityPrivate.class);
                }

                @Override
                public void case_ClassDef(ClassDef classDef) {
                    check(VisibilityPublic.class, ModAbstract.class, ModStatic.class);
                    if (!classDef.isInnerClass() && classDef.attrIsStatic()) {
                        classDef.addError("Top-level class " + classDef.getName() + " cannot be static. "
                                + "Only inner classes can be declared static.");
                    }
                }

                @Override
                public void case_InterfaceDef(InterfaceDef interfaceDef) {
                    check(VisibilityPublic.class);
                }

                @Override
                public void case_TupleDef(TupleDef tupleDef) {
                    check(VisibilityPublic.class);
                }

                @Override
                public void case_WPackage(WPackage wPackage) {
                    check();
                }

                @Override
                public void case_EnumDef(EnumDef enumDef) {
                    check(VisibilityPublic.class);
                }

                @Override
                public void case_EnumMember(EnumMember enumMember) {
                    check();
                }

            });
            if (error.length() > 0) {
                if (m.attrSource().getFile().endsWith(".jurst")) {
                    // for jurst only add a warning:
                    m.addWarning(error.toString());
                } else {
                    m.addError(error.toString());
                }
            }
        }
    }

    private static String printMod(Class<? extends Modifier> c) {
        String name = c.getName().toLowerCase();
        name = name.replaceFirst("^.*\\.", "");
        name = name.replaceAll("^(mod|visibility)", "");
        name = name.replaceAll("impl$", "");
        return name;
    }

    private static String printMod(Modifier m) {
        if (m instanceof Annotation) {
            return ((Annotation) m).getAnnotationType();
        }
        return printMod(m.getClass());
    }

    private void checkConstructor(ConstructorDef d) {
        if (d.attrNearestClassOrModule() instanceof ModuleDef) {
            if (d.getParameters().size() > 0) {
                d.getParameters().addError("Module constructors must not have parameters.");
            }
        }
        StructureDef s = d.attrNearestStructureDef();
        if (s instanceof ClassDef) {
            ClassDef c = (ClassDef) s;
            WurstTypeClass ct = c.attrTypC();
            WurstTypeClass extendedClass = ct.extendedClass();
            if (extendedClass != null) {
                // check if super constructor is called correctly...
                // TODO check constr: get it from ct so that it has the correct type binding
                ConstructorDef sc = d.attrSuperConstructor();
                if (sc == null) {
                    d.addError("No super constructor found.");
                } else {
                    List<WurstType> paramTypes = Lists.newArrayList();
                    for (WParameter p : sc.getParameters()) {
                        paramTypes.add(p.attrTyp());
                    }
                    if (d.getSuperConstructorCall() instanceof NoSuperConstructorCall
                            && paramTypes.size() > 0) {
                        c.addError("The extended class <" + extendedClass.getName() + "> does not expose a no-arg constructor. " +
                                "You must define a constructor that calls super(..) appropriately, in this class.");
                    } else {
                        checkParams(d, "Incorrect call to super constructor: ", superArgs(d), paramTypes);
                    }
                }
            }
        } else {
            if (d.getSuperConstructorCall() instanceof SomeSuperConstructorCall) {
                d.addError("Module constructors cannot have super calls.");
            }
        }
    }


    private void checkArrayAccess(ExprVarArrayAccess ea) {
        checkNameRefDeprecated(ea, ea.tryGetNameDef());
        for (Expr index : ea.getIndexes()) {
            if (!(index.attrTyp().isSubtypeOf(WurstTypeInt.instance(), ea))) {
                index.addError("Arrayindices have to be of type int");
            }
        }
    }

    private void checkInterfaceDef(InterfaceDef i) {
        checkTypeName(i, i.getName());
        // TODO check if functions are refinements
    }

    private void checkNewObj(ExprNewObject e) {
        ConstructorDef constr = e.attrConstructorDef();
        if (constr != null) {
            calledFunctions.put(e.attrNearestScope(), constr);
            if (constr.attrNearestClassDef().attrIsAbstract()) {
                e.addError("Cannot create an instance of the abstract class " + constr.attrNearestClassDef().getName());
                return;
            }
            checkParams(e, "Wrong object creation: ", e.getArgs(), e.attrFunctionSignature());
        }

    }

    private void nameDefsMustNotBeNamedAfterJassNativeTypes(NameDef n) {
        PackageOrGlobal p = n.attrNearestPackage();
        if (p == null) {
            n.addError("Not in package or global: " + n.getName());
        }
        // checkIfTypeDefExists(n, p);
        // if (p instanceof WPackage) {
        // // check global scope
        // p = p.getParent().attrNearestPackage();
        // checkIfTypeDefExists(n, p);
        // }
    }

    private void checkMemberVar(ExprMemberVar e) {
        if (e.getVarName().length() == 0) {
            e.addError("Incomplete member access.");
        }
        if (e.getParent() instanceof WStatements) {
            e.addError("Incomplete statement.");
        }
    }

    private void checkPackageName(CompilationUnit cu) {
        if (cu.getPackages().size() == 1 && Utils.isWurstFile(cu.getCuInfo().getFile())) {
            // only one package in a wurst file
            WPackage p = cu.getPackages().get(0);
            if (!Utils.fileName(cu.getCuInfo().getFile()).equals(p.getName() + ".wurst")
                    && !Utils.fileName(cu.getCuInfo().getFile()).equals(p.getName() + ".jurst")) {
                p.addError("The file must have the same name as the package " + p.getName());
            }
        }
    }

    private void checkForDuplicatePackages(WurstModel model) {
        model.attrPackages();
    }

    private void checkBannedFunctions(ExprFunctionCall e) {
        if (e.getFuncName().equals("TriggerRegisterVariableEvent")) {
            if (e.getArgs().size() > 1) {
                if (e.getArgs().get(1) instanceof ExprStringVal) {
                    ExprStringVal varName = (ExprStringVal) e.getArgs().get(1);
                    TRVEHelper.protectedVariables.add(varName.getValS());
                    WLogger.info("keep: " + varName.getValS());
                    return;
                } else if (e.getArgs().get(1) instanceof ExprVarAccess) {
                    // Check if this is a two line hook... thanks Bribe
                    ExprVarAccess varAccess = (ExprVarAccess) e.getArgs().get(1);
                    @Nullable FunctionImplementation nearestFunc = e.attrNearestFuncDef();
                    WStatements fbody = nearestFunc.getBody();
                    if (e.getParent() instanceof StmtReturn && fbody.size() <= 4 && fbody.get(fbody.size() - 2).structuralEquals(e.getParent())) {
                        WParameters params = nearestFunc.getParameters();
                        if (params.size() == 4 && ((TypeExprSimple) params.get(0).getTyp()).getTypeName().equals("trigger")
                            && ((TypeExprSimple) params.get(1).getTyp()).getTypeName().equals("string")
                            && ((TypeExprSimple) params.get(2).getTyp()).getTypeName().equals("limitop")
                            && ((TypeExprSimple) params.get(3).getTyp()).getTypeName().equals("real")) {
                            trveWrapperFuncs.add(nearestFunc.getName());
                            WLogger.info("found wrapper: " + nearestFunc.getName());
                            return;
                        }
                    }
                }
            } else {

                e.addError("Map contains TriggerRegisterVariableEvent with non-constant arguments. Can't be optimized.");
            }
        }

        if (e.getFuncName().equals("ExecuteFunc")) {
            // executeFunc can only use constant string arguments
            if (e.getArgs().size() != 1) {
                e.addError("Wrong number of args");
                return;
            }
            if (e.getArgs().get(0) instanceof ExprStringVal) {
                ExprStringVal s = (ExprStringVal) e.getArgs().get(0);
                String exFunc = s.getValS();
                Collection<FuncLink> funcs = e.lookupFuncs(exFunc);
                if (funcs.isEmpty()) {
                    e.addError("Could not find function " + exFunc + ".");
                    return;
                }
                if (funcs.size() > 1) {
                    StringBuilder alternatives = new StringBuilder();
                    for (NameLink nameLink : funcs) {
                        alternatives.append("\n - ").append(Utils.printElementWithSource(Optional.of(nameLink.getDef())));
                    }
                    e.addError("Ambiguous function name: " + exFunc + ". Alternatives are: " + alternatives);
                    return;
                }
                FuncLink func = Utils.getFirst(funcs);
                if (func.getParameterTypes().size() != 0) {
                    e.addError("Function " + exFunc + " must not have any parameters.");
                }
            } else {
                e.addError("Wurst does only support ExecuteFunc with a single string as argument.");
            }
        }
    }

    private boolean isViableSwitchtype(Expr expr) {
        WurstType typ = expr.attrTyp();
        if (typ.equalsType(WurstTypeInt.instance(), null) || typ.equalsType(WurstTypeString.instance(), null)) {
            return true;
        } else if (typ instanceof WurstTypeEnum) {
            WurstTypeEnum wte = (WurstTypeEnum) typ;
            return !wte.isStaticRef();
        } else {
            return false;
        }
    }

    private void checkSwitch(SwitchStmt s) {
        if (!isViableSwitchtype(s.getExpr())) {
            s.addError("The type " + s.getExpr().attrTyp()
                    + " is not viable as switchtype.\nViable switchtypes: int, string, enum");
        } else {
            List<Expr> switchExprs = s.getCases().stream()
                    .flatMap(e -> e.getExpressions().stream())
                    .collect(Collectors.toList());
            for (Expr cExpr : switchExprs) {
                if (!cExpr.attrTyp().isSubtypeOf(s.getExpr().attrTyp(), cExpr)) {
                    cExpr.addError("The type " + cExpr.attrTyp() + " does not match the switchtype "
                            + s.getExpr().attrTyp() + ".");
                }
            }
            for (int i = 0; i < switchExprs.size(); i++) {
                Expr ei = switchExprs.get(i);
                for (int j = 0; j < i; j++) {
                    Expr ej = switchExprs.get(j);
                    if (ei.structuralEquals(ej)) {
                        ei.addError("The case " + Utils.prettyPrint(ei) + " is already handled in line " + ej.attrSource().getLine());
                        return;
                    }
                }
            }
        }
        for (String unhandledCase : s.calculateUnhandledCases()) {
            s.addError(unhandledCase + " not covered in switchstatement and no default found.");
        }
        if (s.getCases().isEmpty()) {
            s.addError("Switch statement without any cases.");
        }
    }

    public static void computeFlowAttributes(Element node) {
        if (node instanceof WStatement) {
            WStatement s = (WStatement) node;
            s.attrNextStatements();
        }

        // traverse childs
        for (int i = 0; i < node.size(); i++) {
            computeFlowAttributes(node.get(i));
        }
    }

    private void checkCodeArrays(TypeExprArray e) {
        if (e.getBase() instanceof TypeExprSimple) {
            TypeExprSimple base = (TypeExprSimple) e.getBase();
            if (base.getTypeName().equals("code")) {
                e.addError("Code arrays are not supported. Try using an array of triggers or conditionfuncs.");
            }

        }
    }


    /**
     * checks if func1 can override func2
     */
    public static boolean canOverride(FuncLink func1, FuncLink func2, boolean allowStaticOverride) {
        return checkOverride(func1, func2, allowStaticOverride) == null;
    }

    /**
     * checks if func1 can override func2
     * <p>
     * Returns null if yes and an error message if not.
     */
    public static String checkOverride(FuncLink func1, FuncLink func2, boolean allowStaticOverride) {
        if (!allowStaticOverride) {
            if (func1.isStatic()) {
                return "Static method " + func1.getName() + " cannot override other methods.";
            }
            if (func2.isStatic()) {
                return "Static " + Utils.printElementWithSource(Optional.of(func2.getDef())) + " cannot be overridden.";
            }
        } else {
            if (func1.isStatic() && !func2.isStatic()) {
                return "Static method "
                    + func1.getName()
                    + " cannot override dynamic "
                    + Utils.printElementWithSource(Optional.of(func2.getDef()))
                    + ".";
            } else if (!func1.isStatic() && func2.isStatic()) {
                return "Method "
                    + func1.getName()
                    + " cannot override static "
                    + Utils.printElementWithSource(Optional.of(func2.getDef()))
                    + ".";
            }
        }
        if (func1.isVarargMethod() && !func2.isVarargMethod()) {
            return "Vararg method "
                + func1.getName()
                + " cannot override non-vararg method "
                + Utils.printElementWithSource(Optional.of(func2.getDef()))
                + ".";
        }
        if (!func1.isVarargMethod() && func2.isVarargMethod()) {
            return "Non-vararg method "
                + func1.getName()
                + " cannot override vararg method "
                + Utils.printElementWithSource(Optional.of(func2.getDef()))
                + ".";
        }
        int paramCount2 = func2.getParameterTypes().size();
        int paramCount1 = func1.getParameterTypes().size();
        if (paramCount1 != paramCount2) {
            return Utils.printElement(func2.getDef()) + " takes " + paramCount2
                    + " parameters, but there are only " + paramCount1 + " parameters here.";
        }

        // contravariant parametertypes
        for (int i = 0; i < paramCount1; i++) {
            WurstType type1 = func1.getParameterType(i);
            WurstType type2 = func2.getParameterType(i);
            if (!type1.isSupertypeOf(type2, func1.getDef())) {
                return "Parameter " + type1 + " " + func1.getParameterName(i) + " should have type " + type2
                        + " to override " + Utils.printElementWithSource(Optional.of(func2.getDef())) + ".";
            }
        }
        // covariant return types
        if (!func1.getReturnType().isSubtypeOf(func2.getReturnType(), func1.getDef())) {
            return "Return type should be "
                + func2.getReturnType()
                + " to override "
                + Utils.printElementWithSource(Optional.of(func2.getDef()))
                + ".";
        }
        // no error
        return null;
    }

    private void checkForDuplicateNames(WScope scope) {
        ImmutableMultimap<String, DefLink> links = scope.attrNameLinks();
        for (String name : links.keySet()) {
            ImmutableCollection<DefLink> nameLinks = links.get(name);
            if (nameLinks.size() <= 1) {
                continue;
            }
            List<FuncLink> funcs = Lists.newArrayList();
            List<NameLink> other = Lists.newArrayList();
            for (NameLink nl : nameLinks) {
                if (nl.getDefinedIn() == scope) {
                    if (nl instanceof FuncLink) {
                        funcs.add(((FuncLink) nl));
                    } else {
                        other.add(nl);
                    }
                }
            }
            if (other.size() > 1) {
                other.sort(Comparator.comparingInt(o -> o.getDef().attrSource().getLeftPos()));
                NameLink l1 = other.get(0);
                for (int j = 1; j < other.size(); j++) {
                    other.get(j).getDef().addError("An element with name " + name + " already exists: "
                            + Utils.printElementWithSource(Optional.of(l1.getDef())));
                }
            }
            if (funcs.size() <= 1) {
                continue;
            }
            for (int i = 0; i < funcs.size() - 1; i++) {
                FuncLink f1 = funcs.get(i);
                for (int j = i + 1; j < funcs.size(); j++) {
                    FuncLink f2 = funcs.get(j);
                    if (!distinctFunctions(f1, f2)) {
                        f1.getDef().addError(
                                "Function already defined : " + Utils.printElementWithSource(Optional.of(f2.getDef())));
                        f2.getDef().addError(
                                "Function already defined : " + Utils.printElementWithSource(Optional.of(f1.getDef())));
                    }
                }
            }
        }
    }

    private boolean distinctFunctions(FuncLink nl1, FuncLink nl2) {
        if (receiverTypesDifferent(nl1, nl2)) {
            return true;
        }
        FunctionDefinition f1 = nl1.getDef();
        FunctionDefinition f2 = nl2.getDef();
        WParameters ps1 = f1.getParameters();
        WParameters ps2 = f2.getParameters();
        if (ps1.size() != ps2.size()) {
            return true;
        }
        return parametersTypeDisjunct(ps1, ps2);
    }

    private boolean receiverTypesDifferent(FuncLink nl1, FuncLink nl2) {
        if (nl1.getReceiverType() == null) {
            return nl2.getReceiverType() != null;
        } else {
            return nl2.getReceiverType() == null || !nl1.getReceiverType().equalsType(nl2.getReceiverType(), nl1.getDef());
        }
    }

    private void checkForDuplicateImports(WPackage p) {
        Set<String> imports = Sets.newLinkedHashSet();
        for (WImport imp : p.getImports()) {
            if (!imports.add(imp.getPackagename())) {
                imp.addError("The package " + imp.getPackagename() + " is already imported.");
            }
        }
    }

    private void checkVarDef(VarDef v) {
        WurstType vtype = v.attrTyp();

        if (vtype instanceof WurstTypeCode && v.attrIsDynamicClassMember()) {
            v.addError("Code members not allowed as dynamic class members (variable " + v.getName() + ")\n"
                    + "Try using a trigger or conditionfunc instead.");
        }

        if (v instanceof GlobalOrLocalVarDef) {
            GlobalOrLocalVarDef g = (GlobalOrLocalVarDef) v;
            if (g.attrIsConstant() && g.getInitialExpr() instanceof NoExpr && !g.attrIsDynamicClassMember()) {
                g.addError("Constant variable " + g.getName() + " needs an initial value.");
            }
        }

        if (vtype instanceof WurstTypeArray) {
            WurstTypeArray wta = (WurstTypeArray) vtype;
            switch (wta.getDimensions()) {
                case 0:
                    v.addError("0-dimensional arrays are not allowed");
                    break;
                case 1:
                    if (v.attrIsDynamicClassMember() && wta.getSize(0) <= 0) {
                        v.addError("Array members require a fixed size greater 0.");
                    }
                    break;
                default:
                    v.addError("Multidimensional Arrays are not yet supported.");
                    break;
            }
        }

        if (vtype instanceof WurstTypeNull) {
            v.addError("Initial value of variable " + v.getName() + " is 'null'. Specify a concrete type.");
        }

    }

    private void checkLocalShadowing(LocalVarDef v) {
        NameLink shadowed = v.getParent().getParent().lookupVar(v.getName(), false);
        if (shadowed != null) {
            if (shadowed.getDef() instanceof LocalVarDef) {
                v.addError("Variable " + v.getName() + " hides an other local variable with the same name.");
            } else if (shadowed.getDef() instanceof WParameter) {
                v.addError("Variable " + v.getName() + " hides a parameter with the same name.");
            }
        }
    }

    private void checkConstructorSuperCall(ConstructorDef c) {
        if (c.getSuperConstructorCall() instanceof SomeSuperConstructorCall) {
            if (c.attrNearestClassDef() != null) {
                ClassDef classDef = c.attrNearestClassDef();
                if (classDef.getExtendedClass() instanceof NoTypeExpr) {
                    c.addError("Super call in a class which extends nothing.");
                }
            }
        }
    }

    private void checkParameter(WParameter param) {
        if (param.attrTyp() instanceof WurstTypeArray) {
            param.addError("Cannot use arrays as parameters.");
        }
    }
}
