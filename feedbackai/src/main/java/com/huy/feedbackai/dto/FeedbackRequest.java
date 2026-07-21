package com.huy.feedbackai.dto;

import lombok.Data;

@Data
public class FeedbackRequest {
    private String customerName;
    private String content;
}
