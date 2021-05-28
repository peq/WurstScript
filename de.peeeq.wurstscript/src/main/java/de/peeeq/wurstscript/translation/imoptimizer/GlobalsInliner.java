package de.peeeq.wurstscript.translation.imoptimizer;

import com.google.common.collect.Sets;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.utils.Utils;
import de.peeeq.wurstscript.validation.TRVEHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GlobalsInliner implements OptimizerPass {

    public int optimize(ImTranslator trans) {
        int obsoleteCount = 0;
        ImProg prog = trans.getImProg();
        prog.clearAttributes(); // TODO only clear read/write attributes

        Set<ImVar> obsoleteVars = Sets.newLinkedHashSet();
        for (final ImVar v : prog.getGlobals()) {
            if (trans.isUnitTestMode() && v.getName().equals("MagicFunctions_compiletime")) {
                // in unit test mode we run tests and compiletime functions with optimizations,
                // so it is important, that we do not optimize away the compiletime constant
                continue;
            }
            if (v.getType() instanceof ImArrayType
                || v.getType() instanceof ImArrayTypeMulti) {
                // cannot optimize arrays yet
                continue;
            }
            if (TRVEHelper.protectedVariables.contains(v.getName())) {
                // keep TRVE vars
                continue;
            }

            if (v.attrWrites().size() == 1) {
                ImExpr right = null;
                ImVarWrite obs = null;
                for (ImVarWrite write : v.attrWrites()) {
                    ImFunction func = write.getNearestFunc();
                    if (isInInit(func)) {
                        right = write.getRight();
                        obs = write;
                        break;
                    }
                }
                if (obs == null) {
                    continue;
                }

                ImExpr replacement = findReplacement(right, obs);
                if (replacement != null) {
                    for (ImVarRead v3 : v.attrReads()) {
                        v3.replaceBy(replacement.copy());
                    }
                }
                if (replacement != null || v.attrReads().size() == 0) {
                    obsoleteVars.add(v);
                }
            } else if (v.attrWrites().size() > 1 && !(v.getType() instanceof ImTupleType)) {
                List<ImVarWrite> initWrites = v.attrWrites().stream().filter(write -> {
                    ImFunction nearestFunc = write.getNearestFunc();
                    return isInInit(nearestFunc);
                }).collect(Collectors.toList());
                if (initWrites.size() == 1) {
                    ImExpr write = v.attrWrites().iterator().next().getRight();
                    try {
                        ImExpr defaultValue = ImHelper.defaultValueForType((ImSimpleType) v.getType());
                        boolean isDefault = defaultValue.structuralEquals(write);
                        if (isDefault) {
                            // Assignment is default value and can be removed
                            v.attrWrites().iterator().next().replaceBy(ImHelper.nullExpr());
                        }
                    } catch (Exception e) {
                        throw new CompileError(write.attrTrace().attrErrorPos(),
                            "Could not inline " + Utils.printElementWithSource(Optional.of(v.getTrace())),
                            CompileError.ErrorType.ERROR, e);
                    }
                }
            }

        }
        obsoleteCount += obsoleteVars.size();
        for (ImVar i : obsoleteVars) {
            // remove the write
            if (i.attrWrites().size() > 0) {
                ImVarWrite write = Utils.getFirstAndOnly(i.attrWrites());
                if (write.getParent() != null) {
                    write.replaceBy(write.getRight().copy());
                }
            }
        }
        prog.getGlobals().removeAll(obsoleteVars);
        return obsoleteCount;
    }

    @Nullable
    private ImExpr findReplacement(ImExpr right, ImVarWrite obs) {
        ImExpr replacement;
        if (right instanceof ImIntVal) {
            ImIntVal val = (ImIntVal) right;
            replacement = (JassIm.ImIntVal(val.getValI()));
            if (obs.getParent() != null)
                obs.replaceBy(ImHelper.nullExpr());
        } else if (right instanceof ImRealVal) {
            ImRealVal val = (ImRealVal) right;
            replacement = (JassIm.ImRealVal(val.getValR()));
            if (obs.getParent() != null)
                obs.replaceBy(ImHelper.nullExpr());
        } else if (right instanceof ImStringVal) {
            ImStringVal val = (ImStringVal) right;
            replacement = (JassIm.ImStringVal(val.getValS()));
            if (obs.getParent() != null)
                obs.replaceBy(ImHelper.nullExpr());
        } else if (right instanceof ImBoolVal) {
            ImBoolVal val = (ImBoolVal) right;
            replacement = (JassIm.ImBoolVal(val.getValB()));
            if (obs.getParent() != null)
                obs.replaceBy(ImHelper.nullExpr());
        } else {
            replacement = null;
        }
        return replacement;
    }

    @Override
    public String getName() {
        return "Globals Inlined";
    }


    private static boolean isInInit(ImFunction func) {
        return func != null && (func.getName().startsWith("init_") || func.getName().equals("main") || func.getName().startsWith("InitTrig_")
                || func.getName().equals("initGlobals"));
    }

}
