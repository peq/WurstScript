package tests.prettyprint;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import de.peeeq.wurstio.WurstCompilerJassImpl;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.attributes.prettyPrint.MaxOneSpacer;
import de.peeeq.wurstscript.attributes.prettyPrint.PrettyPrinter;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;
import org.testng.annotations.Test;
import tests.wurstscript.tests.WurstScriptTest;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static org.testng.AssertJUnit.assertEquals;

public class PrettyPrintTest extends WurstScriptTest {

    private String setUp(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        File inFile = new File(filename);
        String content = Files.toString(inFile, Charsets.UTF_8);

        WurstGui gui = new WurstGuiCliImpl();
        WurstCompilerJassImpl compiler = new WurstCompilerJassImpl(gui, null, new RunArgs("-prettyPrint"));

        CompilationUnit cu = compiler.parse("test", new StringReader(content));
        PrettyPrinter.prettyPrint(cu, new MaxOneSpacer(), sb, 0);

        return sb.toString();
    }

    private String expectedFile(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        File inFile = new File(filename);
        return Files.toString(inFile, Charsets.UTF_8);
    }

    // Note: only works with single extension files, i.e it doesn't work with:
    // asdf.tar.gz
    private static String extension(String filename) {
        String extension = "";

        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i+1);
        }
        return extension;
    }

    private void test(String filename) throws IOException {
        String testFilenameFormat = "testscripts/pretty/%s";
        String pretty = setUp(String.format(testFilenameFormat, filename));
        String expected = expectedFile(String.format(testFilenameFormat, filename));

        assertEquals(expected, pretty);
    }

    @Test
    public void testPrettyIf() throws IOException {
        test("If.wurst");
    }

    @Test
    public void testPrettyLoops() throws IOException {
        test("Loops.wurst");
    }

    @Test
    public void testPrettyAssignments() throws IOException {
        test("Assignment_shorthand.wurst");
    }

    @Test
    public void testCascade() throws IOException {
        test("Cascade.wurst");
    }

    @Test
    public void testSwitch() throws IOException {
        test("Switch.wurst");
    }

    @Test
    public void testAnnotations() throws IOException {
        test("Annotations.wurst");
    }

    @Test
    public void testClosures() throws IOException {
        test("Closures.wurst");
    }

    @Test
    public void testEnum() throws IOException {
        test("Enum.wurst");
    }

    @Test
    public void testFunctionDefinitions() throws IOException {
        test("FunctionDefinitions.wurst");
    }

    @Test
    public void testTernary() throws IOException {
        test("Ternary.wurst");
    }

    @Test
    public void testBinaryExprAndMethod() throws IOException {
        test("BinaryExprAndMethod.wurst");
    }

    @Test
    public void testReal1() throws IOException {
        test("Real1.wurst");
    }

    @Test
    public void testReal2() throws IOException {
        test("Real2.wurst");
    }

    @Test
    public void testReal3() throws IOException {
        test("Real3.wurst");
    }
}
