package com.ymtxb.lucene.no4.synonym;

import com.ymtxb.lucene.no4.synonym.SynonymEngine;

import java.io.IOException;
import java.util.HashMap;

/**
 * ClassName: SmartSynonymEngine
 * Description:
 * date: 2022/4/1 12:00
 *
 * @author 小刘讲师，微信：vv517956494
 * 本课程属于 小刘讲师 VIP 源码特训班课程
 * 严禁非法盗用（如有发现非法盗取行为，必将追究法律责任）
 * <p>
 * 如有同学发现非 小刘讲源码 官方号传播本视频资源，请联系我！
 * @since 1.0.0
 */
public class SmartSynonymEngine implements SynonymEngine {
    private static HashMap<String, String[]> map =
            new HashMap<String, String[]>();

    static {
        map.put("quick", new String[] {"fast", "speedy"});
        map.put("jumps", new String[] {"leaps", "hops"});
        map.put("over", new String[] {"above"});
        map.put("lazy", new String[] {"apathetic", "sluggish"});
        map.put("dog", new String[] {"canine", "pooch"});
    }

    @Override
    public String[] getSynonyms(String s) throws IOException {
        return map.get(s);
    }
}
