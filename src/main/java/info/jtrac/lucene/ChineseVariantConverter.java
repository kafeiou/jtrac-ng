package info.jtrac.lucene;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.github.houbb.opencc4j.util.ZhTwConverterUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure offline, sub-millisecond Chinese character & cross-strait phrase-level converter
 * powered by OpenCC (opencc4j tw2sp / s2twp) plus domain-specific IT/business phrase pairs.
 * Operates with zero external AI or network dependency.
 */
public final class ChineseVariantConverter {

    private static final int MAX_OFFLINE_VARIANTS = 12;

    /**
     * Bidirectional cross-strait enterprise/IT phrase equivalence groups
     * complementing OpenCC's built-in tw2sp / s2twp dictionary.
     * Ordered with longer compound phrases first.
     */
    private static final List<List<String>> CROSS_STRAIT_PHRASE_GROUPS = Arrays.asList(
            Arrays.asList("電子發票", "电子发票"),
            Arrays.asList("資料夾", "文件夹"),
            Arrays.asList("程式碼", "代码", "代碼", "程式码"),
            Arrays.asList("記憶體", "内存", "內存", "记忆体"),
            Arrays.asList("伺服器", "服务器", "服務器"),
            Arrays.asList("資料庫", "数据库", "數據庫", "资料库"),
            Arrays.asList("使用者", "用户", "用戶"),
            Arrays.asList("排程", "定时任务", "计划任务", "定時任務"),
            Arrays.asList("專案", "项目", "項目", "专案"),
            Arrays.asList("發票", "发票", "开票", "開票"),
            Arrays.asList("開立", "开具", "開具"),
            Arrays.asList("欄位", "字段", "栏位"),
            Arrays.asList("檔案", "文件", "档案"),
            Arrays.asList("設定", "设置", "設置", "设定"),
            Arrays.asList("預設", "默认", "默認", "预设"),
            Arrays.asList("登入", "登录", "登錄"),
            Arrays.asList("帳號", "账号", "帐号"),
            Arrays.asList("網路", "网络", "網絡", "网路"),
            Arrays.asList("軟體", "软件", "軟件", "软体"),
            Arrays.asList("硬體", "硬件", "硬体"),
            Arrays.asList("螢幕", "屏幕", "荧幕"),
            Arrays.asList("快取", "缓存", "緩存"),
            Arrays.asList("憑證", "证书", "證書", "凭证"),
            Arrays.asList("金鑰", "密钥", "密鑰"),
            Arrays.asList("連結", "链接", "鏈接", "连结"),
            Arrays.asList("資訊", "信息", "资讯"),
            Arrays.asList("訊息", "消息", "讯息"),
            Arrays.asList("匯入", "导入", "導入", "汇入"),
            Arrays.asList("匯出", "导出", "導出", "汇出"),
            Arrays.asList("還原", "还原", "恢复", "恢復"),
            Arrays.asList("搜尋", "搜索", "搜寻")
    );

    private ChineseVariantConverter() {
    }

    /**
     * Generates bidirectional Traditional/Simplified character and Taiwan/Mainland phrase-level
     * variants completely offline without requiring Ollama AI.
     */
    public static List<String> expandOfflineVariants(String query) {
        if (query == null) {
            return Collections.emptyList();
        }
        String trimmed = query.trim();
        if (trimmed.isEmpty() || !OllamaSearchExpander.containsChinese(trimmed)) {
            return Collections.emptyList();
        }

        Set<String> candidates = new LinkedHashSet<String>();

        // 1. OpenCC phrase-level Taiwan <-> Mainland conversion (tw2sp / s2twp)
        addIfValid(candidates, trimmed, ZhTwConverterUtil.toSimple(trimmed));
        addIfValid(candidates, trimmed, ZhTwConverterUtil.toTraditional(trimmed));

        // 2. OpenCC direct character-level conversion (t2s / s2t)
        addIfValid(candidates, trimmed, ZhConverterUtil.toSimple(trimmed));
        addIfValid(candidates, trimmed, ZhConverterUtil.toTraditional(trimmed));

        // 3. Domain-specific cross-strait IT/business phrase substitutions (normalized to consistent script)
        for (List<String> group : CROSS_STRAIT_PHRASE_GROUPS) {
            String matchedSource = null;
            for (String phrase : group) {
                if (trimmed.contains(phrase)) {
                    matchedSource = phrase;
                    break;
                }
            }
            if (matchedSource != null) {
                for (String replacement : group) {
                    if (!replacement.equals(matchedSource)) {
                        String substituted = trimmed.replace(matchedSource, replacement);
                        // Normalize mixed script into clean Mainland Simplified & Taiwan Traditional forms
                        addIfValid(candidates, trimmed, ZhTwConverterUtil.toSimple(substituted));
                        addIfValid(candidates, trimmed, ZhConverterUtil.toSimple(substituted));
                        addIfValid(candidates, trimmed, ZhTwConverterUtil.toTraditional(substituted));
                        addIfValid(candidates, trimmed, ZhConverterUtil.toTraditional(substituted));
                    }
                }
            }
            if (candidates.size() >= MAX_OFFLINE_VARIANTS) {
                break;
            }
        }

        List<String> result = new ArrayList<String>(candidates);
        if (result.size() > MAX_OFFLINE_VARIANTS) {
            return new ArrayList<String>(result.subList(0, MAX_OFFLINE_VARIANTS));
        }
        return result;
    }

    private static void addIfValid(Set<String> set, String original, String candidate) {
        if (candidate == null) {
            return;
        }
        String c = candidate.trim();
        if (!c.isEmpty() && !c.equalsIgnoreCase(original) && OllamaSearchExpander.containsChinese(c)) {
            set.add(c);
        }
    }
}
