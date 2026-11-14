package com.java3y.austin.web.vo;

import lombok.Data;

import java.util.List;

@Data
public class InAppMessageBatchRequest {
    private String receiver;
    private List<Long> ids;
}
