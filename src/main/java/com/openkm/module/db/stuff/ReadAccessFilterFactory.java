package com.openkm.module.db.stuff;

import com.openkm.core.Config;
import com.openkm.dao.SearchDAO;
import com.openkm.spring.PrincipalUtils;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.index.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;


public class ReadAccessFilterFactory {

    private static final Logger log = LoggerFactory.getLogger(SearchDAO.class);


    public static Query buildQuery() {
        log.debug("buildQuery()");

        if (!SearchDAO.SEARCH_LUCENE.equals(Config.SECURITY_SEARCH_EVALUATION)) {
            return null; // Security filtering disabled
        }

        String user = PrincipalUtils.getUser();
        Set<String> roles = PrincipalUtils.getRoles();

        // Admins or system users have unrestricted access
        if (roles.contains(Config.DEFAULT_ADMIN_ROLE)
                || Config.ADMIN_USER.equals(user)
                || Config.SYSTEM_USER.equals(user)) {
            return null;
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term("userPermission", user)), BooleanClause.Occur.SHOULD);

        for (String role : roles) {
            builder.add(new TermQuery(new Term("rolePermission", role)), BooleanClause.Occur.SHOULD);
        }

        Query query = builder.build();
        log.info("Permission filter query: {}", query);
        return query;
    }
}
