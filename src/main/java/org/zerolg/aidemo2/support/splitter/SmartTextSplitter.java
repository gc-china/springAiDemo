package org.zerolg.aidemo2.support.splitter;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能语义切片器
 * 核心逻辑：递归字符切分 (Recursive Character Text Splitting)
 * 优先按自然段落 -> 句子 -> 标点 -> 字符进行切分，尽可能保持语义完整性。
 */
@Component
public class SmartTextSplitter {

    // 默认配置: 500 字符 (约等于 300-400 tokens), 50 字符重叠
    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    // 分隔符优先级：双换行(段落) -> 单换行 -> 句子结束符 -> 逗号/分号 -> 空格 -> 字符
    // 使用正则 Lookbehind (?<=...) 确保切分后保留标点符号
    private static final String[] SEPARATORS = {
            "\n\n",
            "\n",
            "(?<=[。？！.?!])",
            "(?<=[，,；;])",
            " ",
            ""
    };

    /**
     * 执行切分
     *
     * @param text 原始文本
     * @return 切片列表
     */
    public List<String> split(String text) {
        return split(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    /**
     * 执行切分 (自定义参数)
     *
     * @param text         原始文本
     * @param chunkSize    目标切片大小 (字符数)
     * @param chunkOverlap 重叠大小 (字符数)
     */
    public List<String> split(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        return splitRecursive(text, new ArrayList<>(Arrays.asList(SEPARATORS)), chunkSize, chunkOverlap);
    }

    private List<String> splitRecursive(String text, List<String> separators, int chunkSize, int chunkOverlap) {
        List<String> finalChunks = new ArrayList<>();
        String separator = separators.get(0);
        List<String> nextSeparators = separators.size() > 1 ? separators.subList(1, separators.size()) : new ArrayList<>();

        List<String> splits = new ArrayList<>();

        // 1. 使用当前分隔符切分文本
        if (separator.isEmpty()) {
            // 兜底：按字符切分
            for (char c : text.toCharArray()) {
                splits.add(String.valueOf(c));
            }
        } else {
            // 正则切分
            if (separator.startsWith("(?<=")) {
                // 如果是正则 Lookbehind，使用 Matcher 查找
                splits = splitByRegex(text, separator);
            } else {
                // 普通字符串切分
                String[] parts = text.split(Pattern.quote(separator));
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    if (!part.isEmpty()) {
                        // 恢复分隔符 (除了最后一个部分，或者如果 split 丢弃了分隔符)
                        // 注意：String.split 默认行为会丢弃分隔符，我们需要根据逻辑决定是否拼回去
                        // 这里简化处理：对于 \n\n 和 \n，我们通常希望保留格式或替换为空格，这里选择拼回去以便递归
                        if (i < parts.length - 1 || text.endsWith(separator)) {
                            splits.add(part + separator);
                        } else {
                            splits.add(part);
                        }
                    }
                }
            }
        }

        // 2. 合并碎片
        List<String> goodSplits = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String s : splits) {
            if (currentChunk.length() + s.length() > chunkSize) {
                // 当前块已满，先处理当前块
                if (currentChunk.length() > 0) {
                    // 如果当前块本身就超大（说明当前分隔符力度不够），需要递归切分
                    if (currentChunk.length() > chunkSize && !nextSeparators.isEmpty()) {
                        goodSplits.addAll(splitRecursive(currentChunk.toString(), nextSeparators, chunkSize, chunkOverlap));
                    } else {
                        goodSplits.add(currentChunk.toString());
                    }

                    // ✅ 核心修复：实现 Overlap (重叠) 逻辑
                    if (chunkOverlap > 0 && currentChunk.length() > chunkOverlap) {
                        // 保留当前块末尾的 chunkOverlap 个字符，作为下一个块的起始上下文
                        String overlapText = currentChunk.substring(currentChunk.length() - chunkOverlap);
                        currentChunk.setLength(0);
                        currentChunk.append(overlapText);
                    } else {
                        currentChunk.setLength(0);
                    }
                }
            }
            currentChunk.append(s);
        }

        // 处理剩余部分
        if (currentChunk.length() > 0) {
            if (currentChunk.length() > chunkSize && !nextSeparators.isEmpty()) {
                goodSplits.addAll(splitRecursive(currentChunk.toString(), nextSeparators, chunkSize, chunkOverlap));
            } else {
                goodSplits.add(currentChunk.toString());
            }
        }

        return goodSplits;
    }

    /**
     * 辅助方法：使用正则切分但保留分隔符
     */
    private List<String> splitByRegex(String text, String regex) {
        List<String> res = new ArrayList<>();
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            int end = matcher.end();
            res.add(text.substring(lastEnd, end));
            lastEnd = end;
        }
        if (lastEnd < text.length()) {
            res.add(text.substring(lastEnd));
        }
        return res;
    }
}