/**
 * OpenKM, Open Document Management System (http://www.openkm.com)
 * Copyright (c) 2006-2017 Paco Avila & Josep Llort
 * <p>
 * No bytes were intentionally harmed during the development of this application.
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.openkm.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;

import java.io.IOException;

/**
 * Demo showing how Lucene analyzers behave in modern API.
 */
public class SearchDemo {
    private static final String DOC_FIELD = "content";
    private static final int NUM_HITS = 10;
    private static final String SEARCH_TERM = "专项信息*";

    private static final String[] strings = {
            "专项信息管理.doc",
            "Lucene in Action",
            "Lucene for Dummies",
            "Managing Gigabytes",
            "The Art of Computer Science"
    };

    private static final Analyzer[] analyzers = {
            new SimpleAnalyzer(),
            new StandardAnalyzer(),
            new CJKAnalyzer(),
            new WhitespaceAnalyzer()
    };

    public static void main(String[] args) throws Exception {
        for (Analyzer analyzer : analyzers) {
            System.out.println("** Analyzer: " + analyzer.getClass().getName() + " **");
            Directory index = new RAMDirectory();

            for (String str : strings) {
                add(index, analyzer, str);
            }

            search(index, analyzer, SEARCH_TERM);
            System.out.println();
        }
    }

    /**
     * Add documents
     */
    private static void add(Directory index, Analyzer analyzer, String str) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter w = new IndexWriter(index, config)) {
            Document doc = new Document();
            // TextField = tokenized & analyzed (replaces Field.ANALYZED)
            doc.add(new TextField(DOC_FIELD, str, Field.Store.YES));
            w.addDocument(doc);
        }
    }

    /**
     * Search in documents
     */
    private static void search(Directory index, Analyzer analyzer, String str) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(index)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // WildcardQuery for pattern matching
            Query q = new WildcardQuery(new Term(DOC_FIELD, str.toLowerCase()));
            System.out.println("Query: " + q);

            TopScoreDocCollector collector = TopScoreDocCollector.create(NUM_HITS, Integer.MAX_VALUE);
            searcher.search(q, collector);

            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            System.out.println("Found " + hits.length + " hits.");

            for (int i = 0; i < hits.length; i++) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);
                System.out.println((i + 1) + ". " + d.get(DOC_FIELD));
            }
        }
    }
}
