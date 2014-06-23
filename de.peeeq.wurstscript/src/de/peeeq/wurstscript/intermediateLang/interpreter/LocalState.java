package de.peeeq.wurstscript.intermediateLang.interpreter;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.intermediateLang.ILconst;




public class LocalState extends State {

	private @Nullable ILconst returnVal = null;
	
	public LocalState(ILconst returnVal) {
		this.setReturnVal(returnVal);
	}

	public LocalState() {
	}

	public @Nullable ILconst getReturnVal() {
		return returnVal;
	}

	public LocalState setReturnVal(@Nullable ILconst returnVal) {
		this.returnVal = returnVal;
		return this;
	}

	

	

}
