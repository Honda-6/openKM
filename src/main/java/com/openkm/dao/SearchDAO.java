package com.openkm.dao;

import com.openkm.bean.Permission;
import com.openkm.bean.nr.NodeQueryResult;
import com.openkm.bean.nr.NodeResultSet;
import com.openkm.cache.CacheProvider;
import com.openkm.core.Config;
import com.openkm.core.DatabaseException;
import com.openkm.core.ParseException;
import com.openkm.core.PathNotFoundException;
import com.openkm.dao.bean.NodeBase;
import com.openkm.dao.bean.NodeDocument;
import com.openkm.dao.bean.NodeFolder;
import com.openkm.dao.bean.NodeMail;
import com.openkm.module.db.stuff.DbAccessManager;
import com.openkm.module.db.stuff.SecurityHelper;
import com.openkm.spring.PrincipalUtils;
import com.openkm.util.FormatUtil;
import com.openkm.util.SystemProfiling;
import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document; // only for highlighter API types (not entity)
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.engine.search.query.SearchQuery;
import org.hibernate.search.engine.search.query.SearchResult;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hibernate Search 6 rewrite of SearchDAO.
 *
 * Security filtering:
 *  - In HS6 there is no enableFullTextFilter/readAccess hook like old HS4,
 *    so we evaluate access with DbAccessManager after fetching hits, preserving
 *    the previous "MORE", "WINDOW", and "LIMITED" behaviors.
 */
public class SearchDAO {
    private static final Logger log = LoggerFactory.getLogger(SearchDAO.class);
    private static final SearchDAO SINGLETON = new SearchDAO();

    private static final int MAX_FRAGMENT_LEN = 256;

    public static final String SEARCH_LUCENE = "lucene";
    public static final String SEARCH_ACCESS_MANAGER_MORE = "am_more";
    public static final String SEARCH_ACCESS_MANAGER_WINDOW = "am_window";
    public static final String SEARCH_ACCESS_MANAGER_LIMITED = "am_limited";

    private static final String CACHE_SEARCH_FOLDERS_IN_DEPTH = "com.openkm.cache.searchFoldersInDepth";

    public static Analyzer analyzer;

    static {
        try {
            // Try to respect custom analyzer from config if present.
            Class<?> analyzerClass = Class.forName(Config.HIBERNATE_SEARCH_ANALYZER);
            if (Analyzer.class.isAssignableFrom(analyzerClass)) {
                // Prefer no-arg constructor (Lucene 9 analyzers no longer take Version)
                try {
                    analyzer = (Analyzer) analyzerClass.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException noNoArg) {
                    // fallback: try any constructor with zero parameters anyway
                    Constructor<?>[] ctors = analyzerClass.getConstructors();
                    for (Constructor<?> c : ctors) {
                        if (c.getParameterCount() == 0) {
                            analyzer = (Analyzer) c.newInstance();
                            break;
                        }
                    }
                    if (analyzer == null) throw noNoArg;
                }
            } else {
                analyzer = new StandardAnalyzer();
            }
        } catch (Exception e) {
            log.warn("Falling back to StandardAnalyzer: {}", e.getMessage(), e);
            analyzer = new StandardAnalyzer();
        }
        log.debug("Analyzer in use: {}", analyzer.getClass().getName());
    }

    private SearchDAO() {}

    public static SearchDAO getInstance() {
        return SINGLETON;
    }

    /* ------------------------------------------------------------------
     * Public API
     * ------------------------------------------------------------------ */

    /**
     * Execute a Lucene query against NodeDocument, NodeFolder, NodeMail.
     * Applies security filtering according to Config.SECURITY_SEARCH_EVALUATION.
     */
    public NodeResultSet findByQuery(Query luceneQuery, int offset, int limit)
            throws ParseException, DatabaseException {
        log.debug("findByQuery({}, {}, {})", luceneQuery, offset, limit);

        Session session = null;
        Transaction tx = null;

        try {
            long begin = System.currentTimeMillis();
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();
            SearchSession searchSession = Search.session(session);

            NodeResultSet result;

            // Route based on configured evaluation strategy
            String mode = Config.SECURITY_SEARCH_EVALUATION;
            if (SEARCH_LUCENE.equals(mode)) {
                // In HS6 there is no Lucene-side security filter hook.
                // We treat this like AccessManagerLimited (fast cap) to avoid very large scans.
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_MORE.equals(mode)) {
                result = runQueryAccessManagerMore(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_WINDOW.equals(mode)) {
                result = runQueryAccessManagerWindow(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_LIMITED.equals(mode)) {
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            } else {
                // Default safety
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            }

            HibernateUtil.commit(tx);
            SystemProfiling.log(luceneQuery + ", " + offset + ", " + limit, System.currentTimeMillis() - begin);
            log.trace("findByQuery.Time: {}", FormatUtil.formatMiliSeconds(System.currentTimeMillis() - begin));
            log.debug("findByQuery: {}", result);
            return result;
        } catch (IOException | InvalidTokenOffsetsException | HibernateException e) {
            HibernateUtil.rollback(tx);
            throw new DatabaseException(e.getMessage(), e);
        } finally {
            HibernateUtil.close(session);
        }
    }

    /**
     * Parse a simple query string with Lucene classic QueryParser, then route to findByQuery.
     */
    public NodeResultSet findBySimpleQuery(String expression, int offset, int limit)
            throws ParseException, DatabaseException {
        log.debug("findBySimpleQuery({}, {}, {})", expression, offset, limit);

        Session session = null;
        Transaction tx = null;

        try {
            long begin = System.currentTimeMillis();
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();

            QueryParser parser = new QueryParser(NodeDocument.TEXT_FIELD, analyzer);
            Query luceneQuery = parser.parse(expression);
            log.debug("findBySimpleQuery.query: {}", luceneQuery);

            SearchSession searchSession = Search.session(session);
            NodeResultSet result;

            String mode = Config.SECURITY_SEARCH_EVALUATION;
            if (SEARCH_LUCENE.equals(mode)) {
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_MORE.equals(mode)) {
                result = runQueryAccessManagerMore(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_WINDOW.equals(mode)) {
                result = runQueryAccessManagerWindow(searchSession, luceneQuery, offset, limit);
            } else if (SEARCH_ACCESS_MANAGER_LIMITED.equals(mode)) {
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            } else {
                result = runQueryAccessManagerLimited(searchSession, luceneQuery, offset, limit);
            }

            HibernateUtil.commit(tx);
            SystemProfiling.log(expression + ", " + offset + ", " + limit, System.currentTimeMillis() - begin);
            log.trace("findBySimpleQuery.Time: {}", FormatUtil.formatMiliSeconds(System.currentTimeMillis() - begin));
            log.debug("findBySimpleQuery: {}", result);
            return result;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            HibernateUtil.rollback(tx);
            throw new ParseException(e.getMessage(), e);
        } catch (IOException | InvalidTokenOffsetsException | HibernateException e) {
            HibernateUtil.rollback(tx);
            throw new DatabaseException(e.getMessage(), e);
        } finally {
            HibernateUtil.close(session);
        }
    }

    /**
     * Utility to parse a Lucene Query from expression/field using configured analyzer.
     */
    public Query parseQuery(String expression, String field) throws ParseException, DatabaseException {
        log.debug("parseQuery({}, {})", expression, field);
        try {
            QueryParser parser = new QueryParser(field, analyzer);
            Query q = parser.parse(expression);
            log.debug("parseQuery: {}", q);
            return q;
        } catch (org.apache.lucene.queryparser.classic.ParseException e) {
            throw new ParseException(e.getMessage(), e);
        } catch (HibernateException e) {
            throw new DatabaseException(e.getMessage(), e);
        }
    }

    /* ------------------------------------------------------------------
     * Main query implementations (HS6 + AccessManager filters)
     * ------------------------------------------------------------------ */

    private NodeResultSet runQueryAccessManagerMore(SearchSession ss, Query luceneQuery, int offset, int limit)
            throws IOException, InvalidTokenOffsetsException, DatabaseException {
        log.debug("runQueryAccessManagerMore({}, {}, {}, {})", "SearchSession", luceneQuery, offset, limit);
        return filterAndCollect(ss, luceneQuery, offset, limit,
                /*pendingCountStrategy*/ PendingCountStrategy.MORE);
    }

    private NodeResultSet runQueryAccessManagerWindow(SearchSession ss, Query luceneQuery, int offset, int limit)
            throws IOException, InvalidTokenOffsetsException, DatabaseException {
        log.debug("runQueryAccessManagerWindow({}, {}, {}, {})", "SearchSession", luceneQuery, offset, limit);
        return filterAndCollect(ss, luceneQuery, offset, limit,
                /*pendingCountStrategy*/ PendingCountStrategy.WINDOW);
    }

    private NodeResultSet runQueryAccessManagerLimited(SearchSession ss, Query luceneQuery, int offset, int limit)
            throws IOException, InvalidTokenOffsetsException, DatabaseException {
        log.debug("runQueryAccessManagerLimited({}, {}, {}, {})", "SearchSession", luceneQuery, offset, limit);
        return filterAndCollect(ss, luceneQuery, offset, limit,
                /*pendingCountStrategy*/ PendingCountStrategy.LIMITED);
    }

    private enum PendingCountStrategy { MORE, WINDOW, LIMITED }

    /**
     * Core fetch/filter/highlight flow used by all strategies.
     */
    @SuppressWarnings({ "unchecked", "deprecation" })
    private NodeResultSet filterAndCollect(SearchSession ss, Query luceneQuery, int offset, int limit,
                                           PendingCountStrategy strategy)
            throws IOException, InvalidTokenOffsetsException, DatabaseException {

        // We'll search across these types together
        List<Class<?>> types = Arrays.asList(NodeDocument.class, NodeFolder.class, NodeMail.class);

        // Build a projector to get score + entity
        var search = ss.search(types);
        var resultWithProjection = search.extension(LuceneExtension.get())
                .select(f -> f.composite(
                    (Float score, Object entity) -> new AbstractMap.SimpleEntry<>(score, entity),
                    f.score(),
                    f.entity()
                ));

        // We fetch in chunks to honor offset semantics after access checks.
        final int pageSize = Math.max(limit * 2, 50); // heuristic to reduce round-trips
        int fetched = 0;
        int scannedReadable = 0;
        int totalCountForStrategy = 0;
        int page = 0;

        DbAccessManager am = SecurityHelper.getAccessManager();
        List<NodeQueryResult> out = new ArrayList<>();

        // Highlighter setup (works on text we load from entities)
        QueryScorer scorer = new QueryScorer(luceneQuery,
                NodeDocument.TEXT_FIELD /* used when highlighting document text */);
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<span class='highlight'>", "</span>");
        Highlighter highlighter = new Highlighter(formatter, scorer);
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, MAX_FRAGMENT_LEN));

        // First, skip readable docs until we reach the desired offset
        while (true) {
            var pageResult = resultWithProjection
                    .where(f -> f.fromLuceneQuery(luceneQuery))
                    .fetch(page * pageSize, pageSize);

            List<AbstractMap.SimpleEntry<Float, Object>> hits = pageResult.hits();
            if (hits.isEmpty()) break;

            for (AbstractMap.SimpleEntry<Float, Object> hit : hits) {
                Object entity = hit.getValue();
                if (!(entity instanceof NodeBase)) continue;
                NodeBase nBase = (NodeBase) entity;

                if (am.isGranted(nBase, Permission.READ)) {
                    if (scannedReadable < offset) {
                        scannedReadable++;
                    } else if (out.size() < limit) {
                        addResult(ss, out, highlighter, hit.getKey(), nBase);
                    } else {
                        
                        // We've filled the window; we may still need to count "pending" depending on strategy
                        switch (strategy) {
                            case MORE -> {
                                totalCountForStrategy = countMoreReadable(resultWithProjection.where(f -> f.fromLuceneQuery(luceneQuery)).toQuery(),page, hits.indexOf(hit), scannedReadable + out.size(), offset, limit, am);
                                NodeResultSet res = new NodeResultSet();
                                res.setResults(out);
                                res.setTotal(totalCountForStrategy);
                                return res;
                            }
                            case WINDOW -> {
                                totalCountForStrategy = countWindowReadable(resultWithProjection.where(f -> f.fromLuceneQuery(luceneQuery)).toQuery(),
                                        page, hits.indexOf(hit), scannedReadable + out.size(), offset, limit, am);
                                NodeResultSet res = new NodeResultSet();
                                res.setResults(out);
                                res.setTotal(totalCountForStrategy);
                                return res;
                            }
                            case LIMITED -> {
                                // We’ll compute total up to MAX_SEARCH_RESULTS readable (cap)
                                totalCountForStrategy = countLimitedReadable(resultWithProjection.where(f -> f.fromLuceneQuery(luceneQuery)).toQuery(),
                                        page, hits.indexOf(hit), scannedReadable + out.size(), offset, limit, am);
                                NodeResultSet res = new NodeResultSet();
                                res.setResults(out);
                                res.setTotal(totalCountForStrategy);
                                return res;
                            }
                        }
                    }
                }
            }

            // If we didn’t fill the result yet, continue to next page
            page++;
            fetched += hits.size();
        }

        // If we reach here, either we filled less than limit or exhausted results
        int totalReadable = scannedReadable + out.size();

        NodeResultSet res = new NodeResultSet();
        res.setResults(out);
        res.setTotal(totalReadable);
        return res;
    }

    /* -------------------- Pending count helpers -------------------- */

    private int countMoreReadable(
            SearchQuery<AbstractMap.SimpleEntry<Float, Object>> query,
            int currentPage,
            int currentIndexInPage,
            int alreadyCounted,
            int offset,
            int limit,
            DbAccessManager am
    ) {
        // Continue counting readable docs until we have (offset + limit + 1)
        return countUntil(query,
                currentPage, currentIndexInPage, alreadyCounted, offset + limit + 1, am);
    }

    private int countWindowReadable(
            SearchQuery<AbstractMap.SimpleEntry<Float, Object>> query,
            int currentPage,
            int currentIndexInPage,
            int alreadyCounted,
            int offset,
            int limit,
            DbAccessManager am
    ) {
        // Continue counting up to offset + limit*2
        return countUntil( query,
                currentPage, currentIndexInPage, alreadyCounted, offset + (limit * 2), am);
    }

    private int countLimitedReadable(
            SearchQuery<AbstractMap.SimpleEntry<Float, Object>> query,
            int currentPage,
            int currentIndexInPage,
            int alreadyCounted,
            int offset,
            int limit,
            DbAccessManager am
    ) {
        // Continue counting up to Config.MAX_SEARCH_RESULTS (cap)
        return countUntil( query,
                currentPage, currentIndexInPage, alreadyCounted, Config.MAX_SEARCH_RESULTS, am);
    }

    @SuppressWarnings("unchecked")
    private int countUntil(
            SearchQuery<AbstractMap.SimpleEntry<Float, Object>> query,
            int page,
            int indexInPage,
            int alreadyCountedReadable,
            int targetReadableCount,
            DbAccessManager am
    ) {
        
        final int pageSize = 200; // just for counting
        int readable = alreadyCountedReadable;
        int currentPage = page;
        int startIndex = indexInPage; // continue within current page

        while (readable < targetReadableCount) {
            
            
        var r = query.fetch(currentPage * pageSize, pageSize);
        List<AbstractMap.SimpleEntry<Float, Object>> hits = r.hits();


            if (hits.isEmpty()) break;

            for (int i = startIndex; i < hits.size(); i++) {
                Object entity = hits.get(i).getValue();
                if (entity instanceof NodeBase nBase) {
                    try {
                        if (am.isGranted(nBase, Permission.READ)) {
                            readable++;
                            if (readable >= targetReadableCount) break;
                        }
                    } catch (DatabaseException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            currentPage++;
            startIndex = 0;
        }

        return readable;
    }

    /* ------------------------------------------------------------------
     * Result assembling & highlighting
     * ------------------------------------------------------------------ */

    private void addResult(SearchSession ss, List<NodeQueryResult> results, Highlighter highlighter,
                           Float score, NodeBase nBase)
            throws IOException, InvalidTokenOffsetsException, DatabaseException {

        NodeQueryResult qr = new NodeQueryResult();
        NodeDocument nDocument = null;
        NodeMail nMail = null;
        String excerpt = null;

        if (nBase instanceof NodeDocument) {
            nDocument = (NodeDocument) nBase;
            if (NodeMailDAO.getInstance().itemExists(nDocument.getParent())) {
                log.debug("NODE DOCUMENT - ATTACHMENT");
                qr.setAttachment(nDocument);
            } else {
                log.debug("NODE DOCUMENT");
                qr.setDocument(nDocument);
            }
        } else if (nBase instanceof NodeFolder) {
            log.debug("NODE FOLDER");
            qr.setFolder((NodeFolder) nBase);
        } else if (nBase instanceof NodeMail) {
            log.debug("NODE MAIL");
            nMail = (NodeMail) nBase;
            qr.setMail(nMail);
        } else {
            log.warn("NODE UNKNOWN");
        }

        // Highlight on entity text/content if available
        if (nDocument != null && nDocument.getText() != null) {
            excerpt = highlighter.getBestFragment(analyzer, NodeDocument.TEXT_FIELD, nDocument.getText());
        } else if (nMail != null && nMail.getContent() != null) {
            excerpt = highlighter.getBestFragment(analyzer, NodeMail.CONTENT_FIELD, nMail.getContent());
        }

        log.debug("Result: SCORE({}), EXCERPT({}), ENTITY({})", score, excerpt, nBase);
        qr.setScore(score);
        qr.setExcerpt(FormatUtil.stripNonValidXMLCharacters(excerpt));

        if (qr.getDocument() != null) {
            NodeDocumentDAO.getInstance().initialize(qr.getDocument(), false);
            results.add(qr);
        } else if (qr.getFolder() != null) {
            NodeFolderDAO.getInstance().initialize(qr.getFolder());
            results.add(qr);
        } else if (qr.getMail() != null) {
            NodeMailDAO.getInstance().initialize(qr.getMail(), false);
            results.add(qr);
        } else if (qr.getAttachment() != null) {
            NodeDocumentDAO.getInstance().initialize(qr.getAttachment(), false);
            results.add(qr);
        }
    }

    /* ------------------------------------------------------------------
     * Folders-in-depth (same HQL, Hibernate 6 compatible)
     * ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    public List<String> findFoldersInDepth(String parentUuid) throws PathNotFoundException, DatabaseException {
        log.debug("findFoldersInDepth({})", parentUuid);
        Cache fldResultCache = (Cache) CacheProvider.getInstance().getCache(CACHE_SEARCH_FOLDERS_IN_DEPTH);
        String key = "searchFoldersInDepth:" + PrincipalUtils.getUser();
        Element elto = fldResultCache.get(key);
        List<String> ret;

        if (elto != null) {
            log.debug("Get '{}' from cache", key);
            ret = (ArrayList<String>) elto.getValue();
        } else {
            log.debug("Get '{}' from database", key);
            Session session = null;
            Transaction tx = null;

            try {
                long begin = System.currentTimeMillis();
                session = HibernateUtil.getSessionFactory().openSession();
                tx = session.beginTransaction();

                // Security Check
                NodeBase parentNode = session.get(NodeBase.class, parentUuid);
                SecurityHelper.checkRead(parentNode);

                ret = findFoldersInDepthHelper(session, parentUuid);
                HibernateUtil.commit(tx);

                // Disabled cache as per original TODO
                // fldResultCache.put(new Element(key, ret));

                SystemProfiling.log(parentUuid, System.currentTimeMillis() - begin);
                log.trace("findFoldersInDepth.Time: {}", FormatUtil.formatMiliSeconds(System.currentTimeMillis() - begin));
            } catch (PathNotFoundException | DatabaseException e) {
                HibernateUtil.rollback(tx);
                throw e;
            } catch (HibernateException e) {
                HibernateUtil.rollback(tx);
                throw new DatabaseException(e.getMessage(), e);
            } finally {
                HibernateUtil.close(session);
            }
        }

        log.debug("findFoldersInDepth: {}", ret);
        return ret;
    }

    @SuppressWarnings("unchecked")
    private List<String> findFoldersInDepthHelper(Session session, String parentUuid)
            throws HibernateException, DatabaseException {
        log.debug("findFoldersInDepthHelper({}, {})", "session", parentUuid);
        List<String> ret = new ArrayList<>();
        String hql = "from NodeFolder nf where nf.parent=:parent";
        org.hibernate.query.Query<NodeFolder> q = session.createQuery(hql, NodeFolder.class).setCacheable(true);
        q.setParameter("parent", parentUuid);
        List<NodeFolder> results = q.getResultList();

        DbAccessManager am = SecurityHelper.getAccessManager();
        for (NodeFolder node : results) {
            if (am.isGranted(node, Permission.READ)) {
                ret.add(node.getUuid());
                ret.addAll(findFoldersInDepthHelper(session, node.getUuid()));
            }
        }

        log.debug("findFoldersInDepthHelper: {}", ret);
        return ret;
    }

    /* ------------------------------------------------------------------
     * MoreLikeThis (HS6-friendly reimplementation)
     * ------------------------------------------------------------------ */

    public NodeResultSet moreLikeThis(String uuid, int maxResults)
            throws DatabaseException, PathNotFoundException {
        log.debug("moreLikeThis({}, {})", uuid, maxResults);

        Session session = null;
        Transaction tx = null;

        try {
            long begin = System.currentTimeMillis();
            session = HibernateUtil.getSessionFactory().openSession();
            tx = session.beginTransaction();
            SearchSession ss = Search.session(session);

            String sourceText = NodeDocumentDAO.getInstance().getExtractedText(session, uuid);

            NodeResultSet result = new NodeResultSet();
            if (sourceText != null && !sourceText.isEmpty()) {
                // Build a simpleQueryString against the "text" field and exclude the same uuid
                var search = ss.search(NodeDocument.class).select(f -> f.composite(
                        (Float score, NodeDocument doc) -> new AbstractMap.SimpleEntry<>(score, doc),
                        f.score(), f.entity()
                ));

                var sr = search.where(f -> f.bool(b -> {
                            b.must(f.simpleQueryString().fields(NodeDocument.TEXT_FIELD).matching(sourceText));
                            b.mustNot(f.match().field("uuid").matching(uuid));
                        }))
                        .fetch(0, maxResults);

                // Build highlighter against the sourceText-based query for consistent snippets
                try {
                    QueryParser parser = new QueryParser(NodeDocument.TEXT_FIELD, analyzer);
                    Query q = parser.parse(QueryParser.escape(sourceText));
                    QueryScorer scorer = new QueryScorer(q, NodeDocument.TEXT_FIELD);
                    SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<span class='highlight'>", "</span>");
                    Highlighter highlighter = new Highlighter(formatter, scorer);
                    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, MAX_FRAGMENT_LEN));

                    List<NodeQueryResult> out = new ArrayList<>();
                    for (AbstractMap.SimpleEntry<Float, NodeDocument> e : sr.hits()) {
                        addResult(ss, out, highlighter, e.getKey(), e.getValue());
                    }
                    result.setResults(out);
                    result.setTotal(out.size());
                } catch (Exception highlightEx) {
                    // Fallback without highlighting if something goes wrong
                    log.warn("Highlighter fallback in moreLikeThis: {}", highlightEx.getMessage());
                    List<NodeQueryResult> out = sr.hits().stream().map(e -> {
                        NodeQueryResult qr = new NodeQueryResult();
                        qr.setDocument(e.getValue());
                        qr.setScore(e.getKey());
                        return qr;
                    }).collect(Collectors.toList());
                    result.setResults(out);
                    result.setTotal(out.size());
                }

            } else {
                log.warn("Document has no extracted text: {}", uuid);
                result.setResults(Collections.emptyList());
                result.setTotal(0);
            }

            HibernateUtil.commit(tx);
            SystemProfiling.log(uuid + ", " + maxResults, System.currentTimeMillis() - begin);
            log.trace("moreLikeThis.Time: {}", FormatUtil.formatMiliSeconds(System.currentTimeMillis() - begin));
            log.debug("moreLikeThis: {}", result);
            return result;
        } catch (HibernateException e) {
            HibernateUtil.rollback(tx);
            throw new DatabaseException(e.getMessage(), e);
        } finally {
            HibernateUtil.close(session);
        }
    }

    /* ------------------------------------------------------------------
     * Terms extraction – analyze entity text (no direct IndexReader in HS6)
     * ------------------------------------------------------------------ */

    public List<String> getTerms(Class<?> entityType, String nodeUuid) throws IOException {
        List<String> terms = new ArrayList<>();
        Session session = null;

        try {
            session = HibernateUtil.getSessionFactory().openSession();

            if (NodeDocument.class.equals(entityType)) {
                NodeDocument doc = session.get(NodeDocument.class, nodeUuid);
                if (doc != null && doc.getText() != null) {
                    terms = analyzeToTerms(NodeDocument.TEXT_FIELD, doc.getText());
                }
            } else if (NodeMail.class.equals(entityType)) {
                NodeMail mail = session.get(NodeMail.class, nodeUuid);
                if (mail != null && mail.getContent() != null) {
                    terms = analyzeToTerms(NodeMail.CONTENT_FIELD, mail.getContent());
                }
            } else {
                // For folders or unknown types, return empty
                terms = Collections.emptyList();
            }
        } finally {
            HibernateUtil.close(session);
        }

        return terms;
    }

    private List<String> analyzeToTerms(String field, String text) throws IOException {
        List<String> out = new ArrayList<>();
        try (TokenStream ts = analyzer.tokenStream(field, new StringReader(text))) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                out.add(termAttr.toString());
            }
            ts.end();
        }
        return out;
    }
}