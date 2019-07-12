package de.peeeq.wurstio.languageserver;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import de.peeeq.wurstscript.WLogger;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 *
 */
public class ConfigProvider {
    private final LanguageClient languageClient;

    public ConfigProvider(LanguageClient languageClient) {
        this.languageClient = languageClient;
    }

    public String getConfig(String key, String defaultValue) {
        ConfigurationItem ci = new ConfigurationItem();
        ci.setSection("wurst");
        CompletableFuture<List<Object>> res = languageClient.configuration(new ConfigurationParams(Collections.singletonList(ci)));
        try {
            List<Object> config = res.get();
            for (Object c : config) {
                if (c instanceof JsonObject) {
                    JsonObject cfg = (JsonObject) c;
                    JsonElement result = cfg.get(key);
                    if (result instanceof JsonNull) {
                        return null;
                    } else if (result != null) {
                        return result.getAsString();
                    }
                }
            }
            return defaultValue;
        } catch (InterruptedException | ExecutionException e) {
            String msg = "Could not get config " + key + ", using default value " + defaultValue;
            WLogger.warning(msg, e);
            languageClient.showMessage(new MessageParams(MessageType.Warning, msg));
            return defaultValue;
        }
    }

    public String getJhcrExe() {
        return getConfig("jhcrExe", "jhcr.exe");
    }

    /**
     * The path where to put maps before running them
     */
    public String getMapDocumentPath() {
        return getConfig("mapDocumentPath", null);
    }
}

