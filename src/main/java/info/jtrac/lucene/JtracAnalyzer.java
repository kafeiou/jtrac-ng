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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.classic.ClassicFilter;
import org.apache.lucene.analysis.classic.ClassicTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;

import java.util.Set;

/**
 * Custom Lucene 10 analyzer for JTrac combining:
 * 1. ClassicTokenizer (preserves emails & dotted hosts while splitting CJK into unigrams)
 * 2. ClassicFilter (normalizing acronyms/possessives)
 * 3. LowerCaseFilter (case-insensitive indexing and search)
 * 4. SubTokenFilter (index-time sub-token splitting for emails, filenames, URLs & paths)
 * 5. StopFilter (common English stop-words filtering)
 * 6. PorterStemFilter (English stemming: window <-> windows, test <-> testing)
 */
public class JtracAnalyzer extends Analyzer {

    private final CharArraySet stopWords;
    private final boolean splitSubTokens;

    public JtracAnalyzer() {
        this(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, true);
    }

    public JtracAnalyzer(boolean splitSubTokens) {
        this(EnglishAnalyzer.ENGLISH_STOP_WORDS_SET, splitSubTokens);
    }

    public JtracAnalyzer(Set<?> stopWords) {
        this(stopWords, true);
    }

    public JtracAnalyzer(Set<?> stopWords, boolean splitSubTokens) {
        if (stopWords instanceof CharArraySet) {
            this.stopWords = (CharArraySet) stopWords;
        } else if (stopWords != null) {
            this.stopWords = new CharArraySet(stopWords, true);
        } else {
            this.stopWords = CharArraySet.EMPTY_SET;
        }
        this.splitSubTokens = splitSubTokens;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        ClassicTokenizer tokenizer = new ClassicTokenizer();
        TokenStream result = new ClassicFilter(tokenizer);
        result = new LowerCaseFilter(result);
        if (splitSubTokens) {
            result = new SubTokenFilter(result);
        }
        if (stopWords != null && !stopWords.isEmpty()) {
            result = new StopFilter(result, stopWords);
        }
        result = new PorterStemFilter(result);
        return new TokenStreamComponents(tokenizer, result);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }
}
