package com.ymtxb.lucene.no4;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.junit.Assert;

import java.io.IOException;
import java.io.StringReader;

/**
 * ClassName: AnalyzerUtils
 * Description:
 * date: 2022/3/31 9:54
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class AnalyzerUtils {
    public static void displayTokens(Analyzer analyzer, String text) throws IOException {
        displayTokens(analyzer.tokenStream("contents", text));
    }

    public static void displayTokens(TokenStream stream) throws IOException {
        try {
            stream.reset();
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            while(stream.incrementToken()) {
                System.out.print("[" + term.toString() + "]");
            }
        } finally {
            stream.close();
        }
    }

    public static void displayTokensWithFullDetails(Analyzer analyzer, String text) throws IOException {
        TokenStream stream = analyzer.tokenStream("contents", new StringReader(text));

        // 项值属性
        CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
        // 位置增量属性
        PositionIncrementAttribute posInc = stream.addAttribute(PositionIncrementAttribute.class);
        // 偏移量属性
        OffsetAttribute offset = stream.addAttribute(OffsetAttribute.class);
        // 类型
        TypeAttribute type = stream.addAttribute(TypeAttribute.class);

        int position = 0;
        try {
            stream.reset();
            while(stream.incrementToken()) {

                int increment = posInc.getPositionIncrement();
                if(increment > 0) {
                    position = position + increment;
                    System.out.println();
                    System.out.print(position + "：");
                }
                System.out.print("[" + term.toString() + ":" + offset.startOffset() + "->" + offset.endOffset() + type.type() + "]");
            }
            System.out.println();
        } finally {
            stream.close();
        }
    }


    public static void assertAnalyzesTo(Analyzer analyzer, String input, String[] output) throws IOException {
        TokenStream stream = analyzer.tokenStream("contents", new StringReader(input));
        try {
            stream.reset();
            CharTermAttribute termAttribute = stream.addAttribute(CharTermAttribute.class);
            for(String expected : output) {
                Assert.assertTrue(stream.incrementToken());
                Assert.assertEquals(expected, termAttribute.toString());
            }
            Assert.assertFalse(stream.incrementToken());
        } finally {
            stream.close();
        }
    }

    public static void displayTokensWithPositions(Analyzer analyzer, String text) throws IOException {
        TokenStream stream = analyzer.tokenStream("contents", new StringReader(text));
        CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
        PositionIncrementAttribute posIncr = stream.addAttribute(PositionIncrementAttribute.class);
        try {
            stream.reset();

            int position = 0;
            while(stream.incrementToken()) {
                int increment = posIncr.getPositionIncrement();
                if(increment > 0) {
                    position = position + increment;
                    System.out.println();
                    System.out.print(position + "：");
                }

                System.out.print("[" + term.toString() + "]");
            }
            System.out.println();

        } finally {
            stream.close();
        }

    }
}
