package com.ymtxb.lucene.no4.synonym;

import com.ymtxb.lucene.common.TestUtil;
import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: SynonymAnalyzerTest
 * Description:
 * date: 2022/4/1 12:34
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SynonymAnalyzerTest extends TestCase {
    private IndexSearcher searcher;
    private static SynonymAnalyzer synonymAnalyzer = new SynonymAnalyzer(new SmartSynonymEngine());

    public void setUp() throws IOException {
        RAMDirectory directory = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(synonymAnalyzer);
        IndexWriter writer = new IndexWriter(directory, config);

        Document doc = new Document();
        doc.add(new TextField("content",
                "The quick brown fox jumps over the lazy dog", Field.Store.YES));
        writer.addDocument(doc);
        writer.close();

        IndexReader reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
    }

    public void testSearchByAPI() throws IOException {
/*        TermQuery tq = new TermQuery(new Term("content", "hops"));
        assertEquals(1, TestUtil.hitCount(searcher, tq));*/
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        builder.add(new Term("content", "fox"));
        builder.add(new Term("content", "hops"));
        assertEquals(1, TestUtil.hitCount(searcher, builder.build()));
    }

    public void testWithQueryParser() throws ParseException, IOException {
        Query query = new QueryParser("content", synonymAnalyzer).parse("\"fox jumps\"");
        assertEquals(1, TestUtil.hitCount(searcher, query));
        System.out.println("With SynonymAnalyzer, \"fox jumps\" parses to " + query.toString("content"));

        query = new QueryParser("content", new StandardAnalyzer()).parse("\"fox jumps\"");
        assertEquals(1, TestUtil.hitCount(searcher, query));
        System.out.println("With StandardAnalyzer, \"fox jumps\" parses to " + query.toString("content"));
    }
}
