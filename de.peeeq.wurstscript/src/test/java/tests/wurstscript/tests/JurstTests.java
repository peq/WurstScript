package tests.wurstscript.tests;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import de.peeeq.wurstscript.utils.Utils;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;

import static org.testng.AssertJUnit.assertTrue;

public class JurstTests extends WurstScriptTest {

    @Test
    public void multilineString() {
        testJurst(false, false, Utils.string(
                "package test",
                "let s = \"12345678",
                "90\"",
                ""));
    }

    @Test
    public void hexInt1() {
        testJurst(true, false, Utils.string(
                "package test",
                "native testSuccess()",
                "int a = $10",
                "init",
                "  if a == 16 then",
                "    testSuccess()",
                "  end",
                "end",
                ""));
    }

    @Test
    public void hexInt2() {
        testJurst(true, false, Utils.string(
                "package test",
                "native testSuccess()",
                "int a = 0x10",
                "init",
                "  if a == 16 then",
                "    testSuccess()",
                "  end",
                "end",
                ""));
    }

    @Test
    public void asciiChars1() {
        testJurst(true, false, Utils.string(
                "package test",
                "native testSuccess()",
                "int a = 'b'- 'a'",
                "init",
                "  if a == 1 then",
                "    testSuccess()",
                "  end",
                "end",
                ""));
    }

    @Test
    public void asciiChars2() {
        testJurst(true, false, Utils.string(
                "package test",
                "native testSuccess()",
                "int a = 'hfoo'",
                "init",
                "  if a == 1751543663 then",
                "    testSuccess()",
                "  end",
                "end",
                ""));
    }

    @Test
    public void asciiChars3() {
        testJurst(true, false, Utils.string(
                "package test",
                "native testSuccess()",
                "int a = 'h\\\\oo'",
                "init",
                "  if a == 1750888303 then",
                "    testSuccess()",
                "  end",
                "end",
                ""));
    }

    @Test
    public void thisAsVarNameInJass() { // #498
        String jassCode = Utils.string(
                "function foo takes integer this returns integer",
                "	return this",
                "endfunction",
                "function bar takes integer x returns integer",
                "	local integer this = x",
                "	return this",
                "endfunction");


        String jurstCode = Utils.string(
                "package test",
                "	native testSuccess()",
                "	init",
                "		if foo(5) == 5",
                "			testSuccess()",
                "		end",
                "	end",
                "endpackage");

        testJurstWithJass(true, false, jassCode, jurstCode);
    }

    @Test
    public void logicalOperatorPrecedence() { // #641
        String jassCode = Utils.string(
                "function foo takes nothing returns boolean",
                "	return true or false and false",
                "endfunction");


        String jurstCode = Utils.string(
                "package test",
                "	native testSuccess()",
                "	init",
                "		if not foo()",
                "			testSuccess()",
                "		end",
                "	end",
                "endpackage");

        testJurstWithJass(true, false, jassCode, jurstCode);
    }

    @Test
    public void validNames() { // #641
        String jassCode = Utils.string(
                "function foo takes nothing returns boolean",
                "	local integer mod = 1",
                "	local real skip = 1.",
                "	return true",
                "endfunction");


        String jurstCode = Utils.string(
                "package test",
                "	native testSuccess()",
                "	init",
                "		if foo()",
                "			testSuccess()",
                "		end",
                "	end",
                "endpackage");

        testJurstWithJass(true, false, jassCode, jurstCode);
    }

    @Test
    public void returnDetection() { // #641
        String jassCode = Utils.string(
                "function foo takes integer a returns integer",
                "	if false then",
                "		return a",
                "	else",
                "		return -a",
                "	endif",
                "endfunction");


        String jurstCode = Utils.string(
                "package test",
                "	native testSuccess()",
                "	init",
                "		if foo(1) == -1",
                "			testSuccess()",
                "		end",
                "	end",
                "endpackage");

        testJurstWithJass(true, false, jassCode, jurstCode);
    }

    @Test
    public void jassMultilineString() {
        String jassCode =
            "function bar takes string s returns nothing\r" +
            "endfunction\r" +
            "function foo takes integer a returns integer\r" +
            "   call bar(\"Bla\r" +
            "string continues\")\r" +
            "	if false then\r" +
            "		return a\r" +
            "	else\r" +
            "		return -a\r" +
            "	endif\r" +
            "endfunction\r";


        String jurstCode = Utils.string(
            "package test",
            "	native testSuccess()",
            "	init",
            "		if foo(1) == -1",
            "			testSuccess()",
            "		end",
            "	end",
            "endpackage");

        testJurstWithJass(true, false, jassCode, jurstCode);
    }

    @Test
    public void jassAgentTypeComparison() {
        String jassCode = Utils.string(
            "function bar takes sound s, rect r returns boolean",
            "return s == r",
            "endfunction\n");


        String jurstCode = Utils.string(
            "package test",
            "	init",
            "		if bar(null, null)",
            "			testSuccess()",
            "		end",
            "	end",
            "endpackage");

        testJurstWithJass(true, true, jassCode, jurstCode);
    }

    @Test
    public void testBigJassScript() throws IOException {
        String jassCode = new String(Files.readAllBytes(Paths.get(Utils.getResourceFile("test.j"))));

        String jurstCode = Utils.string(
                "package test",
                "	init",
                "		testSuccess()",
                "	end",
                "endpackage");

        testJurstWithJass(false, true, jassCode, jurstCode);
    }

    @Test
    public void testJurstWrapping() throws IOException {
        String jassCode = Utils.string(
                "function foo takes integer a returns string",
                "	return \"   ah \"",
                "endfunction");

        String jurstCode = new String(Files.readAllBytes(Paths.get(Utils.getResourceFile("test.jurst"))));

        testJurstWithJass(false, true, jassCode, jurstCode);
    }

    @Test
    public void testKeepTRVE() throws IOException {
        String jassCode = Utils.string(
            "type agent\t\t\t    extends     handle \n" +
                "type event              extends     agent \n" +
                "type trigger            extends     agent\n" +
                "type eventid            extends     handle\n" +
                "type limitop            extends     eventid\n" +
                "globals",
            "real myVar = 0.",
            "endglobals",
            "native TriggerRegisterVariableEvent takes trigger whichTrigger, string varName, limitop opcode, real limitval returns event",
            "function foo takes nothing returns nothing",
            "	 call TriggerRegisterVariableEvent(null, \"myVar\", null, 0.)",
            "endfunction");


        String jurstCode = Utils.string(
            "package test",
            "endpackage");

        testJurstWithJass(false, false, jassCode, jurstCode);

        String output = com.google.common.io.Files.toString(new File("./test-output/JurstJassTest_inlopt.j"), Charsets.UTF_8);
        assertTrue(output.contains("real myVar"));
    }

    @Test
    public void testKeepTRVEHooked() throws IOException {
        String jassCode = Utils.string(
            "type agent\t\t\t    extends     handle \n" +
                "type event              extends     agent \n" +
                "type trigger            extends     agent\n" +
                "type eventid            extends     handle\n" +
                "type limitop            extends     eventid\n" +
                "globals",
            "real myVar = 0.",
            "endglobals",
            "native TriggerRegisterVariableEvent takes trigger whichTrigger, string varName, limitop opcode, real limitval returns event",
            "function TriggerRegisterVariableEvent_2 takes trigger whichTrigger, string varName, limitop opcode, real limitval returns event",
            "	 return TriggerRegisterVariableEvent(whichTrigger, varName, opcode, limitval)",
            "endfunction",
            "function foo takes nothing returns nothing",
            "	 call TriggerRegisterVariableEvent_2(null, \"myVar\", null, 0.)",
            "endfunction");


        String jurstCode = Utils.string(
            "package test",
            "endpackage");

        testJurstWithJass(false, false, jassCode, jurstCode);

        String output = com.google.common.io.Files.toString(new File("./test-output/JurstJassTest_inlopt.j"), Charsets.UTF_8);
        assertTrue(output.contains("real myVar"));
    }

    private void testJurstWithJass(boolean executeProg, boolean withStdLib, String jass, String jurst) {
        Map<String, String> inputs = ImmutableMap.of(
                "example.j", jass,
                "test.jurst", jurst
        );

        new TestConfig("JurstJassTest")
                .withStdLib(withStdLib)
                .executeTests(false)
                .executeProgOnlyAfterTransforms(false)
                .executeProg(executeProg)
                .withInputFiles(Collections.emptyList())
                .withInputs(inputs)
                .run()
                .getModel();
    }

    private void testJurst(boolean executeProg, boolean withStdLib, String jurst) {
        test().executeProg(executeProg)
                .executeProgOnlyAfterTransforms()
                .withStdLib(withStdLib)
                .withCu(compilationUnit("test.jurst", jurst))
                .run();
    }

}
