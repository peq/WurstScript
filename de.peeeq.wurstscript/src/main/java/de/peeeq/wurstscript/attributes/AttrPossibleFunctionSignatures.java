package de.peeeq.wurstscript.attributes;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.types.FunctionSignature;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.types.WurstTypeUnknown;
import fj.data.TreeMap;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AttrPossibleFunctionSignatures {

    public static ImmutableCollection<FunctionSignature> calculate(FunctionCall fc) {
        ImmutableCollection<FuncLink> fs = fc.attrPossibleFuncDefs();
        ImmutableCollection.Builder<FunctionSignature> resultBuilder = ImmutableList.builder();
        for (FuncLink f : fs) {
            FunctionSignature sig = FunctionSignature.fromNameLink(f);

            if (fc.attrImplicitParameter() instanceof Expr) {
                Expr expr = (Expr) fc.attrImplicitParameter();
                sig = sig.setTypeArgs(fc, expr.attrTyp().getTypeArgBinding());
            }
// TODO handle type arguments by matching?
//            sig = sig.setTypeArgs(fc, fc.attrTypeParameterBindings());
            resultBuilder.add(sig);
        }
        ImmutableCollection.Builder<FunctionSignature> resultBuilder2 = ImmutableList.builder();
        ImmutableCollection<FunctionSignature> res = resultBuilder.build();
        for (FunctionSignature sig : res) {
            if (paramTypesCanMatch(sig.getParamTypes(), AttrFuncDef.argumentTypes(fc), fc)) {
                resultBuilder2.add(sig);
            }
        }
        ImmutableCollection<FunctionSignature> res2 = resultBuilder2.build();
        if (res2.isEmpty()) {
            return res;
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
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding2 = WurstType.emptyMapping();
        List<WurstType> paramTypes = Lists.newArrayList();
        for (WParameter p : f.getParameters()) {
            paramTypes.add(p.attrTyp());
        }
        List<String> pNames = FunctionSignature.getParamNames(f.getParameters());
        Collection<TypeParamDef> typeParams = Collections.emptyList();
        if (struct instanceof AstElementWithTypeParameters) {
            typeParams = ((AstElementWithTypeParameters) struct).getTypeParameters();
        }
        FunctionSignature sig = new FunctionSignature(typeParams, null, paramTypes, pNames, returnType, f.attrIsVararg());
        sig.setTypeArgs(fc, binding2);
        return ImmutableList.of(sig);
    }

}
