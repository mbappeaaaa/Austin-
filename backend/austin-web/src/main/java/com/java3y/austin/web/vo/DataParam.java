package com.java3y.austin.web.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全链路 请求参数
 *
 * @date 2022/2/22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DataParam {


    /**
     * 查看消息Id的链路信息
     */
    private String messageId;

    /**
     * 查看链路信息，支持接收者、messageId、模板ID或businessId
     */
    private String receiver;


    /**
     * 业务Id(数据追踪使用)
     * 生成逻辑参考 TaskInfoUtils
     * 支持模板ID、businessId、messageId，后端会自动识别
     */
    private String businessId;


    /**
     * 日期时间(检索短信的条件使用)
     */
    private Long dateTime;


}
