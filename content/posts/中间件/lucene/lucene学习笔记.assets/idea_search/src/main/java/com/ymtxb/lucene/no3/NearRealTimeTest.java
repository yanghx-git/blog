package com.ymtxb.lucene.no3;

import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: NearRealTimeTest
 * Description:
 * date: 2022/3/5 12:51
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class NearRealTimeTest extends TestCase {
    public void testNearRealTime() throws IOException {
        Directory dir = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        IndexWriter writer = new IndexWriter(dir, config);

        for(int i = 0; i < 10; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", "" + i, Field.Store.NO));
            doc.add(new TextField("text", "This is text", Field.Store.NO));
            writer.addDocument(doc);
        }
        // writer.commit() writer.close()

        DirectoryReader reader= DirectoryReader.open(writer);
        IndexSearcher searcher = new IndexSearcher(reader);

        Query query = new TermQuery(new Term("text", "text"));
        TopDocs docs = searcher.search(query, 1);
        assertEquals(10, docs.totalHits);

        writer.deleteDocuments(new Term("id", "7"));

        Document doc = new Document();
        doc.add(new StringField("id", "11", Field.Store.NO));
        doc.add(new TextField("text", "This is bbb", Field.Store.NO));
        writer.addDocument(doc);

        IndexReader newReader = DirectoryReader.openIfChanged(reader);
        assertFalse(reader == newReader);
        reader.close();
        searcher = new IndexSearcher(newReader);

        TopDocs hits = searcher.search(query, 10);
        assertEquals(9, hits.totalHits);
        query = new TermQuery(new Term("text", "bbb"));
        hits = searcher.search(query, 1);
        assertEquals(1, hits.totalHits);

        newReader.close();
        writer.close();
    }
}
