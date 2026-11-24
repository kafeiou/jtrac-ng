/*
 * Copyright 2002-2005 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.jtrac.lucene;

import info.jtrac.exception.SearchQueryParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene Index Searching implementation using native Lucene API
 */
public class IndexSearcher {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Directory indexDirectory;
    private Analyzer analyzer;
    private final Analyzer defaultQueryAnalyzer = new JtracAnalyzer(false);
    private OllamaSearchExpander searchExpander;

    public void setIndexDirectory(Directory indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Returns the query-time analyzer. For JtracAnalyzer, sub-token splitting (@ . _ -) is disabled
     * at query time so full email addresses / filenames (e.g. app.hlmt@gmail.com) match precisely
     * without exploding into noisy sub-tokens like 'com' or 'gmail', while short single-word queries
     * (e.g. 'hlmt' or 'gmail') still match sub-tokens emitted at index time.
     */
    protected Analyzer getQueryAnalyzer() {
        if (analyzer == null || analyzer.getClass() == JtracAnalyzer.class) {
            return defaultQueryAnalyzer;
        }
        return analyzer;
    }

    public void setSearchExpander(OllamaSearchExpander searchExpander) {
        this.searchExpander = searchExpander;
    }

    public OllamaSearchExpander getSearchExpander() {
        return searchExpander;
    }

    public boolean validateQuery(String text) {
        if (text == null || text.trim().length() == 0) {
            return true;
        }
        QueryParser parser = new QueryParser(Version.LUCENE_29, "text", getQueryAnalyzer());
        parser.setDefaultOperator(QueryParser.Operator.OR);
        try {
            parser.parse(text);
            return true;
        } catch (ParseException e) {
            try {
                parser.parse(QueryParser.escape(text));
                return true;
            } catch (ParseException pe) {
                return false;
            }
        }
    }

    public List<Long> findItemIdsContainingText(String text) {
        return findHitsContainingText(text).getItemIds();
    }

    public SearchResultHits findHitsContainingText(String text) {
        SearchResultHits emptyHits = new SearchResultHits();
        if (text == null || text.trim().length() == 0) {
            return emptyHits;
        }

        QueryParser parser = new QueryParser(Version.LUCENE_29, "text", getQueryAnalyzer());
        parser.setDefaultOperator(QueryParser.Operator.OR);
        parser.setAllowLeadingWildcard(true);
        parser.setPhraseSlop(2);

        Query query;
        List<String> collectedVariants = new ArrayList<String>();
        String expandedText = expandQueryText(text, collectedVariants);
        try {
            query = parser.parse(expandedText);
        } catch (ParseException pe0) {
            logger.debug("Query parsing failed for expanded text '{}', attempting raw text: {}", expandedText, pe0.getMessage());
            try {
                query = parser.parse(text);
            } catch (ParseException e) {
                logger.debug("Query parsing failed for raw text '{}', attempting escaped fallback: {}", text, e.getMessage());
                try {
                    query = parser.parse(QueryParser.escape(text));
                } catch (ParseException pe) {
                    logger.warn("Query parsing failed for escaped '{}': {}", text, pe.getMessage());
                    throw new SearchQueryParseException(pe.getMessage(), pe);
                }
            }
        }

        try {
            if (!IndexReader.indexExists(indexDirectory)) {
                return emptyHits;
            }
        } catch (Exception e) {
            logger.error("Error checking index existence", e);
            return emptyHits;
        }

        IndexReader reader = null;
        org.apache.lucene.search.IndexSearcher searcher = null;
        try {
            reader = IndexReader.open(indexDirectory, true);
            searcher = new org.apache.lucene.search.IndexSearcher(reader);
            Query searchQuery = buildBoostedQuery(query, text, collectedVariants);
            TopDocs topDocs = searcher.search(searchQuery, 1000);

            // If no hits found and query text contains special characters (e.g. Service:Auth), attempt escaped query fallback
            if (topDocs.scoreDocs.length == 0 && containsLuceneSpecialChars(text)) {
                try {
                    Query escapedQuery = parser.parse(QueryParser.escape(text));
                    TopDocs escapedDocs = searcher.search(buildBoostedQuery(escapedQuery, text, collectedVariants), 1000);
                    if (escapedDocs.scoreDocs.length > 0) {
                        topDocs = escapedDocs;
                    }
                } catch (Exception e) {
                    logger.debug("Escaped fallback search failed for '{}': {}", text, e.getMessage());
                }
            }

            // If no hits found for a simple single word, attempt automatic prefix wildcard fallback (e.g. win -> win*)
            if (topDocs.scoreDocs.length == 0 && isEligibleForPrefixFallback(text)) {
                try {
                    Query fallbackQuery = parser.parse(text.trim() + "*");
                    TopDocs fallbackDocs = searcher.search(fallbackQuery, 1000);
                    if (fallbackDocs.scoreDocs.length > 0) {
                        topDocs = fallbackDocs;
                    }
                } catch (Exception e) {
                    logger.debug("Prefix fallback search failed for '{}': {}", text, e.getMessage());
                }
            }

            SearchResultHits hits = new SearchResultHits();
            hits.getExpandedTerms().addAll(collectedVariants);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                String type = doc.get("type");
                Long itemId = ItemIdHitExtractor.extractItemId(doc);
                if (itemId != null && !hits.getItemIds().contains(itemId)) {
                    hits.getItemIds().add(itemId);
                }
                if ("history".equals(type)) {
                    String hIdStr = doc.get("id");
                    if (hIdStr != null) {
                        try {
                            Long hId = Long.valueOf(hIdStr);
                            if (!hits.getHistoryIds().contains(hId)) {
                                hits.getHistoryIds().add(hId);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                } else if ("item".equals(type)) {
                    if (itemId != null && !hits.getItemLevelHitItemIds().contains(itemId)) {
                        hits.getItemLevelHitItemIds().add(itemId);
                    }
                }
            }
            return hits;
        } catch (Exception e) {
            logger.error("Error searching index for query: " + text, e);
            throw new RuntimeException("Error searching index for query: " + text, e);
        } finally {
            if (searcher != null) {
                try {
                    searcher.close();
                } catch (Exception e) {
                    logger.error("Error closing Lucene IndexSearcher", e);
                }
            }
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    logger.error("Error closing Lucene IndexReader", e);
                }
            }
        }
    }

    private boolean isEligibleForPrefixFallback(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private boolean containsLuceneSpecialChars(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ':' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')'
                    || c == '{' || c == '}' || c == '[' || c == ']' || c == '^'
                    || c == '"' || c == '~' || c == '*' || c == '?' || c == '\\' || c == '/') {
                return true;
            }
        }
        return false;
    }

    protected String expandQueryText(String text) {
        return expandQueryText(text, null);
    }

    protected String expandQueryText(String text, List<String> collectedVariants) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        // If quoted phrase
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 2) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (OllamaSearchExpander.containsChinese(inner)) {
                List<String> variants = collectAllChineseVariants(inner);
                if (!variants.isEmpty()) {
                    StringBuilder sb = new StringBuilder("(");
                    sb.append(trimmed);
                    for (String variant : variants) {
                        if (collectedVariants != null && !collectedVariants.contains(variant)) {
                            collectedVariants.add(variant);
                        }
                        sb.append(" OR \"").append(escapeQuotedPhrase(variant)).append("\"");
                    }
                    sb.append(")");
                    return sb.toString();
                }
            }
            return trimmed;
        }

        // If already contains explicit wildcard or explicit Lucene index field prefix, preserve as-is
        if (trimmed.indexOf('*') != -1 || trimmed.indexOf('?') != -1
                || trimmed.startsWith("text:") || trimmed.startsWith("docId:")) {
            return trimmed;
        }

        // Chinese variant and cross-strait phrase expansion (Offline OpenCC + optional Ollama AI)
        if (OllamaSearchExpander.containsChinese(trimmed)) {
            List<String> variants = new ArrayList<String>();
            String[] spaceParts = trimmed.split("[\\s\\u3000]+");
            if (spaceParts.length > 1) {
                // Multi-word query (e.g. "app.hlmt@gmail.com 發票通知"): expand Chinese tokens cleanly without duplicating email
                Set<String> tokenVariants = new LinkedHashSet<String>();
                for (String part : spaceParts) {
                    if (OllamaSearchExpander.containsChinese(part)) {
                        tokenVariants.addAll(collectAllChineseVariants(part));
                    }
                }
                variants.addAll(tokenVariants);
            } else {
                variants.addAll(collectAllChineseVariants(trimmed));
            }

            if (!variants.isEmpty()) {
                String originalExpanded = expandSingleText(trimmed);
                StringBuilder sb = new StringBuilder();
                sb.append("(").append(originalExpanded).append(")");
                for (String variant : variants) {
                    if (collectedVariants != null && !variant.equalsIgnoreCase(trimmed) && !collectedVariants.contains(variant)) {
                        collectedVariants.add(variant);
                    }
                    String vExpanded = expandSingleText(variant);
                    if (!vExpanded.isEmpty()) {
                        sb.append(" OR (").append(vExpanded).append(")");
                    }
                }
                return sb.toString();
            }
        }

        return expandSingleText(trimmed);
    }

    private List<String> collectAllChineseVariants(String chineseQuery) {
        Set<String> merged = new LinkedHashSet<String>();
        // 1. Offline sub-millisecond OpenCC tw2sp / s2twp + cross-strait dictionary (always active even without AI)
        merged.addAll(ChineseVariantConverter.expandOfflineVariants(chineseQuery));
        // 2. Optional Ollama AI semantic synonym expansion when configured & online
        if (searchExpander != null) {
            List<String> aiVariants = searchExpander.expandChineseVariants(chineseQuery);
            if (aiVariants != null) {
                for (String v : aiVariants) {
                    if (v != null && !v.trim().isEmpty() && !v.trim().equalsIgnoreCase(chineseQuery)) {
                        merged.add(v.trim());
                    }
                }
            }
        }
        return new ArrayList<String>(merged);
    }

    /**
     * Wraps the base query with optional SHOULD boost clauses for exact token and exact contiguous phrase matches.
     * Because baseQuery is MUST, the matched document set is strictly unchanged while exact matches are boosted
     * to the top of Lucene's TopDocs(1000) window so they are never truncated by partial/sub-token noise.
     */
    private Query buildBoostedQuery(Query baseQuery, String rawText, List<String> variants) {
        if (baseQuery == null || rawText == null) {
            return baseQuery;
        }
        String clean = rawText.trim();
        if (clean.length() >= 2 && clean.startsWith("\"") && clean.endsWith("\"")) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.isEmpty() || clean.indexOf('*') != -1 || clean.indexOf('?') != -1) {
            return baseQuery;
        }
        try {
            BooleanQuery boosted = new BooleanQuery();
            boosted.add(baseQuery, BooleanClause.Occur.MUST);

            // 1. Exact full token boost (e.g. full email address app.hlmt@gmail.com indexed prior to SubTokenFilter)
            TermQuery exactTermQuery = new TermQuery(new Term("text", clean.toLowerCase(Locale.ROOT)));
            exactTermQuery.setBoost(20.0f);
            boosted.add(exactTermQuery, BooleanClause.Occur.SHOULD);

            // 2. Exact contiguous phrase boost (slop = 0, works for CJK unigrams and multi-word sentences)
            QueryParser exactPhraseParser = new QueryParser(Version.LUCENE_29, "text", getQueryAnalyzer());
            exactPhraseParser.setPhraseSlop(0);
            try {
                Query exactPhrase = exactPhraseParser.parse("\"" + escapeQuotedPhrase(clean) + "\"");
                exactPhrase.setBoost(10.0f);
                boosted.add(exactPhrase, BooleanClause.Occur.SHOULD);
            } catch (Exception ignored) {
            }

            // 3. Variant / TC-SC contiguous phrase boost
            if (variants != null) {
                for (String v : variants) {
                    if (v != null && !v.trim().isEmpty()) {
                        try {
                            Query variantPhrase = exactPhraseParser.parse("\"" + escapeQuotedPhrase(v.trim()) + "\"");
                            variantPhrase.setBoost(5.0f);
                            boosted.add(variantPhrase, BooleanClause.Occur.SHOULD);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return boosted;
        } catch (Exception e) {
            return baseQuery;
        }
    }

    private String escapeQuotedPhrase(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String expandSingleText(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed;
        }
        String[] tokens = trimmed.split("[\\s\\u3000]+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isEmpty()) {
                continue;
            }
            String clause = buildTokenClause(token);
            if (clause.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(clause);
        }
        return sb.length() > 0 ? sb.toString() : QueryParser.escape(trimmed);
    }

    /**
     * Translates a single whitespace-delimited token into a safe, noise-free Lucene query clause:
     * - Pure letter/digit word (e.g. "win", "測試"): "(token OR token*)"
     * - Standalone punctuation (e.g. "-" in "Error - timeout"): skipped ("") so it never triggers NOT
     * - Symbol-connected compound token (., @, _, -, /, \, :, brackets, GUIDs, paths, URLs, C++):
     *   Splits on non-letter/digit symbols into sub-parts and combines them with AND ("+part1 +part2"),
     *   paired with exact atomic token match for HOST/EMAIL tokens (e.g. "(app\\.hlmt OR (+app +hlmt))").
     */
    private String buildTokenClause(String token) {
        if (isPureLetterOrDigit(token)) {
            if (token.length() >= 2) {
                return "(" + token + " OR " + token + "*)";
            }
            return QueryParser.escape(token);
        }

        String[] rawParts = token.split("[^\\p{L}\\p{N}]+");
        List<String> meaningfulParts = new ArrayList<String>();
        List<String> singleCharFallbacks = new ArrayList<String>();
        for (String rp : rawParts) {
            if (rp == null || rp.isEmpty()) {
                continue;
            }
            String lower = rp.toLowerCase(Locale.ROOT);
            if (StopAnalyzer.ENGLISH_STOP_WORDS_SET.contains(lower)) {
                continue;
            }
            if (rp.length() >= 2 || OllamaSearchExpander.containsChinese(rp)) {
                if (!containsIgnoreCase(meaningfulParts, rp)) {
                    meaningfulParts.add(rp);
                }
            } else {
                if (!containsIgnoreCase(singleCharFallbacks, rp)) {
                    singleCharFallbacks.add(rp);
                }
            }
        }

        if (meaningfulParts.isEmpty()) {
            meaningfulParts = singleCharFallbacks;
        }
        if (meaningfulParts.isEmpty()) {
            // Pure punctuation / operator token (e.g. "-", "|", "==>")
            return "";
        }

        if (meaningfulParts.size() == 1) {
            String single = meaningfulParts.get(0);
            if (single.length() >= 2 && isPureLetterOrDigit(single)) {
                return "(" + single + " OR " + single + "*)";
            }
            return QueryParser.escape(single);
        }

        StringBuilder conj = new StringBuilder("(");
        for (int i = 0; i < meaningfulParts.size(); i++) {
            if (i > 0) {
                conj.append(" ");
            }
            conj.append("+").append(QueryParser.escape(meaningfulParts.get(i)));
        }
        conj.append(")");

        if (isAtomicHostOrEmailToken(token)) {
            return "(" + QueryParser.escape(token) + " OR " + conj.toString() + ")";
        }
        return conj.toString();
    }

    private boolean isPureLetterOrDigit(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isLetterOrDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if Lucene StandardTokenizer keeps this token as a single atomic HOST or EMAIL token
     * (either dotted host/identifier like "app.hlmt" or full email "app.hlmt@gmail.com" with dot after '@').
     */
    private boolean isAtomicHostOrEmailToken(String token) {
        if (token == null || token.length() < 3) {
            return false;
        }
        if (token.charAt(0) == '.' || token.charAt(0) == '@'
                || token.charAt(token.length() - 1) == '.' || token.charAt(token.length() - 1) == '@') {
            return false;
        }
        int atCount = 0;
        int atPos = -1;
        int lastDotPos = -1;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '@') {
                atCount++;
                atPos = i;
                if (atCount > 1) {
                    return false;
                }
            } else if (c == '.') {
                lastDotPos = i;
            } else if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        if (atCount == 1) {
            return lastDotPos > atPos + 1;
        }
        return lastDotPos > 0;
    }

    private boolean containsIgnoreCase(List<String> list, String target) {
        for (String item : list) {
            if (item.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
