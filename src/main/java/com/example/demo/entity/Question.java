package com.example.demo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@TableName("question")
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    /** SINGLE / MULTIPLE / JUDGE */
    private String type;
    private String stem;
    private BigDecimal score;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    /** A / A,C / A for judge (A=对, B=错) */
    private String correctAnswer;
    private Integer sortNo;

    @TableField(exist = false)
    private List<String> correctKeys = new ArrayList<>();

    public boolean isSingle() {
        return "SINGLE".equals(type);
    }

    public boolean isMultiple() {
        return "MULTIPLE".equals(type);
    }

    public boolean isJudge() {
        return "JUDGE".equals(type);
    }

    public String getTypeText() {
        if (isMultiple()) return "多选题";
        if (isJudge()) return "判断题";
        return "单选题";
    }

    public List<String> getCorrectKeys() {
        if (correctKeys == null) correctKeys = new ArrayList<>();
        return correctKeys;
    }

    public List<java.util.Map<String, String>> getOptionList() {
        List<java.util.Map<String, String>> options = new ArrayList<>();
        if (isJudge()) {
            options.add(java.util.Map.of("key", "A", "text", "对"));
            options.add(java.util.Map.of("key", "B", "text", "错"));
            return options;
        }
        if (optionA != null && !optionA.isBlank()) options.add(java.util.Map.of("key", "A", "text", optionA));
        if (optionB != null && !optionB.isBlank()) options.add(java.util.Map.of("key", "B", "text", optionB));
        if (optionC != null && !optionC.isBlank()) options.add(java.util.Map.of("key", "C", "text", optionC));
        if (optionD != null && !optionD.isBlank()) options.add(java.util.Map.of("key", "D", "text", optionD));
        return options;
    }

    public boolean isCorrect(String answer) {
        if (answer == null) return false;
        return normalize(answer).equals(normalize(correctAnswer == null ? "" : correctAnswer));
    }

    /** 得分：多选题全对满分、部分正确（不含错误项）得一半分、含错误项 0 分；其他题型全对得分。 */
    public java.math.BigDecimal scoreOf(String answer) {
        if (score == null) return java.math.BigDecimal.ZERO;
        if (answer == null || answer.isBlank()) return java.math.BigDecimal.ZERO;
        if (!isMultiple()) {
            return isCorrect(answer) ? score : java.math.BigDecimal.ZERO;
        }
        java.util.Set<String> correct = new java.util.HashSet<>(answerKeys(correctAnswer));
        java.util.Set<String> picked = new java.util.HashSet<>(answerKeys(answer));
        if (picked.equals(correct)) return score;
        if (correct.containsAll(picked)) {
            return score.divide(new java.math.BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP);
        }
        return java.math.BigDecimal.ZERO;
    }

    private java.util.List<String> answerKeys(String value) {
        java.util.List<String> keys = new ArrayList<>();
        if (value != null) {
            for (String key : value.replace(" ", "").toUpperCase().split(",")) {
                if (!key.isBlank()) keys.add(key);
            }
        }
        return keys;
    }

    /** 题目分值显示：去掉多余小数（10.00 → 10，7.5 保持 7.5）。 */
    public String getScoreText() {
        return score == null ? "0" : score.stripTrailingZeros().toPlainString();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.replace(" ", "").toUpperCase().split(",");
        java.util.Arrays.sort(parts);
        return String.join(",", parts);
    }
}
