package com.openkm.module.db.stuff;

import com.openkm.dao.bean.NodeDocument;
import com.openkm.dao.bean.NodeDocumentVersion;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.spell.LuceneDictionary;
import org.apache.lucene.search.spell.SpellChecker;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.nio.file.Path;

public class IndexHelper {
    private static final Logger log = LoggerFactory.getLogger(IndexHelper.class);
    private final EntityManager entityManager;

    public IndexHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void checkIndexOnStartup() {
        SearchSession searchSession = Search.session(entityManager);

        long count = searchSession.search(NodeDocumentVersion.class)
                .where(f -> f.matchAll())
                .fetchTotalHitCount();

        if (count == 0) {
            log.warn("No objects indexed ... rebuilding Lucene search index from database ...");
            long start = System.currentTimeMillis();

            try {
                int docs = doRebuildIndex();
                long end = System.currentTimeMillis();
                log.info("Took {} ms to re-build the index containing {} documents.", (end - start), docs);
            } catch (Exception exc) {
                throw new RuntimeException("Index rebuild failed", exc);
            }

            buildSpellCheckerIndex();
        }
    }

    protected void buildSpellCheckerIndex() {
        try {
            Path spellCheckPath = Path.of("lucene_index", "spellcheck");
            Directory dir = FSDirectory.open(spellCheckPath);
			long start = System.currentTimeMillis();
            SpellChecker spellChecker = new SpellChecker(dir);
			DirectoryReader reader = DirectoryReader.open(dir);
            
			spellChecker.clearIndex();
            spellChecker.indexDictionary(new LuceneDictionary(reader, NodeDocument.TEXT_FIELD),
                                         null, true);
            spellChecker.close();
            long end = System.currentTimeMillis();
            log.info("SpellChecker index built in {} ms", (end - start));
            spellChecker.close();
            dir.close();
        } catch (Exception e) {
            log.error("Failed to build spell checker index!", e);
        }
    }

    protected int doRebuildIndex() throws InterruptedException {
        SearchSession searchSession = Search.session(entityManager);
        MassIndexer indexer = searchSession.massIndexer(NodeDocumentVersion.class)
                .batchSizeToLoadObjects(300)
                .threadsToLoadObjects(4);

        indexer.startAndWait();
        return (int) searchSession.search(NodeDocumentVersion.class)
                .where(f -> f.matchAll())
                .fetchTotalHitCount();
    }
}
