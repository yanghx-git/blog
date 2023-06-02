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
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: FuzzyQueryTest
 * Description:
 * date: 2022/3/6 14:57
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class FuzzyQueryTest extends TestCase {
    private Directory dir;
    private IndexSearcher searcher;

    protected void setUp() throws IOException {
        dir = new RAMDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        IndexWriter writer = new IndexWriter(dir, config);

        Field[] fields = new Field[]{new TextField("contents", "fuzzy", Field.Store.YES),
                new TextField("contents", "wuzzy", Field.Store.YES)};
        for(Field f: fields) {
            Document doc = new Document();
            doc.add(f);
            writer.addDocument(doc);
        }
        writer.close();

        DirectoryReader reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    protected void tearDown() throws IOException {
        dir.close();
    }

    public void testFuzzy() throws IOException {
        Query query = new FuzzyQuery(new Term("contents", "wuzza"));
        TopDocs matches = searcher.search(query, 10);
        assertEquals("a", 2, matches.totalHits);

        assertTrue("b", matches.scoreDocs[0].score != matches.scoreDocs[1].score);

        Document doc = searcher.doc(matches.scoreDocs[0].doc);
        assertEquals("c", "wuzzy", doc.get("contents"));
    }
}
