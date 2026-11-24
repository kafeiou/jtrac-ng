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

import java.io.File;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;

/**
 * Spring FactoryBean to initialize a native Lucene 10 FSDirectory,
 * automatically purging incompatible legacy Lucene 2.x index files if detected.
 */
public class LuceneDirectoryFactoryBean implements FactoryBean<Directory> {

    private static final Logger logger = LoggerFactory.getLogger(LuceneDirectoryFactoryBean.class);

    private String path;

    public void setPath(String path) {
        if (path != null && path.contains("${jtrac.home}")) {
            String jtracHome = System.getProperty("jtrac.home");
            if (jtracHome == null || jtracHome.trim().isEmpty()) {
                jtracHome = "target/home";
            }
            path = path.replace("${jtrac.home}", jtracHome);
        }
        this.path = path;
    }

    @Override
    public Directory getObject() throws Exception {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Directory fsDir = FSDirectory.open(dir.toPath());
        purgeLegacyIncompatibleIndexIfPresent(dir, fsDir);
        return fsDir;
    }

    private void purgeLegacyIncompatibleIndexIfPresent(File dir, Directory fsDir) {
        try {
            File legacySegmentsGen = new File(dir, "segments.gen");
            boolean purgeNeeded = legacySegmentsGen.isFile();
            if (!purgeNeeded) {
                try {
                    DirectoryReader.indexExists(fsDir);
                } catch (IndexFormatTooOldException e) {
                    purgeNeeded = true;
                }
            }
            if (purgeNeeded) {
                logger.info("Detected legacy pre-Lucene-10 index files in '{}'. Cleaning directory for automatic synchronous Lucene 10 rebuild...", dir.getAbsolutePath());
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            f.delete();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Non-fatal check on index directory '{}': {}", dir.getAbsolutePath(), e.getMessage());
        }
    }

    @Override
    public Class<?> getObjectType() {
        return Directory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
