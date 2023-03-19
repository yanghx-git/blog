package com.ymtxb.lucene.no3;

import com.ymtxb.lucene.common.TestUtil;
import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
 * ClassName: QueryParserTest
 * Description:
 * date: 2022/3/6 15:32
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class QueryParserTest extends TestCase {

    public void testToString() {
        BooleanQuery booleanQuery = new BooleanQuery.Builder()
                .add(new FuzzyQuery(new Term("field", "kountry")), BooleanClause.Occur.MUST)
                .add(new TermQuery(new Term("title", "western")), BooleanClause.Occur.SHOULD)
                .build();

        System.out.println(booleanQuery.toString());
    }


    public void testTermQuery() throws ParseException {
        QueryParser parser = new QueryParser("contents", new StandardAnalyzer());
        Query query = parser.parse("computers");
        assertTrue(query instanceof TermQuery);
    }

    public void testTermRangeQuery() throws ParseException, IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        QueryParser parser = new QueryParser("subject", new StandardAnalyzer());
        Query query = parser.parse("title:[Q TO V]");

        System.out.println(query);
        assertTrue(query instanceof TermRangeQuery);

        TopDocs matches = searcher.search(query, 10);
        assertTrue(TestUtil.hitsIncludeTitle(searcher, matches, "Tapestry in Action"));


        query = parser.parse("title2: {Q TO \"Tapestry in Action\"}");
        matches = searcher.search(query, 10);
        assertFalse(TestUtil.hitsIncludeTitle(searcher, matches, "Tapestry in Action"));
    }

    public void testLowercasing() throws ParseException {
        QueryParser parser = new QueryParser("field", new StandardAnalyzer());
        Query query = parser.parse("PrefixQuery*");
        assertEquals("prefixquery*", query.toString("field"));

        parser.setLowercaseExpandedTerms(false);
        query = parser.parse("PrefixQuery*");
        assertEquals("PrefixQuery*", query.toString("field"));
    }

    public void testPhraseQuery() throws ParseException {
        QueryParser parser = new QueryParser("field", new StandardAnalyzer());
        Query query = parser.parse("\"This is Some Phrase\"");

        assertTrue(query instanceof PhraseQuery);
        assertEquals("\"? ? some phrase\"", query.toString("field"));

        query = parser.parse("\"term\"");
        assertTrue(query instanceof TermQuery);
    }

    public void testSlop() throws ParseException {
        QueryParser parser = new QueryParser("field", new StandardAnalyzer());
        Query query = parser.parse("\"exact phrase\"");
        assertEquals("\"exact phrase\"", query.toString("field"));

        parser.setPhraseSlop(5);
        query = parser.parse("\"exact phrase\"");
        assertEquals("\"exact phrase\"~5", query.toString("field"));

        parser.setPhraseSlop(0);
        query = parser.parse("\"exact phrase\"~5");
        assertEquals("\"exact phrase\"~5", query.toString("field"));
    }

    public void testFuzzyQuery() throws ParseException {
        QueryParser parser = new QueryParser("subject", new StandardAnalyzer());
        Query query = parser.parse("kountry~");
        assertTrue(query instanceof FuzzyQuery);
    }

    public void testMatchAllDocsQuery() throws ParseException {
        QueryParser parser = new QueryParser("field", new StandardAnalyzer());
        Query query = parser.parse("*:*");
        assertTrue(query instanceof MatchAllDocsQuery);
    }

}
