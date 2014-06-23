package de.peeeq.wurstscript.types;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.Expr;
import de.peeeq.wurstscript.ast.OptExpr;

public class CallSignature {
	private final @Nullable Expr receiver;
	private final List<Expr> arguments;
	
	public CallSignature(@Nullable OptExpr optExpr, List<Expr> arguments) {
		if (optExpr instanceof Expr) {
			this.receiver = (Expr) optExpr;
		} else {
			this.receiver = null;
		}
		this.arguments = arguments;
	}
	
	public List<Expr> getArguments() {
		return arguments;
	}

	public @Nullable Expr getReceiver() {
		return receiver;
	}
	
	public void checkSignatureCompatibility(FunctionSignature sig, String funcName, AstElement pos) {
		if (sig.isEmpty()) {
			return;
		}
		Expr l_receiver = receiver;
		if (l_receiver != null) {
			if (sig.getReceiverType() == null) {
				l_receiver.addError("No receiver expected for function " + funcName + ".");
			} else if (!l_receiver.attrTyp().isSubtypeOf(sig.getReceiverType(), l_receiver)) {
				l_receiver.addError("Incompatible receiver type at call to function " + funcName + ".\n" +
						"Found " + l_receiver.attrTyp() + " but expected " + sig.getReceiverType());
			}
		}
		if (getArguments().size() > sig.getParamTypes().size()) {
			pos.addError("Too many arguments. Function " + funcName + " only takes " + sig.getParamTypes().size() 
					+ " parameters.");
			return;
		} else if (getArguments().size() < sig.getParamTypes().size()) { 
			pos.addError("Not enough arguments. Function " + funcName + " requires the following arguments: " + sig.getParameterDescription());
		} else {
			for (int i=0; i<getArguments().size(); i++) {
				if (!getArguments().get(i).attrTyp().isSubtypeOf(sig.getParamTypes().get(i), pos)) {
					getArguments().get(i).addError("Wrong parameter type when calling " + funcName + ".\n"
							+ "Found " + getArguments().get(i).attrTyp() + " but expected " + sig.getParamTypes().get(i) + " " + sig.getParamName(i));
				}
			}
		}
		
	}
	
}
