package de.peeeq.wurstscript;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Supplier;

import de.peeeq.wurstscript.attributes.AttrFuncDef;
import de.peeeq.wurstscript.intermediateLang.ILconst;
import de.peeeq.wurstscript.intermediateLang.ILconstAddable;
import de.peeeq.wurstscript.intermediateLang.ILconstBool;
import de.peeeq.wurstscript.intermediateLang.ILconstInt;
import de.peeeq.wurstscript.intermediateLang.ILconstNum;
import de.peeeq.wurstscript.intermediateLang.ILconstReal;
import de.peeeq.wurstscript.jassAst.JassAst;
import de.peeeq.wurstscript.jassAst.JassOpBinary;
import de.peeeq.wurstscript.jassAst.JassOpUnary;

public enum WurstOperator {
	OR("or", 2),
	AND("and", 2),
	EQ("==", 2),
	NOTEQ("!=", 2),
	LESS_EQ("<=",2),
	LESS("<",2),
	GREATER_EQ(">=",2),
	GREATER(">",2),
	PLUS("+",2),
	MINUS("-",2),
	MULT("*",2),
	DIV_REAL("/",2),
	DIV_INT("div",2),
	MOD_REAL("%",2),
	MOD_INT("mod",2),
	NOT("not",1),
	UNARY_MINUS("-",1);

	private String rep;
	private int numArgs;

	private WurstOperator(String rep, int numArgs) {
		this.rep = rep;
		this.numArgs = numArgs;
	}

	public boolean isBinaryOp() {
		return numArgs == 2;
	}

	public boolean isUnaryOp() {
		return numArgs == 1;
	}

	@Override
	public String toString() {
		return rep;
	}

	public JassOpBinary jassTranslateBinary() {
		switch (this) {
		case AND: return JassAst.JassOpAnd();
		case DIV_INT:
			return JassAst.JassOpDiv();
		case DIV_REAL:
			return JassAst.JassOpDiv();
		case EQ:
			return JassAst.JassOpEquals();
		case GREATER:
			return JassAst.JassOpGreater();
		case GREATER_EQ:
			return JassAst.JassOpGreaterEq();
		case LESS:
			return JassAst.JassOpLess();
		case LESS_EQ:
			return JassAst.JassOpLessEq();
		case MINUS:
			return JassAst.JassOpMinus();
		case MOD_INT:
		case MOD_REAL:
			throw new Error("Cannot translate modulo");
		case MULT:
			return JassAst.JassOpMult();
		case NOTEQ:
			return JassAst.JassOpUnequals();
		case OR:
			return JassAst.JassOpOr();
		case PLUS:
			return JassAst.JassOpPlus();
		case NOT:
		case UNARY_MINUS:
		}
		throw new Error("cannot translate " + this);
	}

	public ILconst evaluateBinaryOperator(ILconst left,
			Supplier<ILconst> right) {
		switch (this) {
		case AND:
			return ILconstBool.instance(((ILconstBool)left).getVal() && ((ILconstBool)right.get()).getVal());
		case OR:
			return ILconstBool.instance(((ILconstBool)left).getVal() || ((ILconstBool)right.get()).getVal());
		case DIV_INT:
			return new ILconstInt(((ILconstInt)left).getVal() / ((ILconstInt)right.get()).getVal());
		case DIV_REAL:
			return new ILconstReal(getReal(left) / getReal(right.get()));
		case EQ:
			return ILconstBool.instance(left.equals(right.get()));
		case GREATER:
			return ((ILconstNum) left).greater((ILconstNum) right.get());
		case GREATER_EQ:
			return ((ILconstNum) left).greaterEq((ILconstNum) right.get());
		case LESS:
			return ((ILconstNum) left).less((ILconstNum) right.get());
		case LESS_EQ:
			return ((ILconstNum) left).lessEq((ILconstNum) right.get());
		case MINUS:
			return ((ILconstNum) left).sub((ILconstNum) right.get());
		case MOD_INT: {
			int right2 = ((ILconstInt)right.get()).getVal();
			int r = ((ILconstInt)left).getVal() % right2;
			if (r < 0) {
				r += right2;
			}
			return new ILconstInt(r);
		}
		case MOD_REAL: {
			float right2 = getReal(right.get());
			float r = getReal(left) % right2;
			if (r < 0) {
				r += right2; 
			}
			return new ILconstReal(r);
		}
		case MULT:
			return ((ILconstNum) left).mul((ILconstNum) right.get());
		case NOTEQ:
			return ILconstBool.instance(!left.equals(right.get()));
		case PLUS:
			return ((ILconstAddable) left).add((ILconstAddable) right.get());
		case NOT:
		case UNARY_MINUS:
			break;
		}
		throw new Error("cannot evaluate " + this);

	}

	private static float getReal(ILconst c) {
		if (c instanceof ILconstReal) {
			return ((ILconstReal)c).getVal();
		} else if (c instanceof ILconstInt) {
			return ((ILconstInt) c).getVal();
		}
		throw new Error();
	}

	@SuppressWarnings("incomplete-switch")
	public ILconst evaluateUnaryOperator(ILconst e) {
		switch (this) {
		case NOT:
			return ((ILconstBool) e).negate();
		case UNARY_MINUS:
			return ((ILconstNum) e).negate();
		}
		throw new Error("cannot evaluate " + this);
	}
	
	@SuppressWarnings("incomplete-switch")
	public @Nullable String getOverloadingFuncName() {
		switch (this) {
		case PLUS:
			return AttrFuncDef.overloadingPlus;
		case MINUS:
			return AttrFuncDef.overloadingMinus;
		case DIV_REAL:
			return AttrFuncDef.overloadingDiv;
		case MULT:
			return AttrFuncDef.overloadingMult;
		}
		return null;
	}

	@SuppressWarnings("incomplete-switch")
	public JassOpUnary jassTranslateUnary() {
		switch (this) {
		case NOT: 
			return JassAst.JassOpNot();
		case UNARY_MINUS: 
			return JassAst.JassOpMinus();
		}
		throw new Error("unhandled operator " + this);
	}

	/**
	 * an operator is lazy if it evaluates its second argument only 
	 * when its first argument evaluates to a certain value 
	 */
	public boolean isLazy() {
		return this == OR || this == AND;
	}

	
}
