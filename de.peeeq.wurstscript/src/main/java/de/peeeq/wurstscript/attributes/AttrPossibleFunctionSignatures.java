package de.peeeq.wurstscript.attributes;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.FuncLink;
import de.peeeq.wurstscript.types.*;
import de.peeeq.wurstscript.types.FunctionSignature.ArgsMatchResult;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.utils.Utils;
import io.vavr.Tuple2;
import io.vavr.control.Option;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.peeeq.wurstscript.attributes.GenericsHelper.givenBinding;
import static de.peeeq.wurstscript.attributes.GenericsHelper.typeParameters;

public class AttrPossibleFunctionSignatures {

    public static ImmutableCollection<FunctionSignature> calculate(FunctionCall fc) {
        ImmutableCollection<FuncLink> fs = fc.attrPossibleFuncDefs();
        ImmutableCollection.Builder<FunctionSignature> resultBuilder = ImmutableList.builder();
        for (FuncLink f : fs) {
            FunctionSignature sig = FunctionSignature.fromNameLink(f);

            OptExpr implicitParameterOpt = AttrImplicitParameter.getFunctionCallImplicitParameter(fc, f, false);
            if (implicitParameterOpt instanceof Expr) {
                Expr expr = (Expr) implicitParameterOpt;
                VariableBinding mapping = expr.attrTyp().matchAgainstSupertype(sig.getReceiverType(), fc, sig.getMapping(), VariablePosition.RIGHT);
                if (mapping == null) {
                    // TODO error message? Or just ignore wrong parameter type?
                    continue;
                } else {
                    sig = sig.setTypeArgs(fc, mapping);
                }
            } // TODO else check?

            VariableBinding mapping = givenBinding(fc, sig.getDefinitionTypeVariables());
            sig = sig.setTypeArgs(fc, mapping);

            resultBuilder.add(sig);
        }
        return findBestSignature(fc, resultBuilder.build());
    }

    private static ImmutableCollection<FunctionSignature> findBestSignature(StmtCall fc, ImmutableCollection<FunctionSignature> res) {
        ImmutableCollection.Builder<FunctionSignature> resultBuilder2 = ImmutableList.builder();
        List<WurstType> argTypes = AttrFuncDef.argumentTypesPre(fc);
        for (FunctionSignature sig : res) {
            FunctionSignature sig2 = sig.matchAgainstArgs(argTypes, fc);
            if (sig2 == null) {
                continue;
            }
            Pair<FunctionSignature, List<CompileError>> typeClassMatched = findTypeClasses(sig2, fc);
            if (typeClassMatched.getB().isEmpty()) {
                resultBuilder2.add(typeClassMatched.getA());
            } else {
                for (CompileError err : typeClassMatched.getB()) {
                    fc.getErrorHandler().sendError(err);
                }
            }
        }
        ImmutableCollection<FunctionSignature> res2 = resultBuilder2.build();
        if (res2.isEmpty()) {
            // no signature matches precisely --> try to match as good as possible
            ImmutableList<ArgsMatchResult> match3 = res.stream()
                .map(sig -> sig.tryMatchAgainstArgs(argTypes, fc.getArgs(), fc))
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

    private static Pair<FunctionSignature, List<CompileError>> findTypeClasses(FunctionSignature sig, StmtCall fc) {
        List<CompileError> errors = new ArrayList<>();
        VariableBinding mapping = sig.getMapping();
        for (TypeParamDef tp : sig.getDefinitionTypeVariables()) {
            Option<WurstTypeBoundTypeParam> matchedTypeOpt = mapping.get(tp);
            List<WurstTypeInterface> constraints = getConstraints(tp);
            if (matchedTypeOpt.isEmpty()) {
                if (!constraints.isEmpty()) {
                    errors.add(new CompileError(fc.attrSource(), "Type parameter " + tp.getName() + " is not bound, so type constraints cannot be solved."));
                }
                continue;
            }
            WurstTypeBoundTypeParam matchedType = matchedTypeOpt.get();
            for (WurstTypeInterface constraint : constraints) {
                VariableBinding mapping2 = findTypeClass(fc, errors, mapping, tp, matchedType, constraint);
                if (mapping2 == null) {
                    errors.add(new CompileError(fc.attrSource(), "Type " + matchedType + " does not satisfy constraint " + tp.getName() + ": " + constraint + "."));
                } else {
                    mapping = mapping2;
                }
            }
        }
        sig = sig.setTypeArgs(fc, mapping);
        return Pair.create(sig, errors);
    }

    @NotNull
    private static List<WurstTypeInterface> getConstraints(TypeParamDef tp) {
        List<WurstTypeInterface> constraints = new ArrayList<>();
        if (tp.getTypeParamConstraints() instanceof TypeParamConstraintList) {
            for (TypeParamConstraint c : ((TypeParamConstraintList) tp.getTypeParamConstraints())) {
                WurstType ct = c.attrConstraintTyp();
                if (ct instanceof WurstTypeInterface) {
                    WurstTypeInterface wti = (WurstTypeInterface) ct;
                    constraints.add(wti);


                }
            }
        }
        return constraints;
    }

    private static VariableBinding findTypeClass(StmtCall fc, List<CompileError> errors, VariableBinding mapping, TypeParamDef tp, WurstTypeBoundTypeParam matchedType, WurstTypeInterface constraint1) {
        WurstTypeInterface constraint = (WurstTypeInterface) constraint1.setTypeArgs(mapping);

        // option 1: the matched type is a type param that also has the right constraint:
        if (matchedType.getBaseType() instanceof WurstTypeTypeParam) {
            WurstTypeTypeParam wtp = (WurstTypeTypeParam) matchedType.getBaseType();
            Optional<TypeParamConstraint> matchingConstraint = wtp.getTypeConstraints().stream()
                .filter(c -> c.attrConstraintTyp().isSubtypeOf(constraint, fc)).findFirst();
            if (matchingConstraint.isPresent()) {
                TypeClassInstance instance = TypeClassInstance.fromTypeParam(
                    fc, matchingConstraint.get());
                return mapping.set(tp, matchedType.withTypeClassInstance(instance));
            }
        }
        // option 2: find instance declarations
        // TODO create index to make this faster and use normal scoped lookup (ony search imports)
        WurstModel model = fc.getModel();
        List<TypeClassInstance> instances = model.stream()
            .flatMap(cu -> cu.getPackages().stream())
            .flatMap(p -> p.getElements().stream())
            .filter(e -> e instanceof InstanceDecl)
            .map(e -> (InstanceDecl) e)
            .flatMap(instance -> {
                WurstType instanceType = instance.getImplementedInterface().attrTyp();
                VariableBinding initialMapping = VariableBinding.emptyMapping().withTypeVariables(instance.getTypeParameters());
                VariableBinding match = instanceType.matchAgainstSupertype(constraint, fc, initialMapping, VariablePosition.LEFT);


                if (match == null) {
                    return Stream.empty();
                }
                instanceType = instanceType.setTypeArgs(match);


                for (Tuple2<TypeParamDef, WurstTypeBoundTypeParam> m : match) {
                    TypeParamDef instanceTp = m._1();
                    WurstTypeBoundTypeParam mType = m._2();
                    List<WurstTypeInterface> instanceConstraints = getConstraints(instanceTp);
                    for (WurstTypeInterface instanceConstraint : instanceConstraints) {
                        VariableBinding match2 = findTypeClass(fc, errors, match, instanceTp, mType, instanceConstraint);
                        if (match2 == null) {
                            return Stream.empty();
                        }
                        match = match2;
                    }
                }

                List<TypeClassInstance> deps = new ArrayList<>();
                List<WurstType> typeArgs = new ArrayList<>();
                for (TypeParamDef instanceTp : instance.getTypeParameters()) {
                    WurstTypeBoundTypeParam i = match.get(instanceTp).get();
                    deps.addAll(i.getInstances());
                    if (instanceTp.getTypeParamConstraints() instanceof TypeParamConstraintList) {
                        typeArgs.add(i);
                    }
                }


                // TODO resolve dependencies

                TypeClassInstance result = TypeClassInstance.fromInstance(instance, typeArgs, deps, (WurstTypeInterface) instanceType);

                return Stream.of(result);

            }).collect(Collectors.toList());

        if (instances.isEmpty()) {
            errors.add(new CompileError(fc,
                "Type " + matchedType + " does not satisfy constraint " + tp.getName() + ": " + constraint.getName()));
            // "Could not find type class instance " + constraint.getName() + " for type " + matchedType));
            return null;
        } else {
            if (instances.size() > 1) {
                errors.add(new CompileError(fc,
                    "There are multiple instances for type " + matchedType + " and constraint " + tp.getName() + ": " + constraint.getName() + "\n" +
                        Utils.printSep("\n", instances)));

            }
            TypeClassInstance instance = Utils.getFirst(instances);
            return mapping.set(tp, matchedType.withTypeClassInstance(instance));
        }
    }

    public static ImmutableCollection<FunctionSignature> calculate(ExprNewObject fc) {
        TypeDef typeDef = fc.attrTypeDef();
        if (!(typeDef instanceof ClassDef)) {
            return ImmutableList.of();
        }

        ClassDef classDef = (ClassDef) typeDef;

        List<ConstructorDef> constructors = classDef.getConstructors();

        ImmutableList.Builder<FunctionSignature> res = ImmutableList.builder();
        for (ConstructorDef f : constructors) {
            WurstType returnType = classDef.attrTyp().dynamic();
            VariableBinding binding2 = givenBinding(fc, typeParameters(classDef));
            List<WurstType> paramTypes = Lists.newArrayList();
            for (WParameter p : f.getParameters()) {
                paramTypes.add(p.attrTyp());
            }
            List<String> pNames = FunctionSignature.getParamNames(f.getParameters());
            List<TypeParamDef> typeParams = classDef.getTypeParameters();
            VariableBinding mapping = VariableBinding.emptyMapping().withTypeVariables(typeParams);
            FunctionSignature sig = new FunctionSignature(f, mapping, null, "construct", paramTypes, pNames, returnType);
            sig = sig.setTypeArgs(fc, binding2);
            res.add(sig);
        }
        return findBestSignature(fc, res.build());
    }

}
