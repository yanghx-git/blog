package com.ymtxb.lucene.no4.synonym;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.AttributeSource;

import java.io.IOException;
import java.util.Stack;

/**
 * ClassName: SynonymFilter
 * Description:
 * date: 2022/4/1 11:47
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SynonymFilter extends TokenFilter {
    public static final String TOKEN_TYPE_SYNONYM = "SYNONYM";

    private Stack<String> synonymStack;
    private SynonymEngine engine;
    private AttributeSource.State current;

    private final CharTermAttribute termAtt;
    private final PositionIncrementAttribute posIncr;



    protected SynonymFilter(TokenStream input, SynonymEngine engine) {
        super(input);
        this.synonymStack = new Stack<String>();
        this.engine = engine;

        this.termAtt = addAttribute(CharTermAttribute.class);
        this.posIncr = addAttribute(PositionIncrementAttribute.class);
    }


    @Override
    public final boolean incrementToken() throws IOException {
        if(synonymStack.size() > 0){
            String syn = synonymStack.pop();
            restoreState(this.current);
            termAtt.setEmpty().append(syn);
            posIncr.setPositionIncrement(0);
            return true;
        }

        if(!input.incrementToken()) {
            return false;
        }

        if(addAliasesToStack()) {
            this.current = captureState();
        }

        return true;
    }

    private boolean addAliasesToStack() throws IOException {
        String[] synonyms = engine.getSynonyms(termAtt.toString());
        if(synonyms == null) {
            return false;
        }
        for(String synonym : synonyms) {
            synonymStack.push(synonym);
        }
        return true;
    }
}
