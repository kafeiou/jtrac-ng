package info.jtrac.lucene;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;

/**
 * Splits email addresses (e.g. user@gmail.com), compound filenames (e.g. thunderbird_gmail.pdf),
 * paths, and IP:Port tokens into sub-tokens (with posIncr = 0) while preserving the original token,
 * and ensures multi-character CJK runs are split into sequential unigrams (posIncr = 1).
 */
public final class SubTokenFilter extends TokenFilter {

    private static final class PendingToken {
        final String term;
        final int posIncr;

        PendingToken(String term, int posIncr) {
            this.term = term;
            this.posIncr = posIncr;
        }
    }

    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final PositionIncrementAttribute posIncrAtt = addAttribute(PositionIncrementAttribute.class);

    private final Queue<PendingToken> pendingTokens = new ArrayDeque<PendingToken>();
    private State savedState;

    private static final Pattern DELIMITERS = Pattern.compile("[@._\\-/:\\\\,]+");

    public SubTokenFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (!pendingTokens.isEmpty()) {
            restoreState(savedState);
            PendingToken next = pendingTokens.poll();
            termAtt.setEmpty().append(next.term);
            posIncrAtt.setPositionIncrement(next.posIncr);
            return true;
        }

        if (!input.incrementToken()) {
            return false;
        }

        String term = termAtt.toString();
        if (term == null || term.length() <= 1) {
            return true;
        }

        // Ensure multi-character pure CJK runs are emitted as sequential unigrams (posIncr = 1)
        if (isAllCjkIdeographs(term)) {
            savedState = captureState();
            termAtt.setEmpty().append(term.charAt(0));
            for (int i = 1; i < term.length(); i++) {
                pendingTokens.add(new PendingToken(String.valueOf(term.charAt(i)), 1));
            }
            return true;
        }

        if (term.length() > 2 && containsSubTokenDelimiter(term)) {
            String[] parts = DELIMITERS.split(term);
            if (parts.length > 1) {
                savedState = captureState();
                int atIdx = term.indexOf('@');
                if (atIdx > 1) {
                    String localPart = term.substring(0, atIdx).trim();
                    if (localPart.length() >= 2 && !localPart.equalsIgnoreCase(term)) {
                        pendingTokens.add(new PendingToken(localPart, 0));
                    }
                }
                for (String part : parts) {
                    String clean = part.trim();
                    if (clean.length() >= 2 && !clean.equalsIgnoreCase(term) && !containsPending(clean)) {
                        pendingTokens.add(new PendingToken(clean, 0));
                    }
                }
            }
        }
        return true;
    }

    private boolean containsPending(String candidate) {
        for (PendingToken pt : pendingTokens) {
            if (pt.term.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        pendingTokens.clear();
        savedState = null;
    }

    private static boolean isAllCjkIdeographs(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeBlock ub = Character.UnicodeBlock.of(s.charAt(i));
            if (ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    && ub != Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                    && ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    && ub != Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsSubTokenDelimiter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '@' || c == '.' || c == '_' || c == '-' || c == '/' || c == '\\' || c == ':' || c == ',') {
                return true;
            }
        }
        return false;
    }
}
