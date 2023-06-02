package com.ymtxb.lucene.no4.synonym;

import com.ymtxb.lucene.no4.AnalyzerUtils;

import java.io.IOException;

/**
 * ClassName: SynonymAnalyzerViewer
 * Description:
 * date: 2022/4/3 10:25
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SynonymAnalyzerViewer {
    public static void main(String[] args) throws IOException {
        SynonymEngine engine = new SmartSynonymEngine();

        AnalyzerUtils.displayTokensWithPositions(new SynonymAnalyzer(engine),
                "The quick brown fox jumps over the lazy dog");
    }
}
