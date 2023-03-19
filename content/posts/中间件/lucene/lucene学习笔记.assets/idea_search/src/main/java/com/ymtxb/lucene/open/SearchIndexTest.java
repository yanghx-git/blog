package com.ymtxb.lucene.open;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;

/**
 * ClassName: SearchIndexTest
 * Description:
 * date: 22-3-19 上午9:49
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SearchIndexTest {
    public static void main(String[] args) throws IOException {
        // 创建目录对象，注意需要和建立索引时的目录位置一致，下面查询对象需要靠它找到索引文件。
        Directory directory = FSDirectory.open(new File("open-class-index").toPath());
        // 基于目录对象，打开一个 索引读取对象，索引的打开 读取 等核心逻辑全部在reader内封装。
        IndexReader reader = DirectoryReader.open(directory);

        // 搜索门户对象，对外层提供友好的查询接口，内部主要依赖reader打开索引，读取索引，执行查询逻辑。
        IndexSearcher searcher = new IndexSearcher(reader);

        // Term 表示项，一个项由 域名和域值 组成
        Term t = new Term("contents", "released");
        // Query：查询条件封装对象，咱们使用的是 TermQuery，TermQuery 即：查询指定域内包含指定“词”的文档
        Query query = new TermQuery(t);

        // 参数2: 10，返回查询结果靠上的前10个
        // 返回值：topDocs，内部封装返回结果。
        //        主要属性和方法: totalHits 查询命中总数，它和10这个限定没有关系。
        //        主要属性和方法: scoreDocs 包含搜索结果的ScoreDoc对象数组
        //        主要属性和方法: getMaxScore() 返回结果集中最大评分
        TopDocs topDocs = searcher.search(query, 10);

        // ScoreDoc：封装文档id，注意这个id是 Lucene 领域内的id；评分
        for(ScoreDoc doc : topDocs.scoreDocs) {
            // 通过查询门户 searcher.doc 方法 根据，文档id，提取出文档。
            Document document = searcher.doc(doc.doc);
            // 打印contents域包含“released”的文档名称
            System.out.println(document.get("fileName"));
        }

        reader.close();
        directory.close();
    }
}
