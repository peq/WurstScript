package de.peeeq.wurstscript.attributes.names;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeBoundTypeParam;
import de.peeeq.wurstscript.types.WurstTypeVararg;
import de.peeeq.wurstscript.utils.Utils;
import fj.data.TreeMap;
import org.eclipse.jdt.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public abstract class DefLink extends NameLink {
    private final @Nullable WurstType receiverType;


    public DefLink(Visibility visibility, WScope definedIn, List<TypeParamDef> typeParams, @Nullable WurstType receiverType) {
        super(visibility, definedIn, typeParams);
        this.receiverType = receiverType;
    }

    /**
     *
     * Replace with instanceof check
     */
    @Deprecated
    public abstract NameLinkType getType();


    protected static @Nullable WurstType getReceiverType(WScope definedIn) {
        if (definedIn instanceof ClassDef) {
            ClassDef classDef = (ClassDef) definedIn;
            return classDef.attrTyp();
        } else if (definedIn instanceof InterfaceDef) {
            InterfaceDef interfaceDef = (InterfaceDef) definedIn;
            return interfaceDef.attrTyp();
        } else if (definedIn instanceof ModuleInstanciation) {
            ModuleInstanciation moduleInstanciation = (ModuleInstanciation) definedIn;
            return moduleInstanciation.attrTyp();
        } else if (definedIn instanceof ModuleDef) {
            ModuleDef moduleDef = (ModuleDef) definedIn;
            return moduleDef.attrTyp();
        }
        return null;
    }


    protected static Stream<TypeParamDef> typeParams(Element scope) {
        if (scope instanceof AstElementWithTypeParameters) {
            return ((AstElementWithTypeParameters) scope).getTypeParameters().stream();
        }
        return Stream.of();
    }




    public @Nullable WurstType getReceiverType() {
        return receiverType;
    }


    public DefLink hidingPrivate() {
        return (DefLink) super.hidingPrivate();
    }

    public DefLink hidingPrivateAndProtected() {
        return (DefLink) super.hidingPrivateAndProtected();
    }

    @Override
    public boolean receiverCompatibleWith(WurstType receiverType, Element location) {
        if (this.receiverType == null) {
            return receiverType == null;
        }
        return this.receiverType.isSubtypeOf(receiverType, location);
    }

    /**
     * Tries to adapt this function to the given receiver
     * Setting type arguments appropriately
     */
    public @Nullable DefLink adaptToReceiverType(WurstType receiverType) {
        if (this.receiverType == null) {
            if (receiverType == null) {
                return this;
            } else {
                return null;
            }
        }
        NameDef def = getDef();
        TreeMap<TypeParamDef, WurstTypeBoundTypeParam> match = this.receiverType.matchAgainstSupertype(receiverType, def, typeParams, WurstType.emptyMapping());
        if (match == null) {
            return null;
        }
        return withTypeArgBinding(def, match);
    }


    @Override
    public abstract DefLink withTypeArgBinding(Element context, TreeMap<TypeParamDef, WurstTypeBoundTypeParam> binding);

    public abstract DefLink withGenericTypeParams(List<TypeParamDef> typeParams);
}
