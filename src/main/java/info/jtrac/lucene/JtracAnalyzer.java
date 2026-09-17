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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.PorterStemFilter;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;
import java.util.Set;

/**
 * Custom Lucene analyzer for JTrac combining:
 * 1. StandardTokenizer (word splitting & CJK unigram tokenization)
 * 2. StandardFilter (normalizing tokens)
 * 3. LowerCaseFilter (case-insensitive indexing and search)
 * 4. StopFilter (common English stop-words filtering)
 * 5. PorterStemFilter (English stemming: window <-> windows, test <-> testing)
 */
public class JtracAnalyzer extends Analyzer {

    private final Set<?> stopWords;

    public JtracAnalyzer() {
        this(StopAnalyzer.ENGLISH_STOP_WORDS_SET);
    }

    public JtracAnalyzer(Set<?> stopWords) {
        this.stopWords = stopWords;
    }

    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        StandardTokenizer tokenizer = new StandardTokenizer(Version.LUCENE_29, reader);
        TokenStream result = new StandardFilter(tokenizer);
        result = new LowerCaseFilter(result);
        result = new SubTokenFilter(result);
        if (stopWords != null) {
            result = new StopFilter(StopFilter.getEnablePositionIncrementsVersionDefault(Version.LUCENE_29), result, stopWords);
        }
        result = new PorterStemFilter(result);
        return result;
    }

    private static final class SavedStreams {
        StandardTokenizer tokenStream;
        TokenStream filteredTokenStream;
    }

    @Override
    public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
        SavedStreams streams = (SavedStreams) getPreviousTokenStream();
        if (streams == null) {
            streams = new SavedStreams();
            setPreviousTokenStream(streams);
            streams.tokenStream = new StandardTokenizer(Version.LUCENE_29, reader);
            TokenStream result = new StandardFilter(streams.tokenStream);
            result = new LowerCaseFilter(result);
            result = new SubTokenFilter(result);
            if (stopWords != null) {
                result = new StopFilter(StopFilter.getEnablePositionIncrementsVersionDefault(Version.LUCENE_29), result, stopWords);
            }
            result = new PorterStemFilter(result);
            streams.filteredTokenStream = result;
        } else {
            streams.tokenStream.reset(reader);
        }
        return streams.filteredTokenStream;
    }
}
