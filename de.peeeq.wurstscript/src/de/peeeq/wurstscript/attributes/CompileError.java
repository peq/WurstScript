package de.peeeq.wurstscript.attributes;

import java.io.File;
import java.io.Serializable;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.utils.LineOffsets;


public class CompileError extends Error implements Serializable {
	public enum ErrorType {
		ERROR, WARNING;
		
		@Override
		public String toString() {
			if (this == ERROR) return "Error";
			else return "Warning";
		}
	}
	private static final long serialVersionUID = 5589441532198109034L;
	
	private final WPos source;
	private final String message;

	private final ErrorType errorType;
	
	public CompileError(WPos source, String message) {
		this(source, message, ErrorType.ERROR);
	}
	
	public CompileError(@Nullable WPos source, String message, ErrorType errorType) {
		if (source == null) {
			this.source = new WPos("", new LineOffsets(), 0, 0);
		} else {
			this.source = source;
		}
		this.message = message;
		this.errorType = errorType;
	}
	
	
	public WPos getSource() {
		return source;
	}
	@Override
	public String getMessage() {
		return message;
	}
	@Override
	public String toString() {
		File file = new File(source.getFile());
		return errorType + " in File " + file.getName()+ " line " + (source.getLine()-1) + ":\n " + 
				message;
	}

	public ErrorType getErrorType() {
		return errorType;
	}
}
