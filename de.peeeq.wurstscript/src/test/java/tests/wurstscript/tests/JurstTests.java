package tests.wurstscript.tests;

import com.google.common.collect.ImmutableMap;
import de.peeeq.wurstscript.utils.Utils;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.Map;

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
    public void testBigjassScript() { // #498
        String jassCode = Utils.getResourceFile("test.j");

        String jurstCode = Utils.string(
                "package test",
                "	native testSuccess()",
                "	init",
                "		testSuccess()",
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

    private void testJurstWithJass(boolean executeProg, boolean withStdLib, String jass, String jurst) {
        Map<String, String> inputs = ImmutableMap.of(
                "example.j", jass,
                "test.jurst", jurst
        );
        testScript(Collections.emptyList(), inputs, "JurstJassTest", executeProg, withStdLib, false, false);
    }

    private void testJurst(boolean executeProg, boolean withStdLib, String jurst) {
        test().executeProg(executeProg)
                .executeProgOnlyAfterTransforms()
                .withStdLib(withStdLib)
                .withCu(compilationUnit("test.jurst", jurst))
                .run();
    }

}
