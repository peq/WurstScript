package de.peeeq.wurstscript.translation.lua.translation;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.validation.TRVEHelper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Removes every class, function, method and global variable
 * that is not used from main or config
 */
public class RemoveGarbage {

    private static class Used {
        private Set<ImFunction> functions = new HashSet<>();
        private Set<ImMethod> methods = new HashSet<>();
        // methods that will be added once the class is used:
        private Multimap<ImClass, ImMethod> waitingMethods = HashMultimap.create();
        private Set<ImClass> classes = new HashSet<>();
        private Set<ImVar> vars = new HashSet<>();

        public void addMethod(ImMethod m) {
            methods.add(m);
        }

        public void maybeVisitMethod(ImMethod m) {
            ImClass c = m.attrClass();
            if (classes.contains(c)) {
                visitMethod(m, this);
            } else {
                waitingMethods.put(c, m);
            }
        }

        public Set<ImFunction> getFunctions() {
            return functions;
        }

        public Set<ImMethod> getMethods() {
            return methods;
        }

        public Set<ImClass> getClasses() {
            return classes;
        }

        public Set<ImVar> getVars() {
            return vars;
        }

        public void addFunction(ImFunction f) {
            functions.add(f);
        }

        public void addVar(ImVar var) {
            vars.add(var);
        }

        public void addClass(ImClass c) {
            classes.add(c);
            Collection<ImMethod> imMethods = waitingMethods.get(c);
            Iterator<ImMethod> it = imMethods.iterator();
            while (it.hasNext()) {
                ImMethod m = it.next();
                visitMethod(m, this);
                it.remove();
            }
        }
    }

    public static void removeGarbage(ImProg prog) {
        Used used = new Used();
        for (ImFunction f : prog.getFunctions()) {
            if (f.getName().equals("main")
                || f.getName().equals("config")) {
                visitFunction(f, used);
            }
        }

        prog.getClasses().removeIf(c -> !used.getClasses().contains(c));
        prog.getGlobals().removeIf(g -> !used.getVars().contains(g) && !TRVEHelper.protectedVariables.contains(g.getName()));
        prog.getFunctions().removeIf(f -> !used.getFunctions().contains(f));
        for (ImClass c : prog.getClasses()) {
            c.getFields().removeIf(g -> !used.getVars().contains(g));
            c.getFunctions().removeIf(f -> !used.getFunctions().contains(f));
            c.getMethods().removeIf(m -> !used.getMethods().contains(m));
            for (ImMethod m : c.getMethods()) {
                m.getSubMethods().removeIf(sm -> !used.getMethods().contains(sm));
            }
        }

    }

    private static void visitFunction(ImFunction f, Used used) {
        if (used.getFunctions().contains(f)) {
            return;
        }
        used.addFunction(f);

        visitType(f.getReturnType(), used);
        f.accept(new Element.DefaultVisitor() {
            @Override
            public void visit(ImFunctionCall e) {
                super.visit(e);
                visitFunction(e.getFunc(), used);
            }

            @Override
            public void visit(ImVar e) {
                super.visit(e);
                visitType(e.getType(), used);
            }

            @Override
            public void visit(ImFuncRef e) {
                super.visit(e);
                visitFunction(e.getFunc(), used);
            }

            @Override
            public void visit(ImCast e) {
                super.visit(e);
                visitType(e.getToType(), used);
            }

            @Override
            public void visit(ImVarAccess e) {
                super.visit(e);
                used.addVar(e.getVar());
            }

            @Override
            public void visit(ImVarArrayAccess e) {
                super.visit(e);
                used.addVar(e.getVar());
            }

            @Override
            public void visit(ImAlloc e) {
                super.visit(e);
                visitClass(e.getClazz().getClassDef(), used);
            }

            @Override
            public void visit(ImDealloc e) {
                super.visit(e);
                visitClass(e.getClazz().getClassDef(), used);
            }

            @Override
            public void visit(ImInstanceof e) {
                super.visit(e);
                visitClass(e.getClazz().getClassDef(), used);
            }

            @Override
            public void visit(ImTypeIdOfObj e) {
                super.visit(e);
                visitClass(e.getClazz().getClassDef(), used);
            }

            @Override
            public void visit(ImTypeIdOfClass e) {
                super.visit(e);
                visitClass(e.getClazz().getClassDef(), used);
            }

            @Override
            public void visit(ImMethodCall e) {
                super.visit(e);
                visitMethod(e.getMethod(), used);
            }

            @Override
            public void visit(ImMemberAccess e) {
                super.visit(e);
                used.addVar(e.getVar());
            }
        });
    }

    private static void visitMethod(ImMethod m, Used used) {
        if (used.getMethods().contains(m)) {
            return;
        }
        used.addMethod(m);
        visitClass(m.getMethodClass().getClassDef(), used);
        visitFunction(m.getImplementation(), used);
        for (ImMethod subMethod : m.getSubMethods()) {
            used.maybeVisitMethod(subMethod);
        }
    }

    private static void visitClass(ImClass c, Used used) {
        if (used.getClasses().contains(c)) {
            return;
        }
        used.addClass(c);
        for (ImClassType superClass : c.getSuperClasses()) {
            visitClass(superClass.getClassDef(), used);
        }
    }

    private static void visitType(ImType t, Used used) {
        t.match(new ImType.MatcherVoid() {

            @Override
            public void case_ImAnyType(ImAnyType imAnyType) {

            }

            @Override
            public void case_ImTupleType(ImTupleType tt) {
                for (ImType type : tt.getTypes()) {
                    visitType(type, used);
                }
            }

            @Override
            public void case_ImTypeVarRef(ImTypeVarRef tt) {

            }

            @Override
            public void case_ImVoid(ImVoid tt) {

            }

            @Override
            public void case_ImSimpleType(ImSimpleType tt) {

            }

            @Override
            public void case_ImArrayTypeMulti(ImArrayTypeMulti tt) {
                visitType(tt.getEntryType(), used);
            }

            @Override
            public void case_ImClassType(ImClassType tt) {
                visitClass(tt.getClassDef(), used);
                for (ImTypeArgument ta : tt.getTypeArguments()) {
                    visitType(ta.getType(), used);
                }
            }

            @Override
            public void case_ImArrayType(ImArrayType tt) {
                visitType(tt.getEntryType(), used);
            }
        });

    }
}
