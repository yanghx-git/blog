package com.ymtxb.lucene.no5;

import junit.framework.TestCase;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: MultiPhraseQueryTest
 * Description:
 * date: 2022/5/11 15:38
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class MultiPhraseQueryTest extends TestCase {
    private IndexSearcher searcher;

    protected void setUp() throws IOException {
        Directory directory = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());
        IndexWriter writer = new IndexWriter(directory,config);

        Document doc1 = new Document();
        doc1.add(new TextField("field",
                "the quick brown fox jumped over the lazy dog", Field.Store.YES));
        writer.addDocument(doc1);

        Document doc2 = new Document();
        doc2.add(new TextField("field",
                "the fast fox hopped over the hound", Field.Store.YES));
        writer.addDocument(doc2);
        writer.close();

        IndexReader reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
    }

    public void testBasic() throws IOException {
        MultiPhraseQuery.Builder builder = new MultiPhraseQuery.Builder();

        builder.add(new Term[]{
                new Term("field", "quick"),
                new Term("field", "fast")
        });

        builder.add(new Term("field", "fox"));
        MultiPhraseQuery query = builder.build();

        System.out.println(query);

        TopDocs hits = searcher.search(query, 10);
        assertEquals("fast fox match", 1, hits.totalHits);

        builder.setSlop(1);
        query = builder.build();

        hits = searcher.search(query, 10);
        assertEquals("both match", 2, hits.totalHits);
    }

    public void testAgainstOR() throws IOException {
        PhraseQuery.Builder builder1 = new PhraseQuery.Builder();
        builder1.add(new Term("field", "quick"));
        builder1.add(new Term("field", "fox"));
        builder1.setSlop(1);
        PhraseQuery quickFox = builder1.build();

        PhraseQuery.Builder builder2 = new PhraseQuery.Builder();
        builder2.add(new Term("field", "fast"));
        builder2.add(new Term("field", "fox"));
        PhraseQuery fastFox = builder2.build();

        BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
        booleanQueryBuilder.add(quickFox, BooleanClause.Occur.SHOULD);
        booleanQueryBuilder.add(fastFox, BooleanClause.Occur.SHOULD);

        TopDocs hits = searcher.search(booleanQueryBuilder.build(), 10);
        assertEquals(2, hits.totalHits);
    }
}
