package com.ymtxb.lucene.no2;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 * ClassName: SetBoostDemo
 * Description:
 * date: 2022/2/26 18:22
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class Demo2 {


    public static void main(String[] args) throws Exception {
        Demo2 demo = new Demo2();

        demo.index();
        demo.search();
    }

    //写测试数据，这些数据是写到索引文档里去的。
    private String ids[] = {"1","2","3","4"}; //标示文档
    private String author[] = {"Jack","Mary","Jerry","Machech"};
    private String title[] = {"java of china","Apple of china","Androw of apple the USA","People of Apple java"}; //
    private String contents[] = {
            "java  of China!the world the why what",
            "why a dertity compante is my hometown!",
            "Jdekia ssde hhh is a beautiful city!",
            "Jdekia ssde hhh is a beautiful java!"};


    private Directory dir = new RAMDirectory();



    /**
     * 获取IndexWriter实例
     * @return
     * @throws Exception
     */
    private IndexWriter getWriter()throws Exception{

        //实例化分析器
        Analyzer analyzer = new StandardAnalyzer();

        //实例化IndexWriterConfig
        IndexWriterConfig con = new IndexWriterConfig(analyzer);

        //实例化IndexWriter
        IndexWriter writer = new IndexWriter(dir, con);

        return writer;
    }



    /**
     * 生成索引（对应图一）
     * @throws Exception
     */
    public void index()throws Exception{

        IndexWriter writer=getWriter();

        for(int i=0;i<ids.length;i++){

            Document doc=new Document();

            doc.add(new StringField("id", ids[i], Field.Store.YES));
            doc.add(new StringField("author", author[i], Field.Store.YES));
            // 加权操作
            TextField field=new TextField("title", title[i], Field.Store.YES);

        /*    if("Jerry".equals(author[i])){

                //设权 默认为1
                field.setBoost(1.5F);
            }*/

            doc.add(field);
            doc.add(new StringField("contents",contents[i],Field.Store.NO));


            // 添加文档
            writer.addDocument(doc);
        }

        //关闭writer
        writer.close();
    }



    public void search()throws Exception {


        //通过dir得到的路径下的所有的文件
        IndexReader reader = DirectoryReader.open(dir);

        //建立索引查询器
        IndexSearcher searcher = new IndexSearcher(reader);

        //查找的范围
        String searchField = "title";

        //查找的字段
        String q = "apple";

        //运用term来查找
        Term t = new Term(searchField, q);

        //通过term得到query对象
        Query query = new TermQuery(t);

        //获得查询的hits
        TopDocs hits = searcher.search(query, 10);

        //显示结果
        System.out.println("匹配 '" + q + "'，总共查询到" + hits.totalHits + "个文档");

        //循环得到文档，得到文档就可以得到数据
        for (ScoreDoc scoreDoc : hits.scoreDocs) {

            Document doc = searcher.doc(scoreDoc.doc);

            System.out.println(doc.get("author"));
        }

        //关闭reader
        reader.close();
    }

}
