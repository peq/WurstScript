package de.peeeq.wurstio.languageserver.requests;

import de.peeeq.wurstio.languageserver.ModelManager;
import de.peeeq.wurstio.languageserver.BufferManager;
import de.peeeq.wurstio.languageserver.Convert;
import de.peeeq.wurstio.languageserver.WFile;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.ast.NameDef;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.lsp4j.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class GetUsages extends UserRequest<List<GetUsages.UsagesData>> {

    private final WFile wFile;
    private final int line;
    private final int column;
    private final boolean global;



    public GetUsages(TextDocumentPositionParams position, BufferManager bufferManager, boolean global) {
        this.wFile = WFile.create(position.getTextDocument().getUri());
        this.line = position.getPosition().getLine() + 1;
        this.column = position.getPosition().getCharacter() + 1;
        this.global = global;
    }


    @Override
    public List<UsagesData> execute(ModelManager modelManager) {
        CompilationUnit cu = modelManager.replaceCompilationUnitContent(wFile, null, false);
        Element astElem = Utils.getAstElementAtPos(cu, line, column, false);
        NameDef nameDef = astElem.tryGetNameDef();
        List<UsagesData> usages = new ArrayList<>();
        if (nameDef != null) {

            if (global || nameDef.getSource().getFile().equals(wFile)) {
                // add declaration
                usages.add(new UsagesData(Convert.posToLocation(nameDef.attrErrorPos()), DocumentHighlightKind.Write));
            }
            Deque<Element> todo = new ArrayDeque<>();
            if (global) {
                todo.push(modelManager.getModel());
            } else {
                todo.push(cu);
            }
            while (!todo.isEmpty()) {
                Element e = todo.pop();
                // visit children:
                for (int i = 0; i < e.size(); i++) {
                    todo.push(e.get(i));
                }
                NameDef e_def = e.tryGetNameDef();
                if (e_def == nameDef) {
                    UsagesData usagesData = new UsagesData(Convert.posToLocation(e.attrErrorPos()), DocumentHighlightKind.Read);
                    usages.add(usagesData);
                }
            }
        }

        return usages;
    }

//    static enum DocumentHighlightKind {
//        Text, Read, Write
//    }

    public static class UsagesData {
        private Location location;
//        private String wFile;
//        private Range range;
        private DocumentHighlightKind kind;


        public UsagesData(Location location, DocumentHighlightKind kind) {
            this.location = location;
            this.kind = kind;
        }

        public String getFilename() {
            return location.getUri();
        }

        public void setFilename(String filename) {
            location.setUri(filename);
        }

        public Range getRange() {
            return location.getRange();
        }

        public void setRange(Range range) {
            location.setRange(range);
        }

        public DocumentHighlightKind getKind() {
            return kind;
        }

        public void setKind(DocumentHighlightKind kind) {
            this.kind = kind;
        }

        public Location getLocation() {
            return location;
        }

        public DocumentHighlight toDocumentHighlight() {
            return new DocumentHighlight(location.getRange(), kind);
        }
    }
}
