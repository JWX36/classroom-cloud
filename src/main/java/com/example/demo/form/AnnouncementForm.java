package com.example.demo.form;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AnnouncementForm {

    @NotBlank(message = "公告标题不能为空")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    private boolean pinned;
}
