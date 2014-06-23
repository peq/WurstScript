package de.peeeq.wurstscript.translation.imtojass;

import static de.peeeq.wurstscript.jassAst.JassAst.JassFunction;
import static de.peeeq.wurstscript.jassAst.JassAst.JassFunctions;
import static de.peeeq.wurstscript.jassAst.JassAst.JassNatives;
import static de.peeeq.wurstscript.jassAst.JassAst.JassProg;
import static de.peeeq.wurstscript.jassAst.JassAst.JassSimpleVars;
import static de.peeeq.wurstscript.jassAst.JassAst.JassStatements;
import static de.peeeq.wurstscript.jassAst.JassAst.JassTypeDefs;
import static de.peeeq.wurstscript.jassAst.JassAst.JassVars;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.jassAst.JassAst;
import de.peeeq.wurstscript.jassAst.JassExpr;
import de.peeeq.wurstscript.jassAst.JassFunction;
import de.peeeq.wurstscript.jassAst.JassFunctionOrNative;
import de.peeeq.wurstscript.jassAst.JassFunctions;
import de.peeeq.wurstscript.jassAst.JassNative;
import de.peeeq.wurstscript.jassAst.JassProg;
import de.peeeq.wurstscript.jassAst.JassSimpleVar;
import de.peeeq.wurstscript.jassAst.JassVar;
import de.peeeq.wurstscript.jassAst.JassVars;
import de.peeeq.wurstscript.jassIm.ImArrayType;
import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImSimpleType;
import de.peeeq.wurstscript.jassIm.ImTupleArrayType;
import de.peeeq.wurstscript.jassIm.ImVar;
import de.peeeq.wurstscript.jassIm.JassImElement;
import de.peeeq.wurstscript.jassIm.JassImElementWithTrace;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.utils.Utils;

public class ImToJassTranslator {

	private ImProg imProg;
	private Multimap<ImFunction, ImFunction> calledFunctions;
	private ImFunction mainFunc;
	private ImFunction confFunction;
	private JassProg prog;
	private Stack<ImFunction> translatingFunctions = new Stack<ImFunction>();
	private Set<ImFunction> translatedFunctions = Sets.newLinkedHashSet();
	private Set<String> usedNames = Sets.newLinkedHashSet();
	private static String restrictedNames[] = {"loop", "endif", "endfunction", "endloop", "globals", "endglobals", "local", "call"};
	private Multimap<ImFunction, String> usedLocalNames = HashMultimap.create();

	public ImToJassTranslator(ImProg imProg, Multimap<ImFunction, ImFunction> calledFunctions, 
			ImFunction mainFunc, ImFunction confFunction) {
		this.imProg = imProg;
		this.calledFunctions = calledFunctions;
		this.mainFunc = mainFunc;
		this.confFunction = confFunction;
	}
	
	public JassProg translate() {
		JassVars globals = JassVars();
		JassFunctions functions = JassFunctions();
		prog = JassProg(JassTypeDefs(), globals, JassNatives(), functions);
		
		collectGlobalVars();
		
		translateFunctionTransitive(mainFunc);
		translateFunctionTransitive(confFunction);
		
		return prog;
	}

	private void collectGlobalVars() {
		for (ImVar v : imProg.getGlobals()) {
			globalImVars.add(v);
			getJassVarFor(v);
		}
	}

	private void translateFunctionTransitive(ImFunction imFunc) {
		if (translatedFunctions.contains(imFunc)) {
			// already translated
			return;
		}
		if (translatingFunctions.contains(imFunc)) {
			// TODO extract method
			if (imFunc != translatingFunctions.peek()) {
				String msg = "cyclic dependency between functions: " ;
				boolean start = false;
				for (ImFunction f : translatingFunctions) {
					if (imFunc == f) {
						start = true;
					}
					if (start) {
						msg += "\n - " + Utils.printElement(getTrace(f)) + "  ( " + f.attrTrace().attrSource().getFile() + " line  " +  f.attrTrace().attrSource().getLine() + ")";
					}
				}
				WPos src = getTrace(imFunc).attrSource();
				throw new CompileError(src, msg);
			}
			// already translating, recursive function
			return;
		}
		translatingFunctions.push(imFunc);
		for (ImFunction f : sorted(calledFunctions.get(imFunc))) {
			translateFunctionTransitive(f);
		}
		
		translateFunction(imFunc);
		
		// translation finished
		if (translatingFunctions.pop() != imFunc) {
			throw new Error("something went wrong...");
		}
		translatedFunctions.add(imFunc);
	}

	private List<ImFunction> sorted(Collection<ImFunction> collection) {
		List<ImFunction> r = Lists.newArrayList(collection);
		Collections.sort(r, new Comparator<ImFunction>() {

			@Override
			public int compare(ImFunction f, ImFunction g) {
				return f.getName().compareTo(g.getName());
			}
		});
		return r;
	}

	public static AstElement getTrace(JassImElement elem) {
		while (elem != null) {
			if (elem instanceof JassImElementWithTrace) {
				JassImElementWithTrace jassImElementWithTrace = (JassImElementWithTrace) elem;
				AstElement t = jassImElementWithTrace.getTrace();
				if (t != null) {
					return t;
				}
			}
			elem = elem.getParent();
		}
		throw new Error("Could not get trace to original program.");
	}

	private void translateFunction(ImFunction imFunc) {
		if (imFunc.isBj()) {
			return;
		}
		// not a native
		JassFunctionOrNative f = getJassFuncFor(imFunc);

		f.setReturnType(imFunc.getReturnType().translateType());
		// translate parameters
		for (ImVar v : imFunc.getParameters()) {
			f.getParams().add((JassSimpleVar) getJassVarFor(v));
		}
		if (f instanceof JassFunction) {
			JassFunction jf = (JassFunction) f;
			// translate locals
			for (ImVar v : imFunc.getLocals()) {
				jf.getLocals().add(getJassVarFor(v));
			}
			imFunc.getBody().translate(jf.getBody(), jf, this);
		}
		
	}


	private String getUniqueGlobalName(String name) { // TODO find local names
		String name2 = "";
		for (int i = 0; i < restrictedNames.length; i++) {
			if ( restrictedNames[i].equals(name)) {
				name2 = "w" + name;
			}
		}
		if (name2.length() < 1) {
			if (!usedNames.contains(name)) {
				usedNames.add(name);
				return name;
			}
		}
		int i = 1;
		do {
			i++;
			name2 = name + "_" + i;
		} while (usedNames.contains(name2));
		usedNames.add(name2);
		return name2;
	}
	
	private String getUniqueLocalName(ImFunction imFunction, String name) {
		if (!usedNames.contains(name) && !usedLocalNames.containsEntry(imFunction, name)) {
			usedLocalNames.put(imFunction, name);
			return name;
		}
		String name2;
		int i = 1;
		do {
			i++;
			name2 = name + "_" + i;
		} while (usedNames.contains(name2) || usedLocalNames.containsEntry(imFunction, name2));
		usedLocalNames.put(imFunction, name2);
		return name2;
	}

	Map<Pair<String, Integer>, JassVar> tempReturnVars = Maps.newLinkedHashMap();
	
	public JassVar getTempReturnVar(String type, int nr) {
		Pair<String, Integer> key = Pair.create(type, nr);
		JassVar v = tempReturnVars.get(key);
		if (v == null) {
			v = JassAst.JassSimpleVar(type, getUniqueGlobalName("temp_return_"+type+"_"+nr));
			prog.getGlobals().add(v);
			tempReturnVars.put(key, v);
		}
		return v;
	}

	Map<ImVar, JassVar> jassVars = Maps.newLinkedHashMap();
	private Set<ImVar> globalImVars = Sets.newLinkedHashSet();
	
	public JassVar getJassVarFor(ImVar v) {
		JassVar result = jassVars.get(v);
		if (result == null) {
			boolean isArray = v.getType() instanceof ImArrayType || v.getType() instanceof ImTupleArrayType;
			String type = v.getType().translateType();
			String name = jassifyName(v.getName());
			if (v.getNearestFunc() != null) {
				name = getUniqueLocalName(v.getNearestFunc(), name);
			} else {
				name = getUniqueGlobalName(name);
			}
			if (isArray) {
				result = JassAst.JassArrayVar(type, name);
			} else {
				if (isGlobal(v) && v.getType() instanceof ImSimpleType) {
					JassExpr initialVal = ImHelper.defaultValueForType((ImSimpleType) v.getType()).translate(this);
					result = JassAst.JassInitializedVar(type, name, initialVal);
				} else {
					result = JassAst.JassSimpleVar(type, name);
				}
			}
			if (isGlobal(v) && !v.getIsBJ()) {
				prog.getGlobals().add(result);
			}
			jassVars.put(v, result);
		}
		return result ;
	}

	private String jassifyName(String name) {
		while (name.startsWith("_")) {
			name = name.substring(1);
		}
		if (name.isEmpty()) {
			name = "empty";
		}
		return name;
	}

	private boolean isGlobal(ImVar v) {
		return globalImVars.contains(v);
	}

	public JassVar newTempVar(JassFunction f, String type, String name) {
		JassSimpleVar v = JassAst.JassSimpleVar(type, getUniqueGlobalName(name));
		f.getLocals().add(v);
		return v;
	}

	Map<ImFunction, JassFunctionOrNative> jassFuncs = Maps.newLinkedHashMap();
	
	public JassFunctionOrNative getJassFuncFor(ImFunction func) {
		JassFunctionOrNative f = jassFuncs.get(func);
		if (f == null) {
			if (func.isNative()) {
				f = JassAst.JassNative(func.getName(), JassSimpleVars(), "nothing");
				if (!func.isBj() && !func.isExtern()) {
					prog.getNatives().add((JassNative) f);
				}
			} else {
				String name = getUniqueGlobalName(func.getName());
				f = JassFunction(name, JassSimpleVars(), "nothing", JassVars(), JassStatements());
				if (!func.isBj() && !func.isExtern()) {
					prog.getFunctions().add((JassFunction) f);
				}
			}
			jassFuncs.put(func, f);
		}
		return f;
	}
	
	Map<ImFunction, JassNative> jassJassNatives = Maps.newLinkedHashMap();
	

	
}