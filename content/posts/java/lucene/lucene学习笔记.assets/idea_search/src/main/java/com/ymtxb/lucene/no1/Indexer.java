package com.ymtxb.lucene.no1;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.*;

/**
 * ClassName: Indexer
 * Description:
 * date: 2022/2/16 10:27
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class Indexer {
    private IndexWriter writer;

    public static void main(String[] args) throws IOException {
        if(args.length != 2) {
            throw new IllegalArgumentException("Usage: java " + Indexer.class.getName() + " <index dir> <data dir>");
        }

        String indexDir = args[0];
        String dataDir = args[1];

        long start = System.currentTimeMillis();
        Indexer indexer = new Indexer(indexDir);
        int numIndexed;
        try {
            numIndexed = indexer.index(dataDir, new TextFilesFilter());
        } finally {
            indexer.close();
        }

        long end = System.currentTimeMillis();

        System.out.println("Indexing " + numIndexed + " files took " + (end - start) + " milliseconds");
    }

    public Indexer(String indexDir) throws IOException {

        Directory dir = FSDirectory.open(new File(indexDir).toPath());
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwConfig = new IndexWriterConfig(analyzer);
        writer = new IndexWriter(dir, iwConfig);
    }

    public int index(String dataDir, FileFilter filter) throws IOException {
        File[] files = new File(dataDir).listFiles();

        for(File f : files) {
            if(!f.isDirectory() && !f.isHidden() && f.exists() && f.canRead() &&
                    (filter == null || filter.accept(f))) {
                indexFile(f);
            }
        }

        return writer.numDocs();
    }

    protected Document getDocument(File f) throws IOException {
        //取文件名
        String file1Name=f.getName();
        //文件的路径
        String path=f.getPath();
        //文件的内容
        String fileContext = FileUtils.readFileToString(f, "utf-8");

        //创建Field
        //参数1:域的名称,参数2：域的内容，参数3：是否储存
        Field fieldName=new TextField("name",file1Name, Field.Store.YES);
        Field fieldPath=new StoredField("path",path);
        Field fieldContext=new TextField("context",fileContext, Field.Store.YES);


        //创建文档对象
        Document document=new Document();

//        4.向文档对象中添加域
        document.add(fieldName);
        document.add(fieldPath);
        document.add(fieldContext);

        return document;
    }

    private void indexFile(File f) throws IOException {
        System.out.println("Indexing " + f.getCanonicalPath());
        Document doc = getDocument(f);
        writer.addDocument(doc);
    }



    public void close() throws IOException {
        writer.close();
    }

    private static class TextFilesFilter implements FileFilter {

        @Override
        public boolean accept(File pathname) {
            return pathname.getName().toLowerCase().endsWith(".txt");
        }
    }
}
