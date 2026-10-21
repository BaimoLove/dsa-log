package com.baimo.dsalogserver.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 复习评分：rating 只接受 good / hard / again，具体校验落在 ReviewScheduler */
@Data
public class RateReviewRequest {

    @NotBlank(message = "rating 不能为空")
    private String rating;
}
