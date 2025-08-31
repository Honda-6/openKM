// /**
//  * OpenKM, Open Document Management System (http://www.openkm.com)
//  * Copyright (c) Paco Avila & Josep Llort
//  * <p>
//  * No bytes were intentionally harmed during the development of this application.
//  * <p>
//  * This program is free software; you can redistribute it and/or modify
//  * it under the terms of the GNU General Public License as published by
//  * the Free Software Foundation; either version 2 of the License, or
//  * (at your option) any later version.
//  * <p>
//  * This program is distributed in the hope that it will be useful,
//  * but WITHOUT ANY WARRANTY; without even the implied warranty of
//  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  * GNU General Public License for more details.
//  * <p>
//  * You should have received a copy of the GNU General Public License along
//  * with this program; if not, write to the Free Software Foundation, Inc.,
//  * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//  */

// package com.openkm.module.db.stuff;

// import com.openkm.dao.bean.NodeDocument;
// import com.openkm.dao.bean.NodeDocumentVersion;
// import org.apache.lucene.analysis.Analyzer;
// import org.apache.lucene.analysis.Token;
// import org.apache.lucene.analysis.TokenStream;
// import org.apache.lucene.index.IndexReader;
// import org.apache.lucene.search.spell.Dictionary;
// import org.apache.lucene.search.spell.LuceneDictionary;
// import org.apache.lucene.search.spell.SpellChecker;
// import org.apache.lucene.store.Directory;
// import org.apache.lucene.store.FSDirectory;
// import org.hibernate.ScrollableResults;
// import org.hibernate.search.FullTextSession;
// import org.hibernate.search.SearchFactory;
// import org.hibernate.search.jpa.FullTextEntityManager;
// import org.hibernate.search.jpa.Search;
// import org.hibernate.search.reader.ReaderProvider;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import jakarta.persistence.EntityManager;
// import java.io.File;
// import java.io.StringReader;
// import java.util.HashSet;
// import java.util.Iterator;
// import java.util.Set;

// /**
//  * Provides various helper operations for working with Lucene indexes.
//  */

// public class IndexHelper {
// 	private static Logger log = LoggerFactory.getLogger(IndexHelper.class);
// 	private EntityManager entityManager;

// 	public void checkIndexOnStartup() {
// 		//log.info("Observed event {1} from Thread {0}", Thread.currentThread().getName(), App.INIT_SUCCESS);

// 		// See if we need to rebuild the index during startup ...
// 		FullTextEntityManager ftEm = Search.getFullTextEntityManager(entityManager);
// 		SearchFactory searchFactory = ftEm.getSearchFactory();
// 		ReaderProvider readerProvider = searchFactory.getReaderProvider();
// 		IndexReader reader = readerProvider.openReader(searchFactory.getDirectoryProviders(NodeDocumentVersion.class)[0]);
// 		int maxDoc = 0;

// 		try {
// 			maxDoc = reader.maxDoc();
// 		} finally {
// 			readerProvider.closeReader(reader);
// 		}

// 		if (maxDoc == 0) {
// 			log.warn("No objects indexed ... rebuilding Lucene search index from database ...");
// 			long _exit = 0L;
// 			long _entr = System.currentTimeMillis();

// 			try {
// 				int docs = doRebuildIndex();
// 				_exit = System.currentTimeMillis();
// 				log.info("Took " + (_exit - _entr)
// 						+ " (ms) to re-build the index containing " + docs
// 						+ " documents.");
// 			} catch (Exception exc) {
// 				if (exc instanceof RuntimeException) {
// 					throw (RuntimeException) exc;
// 				} else {
// 					throw new RuntimeException(exc);
// 				}
// 			}

// 			// build the spell checker index off of the HS index.
// 			buildSpellCheckerIndex(searchFactory);
// 		}
// 	}

// 	// used to feed updates to the spell checker index
// 	class SetDictionary implements Dictionary {
// 		private Set<String> wordSet;

// 		@SuppressWarnings("unused")
// 		SetDictionary(String words, Analyzer analyzer) {
// 			wordSet = new HashSet<>();
// 			if (words != null) {
// 				TokenStream tokenStream = analyzer.tokenStream(NodeDocument.TEXT_FIELD, new StringReader(words));
// 				Token reusableToken = new Token();
// 				Token nextToken = null;

// 				//while ((nextToken = tokenStream.next(reusableToken)) != null) {
// 				//String term = nextToken.term();
// 				//if (term != null) {
// 				//wordSet.add(term);
// 				//}
// 				//}
// 			}
// 		}

// 		@SuppressWarnings({"unchecked", "rawtypes"})
// 		public Iterator getWordsIterator() {
// 			return wordSet.iterator();
// 		}
// 	}

// 	protected void buildSpellCheckerIndex(SearchFactory searchFactory) {
// 		IndexReader reader = null;
// 		Directory dir = null;
// 		long _entr = System.currentTimeMillis();
// 		File spellCheckIndexDir = new File("lucene_index/spellcheck");
// 		log.info("Building SpellChecker index in {}", spellCheckIndexDir.getAbsolutePath());
// 		ReaderProvider readerProvider = searchFactory.getReaderProvider();

// 		try {
// 			reader = readerProvider.openReader(searchFactory.getDirectoryProviders(NodeDocumentVersion.class)[0]);
// 			dir = FSDirectory.open(spellCheckIndexDir);
// 			SpellChecker spell = new SpellChecker(dir);
// 			spell.clearIndex();
// 			spell.indexDictionary(new LuceneDictionary(reader, NodeDocument.TEXT_FIELD));
// 			spell.close();
// 			dir.close();
// 			dir = null;
// 			long _exit = System.currentTimeMillis();
// 			log.info("Took {1} (ms) to build SpellChecker index in {}",
// 					spellCheckIndexDir.getAbsolutePath(), _exit - _entr);
// 		} catch (Exception exc) {
// 			log.error("Failed to build spell checker index!", exc);
// 		} finally {
// 			if (dir != null) {
// 				try {
// 					dir.close();
// 				} catch (Exception ignored) {
// 				}
// 			}
// 			if (reader != null) {
// 				readerProvider.closeReader(reader);
// 			}
// 		}
// 	}

// 	protected int doRebuildIndex() throws Exception {
// 		FullTextSession fullTextSession = (FullTextSession) entityManager.getDelegate();
// 		fullTextSession.setFlushMode(org.hibernate.FlushMode.MANUAL);
// 		fullTextSession.setCacheMode(org.hibernate.CacheMode.IGNORE);
// 		fullTextSession.purgeAll(NodeDocumentVersion.class);
// 		fullTextSession.getSearchFactory().optimize(NodeDocumentVersion.class);

// 		String query = "select ndv from NodeDocumentVersion ndv";
// 		ScrollableResults cursor = fullTextSession.createQuery(query).scroll();
// 		cursor.last();
// 		int count = cursor.getRowNumber() + 1;
// 		log.warn("Re-building Wine index for " + count + " objects.");

// 		if (count > 0) {
// 			int batchSize = 300;
// 			cursor.first(); // Reset to first result row
// 			int i = 0;

// 			while (true) {
// 				fullTextSession.index(cursor.get(0));

// 				if (++i % batchSize == 0) {
// 					fullTextSession.flushToIndexes();
// 					fullTextSession.clear(); // Clear persistence context for each batch
// 					log.info("Flushed index update " + i + " from Thread "
// 							+ Thread.currentThread().getName());
// 				}

// 				if (cursor.isLast()) {
// 					break;
// 				}

// 				cursor.next();
// 			}
// 		}

// 		cursor.close();
// 		fullTextSession.flushToIndexes();
// 		fullTextSession.clear(); // Clear persistence context for each batch
// 		fullTextSession.getSearchFactory().optimize(NodeDocumentVersion.class);

// 		return count;
// 	}
// }
package com.openkm.module.db.stuff;

import com.openkm.dao.bean.NodeDocumentVersion;
import jakarta.persistence.EntityManager;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.fst.FSTCompletionLookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;
import org.hibernate.Session;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * OpenKM IndexHelper – Refactored to Hibernate Search 6 + Lucene 9 suggester.
 * Handles Hibernate Search mass indexing and suggestion lookup.
 */
public class IndexHelper {

    private static final Logger log = LoggerFactory.getLogger(IndexHelper.class);

    private final EntityManager entityManager;
    private final File suggesterIndexDir = new File("lucene_index/suggester");

    public IndexHelper(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Build both the Hibernate Search indexes and the Lucene suggester index.
     */
    public void buildIndexes() {
        long start = System.currentTimeMillis();
        try {
            log.info("Starting Hibernate Search mass indexing for NodeDocumentVersion...");

            // Get Hibernate Session from EntityManager for Hibernate Search 6
            Session session = entityManager.unwrap(Session.class);
            SearchSession searchSession = Search.session(session);

            MassIndexer indexer = searchSession.massIndexer(NodeDocumentVersion.class)
                    .threadsToLoadObjects(4)
                    .batchSizeToLoadObjects(25);

            indexer.startAndWait();

            log.info("Hibernate Search mass indexing complete.");

            // Build suggester index based on TEXT_FIELD content
            buildSuggesterIndex(searchSession);

        } catch (Exception e) {
            log.error("Error while building indexes", e);
        }

        long end = System.currentTimeMillis();
        log.info("Total indexing process took {} ms", (end - start));
    }

    /**
     * Build the Lucene AnalyzingSuggester index from NodeDocumentVersion TEXT_FIELD.
     */
    private void buildSuggesterIndex(SearchSession searchSession) {
        long start = System.currentTimeMillis();
        log.info("Building AnalyzingSuggester index at {}", suggesterIndexDir.getAbsolutePath());

        try {
            // Collect unique TEXT_FIELD terms from database directly
            Session session = searchSession.toOrmSession();
            List<String> texts = session.createQuery(
                "SELECT ndv.text FROM NodeDocumentVersion ndv WHERE ndv.text IS NOT NULL", String.class
            ).getResultList();

            Set<String> uniqueTerms = new HashSet<>();
            for (String text : texts) {
                if (text != null && !text.trim().isEmpty()) {
                    // Split text into words and add them as suggestions
                    String[] words = text.toLowerCase().split("\\s+");
                    for (String word : words) {
                        if (word.length() > 2) { // Only add words longer than 2 characters
                            uniqueTerms.add(word);
                        }
                    }
                }
            }

            // Create FSTCompletionLookup suggester
            FSTCompletionLookup suggester = new FSTCompletionLookup();

            // Build suggester from terms
            suggester.build(new InputIterator() {
                private final Iterator<String> it = uniqueTerms.iterator();
                
                @Override
                public BytesRef next() {
                    return it.hasNext() ? new BytesRef(it.next()) : null;
                }
                
                @Override
                public long weight() { 
                    return 1; 
                }
                
                @Override
                public BytesRef payload() { 
                    return null; 
                }
                
                @Override
                public boolean hasPayloads() { 
                    return false; 
                }
                
                @Override
                public Set<BytesRef> contexts() { 
                    return null; 
                }

                @Override
                public boolean hasContexts() { 
                    return false; 
                }
            });

            // Store suggester to disk
            suggesterIndexDir.mkdirs();
            try (Directory dir = FSDirectory.open(suggesterIndexDir.toPath());
                 IndexOutput out = dir.createOutput("suggester", null)) {
                suggester.store(out);
            }

            long end = System.currentTimeMillis();
            log.info("Built AnalyzingSuggester index with {} terms in {} ms", uniqueTerms.size(), (end - start));

        } catch (Exception e) {
            log.error("Failed to build suggester index", e);
        }
    }

    /**
     * Get query suggestions from the Lucene FSTCompletionLookup index.
     *
     * @param input user input string
     * @param size  max number of suggestions
     * @return list of suggestion strings
     */
    public List<String> getSuggestions(String input, int size) {
        try (Directory dir = FSDirectory.open(suggesterIndexDir.toPath())) {
            FSTCompletionLookup suggester = new FSTCompletionLookup();
            
            try (IndexInput indexInput = dir.openInput("suggester", null)) {
                suggester.load(indexInput);
            }

            List<Lookup.LookupResult> results = suggester.lookup(input, false, size);

            List<String> suggestions = new ArrayList<>();
            for (Lookup.LookupResult r : results) {
                suggestions.add(r.key.toString());
            }
            return suggestions;

        } catch (IOException e) {
            log.error("Error while loading suggester index", e);
            return Collections.emptyList();
        }
    }
}
