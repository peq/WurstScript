package de.peeeq.wurstscript.translation.imtranslation;

import static de.peeeq.wurstscript.jassIm.JassIm.ImExprs;
import static de.peeeq.wurstscript.jassIm.JassIm.ImFunctionCall;
import static de.peeeq.wurstscript.jassIm.JassIm.ImReturn;
import static de.peeeq.wurstscript.jassIm.JassIm.ImSet;
import static de.peeeq.wurstscript.jassIm.JassIm.ImSetArray;
import static de.peeeq.wurstscript.jassIm.JassIm.ImVar;
import static de.peeeq.wurstscript.jassIm.JassIm.ImVarAccess;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ClassDef;
import de.peeeq.wurstscript.ast.ClassOrModuleInstanciation;
import de.peeeq.wurstscript.ast.ConstructorDef;
import de.peeeq.wurstscript.ast.Expr;
import de.peeeq.wurstscript.ast.FuncDef;
import de.peeeq.wurstscript.ast.GlobalVarDef;
import de.peeeq.wurstscript.ast.ModuleInstanciation;
import de.peeeq.wurstscript.ast.OnDestroyDef;
import de.peeeq.wurstscript.ast.OptExpr;
import de.peeeq.wurstscript.ast.StructureDef;
import de.peeeq.wurstscript.ast.TypeExpr;
import de.peeeq.wurstscript.ast.WParameter;
import de.peeeq.wurstscript.jassIm.ImClass;
import de.peeeq.wurstscript.jassIm.ImExprs;
import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImMethod;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImSet;
import de.peeeq.wurstscript.jassIm.ImSetArray;
import de.peeeq.wurstscript.jassIm.ImSetArrayTuple;
import de.peeeq.wurstscript.jassIm.ImSetTuple;
import de.peeeq.wurstscript.jassIm.ImStmt;
import de.peeeq.wurstscript.jassIm.ImStmt.DefaultVisitor;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.ImVar;
import de.peeeq.wurstscript.jassIm.ImVarAccess;
import de.peeeq.wurstscript.jassIm.JassIm;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.types.WurstTypeClass;
import de.peeeq.wurstscript.types.WurstTypeVoid;
import de.peeeq.wurstscript.utils.Pair;

public class ClassTranslator {

	private ClassDef classDef;
	private ImTranslator translator;
//	/** list of statements to initialize a new object **/
	final private List<Pair<ImVar, OptExpr>> dynamicInits;
	private ImClass imClass;
	private ImProg prog;

	public ClassTranslator(ClassDef classDef, ImTranslator translator) {
		this.classDef = classDef;
		this.translator = translator;
		this.prog = translator.getImProg();
//		initStatements = translator.getInitStatement(classDef);
		dynamicInits = translator.getDynamicInits(classDef);
	}

	public static void translate(ClassDef classDef, ImTranslator translator) {
		new ClassTranslator(classDef, translator).translate();

	}

	/**
	 * translates the given classDef
	 */
	private void translate() {
		imClass = translator.getClassFor(classDef);
		prog.getClasses().add(imClass);
		
		addSuperClasses();
		
		List<ClassDef> subClasses = Lists.newArrayList(translator.getSubClasses(classDef));
		
		// order is important here
		translateMethods(classDef, subClasses);
		translateVars(classDef);
		translateConstructors();
		createOnDestroyMethod();
		createDestroyMethod(subClasses);

	}


	private void addSuperClasses() {
		if (classDef.getExtendedClass() instanceof TypeExpr) {
			TypeExpr extended = (TypeExpr) classDef.getExtendedClass();
			addSuperClass(extended);
		}
		for (TypeExpr impl: classDef.getImplementsList()) {
			addSuperClass(impl);
		}
		
	}

	private void addSuperClass(TypeExpr extended) {
		if (extended.attrTypeDef() instanceof StructureDef) {
			StructureDef sc = (StructureDef) extended.attrTypeDef();
			imClass.getSuperClasses().add(translator.getClassFor(sc));
		}
	}

	private void createDestroyMethod(List<ClassDef> subClasses) {
		ImMethod m = translator.destroyMethod.getFor(classDef);
		imClass.getMethods().add(m);
		ImFunction f = translator.destroyFunc.getFor(classDef);
		
		// set sub methods
		for (ClassDef sc : subClasses) {
			ImMethod dm = translator.destroyMethod.getFor(sc);
			if (hasOwnDestroy(sc, classDef)) {
				m.getSubMethods().add(dm);
			}
		}
		
		AstElement trace = classDef.getOnDestroy();
		
		ImVar thisVar = f.getParameters().get(0);
		
		// call ondestroy methods
		ClassDef c = classDef;
		ImFunction scOnDestroy = translator.getFuncFor(c.getOnDestroy());
		f.getBody().add(ImFunctionCall(trace, 
				scOnDestroy, 
				ImExprs(ImVarAccess(thisVar)), false, CallType.NORMAL));
		
		// deallocate
		f.getBody().add(JassIm.ImDealloc(imClass, JassIm.ImVarAccess(thisVar)));
	}

	/**
	 * 
	 */
	private boolean hasOwnDestroy(ClassDef sc, ClassDef classDef2) {
		if (sc == classDef2) {
			return false;
		}
		if (sc.getOnDestroy().attrHasEmptyBody()) {
			WurstTypeClass superClass = (WurstTypeClass) sc.getExtendedClass().attrTyp();
			return hasOwnDestroy(superClass.getClassDef(), classDef2);
		} else {
			return true;
		}
	}

	private void createOnDestroyMethod() {
		OnDestroyDef onDestroy = classDef.getOnDestroy();
		ImFunction f = translator.getFuncFor(onDestroy);
		addOnDestroyActions(f, f.getBody(), classDef, translator.getThisVar(onDestroy));
	}
	
	private void addOnDestroyActions(ImFunction f, List<ImStmt> addTo, ClassOrModuleInstanciation c, ImVar thisVar) { 
		// translate ondestroy statements
		List<ImStmt> stmts = translator.translateStatements(f, c.getOnDestroy().getBody());
		replaceThisExpr(stmts, translator.getThisVar(c.getOnDestroy()), thisVar);
		addTo.addAll(stmts);
		
		// add onDestroy actions from modules
		for (ModuleInstanciation mi : c.getModuleInstanciations()) {
			addOnDestroyActions(f, addTo, mi, thisVar);
		}
		
		if (c instanceof ClassDef) {
			ClassDef cd = (ClassDef) c;
			if (cd.attrExtendedClass() != null) {
				// call onDestroy of super class
				ImFunction onDestroy = translator.getFuncFor(cd.attrExtendedClass().getOnDestroy());
				addTo.add(ImFunctionCall(c, 
						onDestroy, 
						ImExprs(ImVarAccess(thisVar)), false, CallType.NORMAL));
			}
		}
	}

	private void replaceThisExpr(List<ImStmt> stmts, final ImVar oldThis, final ImVar newThis) {
		if (oldThis == newThis) {
			return;
		}
		DefaultVisitor replacer = new ImStmt.DefaultVisitor() {
			@Override
			public void visit(ImVarAccess v) {
				if (v.getVar() == oldThis) {
					v.setVar(newThis);
				}
			}
			
			@Override
			public void visit(ImSet v) {
				if (v.getLeft() == oldThis) {
					v.setLeft(newThis);
				}
			}
			
			@Override
			public void visit(ImSetArray v) {
				if (v.getLeft() == oldThis) {
					v.setLeft(newThis);
				}
			}
			
			@Override
			public void visit(ImSetTuple v) {
				if (v.getLeft() == oldThis) {
					v.setLeft(newThis);
				}
			}
			
			@Override
			public void visit(ImSetArrayTuple v) {
				if (v.getLeft() == oldThis) {
					v.setLeft(newThis);
				}
			}
		};
		for (ImStmt s : stmts) {
			s.accept(replacer);
		}
		
	}

	private void translateConstructors() {
//		// collect init statements from module instantiations:
//		for (ModuleInstanciation mi : classDef.getModuleInstanciations()) {
//			collectModuleInitializers(mi);
//		}

		for (ConstructorDef c : classDef.getConstructors()) {
			translateConstructor(c);
		}

	}


	private void translateVars(ClassOrModuleInstanciation c) {
		for (GlobalVarDef v : c.getVars()) {
			translateVar(v);
		}
		for (ModuleInstanciation mi : c.getModuleInstanciations()) {
			translateVars(mi);
		}
	}
	
	public void translateVar(GlobalVarDef s) {
		ImVar v = translator.getVarFor(s);
		if (s.attrIsDynamicClassMember()) {
			// for dynamic class members create an array
			ImType t = s.attrTyp().imTranslateType();
			v.setType(ImHelper.toArray(t));
			dynamicInits.add(Pair.create(v, s.getInitialExpr()));
		} else { // static class member
			translator.addGlobalInitalizer(v, classDef.attrNearestPackage(), s.getInitialExpr());
		}
		translator.addGlobal(v);
	}

	private void translateMethods(ClassOrModuleInstanciation c, List<ClassDef> subClasses) {
		for (FuncDef f : c.getMethods()) {
			translateMethod(f, subClasses);
		}
		for (ModuleInstanciation mi : c.getModuleInstanciations()) {
			translateMethods(mi, subClasses);
		}
	}

	public void translateMethod(FuncDef s, List<ClassDef> subClasses) {
		ImFunction f = createStaticCallFunc(s);
		if (s.attrIsStatic()) {
			// static method
		} else {
			// dynamic method
			ImMethod m = translator.getMethodFor(s);
			imClass.getMethods().add(m);
			m.setImplementation(f);
			m.setIsAbstract(s.attrIsAbstract());
			// set sub methods
			Map<ClassDef, FuncDef> subClasses2 = translator.getClassesWithImplementation(subClasses, s);
			for (FuncDef subM : subClasses2.values()) {
				m.getSubMethods().add(translator.getMethodFor(subM));
			}
		}
	}

	private ImFunction createStaticCallFunc(FuncDef funcDef) {
		ImFunction f = translator.getFuncFor(funcDef);
		f.getBody().addAll(translator.translateStatements(f, funcDef.getBody()));
		// TODO add return for abstract function
		if (funcDef.attrIsAbstract() && !(funcDef.attrReturnType() instanceof WurstTypeVoid)) {
			f.getBody().add(ImReturn(funcDef, funcDef.attrReturnType().getDefaultValue()));
		}
		return f;
	}


	public void translateConstructor(ConstructorDef constr) {
		createNewFunc(constr);
		createConstructFunc(constr);
	}

	

	private void createNewFunc(ConstructorDef constr) {
		ConstructorDef trace = constr;
		ImFunction f = translator.getConstructNewFunc(constr);
		Map<ImVar, ImVar> varReplacements = Maps.newLinkedHashMap();
		
		for (WParameter p : constr.getParameters()) {
			ImVar imP = ImVar(p, p.attrTyp().imTranslateType(), p.getName(), false);
			varReplacements.put(translator.getVarFor(p), imP);
			f.getParameters().add(imP);
		}
		
		
		ImVar thisVar = JassIm.ImVar(constr, TypesHelper.imInt(), "this", false);
		varReplacements.put(translator.getThisVar(constr), thisVar);
		f.getLocals().add(thisVar);
		
		// allocate class
		f.getBody().add(ImSet(trace, thisVar, JassIm.ImAlloc(imClass)));
		
		// call user defined constructor code:
		ImFunction constrFunc = translator.getConstructFunc(constr);
		ImExprs arguments = ImExprs(ImVarAccess(thisVar));
		for (ImVar a : f.getParameters()) {
			arguments.add(ImVarAccess(a));
		}
		f.getBody().add(ImFunctionCall(trace, constrFunc, arguments, false, CallType.NORMAL));
		
		
		// return this
		f.getBody().add(ImReturn(trace, ImVarAccess(thisVar)));
		
	}

	

	private void createConstructFunc(ConstructorDef constr) {
		ConstructorDef trace = constr;
		ImFunction f = translator.getConstructFunc(constr);
		ImVar thisVar = translator.getThisVar(constr);
		ConstructorDef superConstr = constr.attrSuperConstructor();
		if (superConstr != null) {
			// call super constructor
			ImFunction superConstrFunc = translator.getConstructFunc(superConstr);
			ImExprs arguments = ImExprs(ImVarAccess(thisVar));
			for (Expr a : constr.getSuperArgs()) {
				arguments.add(a.imTranslateExpr(translator, f));
			}
			f.getBody().add(ImFunctionCall(trace, superConstrFunc, arguments, false, CallType.NORMAL));
		}
		// initialize vars
		for (Pair<ImVar, OptExpr> i : translator.getDynamicInits(classDef)) {
			ImVar v = i.getA();
			if (i.getB() instanceof Expr) {
				Expr e = (Expr) i.getB();
				ImStmt s = ImSetArray(trace, v, ImVarAccess(thisVar), e.imTranslateExpr(translator, f));
				f.getBody().add(s);
			}
		}
		// add initializers from modules
		for (ModuleInstanciation mi : classDef.getModuleInstanciations()) {
			addModuleInits(f, mi, thisVar);
		}
		// constructor user code
		f.getBody().addAll(translator.translateStatements(f, constr.getBody()));
	}

	private void addModuleInits(ImFunction f, ModuleInstanciation mi,	ImVar thisVar) {
		// add initializers from modules
		for (ModuleInstanciation mi2 : mi.getModuleInstanciations()) {
			addModuleInits(f, mi2, thisVar);
		}
		
		for (ConstructorDef c : mi.getConstructors()) {
			List<ImStmt> stmts = translator.translateStatements(f, c.getBody());
			ImHelper.replaceVar(stmts, translator.getThisVar(c), thisVar);
			f.getBody().addAll(stmts);
		}
	}

	
	

}
