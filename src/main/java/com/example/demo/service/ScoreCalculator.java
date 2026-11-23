package com.example.demo.service;

import com.example.demo.entity.AssignmentScoreConfig;
import com.example.demo.entity.Question;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 100 分制的分值分配：
 * 1. 平均分配：100 分平均分给所有客观题；
 * 2. 按题型自定义：每种题型先分到自定义的分数，再平均分给该题型的题目；
 * 3. 分配精确到 0.01 分，尾差自动补齐，保证各题分值之和正好等于该部分总分；
 * 4. 题型合计不超过 100 分，剩余分数作为主观题（附件）分，由教师手动打分。
 */
public final class ScoreCalculator {

    public static final String[] TYPES = {"SINGLE", "MULTIPLE", "JUDGE"};
    public static final BigDecimal FULL_SCORE = new BigDecimal("100");

    private ScoreCalculator() {
    }

    /** 按方案给题目写入分值。 */
    public static void apply(AssignmentScoreConfig config, List<Question> questions) {
        if (questions == null || questions.isEmpty()) return;
        if (config != null && config.isCustom()) {
            for (String type : TYPES) {
                List<Question> group = group(questions, type);
                if (group.isEmpty()) continue;
                List<BigDecimal> parts = split(config.percentOf(type), group.size());
                for (int i = 0; i < group.size(); i++) {
                    group.get(i).setScore(parts.get(i));
                }
            }
            // 未知题型兜底为 0 分，避免沿用旧分值
            for (Question question : questions) {
                if (question.getScore() == null) question.setScore(BigDecimal.ZERO);
            }
            return;
        }
        List<BigDecimal> parts = split(FULL_SCORE, questions.size());
        for (int i = 0; i < questions.size(); i++) {
            questions.get(i).setScore(parts.get(i));
        }
    }

    /**
     * 把总分拆成 count 份，精确到 0.01，合计等于 total。
     * 除不尽时前 remainder 份多 0.01（相当于四舍五入后补齐尾差）。
     */
    public static List<BigDecimal> split(BigDecimal total, int count) {
        List<BigDecimal> result = new ArrayList<>();
        if (count <= 0) return result;
        BigDecimal normalized = (total == null ? BigDecimal.ZERO : total)
                .setScale(2, RoundingMode.HALF_UP);
        long cents = normalized.movePointRight(2).longValueExact();
        long base = Math.floorDiv(cents, count);
        long remainder = Math.floorMod(cents, count);
        for (int i = 0; i < count; i++) {
            long value = base + (i < remainder ? 1 : 0);
            result.add(BigDecimal.valueOf(value, 2));
        }
        return result;
    }

    public static BigDecimal sum(List<Question> questions) {
        BigDecimal total = BigDecimal.ZERO;
        if (questions == null) return total;
        for (Question question : questions) {
            total = total.add(question.getScore() == null ? BigDecimal.ZERO : question.getScore());
        }
        return total;
    }

    private static List<Question> group(List<Question> questions, String type) {
        List<Question> list = new ArrayList<>();
        for (Question question : questions) {
            if (type.equals(question.getType())) list.add(question);
        }
        return list;
    }
}
