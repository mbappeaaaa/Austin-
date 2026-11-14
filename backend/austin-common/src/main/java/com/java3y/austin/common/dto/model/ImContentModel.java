package com.java3y.austin.common.dto.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImContentModel extends ContentModel {

    /** 站内信标题。 */
    private String title;

    /** 站内信正文。 */
    private String content;

    /** 可选的业务跳转链接。 */
    private String url;
}
