package de.peeeq.wurstscript.types;

import com.google.common.collect.ImmutableList;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;
import fj.data.Option;
import fj.data.TreeMap;
import org.eclipse.jdt.annotation.Nullable;

import java.util.Collection;
import java.util.List;


public class WurstTypeClass extends WurstTypeClassOrInterface {

    private ClassDef classDef;


    public WurstTypeClass(ClassDef classDef, List<WurstTypeBoundTypeParam> typeParameters, boolean staticRef) {
        super(typeParameters, staticRef);
        if (classDef == null) throw new IllegalArgumentException();
        this.classDef = classDef;
    }

    @Override
    @Nullable TreeMap<TypeParamDef, WurstTypeBoundTypeParam> matchAgainstSupertypeIntern(WurstType obj, @Nullable Element location, Collection<TypeParamDef> typeParams, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping) {
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> superMapping = super.matchAgainstSupertypeIntern(obj, location, typeParams, mapping);
        if (superMapping != null) {
            return superMapping;
        }
        // check module instantiations:
        if (obj instanceof WurstTypeModuleInstanciation) {
            WurstTypeModuleInstanciation mi = (WurstTypeModuleInstanciation) obj;
            @Nullable ClassDef nearestClass = mi.getDef().attrNearestClassDef();
            if (nearestClass == this.classDef) {
                return extendMapping(mapping, getTypeArgBinding(), location);
            }
        }
        return null;


    }

    private TreeMap<TypeParamDef, WurstTypeBoundTypeParam> extendMapping(TreeMap<TypeParamDef, WurstTypeBoundTypeParam> m1, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> m2, Element location) {
        for (TypeParamDef t : m2.keys()) {
            Option<WurstTypeBoundTypeParam> currentVal = m1.get(t);
            WurstTypeBoundTypeParam m2Val = m2.get(t).some();
            if (currentVal.isSome()) {
                WurstTypeBoundTypeParam m1Val = currentVal.some();
                if (!m1Val.equalsType(m2Val, location)) {
                    // no match
                    return null;
                }
            } else {
                m1 = m1.set(t, m2Val);
            }
        }
        return m1;
    }

    public ImmutableList<WurstTypeInterface> implementedInterfaces() {
        return classDef.getImplementsList().stream()
                .map(i -> (WurstTypeInterface) i.attrTyp().setTypeArgs(getTypeArgBinding()))
                .filter(i -> i.level() < level())
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * A type for the class extended by this class (or null if none)
     */
    public @Nullable WurstTypeClass extendedClass() {
        OptTypeExpr extendedClass = classDef.getExtendedClass();
        if (extendedClass instanceof NoTypeExpr) {
            return null;
        }
        WurstType t = extendedClass.attrTyp();
        if (t instanceof WurstTypeClass) {
            WurstTypeClass ct = (WurstTypeClass) t;
            if (ct.level() >= level()) {
                // cyclic dependency
                return null;
            }
            return (WurstTypeClass) ct.setTypeArgs(getTypeArgBinding());
        }
        return null;
    }

    @Override
    public ClassDef getDef() {
        return classDef;
    }

    @Override
    public ImmutableList<? extends WurstTypeClassOrInterface> directSupertypes() {
        WurstTypeClass ec = extendedClass();
        if (ec == null) {
            return implementedInterfaces();
        } else {
            ImmutableList.Builder<WurstTypeClassOrInterface> builder = ImmutableList.builder();
            builder.add(ec);
            builder.addAll(implementedInterfaces());
            return builder.build();
        }
    }

    public ClassDef getClassDef() {
        return classDef;
    }

    @Override
    public String getName() {
        return getDef().getName() + printTypeParams();
    }

    @Override
    public WurstType dynamic() {
        return new WurstTypeClass(getClassDef(), getTypeParameters(), false);
    }

    @Override
    public WurstType replaceTypeVars(List<WurstTypeBoundTypeParam> newTypes) {
        return new WurstTypeClass(classDef, newTypes, isStaticRef());
    }

    @Override
    public ImType imTranslateType() {
        return TypesHelper.imInt();
    }

    @Override
    public ImExprOpt getDefaultValue() {
        return JassIm.ImIntVal(0);
    }

}
