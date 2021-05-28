package de.peeeq.wurstio;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.io.Files;
import de.peeeq.wurstio.languageserver.requests.RequestFailedException;
import de.peeeq.wurstio.map.importer.ImportFile;
import de.peeeq.wurstio.mpq.MpqEditor;
import de.peeeq.wurstio.utils.FileReading;
import de.peeeq.wurstio.utils.FileUtils;
import de.peeeq.wurstscript.*;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.attributes.CompilationUnitInfo;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.attributes.ErrorHandler;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.jassAst.JassProg;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.jassprinter.JassPrinter;
import de.peeeq.wurstscript.luaAst.LuaCompilationUnit;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.translation.imoptimizer.ImOptimizer;
import de.peeeq.wurstscript.translation.imtojass.ImToJassTranslator;
import de.peeeq.wurstscript.translation.imtranslation.*;
import de.peeeq.wurstscript.translation.lua.translation.LuaTranslator;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.utils.LineOffsets;
import de.peeeq.wurstscript.utils.NotNullList;
import de.peeeq.wurstscript.utils.TempDir;
import de.peeeq.wurstscript.utils.Utils;
import de.peeeq.wurstscript.validation.TRVEHelper;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.MessageType;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import static de.peeeq.wurstio.CompiletimeFunctionRunner.FunctionFlagToRun.CompiletimeFunctions;

public class WurstCompilerJassImpl implements WurstCompiler {

    private List<File> files = Lists.newArrayList();
    private Map<String, Reader> otherInputs = Maps.newLinkedHashMap();
    private @Nullable JassProg prog;
    private WurstGui gui;
    private boolean hasCommonJ;
    private RunArgs runArgs;
    private Optional<File> mapFile = Optional.empty();
    private @Nullable File projectFolder;
    private ErrorHandler errorHandler;
    private @Nullable Map<String, File> libCache = null;
    private @Nullable ImProg imProg;
    private List<File> parsedFiles = Lists.newArrayList();
    private final WurstParser parser;
    private final WurstChecker checker;
    private @Nullable ImTranslator imTranslator;
    private List<File> dependencies = Lists.newArrayList();
    private final @Nullable MpqEditor mapFileMpq;
    private TimeTaker timeTaker;

    public WurstCompilerJassImpl(@Nullable File projectFolder, WurstGui gui, @Nullable MpqEditor mapFileMpq, RunArgs runArgs) {
        this(new TimeTaker.Default(), projectFolder, gui, mapFileMpq, runArgs);
    }

    public WurstCompilerJassImpl(TimeTaker timeTaker, @Nullable File projectFolder, WurstGui gui, @Nullable MpqEditor mapFileMpq, RunArgs runArgs) {
        this.timeTaker = timeTaker;
        this.projectFolder = projectFolder;
        this.gui = gui;
        this.runArgs = runArgs;
        this.errorHandler = new ErrorHandler(gui);
        this.parser = new WurstParser(errorHandler, gui);
        this.checker = new WurstChecker(gui, errorHandler);
        this.mapFileMpq = mapFileMpq;
    }

    @Override
    public void loadFiles(String... filenames) {
        gui.sendProgress("Loading Files");
        for (String filename : filenames) {
            File file = new File(filename);
            if (!file.exists()) {
                throw new Error("File " + filename + " does not exist.");
            }
            files.add(file);
        }
    }

    @Override
    public void loadFiles(File... files) {
        gui.sendProgress("Loading Files");
        for (File file : files) {
            loadFile(file);
        }
    }

    @Override
    public void runCompiletime() {
        if (runArgs.runCompiletimeFunctions()) {
            // compile & inject object-editor data
            // TODO run optimizations later?
            gui.sendProgress("Running compiletime functions");
            CompiletimeFunctionRunner ctr = new CompiletimeFunctionRunner(imTranslator, getImProg(), getMapFile(), getMapfileMpqEditor(), gui,
                    CompiletimeFunctions);
            ctr.setInjectObjects(runArgs.isInjectObjects());
            ctr.setOutputStream(new PrintStream(System.err));
            ctr.run();
        }

        if (gui.getErrorCount() > 0) {
            CompileError compileError = gui
                    .getErrorList().get(0);
            throw new RequestFailedException(MessageType.Error, "Could not compile project (error in running compiletime functions/expressions): ", compileError);
        }


        if (runArgs.isInjectObjects()) {
            Preconditions.checkNotNull(mapFileMpq);
            Preconditions.checkNotNull(projectFolder);
            // add the imports
            ImportFile.importFilesFromImportDirectory(projectFolder, mapFileMpq);
        }
    }

    private void loadFile(File file) throws Error {
        Preconditions.checkNotNull(file);
        if (!file.exists()) {
            throw new Error("File " + file + " does not exist.");
        }
        this.files.add(file);
    }

    public void loadWurstFilesInDir(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                loadWurstFilesInDir(f);
            } else if (Utils.isWurstFile(f)) {
                loadFile(f);
            } else if (f.getName().equals("wurst.dependencies")) {
                addDependencyFile(f);
            } else if ((!mapFile.isPresent() || runArgs.isNoExtractMapScript()) && f.getName().equals("war3map.j")) {
                loadFile(f);
            }
        }
    }

    private void addDependencyFile(File f) {
        try (FileReader fr = new FileReader(f); BufferedReader reader = new BufferedReader(fr)) {
            while (true) {
                String line = reader.readLine();
                if (line == null)
                    break;
                addDependencyFolder(f, line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new Error(e);
        }
    }

    private void addDependencyFolder(File f, String folderName) {
        File folder = new File(folderName);
        if (!folder.exists()) {
            gui.sendError(new CompileError(new WPos(f.getAbsolutePath(), new LineOffsets(), 0, 1), "Folder " + folderName + " not found."));
        } else if (!folder.isDirectory()) {
            gui.sendError(new CompileError(new WPos(f.getAbsolutePath(), new LineOffsets(), 0, 1), "" + folderName + " is not a folder."));
        } else {
            dependencies.add(folder);
        }
    }

    @Override
    public @Nullable WurstModel parseFiles() {

        // search mapFile
        for (File file : files) {
            if (file.getName().endsWith(".w3x") || file.getName().endsWith(".w3m")) {
                mapFile = Optional.ofNullable(file);
            } else if (file.isDirectory()) {
                if (projectFolder != null && !file.getParent().equals(projectFolder.getAbsolutePath())) {
                    throw new RuntimeException("Cannot set projectFolder to " + file + " because it is already set to non parent " + projectFolder);
                }
                projectFolder = file;
            }
        }



        // import wurst folder if it exists
        Optional<File> l_mapFile = mapFile;
        if (l_mapFile.isPresent()) {
            if (projectFolder == null) {
                projectFolder = l_mapFile.get().getParentFile();
            }
            File relativeWurstDir = new File(projectFolder, "wurst");
            if (relativeWurstDir.exists()) {
                WLogger.info("Importing wurst files from " + relativeWurstDir);
                loadWurstFilesInDir(relativeWurstDir);
            } else {
                WLogger.info("No wurst folder found in " + relativeWurstDir);
            }
            File dependencyFile = new File(projectFolder, "wurst.dependencies");
            if (dependencyFile.exists()) {
                addDependencyFile(dependencyFile);
            }
            addDependenciesFromFolder(projectFolder, dependencies);
        }

        // add directories:
        List<File> dirs = Lists.newArrayList();
        for (File file : files) {
            if (file.isDirectory()) {
                dirs.add(file);
            }
        }
        for (File dir : dirs) {
            loadWurstFilesInDir(dir);
        }

        gui.sendProgress("Parsing Files");
        // parse all the files:
        List<CompilationUnit> compilationUnits = new NotNullList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                // ignore dirs
            } else if (file.getName().endsWith(".w3x") || file.getName().endsWith(".w3m")) {
                CompilationUnit r = processMap(file);
                if (r != null) {
                    compilationUnits.add(r);
                }
            } else {
                if (file.getName().endsWith("common.j")) {
                    hasCommonJ = true;
                }
                compilationUnits.add(parseFile(file));
            }
        }
        for (Entry<String, Reader> in : otherInputs.entrySet()) {
            compilationUnits.add(parse(in.getKey(), in.getValue()));
        }

        try {
            addImportedLibs(compilationUnits);
        } catch (CompileError e) {
            gui.sendError(e);
            return null;
        }

        if (errorHandler.getErrorCount() > 0)
            return null;

        // merge the compilationUnits:
        WurstModel merged = mergeCompilationUnits(compilationUnits);
        StringBuilder sb = new StringBuilder();
        for (CompilationUnit cu : merged) {
            sb.append(cu.getCuInfo().getFile()).append(", ");
        }
        WLogger.info("Compiling compilation units: " + sb);

        return merged;
    }

    /**
     * Adds dependencies from the _build/dependencies folder
     */
    public static void addDependenciesFromFolder(File projectFolder, Collection<File> dependencies) {
        File dependencyFolder = new File(new File(projectFolder, "_build"), "dependencies");
        File[] depProjects = dependencyFolder.listFiles();
        if (depProjects != null) {
            for (File depFile : depProjects) {
                if (depFile.isDirectory()
                        && dependencies.stream().noneMatch(f -> FileUtils.sameFile(f, depFile))) {
                    dependencies.add(depFile);
                }
            }
        }
    }

    private void addImportedLibs(List<CompilationUnit> compilationUnits) {
        addImportedLibs(compilationUnits, file -> {
            CompilationUnit lib = parseFile(file);
            lib.getCuInfo().setFile(file.getAbsolutePath());
            compilationUnits.add(lib);
            return lib;
        });
    }

    /**
     * this method scans for unsatisfied imports and tries to find them in the lib-path
     */
    public void addImportedLibs(List<CompilationUnit> compilationUnits, Function<File, CompilationUnit> addCompilationUnit) {
        Set<String> packages = Sets.newLinkedHashSet();
        Set<WImport> imports = new LinkedHashSet<>();
        for (CompilationUnit c : compilationUnits) {
            c.getCuInfo().setCuErrorHandler(errorHandler);
            for (WPackage p : c.getPackages()) {
                packages.add(p.getName());
                imports.addAll(p.getImports());
            }
        }

        for (WImport imp : imports) {
            resolveImport(addCompilationUnit, packages, imp);
        }

    }

    private void resolveImport(Function<File, CompilationUnit> addCompilationUnit, Set<String> packages, WImport imp) throws CompileError {
        //		WLogger.info("resolving import: " + imp.getPackagename());
        if (!packages.contains(imp.getPackagename())) {
            if (getLibs().containsKey(imp.getPackagename())) {
                CompilationUnit lib = loadLibPackage(addCompilationUnit, imp.getPackagename());
                boolean foundPackage = false;
                for (WPackage p : lib.getPackages()) {
                    packages.add(p.getName());
                    if (p.getName().equals(imp.getPackagename())) {
                        foundPackage = true;
                    }
                    for (WImport i : p.getImports()) {
                        resolveImport(addCompilationUnit, packages, i);
                    }
                }
                if (!foundPackage) {
                    imp.addError("The import " + imp.getPackagename() + " could not be found in file " + lib.getCuInfo().getFile());
                }
            } else {
                if (imp.getPackagename().equals("Wurst")) {
                    imp.addError("The standard library could not be imported.");
                }
                if (imp.getPackagename().equals("NoWurst")) {
                    // ignore this package
                } else {
                    imp.addError("The import '" + imp.getPackagename() + "' could not be resolved.\n" + "Available packages: "
                            + Utils.join(getLibs().keySet(), ", "));
                }
            }
        } else {
            //			WLogger.info("already imported: " + imp.getPackagename());
        }
    }

    private CompilationUnit loadLibPackage(Function<File, CompilationUnit> addCompilationUnit, String imp) {
        File file = getLibs().get(imp);
        if (file == null) {
            gui.sendError(new CompileError(new WPos("", null, 0, 0), "Could not find lib-package " + imp + ". Are you missing your wurst.dependencies file?"));
            return Ast.CompilationUnit(new CompilationUnitInfo(errorHandler), Ast.JassToplevelDeclarations(), Ast.WPackages());
        } else {
            return addCompilationUnit.apply(file);
        }
    }

    public Map<String, File> getLibs() {
        Map<String, File> lc = libCache;
        if (lc == null) {
            lc = Maps.newLinkedHashMap();
            libCache = lc;
            for (File libDir : runArgs.getAdditionalLibDirs()) {
                addLibDir(libDir);
            }
            for (File libDir : dependencies) {
                addLibDir(libDir);
            }
        }
        return lc;
    }

    private void addLibDir(File libDir) throws Error {
        if (!libDir.exists() || !libDir.isDirectory()) {
            throw new Error("Library folder " + libDir + " does not exist.");
        }
        for (File f : libDir.listFiles()) {
            if (f.isDirectory()) {
                // recursively scan directory
                addLibDir(f);
            }
            if (Utils.isWurstFile(f)) {
                String libName = Utils.getLibName(f);
                getLibs().put(libName, f);
            }
        }
    }

    public void checkProg(WurstModel model) {
        checkProg(model, model);
    }

    public void checkProg(WurstModel model, List<CompilationUnit> toCheck) {
        for (CompilationUnit cu : toCheck) {
            Preconditions.checkNotNull(cu);
            if (!model.contains(cu)) {
                // model has changed since then, no need to do 'toCheck'
                throw new ModelChangedException();
            }
        }

        checker.checkProg(model, toCheck);
    }

    public JassProg transformProgToJass() {
        ImTranslator imTranslator2 = getImTranslator();
        ImProg imProg2 = getImProg();
        imTranslator2.assertProperties();
        checkNoCompiletimeExpr(imProg2);
        int stage = 2;
        // eliminate
        beginPhase(2, "Eliminate generics");
        new EliminateGenerics(imTranslator2, imProg2).transform();
        printDebugImProg("./test-output/im " + stage++ + "_genericsEliminated.im");

        // eliminate classes
        beginPhase(2, "translate classes");

        new EliminateClasses(imTranslator2, imProg2, !runArgs.isUncheckedDispatch()).eliminateClasses();
        imTranslator2.assertProperties();
        printDebugImProg("./test-output/im " + stage++ + "_classesEliminated.im");

        new VarargEliminator(imProg2).run();
        printDebugImProg("./test-output/im " + stage++ + "_varargEliminated.im");
        imTranslator2.assertProperties();
        if (runArgs.isNoDebugMessages()) {
            beginPhase(3, "remove debug messages");
            DebugMessageRemover.removeDebugMessages(imProg2);
        } else {
            // debug: add stacktraces
            if (runArgs.isIncludeStacktraces()) {
                beginPhase(4, "add stack traces");
                new StackTraceInjector2(imProg2, imTranslator2).transform(timeTaker);
            }
        }
        imTranslator2.assertProperties();

        ImOptimizer optimizer = new ImOptimizer(timeTaker, imTranslator2);

        // inliner
        if (runArgs.isInline()) {
            beginPhase(5, "inlining");
            optimizer.doInlining();
            imTranslator2.assertProperties();

            printDebugImProg("./test-output/im " + stage++ + "_afterinline.im");
        }

        // eliminate tuples
        beginPhase(6, "eliminate tuples");
        getImProg().flatten(imTranslator2);
        EliminateTuples.eliminateTuplesProg(getImProg(), imTranslator2);
        getImTranslator().assertProperties(AssertProperty.NOTUPLES);

        printDebugImProg("./test-output/im " + stage++ + "_withouttuples.im");

        new MultiArrayEliminator(imProg2, imTranslator2, runArgs.isIncludeStacktraces() && !runArgs.isNoDebugMessages()).run();
        printDebugImProg("./test-output/im " + stage++ + "_withoutmultiarrays.im");
        imTranslator2.assertProperties();

        beginPhase(7, "remove func refs");
        new FuncRefRemover(imProg2, imTranslator2).run();

        // remove cycles:
        beginPhase(8, "remove cyclic functions");
        new CyclicFunctionRemover(imTranslator2, imProg2).work();

        printDebugImProg("./test-output/im " + stage++ + "_nocyc.im");

        // flatten
        beginPhase(9, "flatten");
        getImProg().flatten(imTranslator2);
        getImTranslator().assertProperties(AssertProperty.NOTUPLES, AssertProperty.FLAT);

        printDebugImProg("./test-output/im " + stage++ + "_flat.im");

        if (runArgs.isLocalOptimizations()) {
            beginPhase(10, "local optimizations");
            optimizer.localOptimizations();
        }

        printDebugImProg("./test-output/im " + stage++ + "_afterlocalopts.im");

        if (runArgs.isNullsetting()) {
            beginPhase(11, "null setting");
            optimizer.doNullsetting();
            printDebugImProg("./test-output/im " + stage++ + "_afternullsetting.im");
        }

        optimizer.removeGarbage();
        imProg.flatten(imTranslator);

        // Re-run to avoid #883
        optimizer.removeGarbage();
        imProg.flatten(imTranslator);

        printDebugImProg("./test-output/im " + stage++ + "_afterremoveGarbage1.im");

        if (runArgs.isHotStartmap() || runArgs.isHotReload()) {
            addJassHotCodeReloadCode();
        }
        if (runArgs.isOptimize()) {
            beginPhase(12, "froptimize");
            optimizer.optimize();

            optimizer.removeGarbage();
            imProg.flatten(imTranslator);
            printDebugImProg("./test-output/im " + stage++ + "_afteroptimize.im");
        }



        // translate flattened intermediate lang to jass:

        beginPhase(13, "translate to jass");
        getImTranslator().calculateCallRelationsAndUsedVariables();
        ImToJassTranslator translator =
                new ImToJassTranslator(getImProg(), getImTranslator().getCalledFunctions(), getImTranslator().getMainFunc(), getImTranslator().getConfFunc());
        prog = translator.translate();
        if (errorHandler.getErrorCount() > 0) {
            prog = null;
        }
        timeTaker.endPhase();
        return prog;
    }

    private void addJassHotCodeReloadCode() {
        Preconditions.checkNotNull(imTranslator);
        Preconditions.checkNotNull(imProg);
        ImFunction mainFunc = imTranslator.getMainFunc();
        Preconditions.checkNotNull(mainFunc);
        Element trace = imProg.getTrace();

        List<ImStmt> stmts = new ArrayList<>();

        // add call to JHCR_Init_init in main
        stmts.add(callExtern(trace, CallType.EXECUTE, "JHCR_Init_init"));


        // add reload trigger for pressing escape
        ImStmts reloadBody = JassIm.ImStmts(
                callExtern(trace, CallType.EXECUTE, "JHCR_Init_parse"),
                callExtern(trace, CallType.NORMAL, "BJDebugMsg", JassIm.ImStringVal("Code reloaded!"))
        );
        ImFunction jhcr_reload = JassIm.ImFunction(trace, "jhcr_reload_on_escape", JassIm.ImTypeVars(), JassIm.ImVars(), JassIm.ImVoid(), JassIm.ImVars(), reloadBody, Collections.emptyList());


        ImVar trig = JassIm.ImVar(trace, TypesHelper.imTrigger(), "trig", false);
        mainFunc.getLocals().add(trig);
        // TriggerRegisterPlayerEventEndCinematic(trig, Player(0))
        stmts.add(JassIm.ImSet(trace, JassIm.ImVarAccess(trig), callExtern(trace, CallType.NORMAL, "CreateTrigger")));
        stmts.add(callExtern(trace, CallType.NORMAL, "TriggerRegisterPlayerEventEndCinematic", JassIm.ImVarAccess(trig),
                callExtern(trace, CallType.NORMAL, "Player", JassIm.ImIntVal(0))));
        stmts.add(callExtern(trace, CallType.NORMAL, "TriggerAddAction", JassIm.ImVarAccess(trig),
                JassIm.ImFuncRef(trace, jhcr_reload)));

        mainFunc.getBody().addAll(0, stmts);
    }

    @NotNull
    private ImFunctionCall callExtern(Element trace, CallType callType, String functionName, ImExpr... arguments) {
        ImFunction jhcrinit = JassIm.ImFunction(trace, functionName, JassIm.ImTypeVars(), JassIm.ImVars(), JassIm.ImVoid(), JassIm.ImVars(), JassIm.ImStmts(), Collections.singletonList(FunctionFlagEnum.IS_EXTERN));
        return JassIm.ImFunctionCall(trace, jhcrinit, JassIm.ImTypeArguments(), JassIm.ImExprs(arguments), true, callType);
    }

    public void checkNoCompiletimeExpr(ImProg prog) {
        prog.accept(new ImProg.DefaultVisitor() {
            @Override
            public void visit(ImCompiletimeExpr e) {
                super.visit(e);
                throw new CompileError(e.attrTrace().attrSource(), "Compiletime expressions require compilation with '-runcompiletimefunctions' option.");
            }
        });
    }

    public ImTranslator getImTranslator() {
        final ImTranslator t = imTranslator;
        if (t != null) {
            return t;
        } else {
            throw new Error("translator not initialized");
        }
    }

    public @Nullable ImProg translateProgToIm(WurstModel root) {
        beginPhase(1, "to intermediate lang");
        // translate wurst to intermediate lang:
        imTranslator = new ImTranslator(root, errorHandler.isUnitTestMode(), runArgs);
        imProg = getImTranslator().translateProg();
        int stage = 1;
        printDebugImProg("./test-output/im " + stage++ + ".im");
        timeTaker.endPhase();
        return imProg;
    }

    private void beginPhase(int phase, String description) {
        errorHandler.setProgress("Translating wurst. Phase " + phase + ": " + description, 0.6 + 0.01 * phase);
        timeTaker.beginPhase(description);
    }

    private void printDebugImProg(String debugFile) {
        if (!errorHandler.isUnitTestMode() ) {
            // output only in unit test mode
            return;
        }
        try {
            // TODO remove test output
            File file = new File(debugFile);
            file.getParentFile().mkdirs();
            try (Writer w = Files.newWriter(file, Charsets.UTF_8)) {
                getImProg().print(w, 0);
            }
        } catch (IOException e) {
            ErrorReporting.instance.handleSevere(e, getCompleteSourcecode());
        }
    }

    private WurstModel mergeCompilationUnits(List<CompilationUnit> compilationUnits) {
        gui.sendProgress("Merging Files");
        WurstModel result = Ast.WurstModel();
        for (CompilationUnit compilationUnit : compilationUnits) {
            // remove from old parent
            compilationUnit.setParent(null);
            result.add(compilationUnit);
        }
        return result;
    }

    private CompilationUnit processMap(File file) {
        gui.sendProgress("Processing Map " + file.getName());
        if (!mapFile.isPresent() || !file.equals(mapFile.get())) {
            // TODO check if file != mapFile is possible, would be strange
            // so this should definitely be done differently
            throw new Error("file: " + file + " is not the mapfile: " + mapFile);
        }

        MpqEditor mapMpq = mapFileMpq;
        if (mapMpq == null) {
            throw new RuntimeException("map mpq is null");
        }

        if (runArgs.isNoExtractMapScript()) {
            return null;
        }

        // extract mapscript:
        try {
            byte[] tempBytes = mapMpq.extractFile("war3map.j");
            File tempFile = File.createTempFile("war3map", ".j", TempDir.get()); // TODO work directly with bytes without temp file
            tempFile.deleteOnExit();
            Files.write(tempBytes, tempFile);

            if (isWurstGenerated(tempFile)) {
                // the war3map.j file was generated by wurst
                // this should not be the case, as we will get duplicate function errors in this case
                throw new AbortCompilationException(
                        "Map was not saved correctly. Please try saving the map again.\n\n" + "This usually happens if you change the name of the map or \n"
                                + "if you have used the test-map-button without saving the map first.");
            }

            // move file to wurst directory
            File wurstFolder = new File(file.getParentFile(), "wurst");
            wurstFolder.mkdirs();
            if (!wurstFolder.isDirectory()) {
                throw new AbortCompilationException("Could not create Wurst folder at " + wurstFolder + ".");
            }
            File wurstwar3map = new File(wurstFolder, "war3map.j");
            wurstwar3map.delete();
            if (tempFile.renameTo(wurstwar3map)) {
                return parseFile(wurstwar3map);
            } else {
                throw new Error("Could not move war3map.j from " + tempFile + " to " + wurstwar3map);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new Error(e);
        }

    }

    private boolean isWurstGenerated(File tempFile) {
        try (FileReader fr = new FileReader(tempFile); BufferedReader in = new BufferedReader(fr)) {
            String firstLine = in.readLine();
            WLogger.info("firstLine = '" + firstLine + "'");
            return firstLine.equals(JassPrinter.WURST_COMMENT);
        } catch (IOException e) {
            WLogger.severe(e);
        }
        return false;
    }

    private CompilationUnit parseFile(File file) {
        if (file.isDirectory()) {
            throw new Error("Is a directory: " + file);
        }
        parsedFiles.add(file);

        gui.sendProgress("Parsing File " + file.getName());
        String source = file.getAbsolutePath();
        try (Reader reader = FileReading.getFileReader(file)) {
            // scanning
            return parse(source, reader);

        } catch (CompileError e) {
            gui.sendError(e);
            return emptyCompilationUnit();
        } catch (FileNotFoundException e) {
            gui.sendError(new CompileError(new WPos(source, LineOffsets.dummy, 0, 0), "File not found."));
            return emptyCompilationUnit();
        } catch (IOException e) {
            gui.sendError(new CompileError(new WPos(source, LineOffsets.dummy, 0, 0), "Could not read file."));
            return emptyCompilationUnit();
        }
    }

    public CompilationUnit parse(String fileName, Reader reader) {
        if (fileName.endsWith(".j")) {
            return parser.parseJass(reader, fileName, hasCommonJ);
        }
        if (fileName.endsWith(".jurst")) {
            return parser.parseJurst(reader, fileName, hasCommonJ);
        }
        return parser.parse(reader, fileName, hasCommonJ);
    }

    private CompilationUnit emptyCompilationUnit() {
        return parser.emptyCompilationUnit();
    }

    public @Nullable JassProg getProg() {
        return prog;
    }

    public void loadReader(String name, Reader input) {
        otherInputs.put(name, input);
    }

    public void setHasCommonJ(boolean hasCommonJ) {
        this.hasCommonJ = hasCommonJ;
    }

    public ImProg getImProg() {
        final ImProg imProg2 = imProg;
        if (imProg2 != null) {
            return imProg2;
        } else {
            throw new Error("imProg is null");
        }
    }

    public Optional<File> getMapFile() {
        return mapFile;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public String getCompleteSourcecode() {

        StringBuilder sb = new StringBuilder();
        try {
            for (File f : parsedFiles) {
                sb.append(" //######################################################\n");
                sb.append(" // File ").append(f.getAbsolutePath()).append("\n");
                sb.append(" //######################################################\n");
                sb.append(Files.toString(f, Charsets.UTF_8));
            }

            for (Entry<String, Reader> entry : otherInputs.entrySet()) {
                sb.append(" //######################################################\n");
                sb.append(" // Input ").append(entry.getKey()).append("\n");
                sb.append(" //######################################################\n");
                try (Reader reader = entry.getValue()) {
                    char[] buffer = new char[1024];
                    while (true) {
                        int len = reader.read(buffer);
                        if (len < 0) {
                            break;
                        }
                        sb.append(buffer, 0, len);
                    }
                }
            }
        } catch (Throwable t) {
            sb.append(Utils.printExceptionWithStackTrace(t));
            WLogger.severe(t);
        }
        return sb.toString();
    }

    public void setRunArgs(RunArgs runArgs) {
        this.runArgs = runArgs;
    }

    public void setMapFile(Optional<File> mapFile) {
        this.mapFile = mapFile;
    }

    public @Nullable MpqEditor getMapfileMpqEditor() {
        return mapFileMpq;
    }

    public LuaCompilationUnit transformProgToLua() {

        if (runArgs.isNoDebugMessages()) {
            beginPhase(3, "remove debug messages");
            DebugMessageRemover.removeDebugMessages(imProg);
        } else {
            // debug: add stacktraces
            if (runArgs.isIncludeStacktraces()) {
                beginPhase(4, "add stack traces");
                new StackTraceInjector2(imProg, imTranslator).transform(timeTaker);
            }
        }

        LuaTranslator luaTranslator = new LuaTranslator(imProg, imTranslator);
        LuaCompilationUnit luaCode = luaTranslator.translate();
        return luaCode;
    }
}
