package de.peeeq.wurstscript;

import static de.peeeq.wurstscript.ast.Ast.Arguments;
import static de.peeeq.wurstscript.ast.Ast.ExprVarAccess;
import static de.peeeq.wurstscript.ast.Ast.NoTypeExpr;
import static de.peeeq.wurstscript.ast.Ast.WStatements;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.peeeq.wurstscript.ast.Ast;
import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.AstElementWithBody;
import de.peeeq.wurstscript.ast.ClassDef;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.ConstructorDef;
import de.peeeq.wurstscript.ast.Expr;
import de.peeeq.wurstscript.ast.ExprIntVal;
import de.peeeq.wurstscript.ast.ExprMemberMethod;
import de.peeeq.wurstscript.ast.ExprMemberVarDot;
import de.peeeq.wurstscript.ast.ExprStatementsBlock;
import de.peeeq.wurstscript.ast.ExprUnary;
import de.peeeq.wurstscript.ast.ExprVarAccess;
import de.peeeq.wurstscript.ast.ExtensionFuncDef;
import de.peeeq.wurstscript.ast.FuncDef;
import de.peeeq.wurstscript.ast.InitBlock;
import de.peeeq.wurstscript.ast.OnDestroyDef;
import de.peeeq.wurstscript.ast.OptTypeExpr;
import de.peeeq.wurstscript.ast.StmtForFrom;
import de.peeeq.wurstscript.ast.StmtForIn;
import de.peeeq.wurstscript.ast.StmtIf;
import de.peeeq.wurstscript.ast.StmtReturn;
import de.peeeq.wurstscript.ast.WImport;
import de.peeeq.wurstscript.ast.WPackage;
import de.peeeq.wurstscript.ast.WStatement;
import de.peeeq.wurstscript.ast.WStatements;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.parser.WPos;


/**
 * general rules for syntactic sugar:
 * 
 *  1. operations must be idempotent: syntacticSugar(syntacticSugar(program)) = syntacticSugar(program)
 *  2. operations must not depend on other compilation units. 
 *
 */
public class SyntacticSugar {

	private int wurstIteratorCounter = 0;


	public void removeSyntacticSugar(CompilationUnit root, boolean hasCommonJ) {
		if (hasCommonJ) {
			addDefaultImports(root);
		}
		rewriteNegatedInts(root);
		addDefaultConstructors(root);
		addEndFunctionStatements(root);
		expandForInLoops(root);
		replaceTypeIdUse(root);
	}
	
	

	

	private void replaceTypeIdUse(CompilationUnit root) {
		final Map<Expr, Expr> replacements = Maps.newLinkedHashMap();
		root.accept(new WurstModel.DefaultVisitor() {
			@Override
			public void visit(ExprMemberVarDot e) {
				if (e.getVarName().equals("typeId")) {
					replacements.put(e, Ast.ExprTypeId(e.getSource(), (Expr) e.getLeft().copy()));
				}
			}
		});
		doReplacements(replacements, "Cannot use typeId here");
	}





	private void rewriteNegatedInts(CompilationUnit root) {
		final Map<Expr, Expr> replacements = Maps.newLinkedHashMap();
		root.accept(new WurstModel.DefaultVisitor() {
			@Override
			public void visit(ExprUnary e) {
				if (e.getOpU() == WurstOperator.UNARY_MINUS
						&& e.getRight() instanceof ExprIntVal) {
					ExprIntVal iv = (ExprIntVal) e.getRight();
					ExprIntVal newExpr = Ast.ExprIntVal(e.getSource(), "-"+iv.getValIraw());
					replacements.put(e, newExpr);
				}
			}
		});
		doReplacements(replacements, "Cannot use typeId here");
	}



	private void doReplacements(Map<Expr, Expr> replacements, String msg) {
		for (Entry<Expr, Expr> e : replacements.entrySet()) {
			Expr oldE = e.getKey();
			Expr newE = e.getValue();
			try {
				doSingleReplacement(oldE, newE);
			} catch (ClassCastException ex) {
				oldE.addError(msg);
			}
		}
		
	}

	public void doSingleReplacement(Expr oldE, Expr newE) throws Error {
		AstElement parent = oldE.getParent();
		for (int i=0; i<parent.size(); i++) {
			if (parent.get(i) == oldE) {
				parent.set(i, newE);
				return;
			}
		}
		throw new Error("could not replace " + oldE + " with " + newE);
	}

	private void addEndFunctionStatements(CompilationUnit root) {
		
		root.accept(new WurstModel.DefaultVisitor() {
			@Override
			public void visit(ExtensionFuncDef f) {
				addEnd(f);
			}

			
			@Override
			public void visit(FuncDef f) {
				addEnd(f);
			}
			
			@Override
			public void visit(ConstructorDef f) {
				addEnd(f);
			}
			
			@Override
			public void visit(InitBlock f) {
				addEnd(f);
			}
			

			@Override
			public void visit(OnDestroyDef f) {
				addEnd(f);
			}
			
			@Override
			public void visit(ExprStatementsBlock f) {
				addEnd(f);
			}
			
			private void addEnd(AstElementWithBody f) {
				WPos pos = f.attrSource();
				pos = pos.withRightPos(pos.getLeftPos()-1);
				f.getBody().add(Ast.EndFunctionStatement(pos));
				f.getBody().add(0, Ast.StartFunctionStatement(pos));
			}
			
		});
		
	}





	private void addDefaultImports(CompilationUnit root) {
		nextPackage: for (WPackage p : root.attrGetByType().packageDefs) {
			// add 'import Wurst' if it does not exist
			for (WImport imp : p.getImports()) {
				if (imp.getPackagename().equals("Wurst")) {
					// wurst package already imported
					continue nextPackage;
				}
				if (imp.getPackagename().equals("NoWurst")) {
					// NoWurst package imported --> no standard lib wanted
					continue nextPackage;
				}
			}
			WPos source = p.getSource();
			source = source.withRightPos(source.getLeftPos() - 1);
			p.getImports().add(Ast.WImport(source, false, false, "Wurst"));
		}
	}
	
	private void expandForInLoops(CompilationUnit root) {
		// collect loops
		final List<StmtForIn> loops = Lists.newArrayList();
		final List<StmtForFrom> loops2 = Lists.newArrayList();
		root.accept(new WurstModel.DefaultVisitor() {
			@Override
			public void visit(StmtForIn stmtForIn) {
				loops.add(stmtForIn);
			}
			
			@Override
			public void visit(StmtForFrom stmtForIn) {
				loops2.add(stmtForIn);
			}
		});
		
		// exand for ... in ... loops
		for (StmtForIn loop : loops) {
			if (loop.getParent() instanceof WStatements) {
				WStatements parent = (WStatements) loop.getParent();
				
				int position = parent.indexOf(loop);
				parent.remove(position);
				
				String iteratorName = "wurst__iterator" +  wurstIteratorCounter++;
				WPos loopVarPos = loop.getLoopVar().getSource();
				WPos loopInPos = loop.getIn().getSource();
				parent.add(position, 
						Ast.LocalVarDef(
								loopInPos, 
								Ast.Modifiers(), 
								NoTypeExpr(), iteratorName, 
									Ast.ExprMemberMethodDot(loopInPos, (Expr) loop.getIn().copy(), "iterator", Ast.TypeExprList(), Arguments())));
				WStatements body = WStatements(
							Ast.LocalVarDef(loopVarPos, 
									Ast.Modifiers(),
									(OptTypeExpr) loop.getLoopVar().getOptTyp().copy(), 
									loop.getLoopVar().getName(), 
									Ast.ExprMemberMethodDot(loopInPos, 
											ExprVarAccess(loopVarPos, iteratorName), "next", Ast.TypeExprList(), Arguments()))
						);
				body.addAll(addIteratorCloseStatemenst(loop.getBody().removeAll(), iteratorName, loopVarPos, loopInPos));
				parent.add(position + 1, Ast.StmtWhile(
						loop.getSource(), 
						Ast.ExprMemberMethodDot(loopInPos, 
								ExprVarAccess(loopVarPos, iteratorName), "hasNext", Ast.TypeExprList(), Arguments()),
						body));
				parent.add(position+2, 
						closeIteratorStatement(iteratorName, loopVarPos, loopInPos));
			} else {
				throw new CompileError(loop.getSource(), "Loop not in statements - " + loop.getParent().getClass().getName());
			}
		}
		
		// exand for .. from ... loops
		for (StmtForFrom loop : loops2) {
			if (loop.getParent() instanceof WStatements) {
				WStatements parent = (WStatements) loop.getParent();
				
				int position = parent.indexOf(loop);
				parent.remove(position);
				
				String iteratorName = "wurst__iterator" +  wurstIteratorCounter++;
				WPos loopVarPos = loop.getLoopVar().getSource();
				WPos loopInPos = loop.getIn().getSource();
				if (loop.getIn() instanceof ExprVarAccess) {
					ExprVarAccess exprVarAccess = (ExprVarAccess) loop.getIn();
					iteratorName = exprVarAccess.getVarName();
				} else {
					parent.add(position, 
							Ast.LocalVarDef(
									loopInPos, 
									Ast.Modifiers(), 
									NoTypeExpr(), iteratorName, 
										(Expr) loop.getIn().copy()));
					position++;
				}
				WStatements body = WStatements(
							Ast.LocalVarDef(loopVarPos, 
									Ast.Modifiers(),
									(OptTypeExpr) loop.getLoopVar().getOptTyp().copy(), 
									loop.getLoopVar().getName(), 
									Ast.ExprMemberMethodDot(loopInPos, 
											ExprVarAccess(loopVarPos, iteratorName), "next", Ast.TypeExprList(), Arguments()))
						);
				body.addAll(addIteratorCloseStatemenst(loop.getBody().removeAll(), iteratorName, loopVarPos, loopInPos));
				parent.add(position + 0, Ast.StmtWhile(
						loop.getSource(), 
						Ast.ExprMemberMethodDot(loopInPos, 
								ExprVarAccess(loopVarPos, iteratorName), "hasNext", Ast.TypeExprList(), Arguments()),
						body));
			} else {
				throw new CompileError(loop.getSource(), "Loop not in statements - " + loop.getParent().getClass().getName());
			}
		}
	}


	private ExprMemberMethod closeIteratorStatement(String iteratorName, WPos loopVarPos, WPos loopInPos) {
		return Ast.ExprMemberMethodDot(loopInPos, 
				ExprVarAccess(loopVarPos, iteratorName), "close", Ast.TypeExprList(), Arguments());
	}
	
	private List<WStatement> addIteratorCloseStatemenst(List<WStatement> statements, String iteratorName, WPos loopVarPos, WPos loopInPos) {
		ListIterator<WStatement> it = statements.listIterator();
		while (it.hasNext()) {
			WStatement s = it.next();
			if (s instanceof AstElementWithBody) {
				addIteratorCloseStatemenst(((AstElementWithBody) s).getBody(), iteratorName, loopVarPos, loopInPos);
			} else if (s instanceof StmtIf) {
				StmtIf stmtIf = (StmtIf) s;
				addIteratorCloseStatemenst(stmtIf.getThenBlock(), iteratorName, loopVarPos, loopInPos);
				addIteratorCloseStatemenst(stmtIf.getElseBlock(), iteratorName, loopVarPos, loopInPos);
			} else if (s instanceof StmtReturn) {
				// add the close statement
				it.previous();
				it.add(closeIteratorStatement(iteratorName, loopVarPos, loopInPos));
				it.next();
			}
			
		}
		return statements;
	}


	/**
	 * add a empty default constructor to every class without any constructor 
	 */
	private void addDefaultConstructors(CompilationUnit root) {
		for (ClassDef c : root.attrGetByType().classes) {
			if (c.getConstructors().size() == 0) {
				// add default constructor if none exists:
				WPos source = c.getSource().withRightPos(c.getSource().getLeftPos()-1);
				c.getConstructors().add(Ast.ConstructorDef(
					source, 
					Ast.Modifiers(), 
					Ast.WParameters(), 
					false, Ast.Arguments(),
					Ast.WStatements()));
			}
		}
	}
	
//	/**
//	 * return all classes occuring in a compilation unit 
//	 */
//	private List<ClassDef> getAllClasses(CompilationUnit root) {
//		List<ClassDef> result = Lists.newArrayList();
//		for (TopLevelDeclaration t : root) {
//			if (t instanceof WPackage) {
//				WPackage p = (WPackage) t;
//				for (WEntity e : p.getElements()) {
//					if (e instanceof ClassDef) {
//						result.add((ClassDef) e);
//					}
//				}
//			}
//		}
//		return result;
//	}
}
