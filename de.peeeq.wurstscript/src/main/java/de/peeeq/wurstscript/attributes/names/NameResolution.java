package de.peeeq.wurstscript.attributes.names;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.utils.Utils;
import fj.data.TreeMap;
import org.eclipse.jdt.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NameResolution {

    public static ImmutableCollection<FuncLink> lookupFuncsNoConfig(Element node, String name, boolean showErrors) {
        StructureDef nearestStructureDef = node.attrNearestStructureDef();
        if (nearestStructureDef != null) {
            // inside a class one can write foo instead of this.foo()
            // so the receiver type is implicitly given by the enclosing class
            WurstType receiverType = nearestStructureDef.attrTyp();
            ImmutableCollection<FuncLink> funcs = node.lookupMemberFuncs(receiverType, name, showErrors);
            if (!funcs.isEmpty()) {
                return funcs;
            }
        }


        List<FuncLink> result = Lists.newArrayList();
        WScope scope = node.attrNearestScope();
        while (scope != null) {
            for (DefLink n : scope.attrNameLinks().get(name)) {
                if (n instanceof FuncLink && n.getReceiverType() == null) {
                    if (!result.contains(n)) {
                        result.add((FuncLink) n);
                    }
                }
            }
            scope = nextScope(scope);
        }
        return removeDuplicates(result);
    }

    public static ImmutableCollection<FuncLink> lookupFuncs(Element e, String name, boolean showErrors) {
        ArrayList<FuncLink> result = Lists.newArrayList(e.lookupFuncsNoConfig(name, showErrors));
        for (int i = 0; i < result.size(); i++) {
            result.set(i, result.get(i).withConfigDef());
        }
        return ImmutableList.copyOf(result);
    }

    private static <T extends NameLink> ImmutableCollection<T> removeDuplicates(List<T> nameLinks) {
        List<T> result = Lists.newArrayList();
        nextLink:
        for (T nl : nameLinks) {
            for (T other : result) {
                if (other.getDef() == nl.getDef()) {
                    continue nextLink;
                }
            }
            result.add(nl);
        }
        return ImmutableList.copyOf(result);
    }

    private static @Nullable WScope nextScope(WScope scope) {
        Element parent = scope.getParent();
        if (parent == null) {
            return null;
        }
        WScope currentScope = scope;
        if (currentScope instanceof ModuleInstanciation) {
            ModuleInstanciation moduleInstanciation = (ModuleInstanciation) currentScope;
            // for module instanciations the next scope is the package in which
            // the module was defined
            return nextScope(moduleInstanciation.attrModuleOrigin());
        }
        return parent.attrNearestScope();
    }

    public static ImmutableCollection<FuncLink> lookupMemberFuncs(Element node, WurstType receiverType, String name, boolean showErrors) {
        List<FuncLink> result = Lists.newArrayList();
        WScope scope = node.attrNearestScope();
        while (scope != null) {
            for (DefLink n : scope.attrNameLinks().get(name)) {
                if (!(n instanceof FuncLink)) {
                    continue;
                }
                DefLink n2 = matchDefLinkReceiver(n, receiverType, node, false);
                if (n2 != null) {
                    FuncLink f = (FuncLink) n2;
                    result.add(f);
                }
            }
            scope = nextScope(scope);
        }
        addMemberMethods(node, receiverType, name, result);
        return removeDuplicates(result);
    }

    public static void addMemberMethods(Element node,
                                        WurstType receiverType, String name, List<FuncLink> result) {
        receiverType.addMemberMethods(node, name, result);
    }

    public static NameLink lookupVarNoConfig(Element node, String name, boolean showErrors) {
        WurstType receiverType;
        @Nullable
        StructureDef nearestStructureDef = node.attrNearestStructureDef();
        if (nearestStructureDef != null) {
            // inside a class one can write bar instead of this.bar
            // so the receiver type is implicitly given by the enclosing class
            receiverType = nearestStructureDef.attrTyp();
        } else {
            receiverType = null;
        }

        NameLink privateCandidate = null;
        List<NameLink> candidates = Lists.newArrayList();


        WScope scope = node.attrNearestScope();
        while (scope != null) {
//			WLogger.info("searching " + receiverType + "." + name + " in scope " + Utils.printElement(scope));
//			WLogger.info("		" + scope.attrNameLinks());

            for (DefLink n : scope.attrNameLinks().get(name)) {
                WurstType n_receiverType = n.getReceiverType();
                if (n instanceof VarLink) {

                    if (n_receiverType != null) {
                        // when we have a receiver type we have to check that it matches with the receiver type of the variable
                        // for example vec2.x could be in scope so we have to check that the current receiver is a vec2
                        if (receiverType == null) {
                            continue;
                        }
                        if (!receiverType.isSubtypeOf(n_receiverType, node)
                                && !receiverType.isNestedInside(n_receiverType)) {
                            continue;
                        }
                    }
//						&& (n_receiverType == null
//						|| (receiverType != null && receiverType.isSubtypeOf(n_receiverType, node))
//						)) {

                    if (n.getVisibility() != Visibility.PRIVATE_OTHER
                            && n.getVisibility() != Visibility.PROTECTED_OTHER) {
                        candidates.add(n);
                    } else if (privateCandidate == null) {
                        privateCandidate = n;
                    }

                } else if (n instanceof TypeDefLink) {
                    candidates.add(n);
                }
            }
            if (candidates.size() > 0) {
                if (showErrors && candidates.size() > 1) {
                    node.addError("Reference to variable " + name + " is ambiguous. Alternatives are:\n"
                            + Utils.printAlternatives(candidates));
                }
                return candidates.get(0);
            }
            scope = nextScope(scope);
        }
        if (showErrors) {
            if (privateCandidate == null) {
                node.addError("Could not find variable " + name + ".");
            } else {
                node.addError(Utils.printElementWithSource(privateCandidate.getDef()) + " is not visible inside this package." +
                        " If you want to access it, declare it public.");
                return privateCandidate;
            }
        }
        return null;
    }

    public static NameLink lookupMemberVar(Element node, WurstType receiverType, String name, boolean showErrors) {
        WScope scope = node.attrNearestScope();
        while (scope != null) {
            for (DefLink n : scope.attrNameLinks().get(name)) {
                if (!(n instanceof VarLink)) {
                    continue;
                }
                DefLink n2 = matchDefLinkReceiver(n, receiverType, node, showErrors);
                if (n2 != null) {
                    return n2;
                }
            }
            scope = nextScope(scope);
        }
        return null;
    }

    public static DefLink matchDefLinkReceiver(DefLink n, WurstType receiverType, Element node, boolean showErrors) {
        WurstType n_receiverType = n.getReceiverType();
        if (n_receiverType == null) {
            return null;
        }
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping = receiverType.matchAgainstSupertype(n_receiverType, node, n.getTypeParams(), WurstType.emptyMapping());
        if (mapping == null) {
            return null;
        }
        if (showErrors) {
            if (n.getVisibility() == Visibility.PRIVATE_OTHER) {
                node.addError(Utils.printElement(n.getDef()) + " is private and cannot be used here.");
            }
            if (n.getVisibility() == Visibility.PROTECTED_OTHER) {
                node.addError(Utils.printElement(n.getDef()) + " is protected and cannot be used here.");
            }
        }
        return n.withTypeArgBinding(node, mapping);
    }

    public static @Nullable TypeDef lookupType(Element node, String name, boolean showErrors) {

        NameLink privateCandidate = null;
        List<NameLink> candidates = Lists.newArrayList();

        WScope scope = node.attrNearestScope();
        while (scope != null) {
            for (NameLink n : scope.attrTypeNameLinks().get(name)) {
                if (n.getDef() instanceof TypeDef) {
                    if (n.getVisibility() != Visibility.PRIVATE_OTHER
                            && n.getVisibility() != Visibility.PROTECTED_OTHER) {
                        candidates.add(n);
                    } else if (privateCandidate == null) {
                        privateCandidate = n;
                    }
                }
            }
            if (candidates.size() > 0) {
                if (showErrors && candidates.size() > 1) {
                    node.addError("Reference to type " + name + " is ambiguous. Alternatives are:\n"
                            + Utils.printAlternatives(candidates));
                }
                return (TypeDef) candidates.get(0).getDef();
            }
            scope = nextScope(scope);
        }
        if (showErrors) {
            if (privateCandidate == null) {
                node.addError("Could not find type " + name + ".");
            } else {
                node.addError(Utils.printElementWithSource(privateCandidate.getDef()) + " is not visible inside this package." +
                        " If you want to access it, declare it public.");
                return (TypeDef) privateCandidate.getDef();
            }
        }
        return null;
    }

    public static PackageLink lookupPackage(Element node, String name, boolean showErrors) {
        WScope scope = node.attrNearestScope();
        while (scope != null) {
            for (NameLink n : scope.attrTypeNameLinks().get(name)) {
                if (n instanceof PackageLink) {
                    return (PackageLink) n;
                }
            }
            scope = nextScope(scope);
        }
        return null;
    }

    public static ImmutableCollection<FuncLink> lookupFuncsShort(Element elem, String name) {
        return lookupFuncs(elem, name, true);
    }

    public static ImmutableCollection<FuncLink> lookupMemberFuncsShort(Element elem, WurstType receiverType, String name) {
        return lookupMemberFuncs(elem, receiverType, name, true);
    }

    public static NameLink lookupVarShort(Element node, String name) {
        return lookupVar(node, name, true);
    }

    public static NameLink lookupMemberVarShort(Element node, WurstType receiverType, String name) {
        return lookupMemberVar(node, receiverType, name, true);
    }

    public static @Nullable TypeDef lookupTypeShort(Element node, String name) {
        return lookupType(node, name, true);
    }

    public static PackageLink lookupPackageShort(Element node, String name) {
        return lookupPackage(node, name, true);
    }

    public static NameLink lookupVar(Element e, String name, boolean showErrors) {
        NameLink v = e.lookupVarNoConfig(name, showErrors);
        if (v != null) {
            NameDef actual = v.getDef().attrConfigActualNameDef();
            return v.withDef(actual);
        }
        return null;
    }


}
