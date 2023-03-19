package com.ymtxb.lucene.open;

import com.ymtxb.lucene.no1.Indexer;
import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;

/**
 * ClassName: CreateIndexTest
 * Description:
 * date: 22-3-19 上午9:01
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class CreateIndexTest {
    public static void main(String[] args) throws IOException {
        if(args.length != 2) {
            throw new IllegalArgumentException("Usage: java " + Indexer.class.getName() + " <index dir> <data dir>");
        }
        // 项目data目录: data
        String dataDir = args[0];
        // 公开课索引存储位置：open-class-index
        String indexDir = args[1];
        // 创建“索引位置”对应的目录对象，后面的写操作，依赖该目录对象
        Directory dir = FSDirectory.open(new File(indexDir).toPath());

        // 索引写入器 依赖 的配置信息对象，构造方法中的StandardAnalyzer 是一个标准的分析器
        // 分析器最主要的作用：索引阶段中，分析用户的“文档”，分析出“文档”都由哪些“词（Term）”组成。当然分析器不止于此...
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        // 注意：设置为非复合存储索引，如果不设置的话，索引的数据将被打包进 .cfs .cfe .si文件内。
        config.setUseCompoundFile(false);

        IndexWriter w = new IndexWriter(dir,config);
        // 获取出项目下 “data” 目录内的全部文件
        File[] files = new File(dataDir).listFiles();

        // 循环处理每一个文件
        for(File f : files) {
            // 条件1：f不是目录
            // 条件2：f不是隐藏文件
            // 条件3：f真实存在
            // 条件4：f可以读取
            if(!f.isDirectory() && !f.isHidden() && f.exists() && f.canRead()) {
                //取文件名
                String fileName=f.getName();
                //文件的路径
                String path=f.getPath();
                //文件的内容
                String contents = FileUtils.readFileToString(f, "utf-8");

                // Document 是什么？文档，Lucene领域内的概念，咱们需要把待索引管理的数据实例化成文档，然后Lucene基于Document来完成索引工作。
                Document doc = new Document();

                // Field 是什么？域，Lucene领域内的概念，类似于数据库中的字段。一个文档可以包含一个或多个域，咱们的数据必须包装在“域”对象内，再加入到文档中。
                // Field 有很多子类，常用的有 TextField StringField StoredField IntPoint LongPoint...
                // TextField和StringField的区别是什么？
                // TextField 管理的内容在索引期间 会被 分析器给分析，生成一系列“语汇单元（Term）”，即一系列词。
                // StringField 管理的内容在索引期间被当做一个整体，即StringField管理的内容 被视为 一个 “语汇单元（Term）”。
                doc.add(new TextField("fileName", fileName, Field.Store.YES));
                doc.add(new StringField("path", path, Field.Store.YES));
                doc.add(new TextField("contents", contents, Field.Store.NO));
                w.addDocument(doc);
            }
        }
        // 关闭索引写入器，内部隐含一个commit操作，会将内存缓冲的索引数据刷盘至目录。
        w.close();
    }
}
