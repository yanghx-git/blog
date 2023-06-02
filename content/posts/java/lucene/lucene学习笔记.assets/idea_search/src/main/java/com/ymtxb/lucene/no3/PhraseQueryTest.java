package com.ymtxb.lucene.no3;

import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: PhraseQueryTest
 * Description:
 * date: 2022/3/6 11:54
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class PhraseQueryTest extends TestCase {
    private Directory dir;
    private IndexSearcher searcher;

    protected void setUp() throws IOException {
        dir = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        IndexWriter writer = new IndexWriter(dir, config);

        Document doc = new Document();
        doc.add(new TextField("field", "the quick brown fox jumped over the lazy", Field.Store.YES));
        writer.addDocument(doc);
        writer.close();

        DirectoryReader reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    protected void tearDown() throws IOException {
        dir.close();
    }

    private boolean matched(String[] phrase, int slop) throws IOException {
        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        for(String word : phrase) {
            builder.add(new Term("field", word));
        }
        builder.setSlop(slop);

        TopDocs matches = searcher.search(builder.build(), 10);
        return matches.totalHits > 0;
    }

    public void testSlopComparison() throws IOException {
        String[] phrase = new String[]{"quick", "fox"};
        assertFalse("exact phrase not found", matched(phrase, 0));
        assertTrue("close enough", matched(phrase, 1));
    }

    public void testReverse() throws IOException {
        String[] phrase = new String[] {"fox", "quick"};
        assertFalse("a",matched(phrase, 2));
        assertTrue("b", matched(phrase, 3));
    }

    // 复合短语查询
    public void testMultiple() throws IOException {
        assertFalse("a", matched(new String[]{"quick", "jumped", "lazy"}, 3));
        assertTrue("b", matched(new String[]{"quick", "jumped", "lazy"}, 4));

        assertFalse("c", matched(new String[]{"lazy", "jumped", "quick"}, 7));
        assertTrue("d", matched(new String[]{"lazy", "jumped", "quick"}, 8));
    }
}
