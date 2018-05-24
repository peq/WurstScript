package de.peeeq.wurstscript.types;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.utils.Utils;
import fj.data.TreeMap;
import org.eclipse.jdt.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FunctionSignature {
    public static final FunctionSignature empty = new FunctionSignature(Collections.emptySet(), null, Collections.emptyList(), Collections.emptyList(), WurstTypeUnknown.instance(),
            false);
    private final @Nullable WurstType receiverType;
    private final List<WurstType> paramTypes;
    private final List<String> paramNames; // optional list of parameter names
    private final WurstType returnType;
    private final Collection<TypeParamDef> typeParams;
    private final boolean isVararg;


    public FunctionSignature(Collection<TypeParamDef> typeParams, @Nullable WurstType receiverType, List<WurstType> paramTypes, List<String> paramNames, WurstType returnType, boolean isVararg) {
        this.typeParams = typeParams;
        Preconditions.checkNotNull(paramTypes);
        Preconditions.checkNotNull(returnType);
        this.isVararg = isVararg;
        this.receiverType = receiverType;
        this.paramTypes = paramTypes;
        this.returnType = returnType;
        this.paramNames = paramNames;
    }


    public List<WurstType> getParamTypes() {
        return paramTypes;
    }

    public WurstType getReturnType() {
        return returnType;
    }

    public @Nullable WurstType getReceiverType() {
        return receiverType;
    }

    public FunctionSignature setTypeArgs(Element context, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> typeArgBinding) {
        WurstType r2 = returnType.setTypeArgs(typeArgBinding);
        List<WurstType> pt2 = Lists.newArrayList();
        for (WurstType p : paramTypes) {
            pt2.add(p.setTypeArgs(typeArgBinding));
        }
        Collection<TypeParamDef> typeParams2 = typeParams.stream()
                .filter(typeArgBinding::contains)
                .collect(Utils.toImmutableList());
        return new FunctionSignature(typeParams2, receiverType, pt2, paramNames, r2, isVararg);
    }


    public static FunctionSignature forFunctionDefinition(@Nullable FunctionDefinition f) {
//		return new FunctionSignature(def.attrReceiverType(), def.attrParameterTypes(), def.attrReturnTyp());
        if (f == null) {
            return FunctionSignature.empty;
        }
        WurstType returnType = f.attrReturnTyp();
        if (f instanceof TupleDef) {
            TupleDef tupleDef = (TupleDef) f;
            returnType = tupleDef.attrTyp().dynamic();
        }


        List<WurstType> paramTypes = f.attrParameterTypes();
        List<String> paramNames = getParamNames(f.getParameters());
        Collection<TypeParamDef> typeParams = Collections.emptyList();
        if (f instanceof AstElementWithTypeParameters) {
            typeParams = ((AstElementWithTypeParameters) f).getTypeParameters();
        }
        return new FunctionSignature(typeParams, f.attrReceiverType(), paramTypes, paramNames, returnType,
                f.getParameters().size() == 1 && f.getParameters().get(0).attrIsVararg());
    }


    public static List<String> getParamNames(WParameters parameters) {
        return parameters.stream()
                .map(WParameter::getName)
                .collect(Collectors.toList());
    }


    public static FunctionSignature fromNameLink(FuncLink f) {
        return new FunctionSignature(f.getTypeParams(), f.getReceiverType(), f.getParameterTypes(), getParamNames(f.getDef().getParameters()), f.getReturnType(), f.getDef().attrIsVararg());
    }


    public boolean isEmpty() {
        return receiverType == null && paramTypes.isEmpty() && returnType instanceof WurstTypeUnknown;
    }


    public String getParameterDescription() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(paramTypes.get(i).toString());
            if (i < paramNames.size()) {
                sb.append(" ");
                sb.append(paramNames.get(i));
            }
        }
        return sb.toString();
    }


    public String getParamName(int i) {
        if (i >= 0 && i < paramNames.size()) {
            return paramNames.get(i);
        } else if (isVararg) {
            return paramNames.get(paramNames.size() - 1);
        }
        return "";
    }

    public boolean isValidParameterNumber(int numParams) {
        if (isVararg) {
            return numParams >= paramTypes.size() - 1;
        } else {
            return numParams == paramTypes.size();
        }
    }

    public int getMinNumParams() {
        if (isVararg) {
            return paramTypes.size() - 1;
        } else {
            return paramTypes.size();
        }
    }

    public int getMaxNumParams() {
        if (isVararg) {
            return Integer.MAX_VALUE;
        } else {
            return paramTypes.size();
        }
    }

    public WurstType getParamType(int i) {
        if (isVararg && i >= paramTypes.size() - 1) {
            return getVarargType();
        }
        if (i >= 0 && i < paramTypes.size()) {
            return paramTypes.get(i);
        }
        throw new RuntimeException("Parameter index out of bounds: " + i);
    }


    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(returnType).append(" ");
        if (receiverType != null) {
            result.append(receiverType).append(".");
        }
        result.append("(");
        result.append(getParameterDescription());
        result.append(")");
        return result.toString();
    }

    public boolean isVararg() {
        return isVararg;
    }

    public WurstType getVarargType() {
        Preconditions.checkArgument(isVararg);
        return ((WurstTypeVararg) paramTypes.get(paramTypes.size() - 1)).getBaseType();
    }

    public FunctionSignature matchAgainstArgs(List<WurstType> argTypes, Element location) {
        if (!isValidParameterNumber(argTypes.size())) {
            return null;
        }
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping = WurstType.emptyMapping();
        for (int i = 0; i < argTypes.size(); i++) {
            WurstType pt = getParamType(i);
            WurstType at = argTypes.get(i);
            mapping = at.matchAgainstSupertype(pt, location, typeParams, mapping);
            if (mapping == null) {
                return null;
            }

        }

        return null;
    }
}
