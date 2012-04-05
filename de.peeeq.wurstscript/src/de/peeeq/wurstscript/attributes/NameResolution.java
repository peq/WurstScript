package de.peeeq.wurstscript.attributes;

import java.util.Collections;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ClassDef;
import de.peeeq.wurstscript.ast.Expr;
import de.peeeq.wurstscript.ast.NameDef;
import de.peeeq.wurstscript.ast.NamedScope;
import de.peeeq.wurstscript.ast.StructureDef;
import de.peeeq.wurstscript.ast.TypeExpr;
import de.peeeq.wurstscript.ast.WPos;
import de.peeeq.wurstscript.ast.WScope;
import de.peeeq.wurstscript.types.PscriptType;
import de.peeeq.wurstscript.types.PscriptTypeInterface;
import de.peeeq.wurstscript.types.PscriptTypeNamedScope;
import de.peeeq.wurstscript.utils.Utils;

public class NameResolution {

	
	public static <T extends AstElement> T searchTypedNameGetOne(Class<T> t, String name, AstElement where, boolean withInheritance) {
		List<T> names = searchTypedNameInScope(t, name, where.attrNearestScope(), withInheritance);
		if (names.size() == 0) {
			attr.addError(where.attrSource(), "Could not resolve reference to " + name);
			return null;
		} else if (names.size() > 1) {
			attr.addError(where.attrSource(), "Reference to " + name + " is ambiguous. Alternatives are:\n" + printAlternatives(names));
		}
		return names.get(0);
	}
	
	private static String printAlternatives(List<? extends AstElement> alternatives) {
		List<String> result = Lists.newArrayList();
		for (AstElement a : alternatives) {
			WPos source = a.attrSource();
			String s = a.getClass().getSimpleName() + " defined in line " + source.getLine() + " ("+source.getFile()+")" ;
			result.add(s);
		}
		return " * " + Utils.join(result, "\n * ") ;
	}
	
	public static <T extends AstElement> List<T> searchTypedName(Class<T> t, String name, AstElement where, boolean withInheritance) {
		if (where == null) throw new IllegalArgumentException();
		return searchTypedNameInScope(t, name, where.attrNearestScope(), withInheritance);
	}
	
	private static <T extends AstElement> List<T> searchTypedNameInScope(Class<T> t, String name, WScope scope, boolean withInheritance) {
		System.out.println("Searching " + t.getSimpleName() + " " + name + " in " + Utils.printElement(scope));
		if (scope == null) {
			return Collections.emptyList();
		}
		List<T> result = Lists.newArrayList();
		if (withInheritance && scope instanceof NamedScope) {
			NamedScope namedScope = (NamedScope) scope;
			T r = getTypedNameFromNamedScope(t, scope, name, namedScope);
			if (r != null) {
				result.add(r);
			}
		} else {
			Multimap<String, NameDef> names = scope.attrVisibleNamesPrivate();
			for (NameDef n : names.get(name)) {
				if (t.isInstance(n)) {
					@SuppressWarnings("unchecked")
					T n2 = (T) n;
					result.add(n2);
				}
			}
		}
		if (result.size() > 0) {
			return result;
		} else {
			// not found yet, search in next scope
			return searchTypedNameInScope(t, name, scope.attrNextScope(), withInheritance);
		}
		
	}
	
	

	public static <T> T getTypedNameFromNamedScope(Class<T> t, Expr context, String name, PscriptTypeNamedScope sr) {
		return getTypedNameFromNamedScope(t, context, name, sr.getDef());
	}
	
	private static <T> T getTypedNameFromNamedScope(Class<T> t, AstElement context, String name, NamedScope ns) {
		System.out.println("named scope searching " + t.getSimpleName() + " " + name + " in " + Utils.printElement(ns));
		Multimap<String, NameDef> names;
		if (isSameClass(context, ns)) {
			names = ns.attrVisibleNamesPrivate();
		} else if (isSamePackage(context, ns)) {
			names = ns.attrVisibleNamesProtected();
		} else {
			// different package
			names = ns.attrVisibleNamesPublic();
		}
		for (NameDef n : names.get(name)) {
			if (t.isInstance(n)) {
				@SuppressWarnings("unchecked")
				T n2 = (T) n;
				return n2;
			}
		}		
		// if not found yet, try to find implementation in supertypes:
		if (ns instanceof ClassDef) {
			ClassDef c = (ClassDef) ns;
			// search for impl in extended class
			if (c.getExtendedClass() instanceof TypeExpr) {
				TypeExpr typeExpr = (TypeExpr) c.getExtendedClass();
				PscriptType superType = typeExpr.attrTyp();
				if (superType instanceof PscriptTypeNamedScope) {
					PscriptTypeNamedScope superTypeNs = (PscriptTypeNamedScope) superType;
					T r = getTypedNameFromNamedScope(t, context, name, superTypeNs.getDef());
					if (r != null) {
						return r;
					}
				}
				
			}
			
			// search for default implementation in interfaces
			for (PscriptTypeInterface i : c.attrImplementedInterfaces()) {
				T r = getTypedNameFromNamedScope(t, context, name, i.getDef());
				if (r != null) {
					return r;
				}
			}
		}
		return null;
	}

	private static boolean isSamePackage(AstElement context, NamedScope ns) {
		return ns.attrNearestPackage() == context.attrNearestPackage();
	}

	private static boolean isSameClass(AstElement context, NamedScope ns) {
		return ns == context.attrNearestNamedScope();
	}

}
