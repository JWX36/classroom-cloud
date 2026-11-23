package com.example.demo.form;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeForm {

    /** 主观题（附件）得分；没有客观题的作业则表示总分。 */
    @DecimalMin(value = "0", message = "分数不能小于 0")
    @DecimalMax(value = "100", message = "分数不能大于 100")
    private BigDecimal score;

    @Size(max = 1000, message = "评语不能超过 1000 个字符")
    private String comment;

    /** 空表单/空串时为 null，避免 "" 绑定基本类型 boolean 触发 500。 */
    private Boolean publish;

    public boolean isPublish() {
        return Boolean.TRUE.equals(publish);
    }
}
