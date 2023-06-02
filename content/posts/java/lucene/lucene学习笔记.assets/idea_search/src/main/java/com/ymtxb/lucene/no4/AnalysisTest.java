package com.ymtxb.lucene.no4;

import junit.framework.TestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.io.IOException;

/**
 * ClassName: AnalysisTest
 * Description:
 * date: 2022/3/31 9:53
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class AnalysisTest extends TestCase {

    private static final String[] example =
            {"The quick brown fox jumped over the lazy dog"/*,
                    "XY&Z Corporation - xyz@example.com"*/};

    private static final Analyzer[] analyzers = new Analyzer[]{
            new WhitespaceAnalyzer(),
            new SimpleAnalyzer(),
            new StopAnalyzer(),
            new StandardAnalyzer()
    };

    public static void main(String[] args) throws IOException {
        String[] strings = example;
        if (args.length > 0) {
            strings = args;
        }

        for (String text : strings) {
            analyze(text);
        }
    }

    private static void analyze(String text) throws IOException {
        System.out.println("Analyzing \"" + text + "\"");

        for(Analyzer analyzer : analyzers) {
            String name = analyzer.getClass().getSimpleName();
            System.out.println("  " + name + ":");
            System.out.print("     ");
            AnalyzerUtils.displayTokens(analyzer, text);
            System.out.println("\n");
        }
    }


    public void testFullDetails() throws IOException {
        AnalyzerUtils.displayTokensWithFullDetails(new SimpleAnalyzer(), "The quick brown fox....");
    }

    public void testStopAnalyzer2() throws IOException {
        AnalyzerUtils.assertAnalyzesTo(new StopAnalyzer2(), "The quick brown....",
                new String[]{"quick", "brown"});
    }

    public void testStopAnalyzerFlawed() throws IOException {
        AnalyzerUtils.assertAnalyzesTo(new StopAnalyzerFlawed(), "The quick brown....",
                new String[]{"the", "quick", "brown"});
    }


}
