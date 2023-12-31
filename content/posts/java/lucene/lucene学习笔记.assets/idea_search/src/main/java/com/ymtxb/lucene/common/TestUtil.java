package com.ymtxb.lucene.common;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;

/**
 * ClassName: TestUtil
 * Description:
 * date: 2022/3/4 17:39
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class TestUtil {
    public static boolean hitsIncludeTitle(IndexSearcher searcher, TopDocs hits, String title)
            throws IOException {
        for (ScoreDoc match : hits.scoreDocs) {
            Document doc = searcher.doc(match.doc);
            if (title.equals(doc.get("title"))) {
                return true;
            }
        }
        System.out.println("title '" + title + "' not found");
        return false;
    }

    public static int hitCount(IndexSearcher searcher, Query query) throws IOException {
        return searcher.search(query, 1).totalHits;
    }

//    public static int hitCount(IndexSearcher searcher, Query query, Filter filter) throws IOException {
//        return searcher.search(query, filter, 1).totalHits;
//    }

    public static void dumpHits(IndexSearcher searcher, TopDocs hits)
            throws IOException {
        if (hits.totalHits == 0) {
            System.out.println("No hits");
        }

        for (ScoreDoc match : hits.scoreDocs) {
            Document doc = searcher.doc(match.doc);
            System.out.println(match.score + ":" + doc.get("title"));
        }
    }

    public static Directory getBookIndexDirectory() throws IOException {
        return FSDirectory.open(new File("index-books").toPath());
    }

    public static void rmDir(File dir) throws IOException {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (int i = 0; i < files.length; i++) {
                if (!files[i].delete()) {
                    throw new IOException("could not delete " + files[i]);
                }
            }
            dir.delete();
        }
    }
}
