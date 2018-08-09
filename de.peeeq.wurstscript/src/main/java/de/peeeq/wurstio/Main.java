package de.peeeq.wurstio;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import de.peeeq.wurstio.Pjass.Result;
import de.peeeq.wurstio.compilationserver.WurstServer;
import de.peeeq.wurstio.gui.AboutDialog;
import de.peeeq.wurstio.gui.WurstGuiImpl;
import de.peeeq.wurstio.hotdoc.HotdocGenerator;
import de.peeeq.wurstio.languageserver.LanguageServerStarter;
import de.peeeq.wurstio.languageserver.requests.RunTests;
import de.peeeq.wurstio.map.importer.ImportFile;
import de.peeeq.wurstio.mpq.MpqEditor;
import de.peeeq.wurstio.mpq.MpqEditorFactory;
import de.peeeq.wurstio.utils.FileUtils;
import de.peeeq.wurstio.utils.W3Utils;
import de.peeeq.wurstscript.*;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.attributes.prettyPrint.PrettyUtils;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.gui.WurstGuiCliImpl;
import de.peeeq.wurstscript.intermediatelang.interpreter.ILStackFrame;
import de.peeeq.wurstscript.jassAst.JassProg;
import de.peeeq.wurstscript.jassprinter.JassPrinter;
import de.peeeq.wurstscript.utils.Utils;
import net.moonlightflower.wc3libs.bin.GameExe;
import org.eclipse.jdt.annotation.Nullable;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import static de.peeeq.wurstio.CompiletimeFunctionRunner.FunctionFlagToRun.CompiletimeFunctions;
import static javax.swing.SwingConstants.CENTER;

public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            // If no args are passed, display help
            new RunArgs("-help");
            return;
        }

        WurstGui gui = null;
        RunArgs runArgs = new RunArgs(args);
        try {
            if (runArgs.isLanguageServer()) {
                WLogger.setLogger("languageServer");
            }
            logStartup(args);

            if (runArgs.showHelp()) {
                return;
            }

            if (runArgs.isShowVersion()) {
                System.out.println(CompileTimeInfo.version);
                return;
            }

            if (runArgs.showAbout()) {
                new AboutDialog(null, false).setVisible(true);
                return;
            }

            if (runArgs.isFixInstall()) {
                fixInstallation();
                return;
            }

            if (runArgs.isCopyMap()) {
                copyMap();
                return;
            }

            if (runArgs.isStartServer()) {
                WurstServer.startServer();
                return;
            }

            if (runArgs.isLanguageServer()) {
//                new LanguageServer().start();
                LanguageServerStarter.start();
                return;
            }

            if (runArgs.isPrettyPrint()) {
                PrettyUtils.pretty(runArgs.getFiles());
                return;
            }

            WLogger.info("runArgs.isExtractImports() = " + runArgs.isExtractImports());
            String mapFilePath = runArgs.getMapFile();
            if (runArgs.isExtractImports()) {
                File mapFile = new File(mapFilePath);
                ImportFile.extractImportsFromMap(mapFile, runArgs);
                return;
            }

            if (runArgs.createHotDoc()) {
                HotdocGenerator hg = new HotdocGenerator(runArgs.getFiles());
                hg.generateDoc();
                return;
            }

            if (runArgs.isGui()) {
                gui = new WurstGuiImpl();
                // use the error reporting with GUI
                ErrorReporting.instance = new ErrorReportingIO();
            } else {
                gui = new WurstGuiCliImpl();
            }

            if (runArgs.showLastErrors()) {
                JOptionPane.showMessageDialog(null, "not implemented");
                return;
            }

            try {
                if (mapFilePath != null) {
                    // tempfolder
                    File tempFolder = new File("./temp/");
                    tempFolder.mkdirs();
                    BackupController bc = new BackupController();
                    bc.makeBackup(mapFilePath);
                }

                if (mapFilePath != null) {
                    try (MpqEditor mpqEditor = MpqEditorFactory.getEditor(new File(mapFilePath))) {
                        CharSequence mapScript = doCompilation(gui, mpqEditor, runArgs);
                        if (mapScript != null) {
                            gui.sendProgress("Writing to map");
                            mpqEditor.deleteFile("war3map.j");
                            byte[] war3map = mapScript.toString().getBytes(Charsets.UTF_8);
                            mpqEditor.insertFile("war3map.j", war3map);
                        }
                    }
                } else {
                    doCompilation(gui, null, runArgs);
                }

                gui.sendProgress("Finished!");
            } catch (AbortCompilationException e) {
                gui.showInfoMessage(e.getMessage());
            }
        } catch (Throwable t) {
            String source = "";
            // TODO add additional information to source
            ErrorReporting.instance.handleSevere(t, source);
            if (!runArgs.isGui()) {
                System.exit(2);
            }
        } finally {
            if (gui != null) {
                gui.sendFinished();
                if (!runArgs.isGui()) {
                    if (gui.getErrorCount() > 0) {
                        // 	print error messages
                        for (CompileError err : gui.getErrorList()) {
                            System.out.println(err);
                        }
                        // signal that there was an error when compiling
                        System.exit(1);
                    }
                    // print warnings:
                    for (CompileError err : gui.getWarningList()) {
                        System.out.println(err);
                    }
                }
            }
        }
    }

    private static void logStartup(String[] args) {
        // VM Arguments
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();

        WLogger.info("### Started wurst version: (" + AboutDialog.version + ")");
        WLogger.info("### With wurst-args " + Utils.printSep(", ", args));
        if (arguments != null && arguments.size() > 0) {
            WLogger.info("### With vm-args " + Utils.printSep(", ", arguments.toArray(new String[0])));
        }
        try {
            WLogger.info("### compiler path1: " + Main.class.getProtectionDomain().getCodeSource().getLocation());
            WLogger.info("### compiler path2: " + ClassLoader.getSystemClassLoader().getResource(".").getPath());
        } catch (Throwable ignored) {
        }
        WLogger.info("### ============================================");
    }

    private static final String COMPAT_FOLDER = "compat\\";

    /**
     * Creates a copy of the wc3 game data files inside a compat/ folder that allows running JNGP.
     */
    private static void fixInstallation() throws Exception {
        String wc3Path = W3Utils.getGamePath();
        if (wc3Path == null) {
            WLogger.severe("installation could not be found");
        }
        String compatPath = wc3Path + COMPAT_FOLDER;
        WLogger.info("Wc3 Path: " + wc3Path);

        GameExe.Version patchVersion = W3Utils.getWc3PatchVersion();
        if (patchVersion.compareTo(new GameExe.Version("1.27")) > 0 && !new File(compatPath).exists()) {
            JLabel notice = new JLabel("Patch 1.28 or higher has been detected on your system.\n" +
                    "Selecting yes will create a compatibility copy of your installation.\n" +
                    "Selecting no will leave everything as is and the editor won't start.", CENTER);

            int result = JOptionPane.showConfirmDialog(null, notice, "Wurst Note", JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                JOptionPane.showMessageDialog(null, "Wurst is now working. This may take a few minutes.\n " +
                        "You will be notified about further progress.");
                checkLoadFiles();
                copyBinaries(wc3Path, compatPath);
                insertKeys(compatPath);
                WLogger.info("Compatibility installation created.");
                JOptionPane.showMessageDialog(null, "Done. The editor should start momentarily");
            }
        }
    }

    private static void copyMap() throws Exception {
        String wc3Path = Objects.requireNonNull(GameExe.fromRegistry()).getFile().getParent();
        WLogger.info("Wc3 Path: " + wc3Path);

        String documentPath = FileSystemView.getFileSystemView().getDefaultDirectory().getPath() + File.separator + "Warcraft III";
        if (new File(documentPath).exists()) {
            File compatMap = new File(wc3Path + COMPAT_FOLDER + "Maps\\Test\\WorldEditTestMap.w3x");
            if (compatMap.exists()) {
                Files.copy(compatMap, new File(documentPath + "\\Maps\\Test\\WorldEditTestMap.w3x"));
                WLogger.info("Map copied");
            }
        }

    }

    private static void copyBinaries(String wc3Path, String compatPath) throws IOException {
        File compatDir = new File(compatPath);
        compatDir.mkdirs();
        File compatExe = new File(compatPath + "war3.exe");
        if (!compatExe.exists()) {
            // Create copy war3.exe
            File newExe = new File(wc3Path + "Warcraft III.exe");
            if (newExe.exists()) {
                Files.copy(newExe, compatExe);
                WLogger.info("Copied war3.exe");
            } else {
                WLogger.severe("Could not find valid wc3 executable");
            }
        }

        File compatEditor = new File(compatPath + "worldedit.exe");
        if (!compatEditor.exists()) {
            // Create copy war3.exe
            File newEditor = new File(wc3Path + "World Editor.exe");
            if (newEditor.exists()) {
                Files.copy(newEditor, compatEditor);
                WLogger.info("Created worldedit.exe");
            } else {
                WLogger.severe("Could not find valid editor executable");
            }
        }

        File[] files = new File(wc3Path).listFiles((File pathname) -> pathname.getName().endsWith(".mpq") || pathname.getName().endsWith(".dll"));
        for (File file : files) {
            Files.copy(file, new File(compatPath, file.getName()));
        }

        File storm = new File(compatPath + "Storm.dll");
        if (!storm.exists() || storm.delete()) {
            Files.write(java.nio.file.Files.readAllBytes(stormDll), storm);
            if (storm.exists()) {
                WLogger.info("Storm.dll written");
            }
        }

    }

    private static void insertKeys(String compatPath) throws Exception {
        File rocMpq = new File(compatPath + "War3.mpq");
        File tftMpq = new File(compatPath + "War3x.mpq");
        MpqEditor roceditor = MpqEditorFactory.getEditor(rocMpq);
        boolean rocHasKeys = roceditor.hasFile("font\\font.ccd") && roceditor.hasFile("font\\font.gid") && roceditor.hasFile("font\\font.clh");
        if (!rocHasKeys) {
            JOptionPane.showMessageDialog(null, "Now inserting ROC mpq.\n"
                    + "This might take a few minutes. Please be patient.");
            roceditor.insertFile("font\\font.gid", fontGID.toFile());
            roceditor.insertFile("font\\font.clh", fontCLH.toFile());
            roceditor.insertFile("font\\font.ccd", fontROC.toFile());
            roceditor.close();
            WLogger.info("inserted roc keys");
        } else {
            WLogger.info("Already has roc keys");
        }
        MpqEditor tfteditor = MpqEditorFactory.getEditor(tftMpq);
        boolean tftHasKeys = tfteditor.hasFile("font\\font.ccd") && tfteditor.hasFile("font\\font.exp");
        if (!tftHasKeys) {
            JOptionPane.showMessageDialog(null, "Now inserting TFT mpq.\n"
                    + "This might take a few minutes. Please be patient.");
            tfteditor.insertFile("font\\font.exp", fontEXP.toFile());
            tfteditor.insertFile("font\\font.ccd", fontTFT.toFile());
            tfteditor.close();
            WLogger.info("inserted tft keys");
        } else {
            WLogger.info("Already has tft keys");
        }
    }

    private static Path fontGID;
    private static Path fontCLH;
    private static Path fontROC;
    private static Path fontEXP;
    private static Path fontTFT;
    private static Path stormDll;


    private static void checkLoadFiles() {
        String folder = "font/";
        fontGID = Paths.get(Utils.getResourceFile(folder + "font.gid"));
        fontCLH = Paths.get(Utils.getResourceFile(folder + "font.clh"));
        fontROC = Paths.get(Utils.getResourceFile(folder + "font_roc.ccd"));
        fontEXP = Paths.get(Utils.getResourceFile(folder + "font.exp"));
        fontTFT = Paths.get(Utils.getResourceFile(folder + "font_tft.ccd"));
        stormDll = Paths.get(Utils.getResourceFile(folder + "Storm.dll"));
    }

    private static @Nullable CharSequence doCompilation(WurstGui gui, @Nullable MpqEditor mpqEditor, RunArgs runArgs) throws IOException {
        WurstCompilerJassImpl compiler = new WurstCompilerJassImpl(gui, mpqEditor, runArgs);
        gui.sendProgress("Check input map");
        if (mpqEditor != null && !mpqEditor.canWrite()) {
            WLogger.severe("The supplied map is invalid/corrupted/protected and Wurst cannot write to it.\n" +
                    "Please supply a valid .w3x input map that can be opened in the world editor.");
        }

        for (String file : runArgs.getFiles()) {
            compiler.loadFiles(file);
        }
        WurstModel model = compiler.parseFiles();

        if (gui.getErrorCount() > 0) {
            return null;
        }
        if (model == null) {
            return null;
        }

        compiler.checkProg(model);

        if (gui.getErrorCount() > 0) {
            return null;
        }

        compiler.translateProgToIm(model);

        if (gui.getErrorCount() > 0) {
            return null;
        }

        File mapFile = compiler.getMapFile();

        if (runArgs.isRunTests()) {
            PrintStream out = System.out;
            // tests
            gui.sendProgress("Running tests");
            System.out.println("Running tests");
            RunTests runTests = new RunTests(null, 0, 0) {
                @Override
                protected void print(String message) {
                    out.print(message);
                }
            };
            runTests.runTests(compiler.getImProg(), null, null);

            for (RunTests.TestFailure e : runTests.getFailTests()) {
                gui.sendError(new CompileError(e.getFunction().attrTrace().attrErrorPos(), e.getMessage()));
                if (runArgs.isGui()) {
                    // when using graphical user interface, send stack trace to GUI
                    for (ILStackFrame sf : Utils.iterateReverse(e.getStackTrace().getStackFrames())) {
                        gui.sendError(sf.makeCompileError());
                    }
                }
            }

            System.out.println("Finished running tests");
        }

        if (runArgs.runCompiletimeFunctions()) {

            // compiletime functions
            gui.sendProgress("Running compiletime functions");
            CompiletimeFunctionRunner ctr = new CompiletimeFunctionRunner(compiler.getImProg(), mapFile, mpqEditor, gui, CompiletimeFunctions);
            ctr.setInjectObjects(runArgs.isInjectObjects());
            ctr.run();
        }

        if (runArgs.isInjectObjects()) {
            Preconditions.checkNotNull(mpqEditor);
            // add the imports
            ImportFile.importFilesFromImportDirectory(new File(runArgs.getMapFile()), mpqEditor);
        }

        JassProg jassProg = compiler.transformProgToJass();

        if (jassProg == null || gui.getErrorCount() > 0) {
            return null;
        }

        boolean withSpace;
        withSpace = !runArgs.isOptimize();

        gui.sendProgress("Printing Jass");
        JassPrinter printer = new JassPrinter(withSpace, jassProg);
        CharSequence mapScript = printer.printProg();

        // output to file
        gui.sendProgress("Writing output file");
        File outputMapscript;
        if (runArgs.getOutFile() != null) {
            outputMapscript = new File(runArgs.getOutFile());
        } else {
            //outputMapscript = File.createTempFile("outputMapscript", ".j");
            outputMapscript = new File("./temp/output.j");
        }
        outputMapscript.getParentFile().mkdirs();
        FileUtils.write(mapScript, outputMapscript);

        if (!runArgs.isDisablePjass()) {
            Result pJassResult = Pjass.runPjass(outputMapscript);
            WLogger.info(pJassResult.getMessage());
            if (!pJassResult.isOk()) {
                for (CompileError err : pJassResult.getErrors()) {
                    gui.sendError(err);
                }
                return null;
            }
        }
        return mapScript;
    }

}
