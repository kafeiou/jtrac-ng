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

import info.jtrac.domain.AbstractItem;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene 10 Indexing implementation using native Lucene API
 */
public class Indexer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Directory indexDirectory;
    private Analyzer analyzer;

    public void setIndexDirectory(Directory indexDirectory) {
        this.indexDirectory = indexDirectory;
    }

    public void setAnalyzer(Analyzer analyzer) {
        this.analyzer = analyzer;
    }

    public synchronized void index(AbstractItem item) {
        String docIdPrefix = (item instanceof info.jtrac.domain.History) ? "history:" : "item:";
        Term docTerm = new Term("docId", docIdPrefix + item.getId());
        try {
            writeDocument(docTerm, item, IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        } catch (IndexFormatTooOldException legacyEx) {
            logger.warn("Legacy Lucene index format detected during indexing; recreating index with Lucene 10 codec...");
            try {
                writeDocument(docTerm, item, IndexWriterConfig.OpenMode.CREATE);
            } catch (Exception e) {
                logger.error("Error recreating index for item: " + item, e);
                throw new RuntimeException("Error indexing item: " + item, e);
            }
        } catch (Exception e) {
            logger.error("Error indexing item: " + item, e);
            throw new RuntimeException("Error indexing item: " + item, e);
        }
    }

    private void writeDocument(Term docTerm, AbstractItem item, IndexWriterConfig.OpenMode openMode) throws Exception {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(openMode);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            writer.updateDocument(docTerm, item.createDocument());
            writer.commit();
        }
    }
}
