package de.peeeq.wurstio.jassinterpreter.providers;

import de.peeeq.wurstio.jassinterpreter.mocks.TriggerMock;
import de.peeeq.wurstscript.intermediatelang.*;
import de.peeeq.wurstscript.intermediatelang.interpreter.AbstractInterpreter;

public class TriggerProvider extends Provider {


    public TriggerProvider(AbstractInterpreter interpreter) {
        super(interpreter);
    }

    public IlConstHandle CreateTrigger() {
        return new IlConstHandle(NameProvider.getRandomName("trigger"), new TriggerMock());
    }

    public void DestroyTrigger(IlConstHandle trigger) {
    }

    public ILconstBool TriggerEvaluate(IlConstHandle trigger) {
        return ((TriggerMock) trigger.getObj()).evaluate(interpreter);
    }

    public void TriggerAddCondition(IlConstHandle trigger, IlConstHandle boolexpr) {
        TriggerMock triggerMock = (TriggerMock) trigger.getObj();
        triggerMock.addCondition(boolexpr);
    }

    public void TriggerClearConditions(IlConstHandle trigger) {
        TriggerMock triggerMock = (TriggerMock) trigger.getObj();
        triggerMock.clearConditions();
    }

    public void TriggerAddAction(IlConstHandle trigger, ILconstFuncRef code) {
        TriggerMock triggerMock = (TriggerMock) trigger.getObj();
        triggerMock.addAction(code);
    }

    public void TriggerRegisterPlayerChatEvent(IlConstHandle trigger, IlConstHandle whichPlayer, ILconstString chatMessageToDetect, ILconstBool
            exactMatchOnly) {
        TriggerMock triggerMock = (TriggerMock) trigger.getObj();
        // TODO
//        triggerMock.registerEvent();
    }

    public void TriggerRegisterVariableEvent(IlConstHandle trigger, ILconstString varName, IlConstHandle opcode, ILconstReal limitval) {
        TriggerMock triggerMock = (TriggerMock) trigger.getObj();
        // TODO
//        triggerMock.registerEvent();
    }

    public IlConstHandle Condition(ILconstFuncRef code) {
        return new IlConstHandle(NameProvider.getRandomName("conditionfunc"), code);
    }

    public IlConstHandle Filter(ILconstFuncRef code) {
        return new IlConstHandle(NameProvider.getRandomName("filterfunc"), code);
    }
}
