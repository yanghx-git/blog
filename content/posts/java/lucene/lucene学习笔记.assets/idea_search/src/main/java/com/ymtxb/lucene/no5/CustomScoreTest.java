package com.ymtxb.lucene.no5;

import com.ymtxb.lucene.common.TestUtil;
import junit.framework.TestCase;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.queries.CustomScoreProvider;
import org.apache.lucene.queries.CustomScoreQuery;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;

import java.io.IOException;

/**
 * ClassName: CustomScoreTest
 * Description:
 * date: 2022/5/12 14:34
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class CustomScoreTest extends TestCase {

    public void testRecency() throws IOException, ParseException {
        Directory dir = TestUtil.getBookIndexDirectory();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        QueryParser parser = new QueryParser("contents", new StandardAnalyzer());
        Query q = parser.parse("java in action");
        Query q2 = new RecencyBoostingQuery(q, 2.0, 14*365, "pubmonthAsDay");

        Sort sort = new Sort(new SortField[] {
                SortField.FIELD_SCORE,
                new SortField("title2", SortField.Type.STRING)
        });
        TopDocs hits = searcher.search(q2, 5, sort, true, false);

        for(int i = 0; i < hits.scoreDocs.length; i++) {
            Document doc = searcher.doc(hits.scoreDocs[i].doc);
            System.out.println((i + 1) + ":" + doc.get("title") + ":pubmonth=" + doc.get("pubmonth") +
                    " score=" + hits.scoreDocs[i].score);
        }
        reader.close();
    }

    // 继承 CustomScoreQuery 就可以实现 自定义评分了... 内部有一个方法 getCustomScoreProvider 返回一个 CustomScoreProvider 实例，
    // 该实例内部封装 评分规则代码...
    static class RecencyBoostingQuery extends CustomScoreQuery {
        static int MSEC_PER_DAY = 1000 * 3600 * 24;
        // 乘数
        double multiplier;
        // 今天
        int today;
        // 最大天数
        int maxDaysAgo;
        // 日期域名称
        String dayField;

        public RecencyBoostingQuery(Query q, double multiplier, int maxDaysAgo, String dayField) {
            super(q);
            this.today = (int) (System.currentTimeMillis() / MSEC_PER_DAY);
            this.multiplier = multiplier;
            this.maxDaysAgo = maxDaysAgo;
            this.dayField = dayField;
        }

        @Override
        protected CustomScoreProvider getCustomScoreProvider(LeafReaderContext context) throws IOException {
            return new RecencyBooster(context);
        }

        private class RecencyBooster extends CustomScoreProvider {
            private NumericDocValues publishDay;

            public RecencyBooster(LeafReaderContext context) throws IOException {
                super(context);
                publishDay = context.reader().getNumericDocValues(dayField);
            }

            @Override
            public float customScore(int doc, float subQueryScore, float valSrcScore) {
                int daysAgo = today - (int)publishDay.get(doc);
                if(daysAgo < maxDaysAgo) {
                    float boost = (float) (multiplier * (maxDaysAgo - daysAgo) / maxDaysAgo);
                    return (float) (subQueryScore * (1.0 + boost));
                } else {
                    return subQueryScore;
                }
            }
        }
    }
}
