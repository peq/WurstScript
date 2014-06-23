package de.peeeq.eclipsewurstplugin.editor.highlighting;

import static de.peeeq.eclipsewurstplugin.WurstConstants.SYNTAXCOLOR_COLOR;
import static de.peeeq.eclipsewurstplugin.WurstConstants.SYNTAXCOLOR_KEYWORD;

import javax.swing.text.html.parser.Parser;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWhitespaceDetector;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Display;

import de.peeeq.eclipsewurstplugin.WurstConstants;
import de.peeeq.eclipsewurstplugin.WurstPlugin;
import de.peeeq.eclipsewurstplugin.util.UtilityFunctions;
import de.peeeq.wurstscript.WurstKeywords;

public class SimpleCodeScanner extends RuleBasedScanner implements WurstScanner {

	
	private Token keywordToken;
	private Token commentToken;
	private Token stringToken;
	private Token annotationToken;
	private Token jasstypeToken;
	private Token identifierToken;

	public SimpleCodeScanner() {
		
		UtilityFunctions.getDefaultPreferenceStore()
		  .addPropertyChangeListener(new IPropertyChangeListener() {
		    @Override
		    public void propertyChange(PropertyChangeEvent event) {
		    	updateColors();
		    	setRules();
		    	WurstPlugin.refreshEditors();
		    }
		  }); 
		
		updateColors();
		setRules();
	}

	private void setRules() {
		WordRule keywordRule = new WordRule(new IWordDetector() {
			public boolean isWordStart(char c) {
				return Character.isJavaIdentifierStart(c);
			}

			public boolean isWordPart(char c) {
				return Character.isJavaIdentifierPart(c);
			}
		}, identifierToken, false);
		// add tokens for each reserved word
		for (String keyword : WurstKeywords.KEYWORDS) {
			keywordRule.addWord(keyword, keywordToken);
		}
		
		for (String jasstype : WurstKeywords.JASSTYPES) {
			keywordRule.addWord(jasstype, jasstypeToken);
		}
		
		WhitespaceRule whitespaceRule = new WhitespaceRule(new IWhitespaceDetector() {
			public boolean isWhitespace(char c) {
				return Character.isWhitespace(c);
			}
		});
		setRules(new IRule[] { 
				new EndOfLineRule("//", commentToken), 
				new SingleLineRule("\"", "\"", stringToken, '\\'),
				new SingleLineRule("@", " ", annotationToken),
				new SingleLineRule("'", "'", stringToken, '\\'), 
//				new MultiLineRule("/*", "*/", commentToken),
				whitespaceRule,
				keywordRule,

			});
	}
	
	private void updateColors() {
		IPreferenceStore preferencestore = UtilityFunctions.getDefaultPreferenceStore();
		jasstypeToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_JASSTYPE);
		keywordToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_KEYWORD);
		commentToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_COMMENT);
		stringToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_STRING);
		annotationToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_ANNOTATION);
		identifierToken = makeToken(preferencestore, WurstConstants.SYNTAXCOLOR_TEXT);
		System.out.println("Colors updated");
	}

	private Token makeToken(IPreferenceStore preferencestore, String key) {
		return new Token(new TextAttribute(new Color(Display.getCurrent(), PreferenceConverter.getColor(preferencestore, SYNTAXCOLOR_COLOR
				+ key)), null, UtilityFunctions.computeAttributes(preferencestore, key)));
	}

	@Override
	public String getPartitionType() {
		return IDocument.DEFAULT_CONTENT_TYPE;
	}

}
