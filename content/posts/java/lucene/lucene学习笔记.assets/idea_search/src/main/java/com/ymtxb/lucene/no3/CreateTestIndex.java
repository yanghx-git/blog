package com.ymtxb.lucene.no3;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * ClassName: CreateTestIndex
 * Description:
 * date: 2022/3/4 17:06
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class CreateTestIndex {
    public static void main(String[] args) throws IOException {
        String dataDir = args[0];
        String indexDir = args[1];
        List<File> results = new ArrayList<File>();

        findFiles(results, new File(dataDir));
        System.out.println(results.size() + " books to index");
        Directory dir = FSDirectory.open(new File(indexDir).toPath());

        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());

        IndexWriter w = new IndexWriter(dir,config);

        for(File file : results) {
            Document doc = getDocument(dataDir, file);
            w.addDocument(doc);
        }
        w.close();
        dir.close();
    }


    public static Document getDocument(String rootDir, File file) throws IOException {
        Properties props = new Properties();
        props.load(new FileInputStream(file));

        Document doc = new Document();

        // category comes from relative path below the base directory
        String category = file.getParent().substring(rootDir.length());
        category = category.replace(File.separatorChar, '/');

        String isbn = props.getProperty("isbn");
        String title = props.getProperty("title");
        String author = props.getProperty("author");
        String url = props.getProperty("url");
        String subject = props.getProperty("subject");
        String pubmonth = props.getProperty("pubmonth");

        System.out.println(title + "\n" + author + "\n" + subject + "\n" + pubmonth + "\n" + category + "\n---------");


        doc.add(new StringField("isbn", isbn, Field.Store.YES));

        doc.add(new SortedDocValuesField("category", new BytesRef(category.getBytes())));
        doc.add(new StringField("category", category, Field.Store.YES));
        doc.add(new SortedDocValuesField("title", new BytesRef(title.getBytes())));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new SortedDocValuesField("title2", new BytesRef(title.toLowerCase().getBytes())));
        doc.add(new StringField("title2", title.toLowerCase(), Field.Store.YES));

        // split multiple authors into unique field instances
        String[] authors = author.split(",");
        for (String a : authors) {
            doc.add(new StringField("author", a, Field.Store.YES));
        }

        doc.add(new StringField("url", url, Field.Store.YES));
        doc.add(new TextField("subject", subject, Field.Store.YES));

        doc.add(new NumericDocValuesField("pubmonth", Integer.parseInt(pubmonth)));
        doc.add(new StoredField("pubmonth", Integer.parseInt(pubmonth)));

        Date d;
        try {
            d = DateTools.stringToDate(pubmonth);
        } catch (ParseException pe) {
            throw new RuntimeException(pe);
        }

        doc.add(new NumericDocValuesField("pubmonthAsDay", (int)(d.getTime()/(1000*3600*24))));
        doc.add(new StoredField("pubmonthAsDay", (int)(d.getTime()/(1000*3600*24))));
        for(String text : new String[] {title, subject, author, category}) {
            doc.add(new TextField("contents", text, Field.Store.NO));
        }
        return doc;
    }

    private static void findFiles(List<File> result, File dir) {
        for(File file : dir.listFiles()) {
            if (file.getName().endsWith(".properties")) {
                result.add(file);
            } else if (file.isDirectory()) {
                findFiles(result, file);
            }
        }
    }


}
