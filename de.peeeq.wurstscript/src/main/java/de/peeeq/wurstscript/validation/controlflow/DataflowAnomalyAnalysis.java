package de.peeeq.wurstscript.validation.controlflow;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Sets;
import de.peeeq.immutablecollections.ImmutableList;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.types.WurstTypeArray;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.jdt.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;

//w(11)->{r(17), r(19)} & w(19)->{r(17), r(19)} & w(22)
class VarStates {
    final ImmutableMap<LocalVarDef, VState> states;

    public VarStates(ImmutableMap<LocalVarDef, VState> states) {
        this.states = states;
    }

    VarStates merge(VarStates other) {
        ImmutableMap<LocalVarDef, VState> merged = Utils.mergeMaps(states, other.states, VState::merge);
        return new VarStates(merged);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + states.hashCode();
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VarStates other = (VarStates) obj;
        if (!states.equals(other.states))
            return false;
        return true;
    }

    public static VarStates initial(Set<LocalVarDef> r) {
        ImmutableMap.Builder<LocalVarDef, VState> s = ImmutableMap.builder();
        for (LocalVarDef v : r) {
            s.put(v, VState.initial);
        }
        return new VarStates(s.build());
    }

    public boolean uninitialized(NameDef v) {
        VState s = states.get(v);
        return s != null && s.mightBeUninitialized;
    }

    public VarStates addRead(LocalVarDef v, Element r) {
        VState s = getVarState(v);
        if (s == null) {
            s = VState.initialDefined;
        }
        s = s.addRead(r);
        Builder<LocalVarDef, VState> builder = ImmutableMap.<LocalVarDef, VState>builder();
        for (Entry<LocalVarDef, VState> e : states.entrySet()) {
            if (e.getKey() != v) {
                builder.put(e);
            }
        }
        ImmutableMap<LocalVarDef, VState> rs = builder
                .put(v, s)
                .build();
        return new VarStates(rs);
    }

    public ImmutableSet<WStatement> getUnreadWrites(NameDef var) {
        ImmutableSet.Builder<WStatement> res = ImmutableSet.builder();
        VState vState = states.get(var);
        if (vState == null) return ImmutableSet.of();
        for (WStatement wr : vState.allWrites) {
            if (vState.writesAndReads.get(wr).isEmpty()) {
                res.add(wr);
            }
        }

        return res.build();
    }

    public VarStates addWrite(LocalVarDef var, WStatement s) {
        ImmutableMap.Builder<LocalVarDef, VState> res = ImmutableMap.builder();
        for (Entry<LocalVarDef, VState> e : states.entrySet()) {
            if (e.getKey() != var) {
                res.put(e);
            }
        }
        VState vState = getVarState(var);
        if (vState == null) {
            vState = VState.initialDefined;
        }
        vState = vState.addWrite(s);
        res.put(var, vState);
        return new VarStates(res.build());
    }

    private @Nullable VState getVarState(LocalVarDef var) {
        return states.get(var);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("VarStates [");
        for (Entry<LocalVarDef, VState> e : this.states.entrySet()) {
            sb.append("\n\t");
            sb.append(e.getKey().getName()).append(" -> ").append(e.getValue());

        }
        sb.append("]");
        return sb.toString();
    }


}

class VState {
    public static final VState initialDefined = new VState(false, ImmutableSetMultimap.<WStatement, Element>of()
            , ImmutableSet.<WStatement>of(), ImmutableSet.<WStatement>of());
    public static final VState initial = new VState(true, ImmutableSetMultimap.<WStatement, Element>of()
            , ImmutableSet.<WStatement>of(), ImmutableSet.<WStatement>of());
    final boolean mightBeUninitialized;
    final ImmutableSetMultimap<WStatement, Element> writesAndReads;
    final ImmutableSet<WStatement> activeWrites;
    final ImmutableSet<WStatement> allWrites;

    public VState(boolean mightBeUninitialized, ImmutableSetMultimap<WStatement, Element> writesAndReads, ImmutableSet<WStatement> activeWrites, ImmutableSet<WStatement> allWrites) {
        this.mightBeUninitialized = mightBeUninitialized;
        this.writesAndReads = writesAndReads;
        this.activeWrites = activeWrites;
        this.allWrites = allWrites;
    }

    public VState addWrite(WStatement s) {
        ImmutableSet.Builder<WStatement> wr = ImmutableSet.builder();
        wr.addAll(allWrites);
        wr.add(s);
        return new VState(false, writesAndReads, ImmutableSet.of(s), wr.build());
    }

    public VState addRead(Element r) {
        ImmutableSetMultimap.Builder<WStatement, Element> builder = ImmutableSetMultimap.builder();
        builder.putAll(writesAndReads);
        for (WStatement s : this.activeWrites) {
            builder.put(s, r);
        }
        return new VState(mightBeUninitialized, builder.build(), activeWrites, allWrites);
    }

    public VState merge(VState other) {
        return new VState(mightBeUninitialized || other.mightBeUninitialized,
                Utils.mergeMultiMaps(writesAndReads, other.writesAndReads),
                Utils.mergeSets(activeWrites, other.activeWrites),
                Utils.mergeSets(allWrites, other.allWrites));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + activeWrites.hashCode();
        result = prime * result + allWrites.hashCode();
        result = prime * result + (mightBeUninitialized ? 1231 : 1237);
        result = prime * result + writesAndReads.hashCode();
        return result;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        VState other = (VState) obj;
        if (!activeWrites.equals(other.activeWrites))
            return false;
        if (!allWrites.equals(other.allWrites))
            return false;
        if (mightBeUninitialized != other.mightBeUninitialized)
            return false;
        if (!writesAndReads.equals(other.writesAndReads))
            return false;
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("VState [ " + this.mightBeUninitialized + ", ");
        for (Entry<WStatement, Element> e : this.writesAndReads.entries()) {
            sb.append("\n\t\t");
            sb.append(e.getKey().attrSource().getLine()).append(" -> ").append(e.getValue().attrSource().getLine());
        }
        sb.append("]");
        return sb.toString();
    }


}


public class DataflowAnomalyAnalysis extends ForwardMethod<VarStates, AstElementWithBody> {

    @Override
    VarStates calculate(WStatement s, VarStates incoming) {
        if (s instanceof StartFunctionStatement) {
            // initially all vars are uninitialized
            final Set<LocalVarDef> r = Sets.newHashSet();
            collectLocalVars(r, getFuncDef());
            return VarStates.initial(r);
        }


        if (s instanceof CompoundStatement) {
            // for a compound statement check only the expressions in the statement
            for (int i = 0; i < s.size(); i++) {
                if (s.get(i) instanceof Expr) {
                    Expr expr = (Expr) s.get(i);
                    incoming = handleExprInCompound(incoming, expr);
                }
            }
            if (s instanceof SwitchStmt) {
                SwitchStmt swi = (SwitchStmt) s;
                // switch statement must be handled separately, because expressions are not direct children:
                for (SwitchCase switchCase : swi.getCases()) {
                    incoming = handleExprInCompound(incoming, switchCase.getExpr());
                }
            }
        } else {
            checkIfVarsInitialized(s, incoming);
            for (NameDef v : s.attrReadVariables()) {
                if (v instanceof LocalVarDef) {
                    incoming = incoming.addRead((LocalVarDef) v, s);
                }
            }
        }


        NameDef n = getInitializedVar(s);
        if (n != null) {
//			ImmutableSet<WStatement> unreadWrites = incoming.getUnreadWrites(n);
//			for (WStatement wr : unreadWrites) {
//				Element parent = s.getParent();
//				if (isNested(wr, parent)) {				
////					s.addError("Assigning to " + Utils.printElement(n) + " overrides previous assignment in line " + wr.attrSource().getLine());
//				}
//			} 
            if (n instanceof LocalVarDef) {
                LocalVarDef lv = (LocalVarDef) n;
                return incoming.addWrite(lv, s);
            }
        }
        return incoming;
    }

    private VarStates handleExprInCompound(VarStates incoming, Expr expr) {
        checkIfVarsInitialized(expr, incoming);
        for (NameDef v : expr.attrReadVariables()) {
            if (v instanceof LocalVarDef) {
                incoming = incoming.addRead((LocalVarDef) v, expr);
            }
        }
        return incoming;
    }

    private @Nullable NameDef getInitializedVar(WStatement s) {
        NameDef n = null;
        if (s instanceof StmtSet) {
            StmtSet s2 = (StmtSet) s;
            NameLink link = s2.getUpdatedExpr().attrNameLink();
            if (link != null) {
                n = link.getDef();
            }

        } else if (s instanceof LocalVarDef) {
            LocalVarDef l = (LocalVarDef) s;
            if (l.getInitialExpr() instanceof Expr) {
                n = l;
            }
        } else if (s instanceof LoopStatementWithVarDef) {
            LoopStatementWithVarDef s2 = (LoopStatementWithVarDef) s;
            n = s2.getLoopVar();
        }
        return n;
    }

    private void collectLocalVars(Set<LocalVarDef> r, Element e) {
        if (e instanceof LocalVarDef) {
            r.add((LocalVarDef) e);
        }

        for (int i = 0; i < e.size(); i++) {
            Element c = e.get(i);
            if (!(c instanceof ExprClosure) && !(c instanceof ExprStatementsBlock)) {
                collectLocalVars(r, c);
            }
        }
    }

    private void checkIfVarsInitialized(HasReadVariables s, VarStates incoming) {
        ImmutableList<NameDef> readVars = s.attrReadVariables();
        for (NameDef v : readVars) {
            if (v.attrTyp() instanceof WurstTypeArray) {
                continue;
            }
            if (incoming.uninitialized(v)) {
                Element readingExpr = findRead(s, v);
                if (readingExpr == null) {
                    readingExpr = s;
                }
                readingExpr.addError("Variable " + v.getName() + " may not have been initialized");
            }
        }
    }

    private @Nullable HasReadVariables findRead(Element e, NameDef v) {
        HasReadVariables result = null;
        if (e instanceof HasReadVariables) {
            HasReadVariables r = (HasReadVariables) e;
            if (!r.attrReadVariables().contains(v)) {
                return null;
            }
            result = r;
        }
        for (int i = 0; i < e.size(); i++) {
            HasReadVariables r = findRead(e.get(i), v);
            if (r != null) {
                if (isLeftOfStmtSet(r)) {
                    continue;
                }

                return r;
            }
        }
        return result;
    }

    private boolean isLeftOfStmtSet(HasReadVariables r) {
        Element parent = r.getParent();
        if (parent instanceof StmtSet) {
            StmtSet stmtSet = (StmtSet) parent;
            if (stmtSet.getUpdatedExpr() == r) {
                return true;
            }
        }
        return false;
    }

    @Override
    VarStates merge(Collection<VarStates> values) {
        Iterator<VarStates> it = values.iterator();
        VarStates r = it.next();
        while (it.hasNext()) {
            r = r.merge(it.next());
        }
        return r;
    }

    @Override
    String print(VarStates t) {
        if (t == null) {
            return "null";
        }
        return t.toString();
    }

    @Override
    void checkFinal(VarStates fin) {
        for (LocalVarDef var : fin.states.keySet()) {
            if (var.getName().startsWith("_")) {
                // ignore warning, if name starts with "_"
                continue;
            }
            for (WStatement ur : fin.getUnreadWrites(var)) {
                if (ur instanceof LoopStatement) {
                    // no warnings for loop variables
                    continue;
                }

                Element errorPos = ur;
                if (ur instanceof StmtSet) {
                    errorPos = ((StmtSet) ur).getUpdatedExpr();
                }
                errorPos.addWarning("The assignment to " + Utils.printElement(var) + " is never read.");
            }
        }
    }

    @Override
    public VarStates startValue() {
        return VarStates.initial(Collections.<LocalVarDef>emptySet());
    }


}
