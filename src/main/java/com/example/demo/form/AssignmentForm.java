package com.example.demo.form;

import com.example.demo.entity.AssignmentScoreConfig;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AssignmentForm {

    @NotBlank(message = "作业标题不能为空")
    @Size(max = 200, message = "作业标题不能超过 200 个字符")
    private String title;

    @Size(max = 50000, message = "作业要求内容过长")
    private String requirement;

    @NotNull(message = "请选择截止时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime deadline;

    private MultipartFile attachment;

    /** EVEN：客观题平均分配 100 分；CUSTOM：按题型自定义占比。 */
    private String scoreMode = AssignmentScoreConfig.EVEN;

    @DecimalMin(value = "0", message = "单选题占比不能小于 0")
    @DecimalMax(value = "100", message = "单选题占比不能大于 100")
    private BigDecimal singlePercent = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "多选题占比不能小于 0")
    @DecimalMax(value = "100", message = "多选题占比不能大于 100")
    private BigDecimal multiplePercent = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "判断题占比不能小于 0")
    @DecimalMax(value = "100", message = "判断题占比不能大于 100")
    private BigDecimal judgePercent = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "附件占比不能小于 0")
    @DecimalMax(value = "100", message = "附件占比不能大于 100")
    private BigDecimal attachmentPercent = BigDecimal.ZERO;
}
