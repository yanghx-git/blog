package com.ymtxb.lucene.no5;

import junit.framework.TestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;

/**
 * ClassName: MultiSearcherTest
 * Description:
 * date: 2022/5/12 15:53
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class MultiReaderTest extends TestCase {
    private Analyzer analyzer = new WhitespaceAnalyzer();
    Directory aDirectory = new RAMDirectory();
    Directory bDirectory = new RAMDirectory();
    IndexWriterConfig aIndexWriterConfig = new IndexWriterConfig(analyzer);
    IndexWriterConfig bIndexWriterConfig = new IndexWriterConfig(analyzer);
    IndexWriter aIndexWriter;
    IndexWriter bIndexWriter;

    public void setUp() throws IOException {
        String[] animals = {"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
        aIndexWriter = new IndexWriter(aDirectory, aIndexWriterConfig);
        bIndexWriter = new IndexWriter(bDirectory, bIndexWriterConfig);
        for (int i = 0; i < animals.length; i++) {
            Document document = new Document();
            String animal = animals[i];
            document.add(new StringField("animal", animal, Field.Store.YES));
            if (animal.charAt(0) < 'n') {
                aIndexWriter.addDocument(document);
            } else {
                bIndexWriter.addDocument(document);
            }
        }
        aIndexWriter.commit();
        bIndexWriter.commit();
    }

    public void setDown() {
        try {
            if (aIndexWriter != null && aIndexWriter.isOpen()) {
                aIndexWriter.close();
            }
            if (bIndexWriter != null && bIndexWriter.isOpen()) {
                bIndexWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public void testMultiReader() throws IOException {
        IndexReader aIndexReader = DirectoryReader.open(aDirectory);
        IndexReader bIndexReader = DirectoryReader.open(bDirectory);
        MultiReader multiReader = new MultiReader(aIndexReader, bIndexReader);
        IndexSearcher indexSearcher = new IndexSearcher(multiReader);
        TopDocs animal = indexSearcher.search(new TermRangeQuery("animal", new BytesRef("h"), new BytesRef("q"), true, true), 10);
        assertEquals(10, animal.totalHits);
        ScoreDoc[] scoreDocs = animal.scoreDocs;
        for (ScoreDoc sd : scoreDocs) {
            System.out.println(indexSearcher.doc(sd.doc));
        }
    }

}
