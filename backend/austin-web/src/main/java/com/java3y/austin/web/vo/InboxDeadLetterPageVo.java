package com.java3y.austin.web.vo;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class InboxDeadLetterPageVo {
    long count;
    List<InboxDeadLetterVo> rows;
}
