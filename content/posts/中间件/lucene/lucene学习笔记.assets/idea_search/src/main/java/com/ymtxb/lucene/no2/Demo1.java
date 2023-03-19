package com.ymtxb.lucene.no2;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;

/**
 * ClassName: DemoAddDocument
 * Description:
 * date: 2022/2/26 14:23
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class Demo1 {


    public static void main(String[] args) throws IOException {
        Demo1 demo = new Demo1();
        demo.testAddData();
//        demo.testDeleteBeforeOptimize();

//        demo.testDeleteAfterOptimize();
        demo.testUpdate();
    }

    // 待加入文档的测试数据..
    private String[] ids = {"1", "2"};
    private String[] unindexed = {"Netherlands","Italy"};
    private String[] unstored = {"Amsterdam has lots of briges", "Venice has lots of canals"};
    private String[] text = {"Amsterdam", "Venice"};

    private Directory directory;


    public Demo1() {
        // 基于内存的索引目录，IO非常比较优越（注意：程序停止后，就消失了）
        this.directory = new RAMDirectory();
    }

    public void testAddData() throws IOException {
        IndexWriter writer = getWriter();
        for(int i = 0; i < ids.length; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", ids[i], Field.Store.YES));
            doc.add(new StoredField("country", unindexed[i]));
            doc.add(new TextField("contents", unstored[i], Field.Store.NO));
            doc.add(new TextField("city", text[i], Field.Store.YES));
            writer.addDocument(doc);
        }
        // 关闭writer或者显示调用writer.commit()都会将缓冲区内的索引数据落盘。
        writer.close();
    }

    public void testSearcher(String fieldName, String val) throws IOException {
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);

        Term t = new Term(fieldName, val);
        Query query = new TermQuery(t);
        int hit = searcher.search(query, 1).totalHits;
        System.out.println("searcher hits:" + hit);
    }

    private IndexWriter getWriter() throws IOException {
        // 参数：new WhitespaceAnalyzer()  按照“空格”来进行分词的分析器
        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());
        return new IndexWriter(directory, config);
    }

    public void testIndexWriter() throws IOException {
        IndexWriter writer = getWriter();
        System.out.println("索引中管理的文档数量：" + writer.numDocs() + ", 程序写入的文档数量：" + ids.length);
        writer.close();
    }

    public void testIndexReader() throws IOException {
        IndexReader reader = DirectoryReader.open(directory);
        System.out.println("索引中管理的文档数量：" + reader.maxDoc() + ", 程序写入的文档数量：" + ids.length);
        System.out.println("索引中管理的文档数量：" + reader.numDocs() + ", 程序写入的文档数量：" + ids.length);
        reader.close();
    }


    // 删除相关
    public void testDeleteBeforeOptimize() throws IOException {
        IndexWriter writer = getWriter();
        System.out.println("索引中管理的文档数量：" + writer.numDocs() + ", 程序写入的文档数量：" + ids.length);
        // 删除id=1的文档
        writer.deleteDocuments(new Term("id", "1"));
        writer.commit();
        System.out.println("----------------------------------------------");
        System.out.println("索引中是否包含删除标记：" + writer.hasDeletions());
        System.out.println("索引中管理的文档数量<writer.maxDoc()>：" + writer.maxDoc() + ", 程序写入的文档数量：" + ids.length);
        System.out.println("索引中管理的文档数量<writer.numDocs()>：" + writer.numDocs() + ", 程序写入的文档数量：" + ids.length);
        writer.close();
    }

    public void testDeleteAfterOptimize() throws IOException {
        IndexWriter writer = getWriter();
        System.out.println("索引中管理的文档数量：" + writer.numDocs() + ", 程序写入的文档数量：" + ids.length);
        writer.deleteDocuments(new Term("id", "1"));
        writer.forceMerge(1);
        writer.commit();
        System.out.println("索引合并完成");
        System.out.println("----------------------------------------------");
        System.out.println("索引中是否包含删除标记：" + writer.hasDeletions());
        System.out.println("索引中管理的文档数量<writer.maxDoc()>：" + writer.maxDoc() + ", 程序写入的文档数量：" + ids.length);
        System.out.println("索引中管理的文档数量<writer.numDocs()>：" + writer.numDocs() + ", 程序写入的文档数量：" + ids.length);
        writer.close();
    }

    // 更新相关
    public void testUpdate() throws IOException {
        testSearcher("city", "Amsterdam");
        IndexWriter writer = getWriter();

        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));
        doc.add(new StoredField("country", "Netherlands"));
        doc.add(new TextField("contents", "Den Haag has a lot of museums", Field.Store.NO));
        doc.add(new TextField("city", "Den Haag", Field.Store.YES));

        writer.updateDocument(new Term("id", "1"), doc);
        writer.close();

        testSearcher("city", "Amsterdam");
        testSearcher("city", "Haag");

    }

}
