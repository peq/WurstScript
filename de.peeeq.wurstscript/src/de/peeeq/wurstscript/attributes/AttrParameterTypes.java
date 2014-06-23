package de.peeeq.wurstscript.attributes;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.Lists;

import de.peeeq.wurstscript.ast.ExtensionFuncDef;
import de.peeeq.wurstscript.ast.FuncDef;
import de.peeeq.wurstscript.ast.FunctionDefinition;
import de.peeeq.wurstscript.ast.NameDef;
import de.peeeq.wurstscript.ast.NativeFunc;
import de.peeeq.wurstscript.ast.TupleDef;
import de.peeeq.wurstscript.ast.WParameter;
import de.peeeq.wurstscript.types.WurstType;

public class AttrParameterTypes {

	public static List<WurstType> parameterTypesIncludingReceiver(FunctionDefinition f) {
		List<WurstType> result = Lists.newArrayList();
		if (f.attrReceiverType() != null) {
			result.add(f.attrReceiverType());
		}
		result.addAll(f.attrParameterTypes());
		return result;
	}
	
	public static List<WurstType> parameterTypes(FunctionDefinition f) {
		List<WurstType> result = Lists.newArrayList();
		for (WParameter p : f.getParameters()) {
			result.add(p.attrTyp());
		}
		return result;
	}

	public static @Nullable WurstType receiverType(FuncDef f) {
		if (f.attrIsDynamicClassMember()) {
			NameDef n = (NameDef) f.attrNearestStructureDef();
			return n.attrTyp();
		}
		return null;
	}
	
	public static WurstType receiverType(ExtensionFuncDef f) {
		return f.getExtendedType().attrTyp().dynamic();
	}

	public static @Nullable WurstType receiverType(TupleDef tupleDef) {
		return null;
	}
	
	public static @Nullable WurstType receiverType(NativeFunc f) {
		return null;
	}
	
}
