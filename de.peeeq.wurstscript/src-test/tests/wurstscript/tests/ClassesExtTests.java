package tests.wurstscript.tests;

import org.junit.Test;

public class ClassesExtTests extends WurstScriptTest {
	
	
	
	
	@Test
	public void extends_simple() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		function foo() returns int",
				"			return 3",
				"	class D extends C",
				"	init",
				"		if new D().foo() == 3",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void extends_override() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		function foo() returns int",
				"			return 3",
				"	class D extends C",
				"		override function foo() returns int",
				"			return 4",
				"	init",
				"		if new D().foo() == 4",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	
	@Test
	public void extends_override2() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		function bar() returns int",
				"			return foo()",
				"		function foo() returns int",
				"			return 3",
				"	class D extends C",
				"		override function foo() returns int",
				"			return 4",
				"	init",
				"		if new D().bar() == 4",
				"			testSuccess()",
				"endpackage"
			);
	}

	
	@Test
	public void extends_override3() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		function foo() returns int",
				"			return 3",
				"	class D extends C",
				"	class E extends D",
				"		override function foo() returns int",
				"			return 4",
				"	init",
				"		D e = new E()",
				"		if e.foo() == 4",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void extends_override4() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		function foo() returns int",
				"			return 3",
				"	class D extends C",
				"		function bla() returns int",
				"			return foo()",
				"	class E extends D",
				"		override function foo() returns int",
				"			return 4",
				"	init",
				"		D e = new E()",
				"		if e.bla() == 4",
				"			testSuccess()",
				"endpackage"
			);
	}

	@Test
	public void extends_variables() {
		testAssertOkLines(true, 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		int i = 5",
				"	class D extends C",
				"		function foo() returns int",
				"			return i+1",
				"	init",
				"		if new D().foo() == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	@Test
	public void privateVar() {
		testAssertErrorsLines(false, "not visible", 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		private int i = 5",
				"	class D extends C",
				"		function foo() returns int",
				"			return i+1",
				"endpackage"
			);
	}
	
	@Test
	public void privateFunc() {
		testAssertErrorsLines(false, "not visible", 
				"package test",
				"	native testSuccess()",
				"	class C",
				"		private function foo()",
				"	class D extends C",
				"		function bar()",
				"			foo()",
				"endpackage"
			);
	}
	
	@Test
	public void privateFuncOverride() {
		testAssertOkLines(false,  
				"package test",
				"	native testSuccess()",
				"	class C",
				"		private function foo() returns int",
				"			return 3",
				"		function bar() returns int",
				"			return foo() + 1",
				"	class D extends C",
				"		function foo() returns int",
				"			return 10",
				"	init",
				"		if new D().bar() == 4",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	
	@Test
	public void constr1() {
		testAssertErrorsLines(false, "Incorrect call to super constructor",  
				"package test",
				"	native testSuccess()",
				"	class Pair",
				"		int a",
				"		int b",
				"		construct(int a, int b)",
				"			this.a = a",
				"			this.b = b",
				"	class OtherPair extends Pair",
				"	init",
				"		if new OtherPair().a == 2",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void constr2() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	class Pair",
				"		int a",
				"		int b",
				"		construct(int a, int b)",
				"			this.a = a",
				"			this.b = b",
				"	class OtherPair extends Pair",
				"		construct(int a, int b)",
				"			super(a,b)",
				"			skip",
				"	init",
				"		if new OtherPair(2, 3).a == 2",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void constr_super() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	class Pair",
				"		int a",
				"		int b",
				"		construct(int a, int b)",
				"			this.a = a",
				"			this.b = b",
				"	class OtherPair extends Pair",
				"		construct(int a, int b)",
				"			super(a*2,b*2)",
				"	init",
				"		if new OtherPair(2, 3).a == 4",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void constr_super_wrong1() {
		testAssertErrorsLines(true, "Expected integer",  
				"package test",
				"	native testSuccess()",
				"	class Pair",
				"		int a",
				"		int b",
				"		construct(int a, int b)",
				"			this.a = a",
				"			this.b = b",
				"	class OtherPair extends Pair",
				"		construct(int a, int b)",
				"			super(a*2, \"bla\")",
				"	init",
				"		if new OtherPair(2, 3).a == 4",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void ondestroy() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"		ondestroy",
				"			x += 1",
				"	init",
				"		A a = new A()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	@Test
	public void ondestroy_dynamicdispatch() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"		ondestroy",
				"			x += 1",
				"	init",
				"		B a = new A()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	@Test
	public void ondestroy_dynamicdispatch2() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"	class X extends A",
				"		ondestroy",
				"			x += 1",
				"	init",
				"		B a = new X()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void ondestroy_dynamicdispatch3() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"		ondestroy",
				"			x += 1",
				"	class X extends A",
				"	init",
				"		B a = new X()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	
	@Test
	public void ondestroy_dynamicdispatch4() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"		ondestroy",
				"			x += 1",
				"	class X extends A",
				"	init",
				"		A a = new X()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void ondestroy_dynamicdispatch5() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			x *= 2",
				"	class A extends B",
				"		ondestroy",
				"			x += 1",
				"	class X extends A",
				"	init",
				"		X a = new X()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void ondestroy_dynamicdispatchFrotty1() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	class A extends T",
				"	class B extends T",
				"		ondestroy",
				"			s += \"B\"",
				"	abstract class T",
				"	string s=\"\"",
				"	init",
				"		T t = new A()",
				"		destroy t",
				"		if s == \"\"",
				"			testSuccess()",
				"endpackage"
				);
	}
	
	@Test
	public void ondestroy_dynamicdispatchFrotty2() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	class A implements T",
				"	class B implements T",
				"		ondestroy",
				"			s += \"B\"",
				"	interface T",
				"		function f()",
				"			skip",
				"	string s=\"\"",
				"	init",
				"		T t = new A()",
				"		destroy t",
				"		if s == \"\"",
				"			testSuccess()",
				"endpackage"
				);
	}
	
	@Test
	public void ondestroy_dynamicdispatchFrotty3() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	abstract class C",
				"		ondestroy",
				"			s+=\"C\"",
				"	class A extends C implements T",
				"	class B extends C implements T",
				"		ondestroy",
				"			s += \"B\"",
				"	interface T",
				"		function f()",
				"			skip",
				"	string s=\"\"",
				"	init",
				"		T t = new A()",
				"		destroy t",
				"		if s == \"C\"",
				"			testSuccess()",
				"endpackage"
				);
	}
	
	@Test
	public void ondestroy_withVar() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		ondestroy",
				"			let y = 2",
				"			x *= y",
				"	class A extends B",
				"		ondestroy",
				"			let z = 1",
				"			x += z",
				"	class X extends A",
				"	init",
				"		X a = new X()",
				"		destroy a",
				"		if x == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void ondestroyUsingThis() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		int y = 2",
				"		ondestroy",
				"			x *= y",
				"	class A extends B",
				"		ondestroy",
				"			x += y",
				"	init",
				"		A a = new A()",
				"		destroy a",
				"		if x == 8",
				"			testSuccess()",
				"endpackage"
			);
	}
	

	@Test
	public void superCall() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		int y = 2",
				"		function foo()",
				"			x *= y",
				"	class A extends B",
				"		override function foo()",
				"			x += y",
				"			super.foo()",
				"	init",
				"		A a = new A()",
				"		a.foo()",
				"		if x == 8",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void superCall2() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class B",
				"		int y = 2",
				"		function foo()",
				"			x *= y",
				"	class A extends B",
				"		override function foo()",
				"			x += y",
				"			B.foo()",
				"	init",
				"		A a = new A()",
				"		a.foo()",
				"		if x == 8",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void testtest() {
		testAssertOkLines(true,  
				"package test",
				"	native testSuccess()",
				"	int x = 2",
				"	class A",
				"		int y = 2",
				"		function foo()",
				"			x = 4",
				"	class B extends A",
				"		override function foo()",
				"			x = 5",
				"	class C extends B",
				"		override function foo()",
				"			x = 6",
				"	init",
				"		A a = new B()",
				"		a.foo()",
				"		if x == 5",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	@Test
	public void teststaticoverride() {
		testAssertErrorsLines(false, "Cannot override static func",  
				"package test",
				"	native testSuccess()",
				"	class A",
				"		static function foo()",
				"	class B extends A",
				"		override static function foo()",
				"endpackage"
			);
	}
	
	@Test
	public void teststaticoverride2() {
		testAssertErrorsLines(false, "Static functions cannot be abstract",  
				"package test",
				"	native testSuccess()",
				"	abstract class A",
				"		abstract static function foo()",
				"endpackage"
			);
	}
	
	@Test
	public void testNoDispatch() {
		testAssertOkLines(false,
				"package test",
				"	class A",
				"		function foo(int i, int i2)",
				"	class B extends A",
				"		function foo(int i)",
				"	init",
				"		let a = new A()",
				"		let b = new B()",
				"		a.foo(1,1)",
				"		b.foo(1)",
				"endpackage"
			);
	}
	
	@Test
	public void testMultiArray() {
		testAssertOkLines(true,
				"package test",
				"	native testSuccess()",
				"	class A",
				"		int array[5] foo",
				"	init",
				"		let a = new A()",
				"		a.foo[3] = 6",
				"		if a.foo[3] == 6",
				"			testSuccess()",
				"endpackage"
			);
	}
	
	// TODO @Test
	public void testMultiTuple() {
		testAssertOkLines(true,
				"package test",
				"	native testSuccess()",
				"	tuple v(int x, int y)",
				"	class A",
				"		v array[5] foo",
				"	init",
				"		let a = new A()",
				"		a.foo[3] = v(3,4)",
				"		if a.foo[3] == v(3,4)",
				"			testSuccess()",
				"endpackage"
			);
	}
}
