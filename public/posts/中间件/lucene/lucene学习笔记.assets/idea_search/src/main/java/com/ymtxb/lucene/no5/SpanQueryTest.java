package com.ymtxb.lucene.no5;

import junit.framework.TestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.io.StringReader;

/**
 * ClassName: SpanQueryTest
 * Description:
 * date: 2022/5/11 17:07
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SpanQueryTest extends TestCase {
    private RAMDirectory directory;
    private IndexSearcher searcher;
    private IndexReader reader;

    private SpanTermQuery quick;
    private SpanTermQuery brown;
    private SpanTermQuery red;
    private SpanTermQuery fox;
    private SpanTermQuery lazy;
    private SpanTermQuery sleepy;
    private SpanTermQuery dog;
    private SpanTermQuery cat;
    private Analyzer analyzer;

    protected void setUp() throws IOException {
        directory = new RAMDirectory();

        analyzer = new WhitespaceAnalyzer();

        IndexWriterConfig config = new IndexWriterConfig(new WhitespaceAnalyzer());
        IndexWriter writer = new IndexWriter(directory,config);

        Document doc = new Document();
        doc.add(new TextField("f", "the quick brown fox jumps over the lazy dog", Field.Store.YES));
        writer.addDocument(doc);

        doc = new Document();
        doc.add(new TextField("f", "the quick red fox jumps over the sleepy cat", Field.Store.YES));
        writer.addDocument(doc);
        writer.close();

        quick = new SpanTermQuery(new Term("f", "quick"));
        brown = new SpanTermQuery(new Term("f", "brown"));
        red = new SpanTermQuery(new Term("f", "red"));
        fox = new SpanTermQuery(new Term("f", "fox"));
        lazy = new SpanTermQuery(new Term("f", "lazy"));
        sleepy = new SpanTermQuery(new Term("f", "sleepy"));
        dog = new SpanTermQuery(new Term("f", "dog"));
        cat = new SpanTermQuery(new Term("f", "cat"));

        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
    }

    private void assertOnlyBrownFox(Query query) throws IOException {
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits);
        assertEquals("wrong doc", 0, hits.scoreDocs[0].doc);
    }

    private void assertBothFoxed(Query query) throws IOException {
        TopDocs hits = searcher.search(query, 10);
        assertEquals(2, hits.totalHits);
    }

    private void assertNoMatches(Query query) throws IOException {
        TopDocs hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits);
    }

    private void dumpSpans(SpanQuery query) throws IOException {
        System.out.println(query + ":");

        // 获取查询相关的 spans
        SpanWeight weight = query.createWeight(searcher, false);
        TopDocs hits = searcher.search(query, 10);
        // Spans 封装查询命中结果的一组跨度信息，跨度信息中包含： ID，startPosition，endPosition
        Spans spans = weight.getSpans(reader.getContext().leaves().get(0), SpanWeight.Postings.POSITIONS);

        // 保存结果的分值..
        float scores[] = new float[2];
        for (ScoreDoc sd : hits.scoreDocs) {
            scores[sd.doc] = sd.score;
        }

        // 遍历跨度信息，打印...
        while (spans != null && spans.nextDoc() != Spans.NO_MORE_DOCS) {
            // 文档id
            int id = spans.docID();
            // 获取出文档
            Document doc = reader.document(id);
            // 将文档的域内容转换为 TokenStream..
            TokenStream stream = analyzer.tokenStream("contents", new StringReader(doc.get("f")));
            // charTermAttribute 可以访问当前token的内容
            CharTermAttribute attribute = stream.addAttribute(CharTermAttribute.class);

            // 保存加工后的结果..
            StringBuilder builder = new StringBuilder();

            // 使用tokenStream之前必须 reset..
            stream.reset();

            // 开始和结束 position
            int startPos = -1;
            int endPos = -1;

            if (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                startPos = spans.startPosition();
                endPos = spans.endPosition();
            }

            int i = 0;
            while (stream.incrementToken()) {

                if (i == startPos) {
                    builder.append("<");
                }
                builder.append(attribute.toString());
                if (i + 1 == endPos) {
                    builder.append(">");
                    if (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                        startPos = spans.startPosition();
                        endPos = spans.endPosition();
                    } else {
                        startPos = -1;
                        endPos = -1;
                    }
                }
                builder.append(" ");
                i++;
            }

            builder.append("(").append(scores[id]).append(") ");
            System.out.println(builder);
            stream.close();
        }
        System.out.println();
    }


    public void testSpanTermQuery() throws IOException {
        dumpSpans(new SpanTermQuery(new Term("f", "the")));
    }


    public void testSpanFirstQuery() throws IOException {
        SpanFirstQuery sfq = new SpanFirstQuery(brown, 2);
        assertNoMatches(sfq);

        dumpSpans(sfq);

        sfq = new SpanFirstQuery(brown, 3);
        dumpSpans(sfq);
        assertOnlyBrownFox(sfq);
    }

    public void testSpanNearQuery() throws IOException {
        SpanQuery[] quick_brown_dog = new SpanQuery[] {quick,brown,dog};
        SpanNearQuery snq = new SpanNearQuery(quick_brown_dog, 0, true);
        assertNoMatches(snq);
        dumpSpans(snq);

        snq = new SpanNearQuery(quick_brown_dog, 4, true);
        assertNoMatches(snq);
        dumpSpans(snq);

        snq = new SpanNearQuery(quick_brown_dog, 5, true);
        assertOnlyBrownFox(snq);
        dumpSpans(snq);


        snq = new SpanNearQuery(new SpanQuery[]{lazy, fox}, 3, false);
        assertOnlyBrownFox(snq);
        dumpSpans(snq);

        PhraseQuery.Builder builder = new PhraseQuery.Builder();
        builder.add(new Term("f", "lazy"));
        builder.add(new Term("f", "fox"));
        builder.setSlop(4);
        PhraseQuery pq = builder.build();
        assertNoMatches(pq);

        builder.setSlop(5);
        pq = builder.build();
        assertOnlyBrownFox(pq);
    }


    public void testSpanNotQuery() throws IOException {
        SpanNearQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick, fox}, 1, true);
        assertBothFoxed(quick_fox);
        dumpSpans(quick_fox);

        SpanNotQuery quick_fox_dog = new SpanNotQuery(quick_fox, dog);
        assertBothFoxed(quick_fox_dog);
        dumpSpans(quick_fox_dog);

        SpanNotQuery no_quick_red_fox = new SpanNotQuery(quick_fox, red);
        assertOnlyBrownFox(no_quick_red_fox);
        dumpSpans(no_quick_red_fox);
    }

    public void testSpanOrQuery() throws IOException {
        SpanNearQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick, fox}, 1, true);
        SpanNearQuery lazy_dog = new SpanNearQuery(new SpanQuery[]{lazy, dog}, 0, true);
        SpanNearQuery sleepy_cat = new SpanNearQuery(new SpanQuery[]{sleepy, cat}, 0, true);

        SpanNearQuery qf_near_ld = new SpanNearQuery(new SpanQuery[]{quick_fox, lazy_dog}, 3, true);
        assertOnlyBrownFox(qf_near_ld);
        dumpSpans(qf_near_ld);

        SpanNearQuery qf_near_sc = new SpanNearQuery(new SpanQuery[]{quick_fox, sleepy_cat}, 3, true);
        dumpSpans(qf_near_sc);

        SpanOrQuery or = new SpanOrQuery(new SpanQuery[]{qf_near_ld, qf_near_sc});
        assertBothFoxed(or);
        dumpSpans(or);
    }
}
