package de.peeeq.wurstscript.types;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.FuncDef;
import de.peeeq.wurstscript.ast.TypeParamDef;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.attributes.ImplicitFuncs;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;
import de.peeeq.wurstscript.utils.Utils;

public class WurstTypeBoundTypeParam extends WurstType {

	
	private final TypeParamDef typeParamDef;
	private final WurstType baseType;
	private FuncDef fromIndex;
	private FuncDef toIndex;
	private boolean indexInitialized = false;
	private AstElement context;

	public WurstTypeBoundTypeParam(TypeParamDef def, WurstType baseType, AstElement context) {
		this.typeParamDef = def;
		this.baseType = baseType;
		this.context = context;
	}
	
	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		return baseType.isSubtypeOfIntern(other, location);
	}

	@Override
	public String getName() {
		return baseType.getName();
//		return "[" + typeParamDef.getName() + ": " + baseType + "]";
	}

	@Override
	public String getFullName() {
		return typeParamDef.getName() + "<--" + baseType.getFullName();
	}


	public WurstType getBaseType() {
		return baseType;
	}

	@Override
	public ImType imTranslateType() {
		return baseType.imTranslateType();
	}

	@Override
	public ImExprOpt getDefaultValue() {
		return JassIm.ImIntVal(0);
	}

	
	@Override
	public WurstType dynamic() {
		return baseType.dynamic();
	}
	
	@Override
	public boolean canBeUsedInInstanceOf() {
		return baseType.canBeUsedInInstanceOf();
	}
	
	@Override
	public boolean allowsDynamicDispatch() {
		return baseType.allowsDynamicDispatch();
	}
	
	@Override
	public void addMemberMethods(AstElement node, String name,
			List<NameLink> result) {
		baseType.addMemberMethods(node, name, result);
	}
	
	@Override
	public Stream<NameLink> getMemberMethods(AstElement node) {
		return baseType.getMemberMethods(node);
	}
	
	@Override
	public boolean isStaticRef() {
		return baseType.isStaticRef();
	}
	
	@Override
	public boolean isCastableToInt() {
		return true; // because baseType must always be castable to int 
		//return baseType.isCastableToInt();
	}
	
	@Override
	public WurstType normalize() {
		return baseType.normalize();
	}

	public FuncDef getFromIndex() {
		initIndex();
		return fromIndex;
	}
	
	public FuncDef getToIndex() {
		initIndex();
		return toIndex;
	}

	private void initIndex() {
		if (indexInitialized) {
			return;
		}
		// if type does support generics natively, try to find implicit conversion functions 
		if (!baseType.supportsGenerics()) {
			fromIndex = ImplicitFuncs.findFromIndexFunc(baseType, context);
			toIndex = ImplicitFuncs.findToIndexFunc(baseType, context);
		} else if (baseType instanceof WurstTypeBoundTypeParam) {
			WurstTypeBoundTypeParam bt = (WurstTypeBoundTypeParam) baseType;
			fromIndex = bt.getFromIndex();
			toIndex = bt.getToIndex();
		}
		indexInitialized = true;
	}
	
	@Override
	public boolean supportsGenerics() {
		return baseType.supportsGenerics()
				|| getFromIndex() != null && getToIndex() != null;
	}

	@Override
	public WurstTypeBoundTypeParam setTypeArgs(Map<TypeParamDef, WurstTypeBoundTypeParam> typeParamMapping) {
		return this.withBaseType(baseType.setTypeArgs(typeParamMapping));
	}
	
//	public WurstTypeBoundTypeParam applyBinding(Map<TypeParamDef, WurstTypeBoundTypeParam> binding) {
//		if (baseType instanceof WurstTypeTypeParam) {
//			WurstTypeTypeParam tp = (WurstTypeTypeParam) baseType;
//			if (binding.containsKey(tp.getDef())) {
//				return this.withBaseType(binding.get(tp.getDef()));
//			}
//		} else if (baseType instanceof WurstTypeBoundTypeParam) {
//			WurstTypeBoundTypeParam bt = (WurstTypeBoundTypeParam) baseType;
//			return this.withBaseType(bt.applyBinding(binding));
//		}
//		return this.baseType.
//		return this;
//	}

	private WurstTypeBoundTypeParam withBaseType(WurstType t) {
		if (t == baseType) {
			return this;
		}
		return new WurstTypeBoundTypeParam(typeParamDef, t, context);
	}
	
	public TypeParamDef getTypeParamDef() {
		return typeParamDef;
	}

	@Override
	public boolean isTranslatedToInt() {
		return baseType.isTranslatedToInt();
	}
	
}