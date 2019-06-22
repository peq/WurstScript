package de.peeeq.wurstio.languageserver.requests;

import config.WurstProjectConfig;
import config.WurstProjectConfigData;
import de.peeeq.wurstio.gui.WurstGuiImpl;
import de.peeeq.wurstio.languageserver.ConfigProvider;
import de.peeeq.wurstio.languageserver.ModelManager;
import de.peeeq.wurstio.languageserver.ProjectConfigBuilder;
import de.peeeq.wurstio.languageserver.WFile;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.gui.WurstGui;
import org.eclipse.lsp4j.MessageType;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static de.peeeq.wurstio.languageserver.ProjectConfigBuilder.FILE_NAME;

/**
 * Created by peter on 16.05.16.
 */
public class BuildMap extends MapRequest {

    public BuildMap(ConfigProvider configProvider, WFile workspaceRoot, File map, List<String> compileArgs) {
        super(configProvider, map, compileArgs, workspaceRoot);
    }


    @Override
    public Object execute(ModelManager modelManager) throws IOException {
        if (modelManager.hasErrors()) {
            throw new RequestFailedException(MessageType.Error, "Fix errors in your code before building a release.");
        }

        WurstProjectConfigData projectConfig = WurstProjectConfig.INSTANCE.loadProject(workspaceRoot.getFile().toPath().resolve(FILE_NAME));
        if (projectConfig == null) {
            throw new RequestFailedException(MessageType.Error, FILE_NAME + " file doesn't exist or is invalid. " +
                "Please install your project using grill or the wurst setup tool.");
        }

        // TODO use normal compiler for this, avoid code duplication
        WLogger.info("buildMap " + map + " " + compileArgs);
        WurstGui gui = new WurstGuiImpl(workspaceRoot.getFile().getAbsolutePath());
        try {
            if (map != null && !map.exists()) {
                throw new RequestFailedException(MessageType.Error, map.getAbsolutePath() + " does not exist.");
            }

            gui.sendProgress("Copying map");

            // first we copy in same location to ensure validity
            File buildDir = getBuildDir();
            String fileName = projectConfig.getBuildMapData().getFileName();
            File targetMap = map == null ? null : new File(buildDir, fileName.isEmpty() ? projectConfig.getProjectName() : fileName  + ".w3x");
            File compiledScript = compileScript(modelManager, gui, targetMap);

            gui.sendProgress("Applying Map Config...");
            ProjectConfigBuilder.apply(projectConfig, targetMap, compiledScript, buildDir, runArgs);

            gui.sendProgress("Done.");
        } catch (CompileError e) {
            WLogger.info(e);
            throw new RequestFailedException(MessageType.Error, "There was an error when compiling the map:\n" + e);
        } catch (RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            gui.sendFinished();
        }
        return "ok"; // TODO
    }


}
