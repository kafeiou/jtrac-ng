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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
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
    private OllamaSearchExpander searchExpander;

    public void setIndexDirectory(Directory indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
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
        QueryParser parser = new QueryParser(Version.LUCENE_29, "text", analyzer);
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

        QueryParser parser = new QueryParser(Version.LUCENE_29, "text", analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        parser.setAllowLeadingWildcard(true);
        parser.setPhraseSlop(2);

        Query query;
        String expandedText = expandQueryText(text);
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
            TopDocs topDocs = searcher.search(query, 1000);

            // If no hits found and query text contains special characters (e.g. Service:Auth), attempt escaped query fallback
            if (topDocs.scoreDocs.length == 0 && containsLuceneSpecialChars(text)) {
                try {
                    Query escapedQuery = parser.parse(QueryParser.escape(text));
                    TopDocs escapedDocs = searcher.search(escapedQuery, 1000);
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
            if (searchExpander != null && OllamaSearchExpander.containsChinese(inner)) {
                List<String> variants = searchExpander.expandChineseVariants(inner);
                if (variants != null && !variants.isEmpty()) {
                    StringBuilder sb = new StringBuilder("(");
                    sb.append(trimmed);
                    for (String variant : variants) {
                        sb.append(" OR \"").append(variant).append("\"");
                    }
                    sb.append(")");
                    return sb.toString();
                }
            }
            return trimmed;
        }

        // If already contains wildcard or lucene field syntax, preserve as-is
        if (trimmed.indexOf('*') != -1 || trimmed.indexOf('?') != -1 || trimmed.indexOf(':') != -1) {
            return trimmed;
        }

        // Chinese variant and synonym expansion via Ollama
        if (searchExpander != null && OllamaSearchExpander.containsChinese(trimmed)) {
            List<String> variants = searchExpander.expandChineseVariants(trimmed);
            if (variants != null && !variants.isEmpty()) {
                String originalExpanded = expandSingleText(trimmed);
                StringBuilder sb = new StringBuilder();
                sb.append("(").append(originalExpanded).append(")");
                for (String variant : variants) {
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
        String[] tokens = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (token.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" ");
            }
            if (isEligibleForWildcard(token)) {
                sb.append("(").append(token).append(" OR ").append(token).append("*)");
            } else {
                sb.append(token);
            }
        }
        return sb.length() > 0 ? sb.toString() : trimmed;
    }

    private boolean isEligibleForWildcard(String token) {
        if (token == null || token.length() < 2) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }
}
