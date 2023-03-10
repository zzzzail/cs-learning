package alg.leetcodejzof;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zail
 * @date 2022/7/18
 */
public class P48 {
    
    public static void main(String[] args) {
    
    }
    
    public int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> dic = new HashMap<>();
        int res = 0, tmp = 0;
        for(int j = 0; j < s.length(); j++) {
            int i = dic.getOrDefault(s.charAt(j), -1);  // 获取索引 i
            dic.put(s.charAt(j), j);                               // 更新哈希表
            tmp = tmp < j - i ? tmp + 1 : j - i;                   // dp[j - 1] -> dp[j]
            res = Math.max(res, tmp);                              // max(dp[j - 1], dp[j])
        }
        return res;
    }
}
