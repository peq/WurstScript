package tests.wurstscript.tests;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import de.peeeq.wurstio.WurstCompilerJassImpl;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;

public class RealWorldExamples extends WurstScriptTest {
	
	private static final String TEST_DIR = "./testscripts/concept/";
	private static final String BUG_DIR = "./testscripts/realbugs/";
	
	@Override 
	protected boolean testOptimizer() {
		return true;
	}
	
	
	@Test
	public void arrayindex() throws IOException {
		// see bug #96
		super.testAssertOkFileWithStdLib(new File(BUG_DIR + "arrayindex.wurst"), false);
	}
	
	@Test
	public void module() throws IOException {
		super.testAssertOkFileWithStdLib(new File(BUG_DIR + "module.wurst"), false);
	}
	
//	@Test
//	public void testCyclic() throws IOException {
//		super.testAssertErrorFileWithStdLib(new File(BUG_DIR + "cyclic.wurst"), "cyclic dependency", true);
//	}

	@Test
	public void testLists() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "ListsTest.wurst"), false);
	}

	
	@Test
	public void testEditorVars() throws IOException {
		// we expect an error here, but only in the translation phase
		testAssertErrorFileWithStdLib(new File(TEST_DIR + "EditorVariables.wurst"), "Translation Error: Could not find definition of gg_", false);
	}
	
	@Test
	public void setNullTests() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "SetNullTests.wurst"), false);
	}
	
	@Test
	public void setFrottyBugKnockbackNull() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "knockback.wurst"), false);
	}
	
	@Test
	public void setFrottyBugEscaperData() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "escaperdata.wurst"), false);
	}
	

	@Test
	public void setFrottyBugVector() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "vector.wurst"), false);
	}
	
	@Test
	public void test_war3map() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "test_war3map.wurst"), false);
	}
	
	@Test
	public void frottyTupleBug() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "tupleBug.wurst"), false);
	}
	
//	@Test
//	public void optimizer() throws IOException {
//		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "optimizer.wurst"), false);
//	}
	
	@Test
	public void optimizerNew() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "optimizerNewTests.wurst"), false);
	}
	
	@Test
	public void staticCallback() throws IOException {
		super.testAssertOkFileWithStdLib(new File(TEST_DIR + "staticCallback.wurst"), false);
	}
	
	@Test
	public void nonStaticCallback() throws IOException {
		super.testAssertErrorFileWithStdLib(new File(TEST_DIR + "nonStaticCallback.wurst"), "without parameters", false);
	}
	
	@Test
	public void criggesInitOrder1() throws IOException {
		super.testAssertErrorFileWithStdLib(new File(TEST_DIR + "CriggesInitOrder1.wurst"), "not yet initialized", false);
	}
	
	@Test
	public void criggesInitOrder2() throws IOException {
		super.testAssertErrorFileWithStdLib(new File(TEST_DIR + "CriggesInitOrder2.wurst"), "used before it is initialized", false);
	}
	
	
	
	@Test
	public void test_stdlib() throws IOException {
		List<File> inputs = Lists.newLinkedList();
//		settings.put("lib", "./wurstscript/lib/");
//		config.setSetting("lib", "../Wurstpack/wurstscript/lib/");
		// TODO set config
		RunArgs runArgs = RunArgs.defaults();
		runArgs.addLibs(Sets.newHashSet("../Wurstpack/wurstscript/lib/"));
		WurstCompilerJassImpl comp = new WurstCompilerJassImpl(new WurstGuiCliImpl(), null, runArgs);
		for (File f : comp.getLibs().values()) {
			WLogger.info("Adding file: " + f);
			inputs.add(f);
		}
		testScript(inputs, null, "stdlib", false, true, true);
		
	}
	
}