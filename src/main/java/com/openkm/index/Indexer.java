package com.openkm.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Indexer {
	private static Analyzer analyzer = null;
	private static IndexWriter indexWriter = null;
	private static IndexSearcher indexSearcher = null;
	private static final String INDEX_PATH = "index";
	public static final int HITS_PER_PAGE = 32;

	/**
	 * Instance analyzer
	 */
	public static synchronized Analyzer getAnalyzer() {
		if (analyzer == null) {
			analyzer = new StandardAnalyzer();
		}
		return analyzer;
	}

	/**
	 * Obtain index writer
	 */
	public static synchronized IndexWriter getIndexWriter() throws IOException {
		if (indexWriter == null) {
			IndexWriterConfig iwc = new IndexWriterConfig(getAnalyzer());
			Path indexDir = Paths.get(INDEX_PATH);
			FSDirectory fsd = FSDirectory.open(indexDir);
			indexWriter = new IndexWriter(fsd, iwc);
		}
		return indexWriter;
	}

	/**
	 * Close index writer
	 */
	public static synchronized void closeIndexWriter() throws IOException {
		if (indexWriter != null) {
			indexWriter.close();
			indexWriter = null;
		}
	}

	/**
	 * Obtain index searcher
	 */
	public static synchronized IndexSearcher getIndexSearcher() throws IOException {
		if (indexSearcher == null) {
			Path indexDir = Paths.get(INDEX_PATH);
			FSDirectory fsd = FSDirectory.open(indexDir);
			indexSearcher = new IndexSearcher(DirectoryReader.open(fsd));
		}
		return indexSearcher;
	}

	/**
	 * Close index searcher
	 */
	public static synchronized void closeIndexSearcher() throws IOException {
		if (indexSearcher != null) {
			indexSearcher.getIndexReader().close();
			indexSearcher = null;
		}
	}
}
