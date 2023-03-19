package com.ymtxb.lucene.no3;

import com.ymtxb.lucene.common.TestUtil;
import junit.framework.TestCase;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * ClassName: BasicSearchingTest
 * Description:
 * date: 2022/3/4 17:35
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class BasicSearchingTest extends TestCase {

    public void testTerm() throws Exception {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        Term t = new Term("subject", "ant");
        Query query = new TermQuery(t);
        TopDocs docs = searcher.search(query, 10);
        assertEquals("Ant in Action",
                1, docs.totalHits);

        t = new Term("subject", "junit");
        docs = searcher.search(new TermQuery(t), 10);
        assertEquals("Ant in Action, " +
                        "JUnit in Action, Second Edition",
                2, docs.totalHits);
        dir.close();
    }

    public void testQueryParser() throws IOException, ParseException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        QueryParser parser = new QueryParser("contents", new SimpleAnalyzer());
        Query query = parser.parse("+JUNIT +ANT -MOCK");
        TopDocs docs = searcher.search(query, 10);
        assertEquals(1, docs.totalHits);
        Document d = searcher.doc(docs.scoreDocs[0].doc);
        assertEquals("Ant in Action", d.get("title"));

        query = parser.parse("mock OR junit");
        docs = searcher.search(query, 10);
        assertEquals("Ant in Action, " + "Junit in Action, Second Edition",
                2, docs.totalHits);

        reader.close();
        dir.close();
    }

    public void testKeyword() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        Term t = new Term("isbn", "9781935182023");
        Query query = new TermQuery(t);
        TopDocs docs = searcher.search(query, 10);
        assertEquals("Junit in Action, Second Edition", 1, docs.totalHits);

        reader.close();
        dir.close();
    }

    public void testTermRangeQuery() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        TermRangeQuery query =
                new TermRangeQuery("title2", new BytesRef("d"), new BytesRef("j"), true, true);

        TopDocs matches = searcher.search(query, 10);

        for(ScoreDoc doc : matches.scoreDocs) {
            Document document = searcher.doc(doc.doc);
            System.out.println(document.get("title2"));
        }
        assertEquals(3, matches.totalHits);
        reader.close();
        dir.close();
    }


    public void testPointRangeQuery() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        // pub data of TTC was September 2006
        Query query = IntPoint.newRangeQuery("pubmonth", 200605, 200609);
        TopDocs matches = searcher.search(query, 10);
        assertEquals(1, matches.totalHits);
        reader.close();
        dir.close();
    }

    public void testPointRangeQuery2() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        // pub data of TTC was September 2006
        Query query = IntPoint.newRangeQuery("pubmonth", Math.addExact(200605, 1), Math.addExact(200609, -1));
        TopDocs matches = searcher.search(query, 10);
        assertEquals(0, matches.totalHits);
        reader.close();
        dir.close();
    }

    public void testPrefix() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        Term term = new Term("category", "/technology/computers/programming");
        PrefixQuery query = new PrefixQuery(term);

        TopDocs matches = searcher.search(query, 10);
        int programmingAndBelow = matches.totalHits;

        int justProgramming = searcher.search(new TermQuery(term), 10).totalHits;

        assertTrue(programmingAndBelow > justProgramming);
        reader.close();
        dir.close();
    }

    public void testBooleanQuery() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        TermQuery searchingBooks = new TermQuery(new Term("subject", "search"));
        Query books2010 = IntPoint.newRangeQuery("pubmonth", 201001, 201012);

        BooleanQuery booleanQuery = new BooleanQuery.Builder()
                .add(searchingBooks, BooleanClause.Occur.MUST)
                .add(books2010, BooleanClause.Occur.MUST).build();

        TopDocs matches = searcher.search(booleanQuery, 10);

        assertTrue(TestUtil.hitsIncludeTitle(searcher, matches, "Lucene in Action, Second Edition"));

        reader.close();
        dir.close();
    }

    public void testOr() throws IOException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        TermQuery methodologyBooks = new TermQuery(
                new Term("category", "/technology/computers/programming/methodology"));

        TermQuery easternPhilosophyBooks = new TermQuery(
                new Term("category", "/philosophy/eastern"));

        BooleanQuery booleanQuery = new BooleanQuery.Builder()
                .add(methodologyBooks, BooleanClause.Occur.SHOULD)
                .add(easternPhilosophyBooks, BooleanClause.Occur.SHOULD).build();

        TopDocs matches = searcher.search(booleanQuery, 10);
        System.out.println("or = " + booleanQuery);

        assertTrue(TestUtil.hitsIncludeTitle(searcher, matches, "Extreme Programming Explained"));

        assertTrue(TestUtil.hitsIncludeTitle(searcher, matches, "Tao Te Ching \u9053\u5FB7\u7D93"));

        reader.close();
        dir.close();
    }
}
