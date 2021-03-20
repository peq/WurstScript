package de.peeeq.wurstio.gui;

import de.peeeq.wurstio.UtilsIO;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.utils.LineOffsets;

public class TestStartGui {

  /** @param args */
  public static void main(String[] args) {
    WurstGuiImpl gui = new WurstGuiImpl();

    gui.sendProgress(null);
    UtilsIO.sleep(1000);
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/testIf_1.wurst",
                LineOffsets.dummy,
                1,
                8),
            "test"));
    UtilsIO.sleep(1000);
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/testIf_1.wurst",
                LineOffsets.dummy,
                3,
                14),
            "string"));
    gui.sendProgress(null);
    UtilsIO.sleep(1000);
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/testIf_1.wurst",
                LineOffsets.dummy,
                4,
                3),
            "nativetype"));
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/testIf_1.wurst",
                LineOffsets.dummy,
                46,
                9),
            "2 == 2"));
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/testIf_1.wurst",
                LineOffsets.dummy,
                49,
                9),
            "testFail?"));
    gui.sendProgress(null);
    UtilsIO.sleep(1000);
    UtilsIO.sleep(1000);
    gui.sendError(
        new CompileError(
            new WPos(
                "C:/pscript/de.peeeq.wurstscript/testscripts/valid/natives_bj.wurst",
                LineOffsets.dummy,
                10000,
                9),
            "Some really large Text "
                + "which might not fit in here. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. Bla blub blabbel probel blub. "));
    gui.sendProgress(null);
    UtilsIO.sleep(1000);
    gui.sendFinished();
  }
}
