package tests.wurstscript.tests;

import de.peeeq.wurstio.UtilsIO;
import org.testng.annotations.Test;

public class ExpressionTests extends WurstScriptTest {

  @Test
  public void plus() {
    assertOk("3 + 7 == 10");
  }

  @Test
  public void real1() {
    assertOk(".3 + .7 == 1.");
  }

  @Test
  public void minus() {
    assertOk("3 * 4 == 12");
  }

  @Test
  public void parantheses() {
    assertOk("(3-1)*(3-1) + (2-5)*(2-5)  == 13");
  }

  @Test
  public void div1() {
    assertOk("14 div 3 == 4");
  }

  @Test
  public void unaryMinus1() {
    assertOk("(-(1.0 + 2.0) * 3.0) / (4.0 + 5.0) == -1.0");
  }

  @Test
  public void unaryMinus2() {
    assertOk("-4 + 5 == 1");
  }

  @Test
  public void unaryMinus3() {
    assertOk("1 - 2 - - 3 + 4 == 6");
  }

  @Test
  public void unaryMinus4() {
    assertOk("- 3*4 == -12");
  }

  //	@Test
  //	public void div2() {
  //		assertError("Cannot compare types", "14 / 4 == 7");
  //	}

  @Test
  public void div3() {
    assertOk("14 / 3 > 4.0");
  }

  @Test
  public void mod1() {
    assertOk("14 mod 3 == 2");
  }

  @Test
  public void err_assign() {
    assertError("'='", "x = 12");
  }

  @Test
  public void ints1() {
    assertOk("0153 == 107");
  }

  @Test
  public void ints2() {
    assertOk("0xaffe == 45054");
  }

  //	@Test // not supported (strange notation - do not want ;)
  //	public void ints3() {
  //		assertOk("$affe == 45054");
  //	}

  @Test
  public void ints4() {
    assertOk("'a' == 97");
  }

  //	@Test // not yet supported
  //	public void ints5() {
  //		assertOk("'\n' == 11");
  //	}

  @Test
  public void ints6() {
    assertOk("'hfoo' == 1751543663"); // or 1781543663 ?
  }

  @Test
  public void ints7() {
    assertOk("'wc 3' == 2002985011"); // or 2002985611 ?
  }

  @Test
  public void string() {
    assertOk("\"Hallo \\\"Welt\\\"\" != null");
  }

  @Test
  public void conditionalExpr_true() {
    assertOk("(1 < 2 ? 3 : 4) == 3");
  }

  @Test
  public void conditionalExpr_false() {
    assertOk("(1 > 2 ? 3 : 4) == 4");
  }

  @Test
  public void conditionalExpr_linebreaks1() {
    assertOk("(1 < 2 \n ? 3 \n : 4) == 3");
  }

  @Test
  public void conditionalExpr_linebreaks2() {
    assertOk("(1 < 2 ? \n  3 : \n 4) == 3");
  }

  @Test
  public void conditionalExpr_linebreaks3() {
    assertOk("(1 < 2 \n ? \n  3 \n : \n 4) == 3");
  }

  @Test
  public void conditionalExpr_subtypes_ok1() {
    testAssertOkLines(
        false,
        "package test",
        "class A",
        "class B extends A",
        "class C extends A",
        "init",
        "	A a = 1<3 ? new B : new C");
  }

  @Test
  public void conditionalExpr_blocks() {
    testAssertOkLines(
        true,
        "package test",
        "native testSuccess()",
        "init",
        "    int x = 0",
        "    int y = 1 < 3 ? begin",
        "        x = 40",
        "        return 2",
        "    end : begin",
        "        x = 60",
        "        return 3",
        "    end",
        "    if x + y == 42",
        "        testSuccess()");
  }

  @Test
  public void conditionalExpr_subtypes_err1() {
    testAssertErrorsLines(
        false,
        "Cannot assign (B or C) to A",
        "package test",
        "class A",
        "class B extends A",
        "class C",
        "init",
        "	A a = 1<3 ? new B : new C");
  }

  @Test
  public void conditionalExpr_subtypes_err2() {
    testAssertErrorsLines(
        false,
        "Cannot assign (B or C) to A",
        "package test",
        "class A",
        "class B",
        "class C extends A",
        "init",
        "	A a = 1<3 ? new B : new C");
  }

  @Test
  public void conditionalExpr_subtypes_err3() {
    testAssertErrorsLines(
        false,
        "Cannot assign A to int",
        "package test",
        "class A",
        "class B extends A",
        "init",
        "	int i = 1<3 ? new A : new B");
  }

  @Test
  public void conditionalExpr_subtypes_err4() {
    testAssertErrorsLines(
        false,
        "Cannot assign A to int",
        "package test",
        "class A",
        "class B extends A",
        "init",
        "	int i = 1<3 ? new B : new A");
  }

  @Test
  public void conditionalExpr_real() {
    testAssertOkLines(
        true,
        "package test",
        "native testSuccess()",
        "init",
        "    real r = 1<3 ? 123 : 456",
        "    if r == 123.0",
        "        testSuccess()");
  }

  @Test
  public void conditionalExpr_inferNull_right1() {
    testAssertOkLines(
        false, "package test", "class A", "class B extends A", "init", "	A a = 1<3 ? new B : null");
  }

  @Test
  public void conditionalExpr_inferNull_right2() {
    testAssertOkLines(false, "package test", "class A", "init", "	let a = 1<3 ? new A : null");
  }

  @Test
  public void conditionalExpr_inferNull_left() {
    testAssertOkLines(false, "package test", "class A", "init", "	let a = 1<3 ? null : new A");
  }

  @Test
  public void conditionalExpr_inferNull_fail() {
    testAssertErrorsLines(
        false,
        "Both branches of conditional expression have type null",
        "package test",
        "class A",
        "init",
        "	let a = 1<3 ? null : null");
  }

  @Test
  public void conditionalExpr_voidFail() {
    testAssertErrorsLines(
        false,
        "Conditional expression must return a value, but result type of then-expression is void.",
        "type filter extends handle",
        "package test",
        "native Filter(code c) returns filter",
        "function foo()",
        "function bar() returns boolean",
        "    return false",
        "init",
        "    let f = Filter(() -> true ? foo() : bar())");
  }

  private String makeProg(String booleanExpr) {
    String prog =
        "package test \n"
            + "	native testFail(string msg)\n"
            + "	native testSuccess()\n"
            + "	init \n"
            + "		int x = 3\n"
            + "		int y = 4\n"
            + "		int z = 5\n"
            + "		string a = \"bla\"\n"
            + "		string b = \"blub\"\n"
            + "		if "
            + booleanExpr
            + "\n"
            + "			testSuccess()\n"
            + "endpackage";
    return prog;
  }

  public void assertOk(String booleanExpr) {
    String prog = makeProg(booleanExpr);
    testAssertOk(UtilsIO.getMethodName(1), true, prog);
  }

  public void assertError(String errorMessage, String booleanExpr) {
    String prog = makeProg(booleanExpr);
    testAssertErrors(UtilsIO.getMethodName(1), true, prog, errorMessage);
  }
}
