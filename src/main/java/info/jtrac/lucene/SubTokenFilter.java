package info.jtrac.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Splits email addresses (e.g. user@gmail.com) and compound filenames
 * (e.g. thunderbird_gmail.pdf) into sub-tokens while preserving the original token.
 */
public class SubTokenFilter extends TokenFilter {

    private final TermAttribute termAtt;
    private final PositionIncrementAttribute posIncrAtt;
    private final OffsetAttribute offsetAtt;
    private final TypeAttribute typeAtt;

    private final Queue<String> subTokens = new ArrayDeque<String>();
    private State savedState;

    private static final Pattern DELIMITERS = Pattern.compile("[@._\\-]+");

    public SubTokenFilter(TokenStream input) {
        super(input);
        this.termAtt = (TermAttribute) addAttribute(TermAttribute.class);
        this.posIncrAtt = (PositionIncrementAttribute) addAttribute(PositionIncrementAttribute.class);
        this.offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
        this.typeAtt = (TypeAttribute) addAttribute(TypeAttribute.class);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!subTokens.isEmpty()) {
            restoreState(savedState);
            String nextSubToken = subTokens.poll();
            termAtt.setTermBuffer(nextSubToken);
            posIncrAtt.setPositionIncrement(0);
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String term = termAtt.term();
        if (term != null && term.length() > 2) {
            if (term.indexOf('@') != -1 || term.indexOf('.') != -1 || term.indexOf('_') != -1 || term.indexOf('-') != -1) {
                String[] parts = DELIMITERS.split(term);
                if (parts.length > 1) {
                    savedState = captureState();
                    for (String part : parts) {
                        String clean = part.trim();
                        if (clean.length() >= 2 && !clean.equalsIgnoreCase(term)) {
                            subTokens.add(clean);
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        subTokens.clear();
        savedState = null;
    }
}
