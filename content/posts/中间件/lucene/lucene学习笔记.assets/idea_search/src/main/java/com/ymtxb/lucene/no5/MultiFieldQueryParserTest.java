package com.ymtxb.lucene.no5;

import com.ymtxb.lucene.common.TestUtil;
import junit.framework.TestCase;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
 * ClassName: MultiFieldQueryParserTest
 * Description:
 * date: 2022/5/11 16:15
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class MultiFieldQueryParserTest extends TestCase {

    public void testDefaultOperator() throws ParseException, IOException {
        Query query = new MultiFieldQueryParser(new String[]{"title", "subject"},
                new StandardAnalyzer()).parse("development");

        System.out.println(query);

        Directory directory = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);

        TopDocs hits = searcher.search(query, 10);

        assertTrue(TestUtil.hitsIncludeTitle(searcher, hits, "Ant in Action"));

        assertTrue(TestUtil.hitsIncludeTitle(searcher, hits, "Extreme Programming Explained"));

        directory.close();
    }

    public void testSpecifiedOperator() throws ParseException, IOException {
        Query query = MultiFieldQueryParser.parse("lucene", new String[] {"title", "subject"},
                new BooleanClause.Occur[] {BooleanClause.Occur.MUST, BooleanClause.Occur.MUST},
                new SimpleAnalyzer());

        System.out.println(query);

        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        TopDocs hits = searcher.search(query, 10);
        assertTrue(TestUtil.hitsIncludeTitle(searcher, hits, "Lucene in Action, Second Edition"));
        assertEquals("one and only one", 1, hits.scoreDocs.length);
        dir.close();
    }
}
