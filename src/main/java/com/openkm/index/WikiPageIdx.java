package com.openkm.index;


import com.openkm.extension.dao.bean.WikiPage;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class WikiPageIdx {
	private static Logger log = LoggerFactory.getLogger(WikiPageIdx.class);

	/**
	 * Index item
	 */
	public static void index(WikiPage item) throws IOException {
		log.info("index({})", item);
		IndexWriter writer = Indexer.getIndexWriter();
		Document doc = new Document();
		doc.add(new StringField("id", Long.toString(item.getId()), Field.Store.YES));
		doc.add(new StringField("user", item.getUser(), Field.Store.YES));
		doc.add(new TextField("content", item.getContent(), Field.Store.NO));
		writer.addDocument(doc);
	}

	/**
	 * Perform search
	 */
	public static TopDocs performSearch(String field, String qs) throws IOException, ParseException {
		IndexSearcher searcher = Indexer.getIndexSearcher();
		QueryParser parser = new QueryParser(field, Indexer.getAnalyzer());
		Query query = parser.parse(qs);
		TopDocs result = searcher.search(query, Indexer.HITS_PER_PAGE);
		return result;
	}
}
