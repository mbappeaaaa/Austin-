package com.java3y.austin.web.vo;

import com.java3y.austin.support.domain.InAppMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppMessagePageVo {
    private long count;
    private long unreadCount;
    private List<InAppMessage> rows;
}
