package de.peeeq.wurstscript.attributes.names;

import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.types.WurstTypePackage;
import fj.data.TreeMap;

import java.util.Collections;
import java.util.List;


public class PackageLink extends DefLink {
    private final WPackage def;

    public PackageLink(Visibility visibility, WScope definedIn, WPackage def) {
        super(visibility, definedIn, Collections.emptyList(), null);
        this.def = def;
    }

    public static PackageLink create(WPackage def, WScope definedIn) {
        return new PackageLink(calcVisibility(definedIn, def), definedIn, def);
    }

    @Override
    public String getName() {
        return def.getName();
    }

    @Override
    public WPackage getDef() {
        return def;
    }

    @Override
    public PackageLink withVisibility(Visibility newVis) {
        return new PackageLink(newVis, getDefinedIn(), def);
    }

    @Override
    public boolean receiverCompatibleWith(WurstType receiverType, Element location) {
        return receiverType == null;
    }

    @Override
    public NameLinkType getType() {
        return null;
    }

    @Override
    public PackageLink withTypeArgBinding(Element context, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding) {
        // packages do not have type parameters
        return this;
    }

    @Override
    public DefLink withGenericTypeParams(List<TypeParamDef> typeParams) {
        return this;
    }

    @Override
    public WurstType getTyp() {
        return new WurstTypePackage(def);
    }

    @Override
    public PackageLink withDef(NameDef def) {
        return new PackageLink(getVisibility(), getDefinedIn(), (WPackage) def);
    }


    public PackageLink hidingPrivate() {
        return (PackageLink) super.hidingPrivate();
    }

    public PackageLink hidingPrivateAndProtected() {
        return (PackageLink) super.hidingPrivateAndProtected();
    }

}
