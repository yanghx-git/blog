package com.ymtxb.lucene.no4.synonym;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * ClassName: SynonymAnalyzer
 * Description:
 * date: 2022/4/1 11:46
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SynonymAnalyzer extends Analyzer {
    private SynonymEngine engine;

    public SynonymAnalyzer(SynonymEngine engine) {
        this.engine = engine;
    }

    @Override
    protected TokenStreamComponents createComponents(String s) {
        // 标准Tokenizer
        StandardTokenizer tokenizer = new StandardTokenizer();
        // 标准过滤器
        StandardFilter standardFilter = new StandardFilter(tokenizer);
        // 小写
        LowerCaseFilter lowerCaseFilter = new LowerCaseFilter(standardFilter);
        // 停用词
        StopFilter stopFilter = new StopFilter(lowerCaseFilter, StopAnalyzer.ENGLISH_STOP_WORDS_SET);
        // 同义词
        SynonymFilter synonymFilter = new SynonymFilter(stopFilter, this.engine);

        return new TokenStreamComponents(tokenizer, synonymFilter);
    }
}
