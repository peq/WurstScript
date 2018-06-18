package de.peeeq.wurstscript.attributes.names;

import com.google.common.collect.ImmutableList;
import de.peeeq.datastructures.Deferred;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import fj.data.TreeMap;

import java.util.Collections;
import java.util.List;


public class TypeLink extends NameLink {
    private final Deferred<WurstType> type;
    private final TypeDef def;

    public TypeLink(Visibility visibility, WScope definedIn, List<TypeParamDef> typeParams, TypeDef def, Deferred<WurstType> type) {
        super(visibility, definedIn, typeParams);
        this.def = def;
        this.type = type;
    }

    public static TypeLink create(TypeDef def, WScope definedIn) {
        List<TypeParamDef> typeParams = Collections.emptyList();
        if (def instanceof AstElementWithTypeParameters) {
            typeParams = ImmutableList.copyOf(((AstElementWithTypeParameters) def).getTypeParameters());
        }
        // create deferred type to avoid cyclic dependencies
        Deferred<WurstType> type = new Deferred<>(def::attrTyp);
        return new TypeLink(calcVisibility(definedIn, def), definedIn, typeParams, def, type);
    }

    @Override
    public String getName() {
        return def.getName();
    }

    @Override
    public TypeDef getDef() {
        return def;
    }

    @Override
    public NameLink withVisibility(Visibility newVis) {
        return new TypeLink(newVis, getDefinedIn(), typeParams, def, type);
    }

    @Override
    public boolean receiverCompatibleWith(WurstType receiverType, Element location) {
        return receiverType == null;
    }

    @Override
    public TypeLink withTypeArgBinding(Element context, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding) {
        // TODO
        return this;
    }

    @Override
    public WurstType getTyp() {
        return def.attrTyp();
    }

    @Override
    public TypeLink withDef(NameDef def) {
        return new TypeLink(getVisibility(), getDefinedIn(), getTypeParams(), (TypeDef) def, type);
    }

    public WurstType getTyp(TreeMap<TypeParamDef, WurstTypeBoundTypeParam> mapping) {
        // TODO only set the type parameters bound here
        return def.attrTyp().setTypeArgs(mapping);
    }


    public TypeLink hidingPrivate() {
        return (TypeLink) super.hidingPrivate();
    }

    public TypeLink hidingPrivateAndProtected() {
        return (TypeLink) super.hidingPrivateAndProtected();
    }

}
