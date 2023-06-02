package com.ymtxb.lucene.no5;

import com.ymtxb.lucene.common.TestUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.text.DecimalFormat;

/**
 * ClassName: SortingExample
 * Description:
 * date: 2022/5/11 10:36
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SortingExample {
    private Directory directory;

    public SortingExample(Directory directory) {
        this.directory = directory;
    }

    /**
     * @param query 查询封装
     * @param sort 排序封装
     */
    public void displayResults(Query query, Sort sort) throws IOException {
        // 获取查询门户实例
        IndexReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);

        // 注意：sort参数
        // doDocScores ：true，表示强制做评分
        TopDocs results = searcher.search(query, 20,sort, true, false);

        // 输出查询条件和排序规则
        System.out.println("\nResults for: " + query.toString() + " sorted by " + sort);


        System.out.println(StringUtils.rightPad("Title", 30) +
                StringUtils.rightPad("pubmonth", 10) +
                StringUtils.center("id", 4) +
                StringUtils.center("score", 15));

        // 分值格式化：保存小数点后6位
        DecimalFormat scoreFormatter = new DecimalFormat("0.######");

        // 打印结果..
        for(ScoreDoc sd : results.scoreDocs) {
            int docID = sd.doc;
            float score = sd.score;
            Document doc = searcher.doc(docID);
            System.out.println(
                    StringUtils.rightPad(
                            StringUtils.abbreviate(doc.get("title"), 29), 30) +
                            StringUtils.rightPad(doc.get("pubmonth"), 10) +
                            StringUtils.center("" + docID, 4) +
                            StringUtils.leftPad(scoreFormatter.format(score), 12)
            );
            // 类别
            System.out.println("   " + doc.get("category"));
        }
    }


    public static void main(String[] args) throws ParseException, IOException {
        // 匹配全部
        Query allBooks = new MatchAllDocsQuery();
        QueryParser parser = new QueryParser("contents", new StandardAnalyzer());


        // 全部的书 || （java OR action）
        BooleanQuery query = new BooleanQuery.Builder()
                .add(allBooks, BooleanClause.Occur.SHOULD)
                .add(parser.parse("java OR action"), BooleanClause.Occur.SHOULD).build();
        // index-books 索引目录
        Directory directory = TestUtil.getBookIndexDirectory();
        SortingExample example = new SortingExample(directory);

        // Sort.RELEVANCE ： 相关性排序
        example.displayResults(query, Sort.RELEVANCE);
        // Sort.INDEXORDER ：按照文档ID排序
        example.displayResults(query, Sort.INDEXORDER);

        // new Sort(new SortField("category", SortField.Type.STRING))
        // 指定域排序，这里是按照“category”，第二个参数 表示 字段类型
        example.displayResults(query, new Sort(new SortField("category", SortField.Type.STRING)));

        // new Sort(new SortField("pubmonth", SortField.Type.INT))
        // 指定域排序，这里是按照“pubmonth”，第二个参数 表示 字段类型
        example.displayResults(query, new Sort(new SortField("pubmonth", SortField.Type.INT)));


        // 类似于 sql中的 order by 多字段..
        example.displayResults(query, new Sort(new SortField("category", SortField.Type.STRING),
                SortField.FIELD_SCORE,
                new SortField("pubmonth", SortField.Type.INT)));


        example.displayResults(query, new Sort(new SortField[] {
                SortField.FIELD_SCORE,
                new SortField("category", SortField.Type.STRING)
        }));

    }
}
