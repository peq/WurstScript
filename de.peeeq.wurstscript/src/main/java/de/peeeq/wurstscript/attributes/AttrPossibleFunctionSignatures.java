package de.peeeq.wurstscript.attributes;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.types.FunctionSignature;
import de.peeeq.wurstscript.types.FunctionSignature.ArgsMatchResult;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.types.WurstTypeUnknown;
import fj.data.TreeMap;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AttrPossibleFunctionSignatures {

    public static ImmutableCollection<FunctionSignature> calculate(FunctionCall fc) {
        ImmutableCollection<FuncLink> fs = fc.attrPossibleFuncDefs();
        ImmutableCollection.Builder<FunctionSignature> resultBuilder = ImmutableList.builder();
        for (FuncLink f : fs) {
            FunctionSignature sig = FunctionSignature.fromNameLink(f);

            if (fc.attrImplicitParameter() instanceof Expr) {
                Expr expr = (Expr) fc.attrImplicitParameter();
                TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping = expr.attrTyp().matchAgainstSupertype(sig.getReceiverType(), fc, sig.getTypeParams(), WurstType.emptyMapping());
                if (mapping == null) {
                    // TODO error message? Or just ignore wrong parameter type?
                    continue;
                } else {
                    sig = sig.setTypeArgs(fc, mapping);
                }
            } // TODO else check?

            TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping = GenericsHelper.givenBinding(fc, sig.getTypeParams());
            sig = sig.setTypeArgs(fc, mapping);

            resultBuilder.add(sig);
        }
        ImmutableCollection.Builder<FunctionSignature> resultBuilder2 = ImmutableList.builder();
        ImmutableCollection<FunctionSignature> res = resultBuilder.build();
        List<WurstType> argTypes = AttrFuncDef.argumentTypes(fc);
        for (FunctionSignature sig : res) {
            FunctionSignature sig2 = sig.matchAgainstArgs(argTypes, fc);
            if (sig2 != null) {
                resultBuilder2.add(sig2);
            }
        }
        ImmutableCollection<FunctionSignature> res2 = resultBuilder2.build();
        if (res2.isEmpty()) {
            // no signature matches precisely --> try to match as good as possible
            ImmutableList<ArgsMatchResult> match3 = res.stream()
                    .map(sig -> sig.tryMatchAgainstArgs(argTypes, fc.getArgs(),  fc))
                    .collect(ImmutableList.toImmutableList());

            if (match3.isEmpty()) {
                return ImmutableList.of();
            } else {
                // add errors from best match (minimal badness)
                ArgsMatchResult min = Collections.min(match3, Comparator.comparing(ArgsMatchResult::getBadness));
                for (CompileError c : min.getErrors()) {
                    fc.getErrorHandler().sendError(c);
                }

                return match3.stream()
                        .map(ArgsMatchResult::getSig)
                        .collect(ImmutableList.toImmutableList());
            }
        } else {
            return res2;
        }
    }

    private static boolean paramTypesCanMatch(List<WurstType> paramTypes, List<WurstType> argTypes, Element location) {
        if (argTypes.size() > paramTypes.size()) {
            return false;
        }
        for (int i = 0; i < argTypes.size(); i++) {
            if (!argTypes.get(i).isSubtypeOf(paramTypes.get(i), location)) {
                if (!(argTypes.get(i) instanceof WurstTypeUnknown)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static ImmutableCollection<FunctionSignature> calculate(ExprNewObject fc) {
        ConstructorDef f = fc.attrConstructorDef();
        if (f == null) {
            return ImmutableList.of();
        }
        StructureDef struct = f.attrNearestStructureDef();
        assert struct != null; // because constructors can only appear inside a StructureDef

        WurstType returnType = struct.attrTyp().dynamic();
        // TODO get binding by matching args
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding2 = GenericsHelper.givenBinding(fc, GenericsHelper.typeParameters(struct));
        List<WurstType> paramTypes = Lists.newArrayList();
        for (WParameter p : f.getParameters()) {
            paramTypes.add(p.attrTyp());
        }
        List<String> pNames = FunctionSignature.getParamNames(f.getParameters());
        List<TypeParamDef> typeParams = Collections.emptyList();
        if (struct instanceof AstElementWithTypeParameters) {
            typeParams = ((AstElementWithTypeParameters) struct).getTypeParameters();
        }
        FunctionSignature sig = new FunctionSignature(typeParams, null, "construct", paramTypes, pNames, returnType);
        sig = sig.setTypeArgs(fc, binding2);
        return ImmutableList.of(sig);
    }

}
