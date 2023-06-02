package com.ymtxb.lucene.no4;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.util.CharArraySet;


/**
 * ClassName: StopAnalyzer2
 * Description:
 * date: 2022/4/1 9:45
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class StopAnalyzer2 extends Analyzer {
    /** 停用词集合 */
    private CharArraySet stopWords;

    public StopAnalyzer2(){
        stopWords = StopAnalyzer.ENGLISH_STOP_WORDS_SET;
    }

    public StopAnalyzer2(String[] stopWords) {
        this.stopWords = StopFilter.makeStopSet(stopWords);
    }


    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        // 按照非字母拆分的Tokenizer
        Tokenizer source = new LetterTokenizer();
        // 将接收到的Token转换为小写
        LowerCaseFilter lcFilter = new LowerCaseFilter(source);
        // 按照停用词列表移除被停用的语汇单元
        StopFilter stopFilter = new StopFilter(lcFilter, this.stopWords);

        return new TokenStreamComponents(source, stopFilter);
    }
}
