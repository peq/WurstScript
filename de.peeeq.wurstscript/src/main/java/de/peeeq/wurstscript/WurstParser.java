package de.peeeq.wurstscript;

import de.peeeq.wurstscript.antlr.WurstLexer;
import de.peeeq.wurstscript.antlr.WurstParser.CompilationUnitContext;
import de.peeeq.wurstscript.ast.Ast;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.attributes.ErrorHandler;
import de.peeeq.wurstscript.gui.WurstGui;
import de.peeeq.wurstscript.jurst.AntlrJurstParseTreeTransformer;
import de.peeeq.wurstscript.jurst.ExtendedJurstLexer;
import de.peeeq.wurstscript.jurst.antlr.JurstParser;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.parser.antlr.AntlrWurstParseTreeTransformer;
import de.peeeq.wurstscript.parser.antlr.ExtendedWurstLexer;
import de.peeeq.wurstscript.utils.LineOffsets;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.Interval;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

public class WurstParser {
    private static final int MAX_SYNTAX_ERRORS = 15;
    private final ErrorHandler errorHandler;
    private final WurstGui gui;
    private boolean removeSugar = true;

    public WurstParser(ErrorHandler errorHandler, WurstGui gui) {
        this.errorHandler = errorHandler;
        this.gui = gui;
    }

    public void setRemoveSugar(boolean removeSugar) {
        this.removeSugar = removeSugar;
    }

    public CompilationUnit parse(Reader reader, String source, boolean hasCommonJ) {
        try (java.util.Scanner s = new java.util.Scanner(reader)) {
            s.useDelimiter("\\A");
            String input = s.hasNext() ? s.next() : "";
            return parseWithAntlr(new StringReader(input), source, hasCommonJ);
        }
    }

    private CompilationUnit parseWithAntlr(Reader reader, final String source, boolean hasCommonJ) {
        try {
            CharStream input = CharStreams.fromReader(reader);
            // create a lexer that feeds off of input CharStream
            final ExtendedWurstLexer lexer = new ExtendedWurstLexer(input);
            // create a buffer of tokens pulled from the lexer
            TokenStream tokens = new CommonTokenStream(lexer);
            // create a parser that feeds off the tokens buffer
            de.peeeq.wurstscript.antlr.WurstParser parser = new de.peeeq.wurstscript.antlr.WurstParser(tokens);
            ANTLRErrorListener l = new BaseErrorListener() {

                int errorCount = 0;

                @Override
                public void syntaxError(@SuppressWarnings("null") Recognizer<?, ?> recognizer, @SuppressWarnings("null") Object offendingSymbol, int line,
                                        int charPositionInLine,
                                        @SuppressWarnings("null") String msg, @SuppressWarnings("null") RecognitionException e) {

                    // try to improve error message
                    if (e instanceof NoViableAltException) {
                        NoViableAltException ne = (NoViableAltException) e;
                        if (ne.getStartToken().getType() == WurstLexer.HOTDOC_COMMENT) {
                            msg = "Hotdoc comment is in invalid position, it can " +
                                    "only appear before function definitions, classes, and " +
                                    "other elements that can be documented.";
                            offendingSymbol = ne.getStartToken();
                        }
                    }

                    LineOffsets offsets = lexer.getLineOffsets();
                    int pos;
                    int posStop;
                    if (offendingSymbol instanceof Token) {
                        Token token = (Token) offendingSymbol;
                        pos = token.getStartIndex();
                        posStop = token.getStopIndex() + 1;
                    } else {
                        pos = offsets.get(line) + charPositionInLine;
                        posStop = pos + 1;
                    }

                    if (posStop >= input.size()) {
                        posStop = input.size() - 1;
                    }

                    while (pos > 0 && input.getText(new Interval(pos, posStop)).matches("\\s*")){
                        pos--;
                    }
                    CompileError err = new CompileError(new WPos(source, offsets, pos, posStop), msg);
                    gui.sendError(err);

                    errorCount++;
                    if (errorCount > MAX_SYNTAX_ERRORS) {
                        throw new TooManyErrorsException();
                    }
                }

            };
            lexer.setErrorListener(l);
            parser.removeErrorListeners();
            parser.addErrorListener(l);

            CompilationUnitContext cu = parser.compilationUnit(); // begin parsing at init rule

            if (lexer.getTabWarning() != null) {
                CompileError warning = lexer.getTabWarning();
                warning = new CompileError(warning.getSource().withFile(source), warning.getMessage(), CompileError.ErrorType.WARNING);
                gui.sendError(warning);
            }

            CompilationUnit root = new AntlrWurstParseTreeTransformer(source, errorHandler, lexer.getLineOffsets()).transform(cu);
            if (this.removeSugar) {
                removeSyntacticSugar(root, hasCommonJ);
            }
            return root;

        } catch (IOException e) {
            WLogger.severe(e);
            throw new Error(e);
        } catch (TooManyErrorsException e) {
            WLogger.info("Stopped parsing file " + source + ", too many errors");
            return emptyCompilationUnit();
        }
    }


    public CompilationUnit parseJurst(Reader reader, String source, boolean hasCommonJ) {
        return parseJurstWithAntlr(reader, source, hasCommonJ);
    }

    private CompilationUnit parseJurstWithAntlr(Reader reader, final String source, boolean hasCommonJ) {
        try {
            CharStream input = CharStreams.fromReader(reader);
            // create a lexer that feeds off of input CharStream
            final ExtendedJurstLexer lexer = new ExtendedJurstLexer(input);
            // create a buffer of tokens pulled from the lexer
            TokenStream tokens = new CommonTokenStream(lexer);
            // create a parser that feeds off the tokens buffer
            JurstParser parser = new JurstParser(tokens);
            ANTLRErrorListener l = new BaseErrorListener() {

                int errorCount = 0;

                @Override
                public void syntaxError(@SuppressWarnings("null") Recognizer<?, ?> recognizer, @SuppressWarnings("null") Object offendingSymbol, int line,
                                        int charPositionInLine,
                                        @SuppressWarnings("null") String msg, @SuppressWarnings("null") RecognitionException e) {

                    LineOffsets offsets = lexer.getLineOffsets();
                    int pos;
                    int posStop;
                    if (offendingSymbol instanceof Token) {
                        Token token = (Token) offendingSymbol;
                        pos = token.getStartIndex();
                        posStop = token.getStopIndex() + 1;
                    } else {
                        pos = offsets.get(line) + charPositionInLine;
                        posStop = pos + 1;
                    }

                    msg = "line " + line + ": " + msg;

                    while (pos > 0 && input.getText(new Interval(pos, posStop)).matches("\\s*")) {
                        pos--;
                    }
                    CompileError err = new CompileError(new WPos(source, offsets, pos, posStop), msg);
                    gui.sendError(err);


                    errorCount++;
                    if (errorCount > MAX_SYNTAX_ERRORS) {
                        throw new TooManyErrorsException();
                    }
                }

            };
            lexer.addErrorListener(l);
            parser.removeErrorListeners();
            parser.addErrorListener(l);

            de.peeeq.wurstscript.jurst.antlr.JurstParser.CompilationUnitContext cu = parser.compilationUnit(); // begin parsing at init rule
            CompilationUnit root = new AntlrJurstParseTreeTransformer(source, errorHandler, lexer.getLineOffsets()).transform(cu);
            if (this.removeSugar) {
                removeSyntacticSugar(root, hasCommonJ);
            }
            return root;

        } catch (IOException e) {
            WLogger.severe(e);
            throw new Error(e);
        } catch (TooManyErrorsException e) {
            WLogger.info("Stopped parsing file " + source + ", too many errors");
            return emptyCompilationUnit();
        }
    }


    public CompilationUnit emptyCompilationUnit() {
        return Ast.CompilationUnit("<empty compilation unit>", errorHandler, Ast.JassToplevelDeclarations(), Ast.WPackages());
    }

    private void removeSyntacticSugar(CompilationUnit root, boolean hasCommonJ) {
        new SyntacticSugar().removeSyntacticSugar(root, hasCommonJ);
    }

    class TooManyErrorsException extends RuntimeException {
    }
}
