package com.java3y.austin.web.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.text.StrPool;
import com.alibaba.fastjson2.JSON;
import com.java3y.austin.common.constant.AustinConstant;
import com.java3y.austin.common.domain.SimpleAnchorInfo;
import com.java3y.austin.common.enums.AnchorState;
import com.java3y.austin.common.enums.ChannelType;
import com.java3y.austin.common.enums.EnumUtil;
import com.java3y.austin.service.api.domain.TraceResponse;
import com.java3y.austin.service.api.service.TraceService;
import com.java3y.austin.support.dao.MessageTemplateDao;
import com.java3y.austin.support.dao.SmsRecordDao;
import com.java3y.austin.support.domain.MessageTemplate;
import com.java3y.austin.support.domain.SmsRecord;
import com.java3y.austin.support.utils.RedisUtils;
import com.java3y.austin.support.utils.TaskInfoUtils;
import com.java3y.austin.web.service.DataService;
import com.java3y.austin.web.utils.AnchorStateUtils;
import com.java3y.austin.web.utils.Convert4Amis;
import com.java3y.austin.web.vo.DataParam;
import com.java3y.austin.web.vo.amis.EchartsVo;
import com.java3y.austin.web.vo.amis.SmsTimeLineVo;
import com.java3y.austin.web.vo.amis.UserTimeLineVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据链路追踪获取接口 实现类
 *
 */
@Service
public class DataServiceImpl implements DataService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private MessageTemplateDao messageTemplateDao;

    @Autowired
    private SmsRecordDao smsRecordDao;

    @Autowired
    private TraceService traceService;


    @Override
    public UserTimeLineVo getTraceMessageInfo(String messageId, Long tenantId) {
        TraceResponse traceResponse = traceService.traceByMessageId(messageId);
        if (traceResponse == null || CollUtil.isEmpty(traceResponse.getData())) {
            return UserTimeLineVo.builder().items(new ArrayList<>()).build();
        }
        return buildUserTimeLineVo(traceResponse.getData(), tenantId);
    }

    @Override
    public UserTimeLineVo getTraceUserInfo(String queryId, Long tenantId) {
        String normalizedQueryId = CharSequenceUtil.trim(queryId);

        // 1. 优先按接收者查询
        List<String> userInfoList = redisUtils.lRange(normalizedQueryId, 0, -1);
        if (CollUtil.isNotEmpty(userInfoList)) {
            List<SimpleAnchorInfo> receiverAnchors = parseAnchorInfo(userInfoList);
            receiverAnchors.sort(Comparator.comparing(SimpleAnchorInfo::getTimestamp).reversed());
            return buildUserTimeLineVo(receiverAnchors, tenantId);
        }

        // 2. 接收者无结果时，尝试按messageId查询
        TraceResponse traceResponse = traceService.traceByMessageId(normalizedQueryId);
        if (Objects.nonNull(traceResponse) && CollUtil.isNotEmpty(traceResponse.getData())) {
            return buildUserTimeLineVo(traceResponse.getData(), tenantId);
        }

        // 3. 最后按模板ID/businessId查询该模板的发送明细
        String businessId = getRealBusinessId(normalizedQueryId, tenantId);
        if (CharSequenceUtil.isBlank(businessId)) {
            return emptyUserTimeLineVo();
        }
        String businessKey = CharSequenceUtil.join(StrPool.COLON, AustinConstant.CACHE_KEY_PREFIX,
                AustinConstant.BUSINESS_ID, businessId);
        List<SimpleAnchorInfo> businessAnchors = parseAnchorInfo(redisUtils.lRange(businessKey, 0, -1));
        if (CollUtil.isEmpty(businessAnchors)) {
            return emptyUserTimeLineVo();
        }
        businessAnchors.sort(Comparator.comparing(SimpleAnchorInfo::getTimestamp));
        return buildUserTimeLineVo(businessAnchors, tenantId);
    }

    @Override
    public EchartsVo getTraceMessageTemplateInfo(String queryId, Long tenantId) {

        // 支持自动识别模板ID、businessId和messageId
        String businessId = getRealBusinessId(queryId, tenantId);
        if (CharSequenceUtil.isBlank(businessId)) {
            return null;
        }
        Optional<MessageTemplate> optional = messageTemplateDao.findByIdAndTenantId(
                TaskInfoUtils.getMessageTemplateIdFromBusinessId(Long.valueOf(businessId)), tenantId);
        if (!optional.isPresent()) {
            return null;
        }

        /**
         * 获取redis清洗好的数据
         * key：state
         * value:stateCount
         */
        Map<Object, Object> anchorResult = redisUtils.hGetAll(businessId);

        return Convert4Amis.getEchartsVo(anchorResult, optional.get(), businessId);
    }

    @Override
    public SmsTimeLineVo getTraceSmsInfo(DataParam dataParam, Long tenantId) {

        Integer sendDate = Integer.valueOf(DateUtil.format(new Date(dataParam.getDateTime() * 1000L), DatePattern.PURE_DATE_PATTERN));
        List<SmsRecord> smsRecordList = smsRecordDao.findByTenantIdAndPhoneAndSendDate(
                tenantId, Long.valueOf(dataParam.getReceiver()), sendDate);
        if (CollUtil.isEmpty(smsRecordList)) {
            return SmsTimeLineVo.builder().items(Collections.singletonList(SmsTimeLineVo.ItemsVO.builder().build())).build();
        }

        Map<String, List<SmsRecord>> maps = smsRecordList.stream().collect(Collectors.groupingBy(o -> o.getPhone() + o.getSeriesId()));
        return Convert4Amis.getSmsTimeLineVo(maps);
    }

    /**
     * 自动识别查询ID：
     * 1. 模板ID：生成【当天】的businessId
     * 2. 16位businessId：直接查询
     * 3. messageId：从消息链路中识别businessId
     */
    private String getRealBusinessId(String queryId, Long tenantId) {
        String normalizedQueryId = CharSequenceUtil.trim(queryId);
        if (CharSequenceUtil.isBlank(normalizedQueryId)) {
            return null;
        }

        if (normalizedQueryId.length() == AustinConstant.BUSINESS_ID_LENGTH
                && normalizedQueryId.chars().allMatch(Character::isDigit)) {
            return belongsToTenant(normalizedQueryId, tenantId) ? normalizedQueryId : null;
        }

        if (normalizedQueryId.chars().allMatch(Character::isDigit)) {
            try {
                Optional<MessageTemplate> optional = messageTemplateDao.findByIdAndTenantId(
                        Long.valueOf(normalizedQueryId), tenantId);
                if (optional.isPresent()) {
                    MessageTemplate messageTemplate = optional.get();
                    return String.valueOf(TaskInfoUtils.generateBusinessId(messageTemplate.getId(), messageTemplate.getTemplateType()));
                }
            } catch (NumberFormatException ignored) {
                // 超长数字不是合法模板ID，继续尝试按messageId解析
            }
        }

        TraceResponse traceResponse = traceService.traceByMessageId(normalizedQueryId);
        if (Objects.nonNull(traceResponse) && CollUtil.isNotEmpty(traceResponse.getData())) {
            String businessId = String.valueOf(traceResponse.getData().get(0).getBusinessId());
            return belongsToTenant(businessId, tenantId) ? businessId : null;
        }
        return null;
    }

    private boolean belongsToTenant(String businessId, Long tenantId) {
        try {
            Long templateId = TaskInfoUtils.getMessageTemplateIdFromBusinessId(Long.valueOf(businessId));
            return messageTemplateDao.findByIdAndTenantId(templateId, tenantId).isPresent();
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private List<SimpleAnchorInfo> parseAnchorInfo(List<String> anchorJsonList) {
        if (CollUtil.isEmpty(anchorJsonList)) {
            return new ArrayList<>();
        }
        return anchorJsonList.stream()
                .map(value -> JSON.parseObject(value, SimpleAnchorInfo.class))
                .collect(Collectors.toList());
    }

    private UserTimeLineVo emptyUserTimeLineVo() {
        return UserTimeLineVo.builder().items(new ArrayList<>()).build();
    }

    private UserTimeLineVo buildUserTimeLineVo(List<SimpleAnchorInfo> sortAnchorList, Long tenantId) {
        // Flink/Kafka采用至少一次语义，任务恢复时可能重放同一埋点，展示前按事件去重
        Map<String, SimpleAnchorInfo> uniqueAnchors = new LinkedHashMap<>();
        for (SimpleAnchorInfo anchorInfo : sortAnchorList) {
            String eventKey = anchorInfo.getBusinessId() + StrPool.DASHED + anchorInfo.getState()
                    + StrPool.DASHED + anchorInfo.getTimestamp();
            uniqueAnchors.putIfAbsent(eventKey, anchorInfo);
        }
        sortAnchorList = new ArrayList<>(uniqueAnchors.values());

        // 1. 对相同的businessId进行分类  {"businessId":[{businessId,state,timeStamp},{businessId,state,timeStamp}]}
        Map<String, List<SimpleAnchorInfo>> map = MapUtil.newHashMap();
        for (SimpleAnchorInfo simpleAnchorInfo : sortAnchorList) {
            List<SimpleAnchorInfo> simpleAnchorInfos = map.get(String.valueOf(simpleAnchorInfo.getBusinessId()));
            if (CollUtil.isEmpty(simpleAnchorInfos)) {
                simpleAnchorInfos = new ArrayList<>();
            }
            simpleAnchorInfos.add(simpleAnchorInfo);
            map.put(String.valueOf(simpleAnchorInfo.getBusinessId()), simpleAnchorInfos);
        }

        // 2. 封装vo 给到前端渲染展示
        List<UserTimeLineVo.ItemsVO> items = new ArrayList<>();
        for (Map.Entry<String, List<SimpleAnchorInfo>> entry : map.entrySet()) {
            Long messageTemplateId = TaskInfoUtils.getMessageTemplateIdFromBusinessId(Long.valueOf(entry.getKey()));
            MessageTemplate messageTemplate = messageTemplateDao.findByIdAndTenantId(
                    messageTemplateId, tenantId).orElse(null);
            if (Objects.isNull(messageTemplate)) {
                continue;
            }

            StringBuilder sb = new StringBuilder();
            for (SimpleAnchorInfo simpleAnchorInfo : entry.getValue()) {
                if (AnchorState.RECEIVE.getCode().equals(simpleAnchorInfo.getState())) {
                    sb.append(StrPool.CRLF);
                }
                String startTime = DateUtil.format(new Date(simpleAnchorInfo.getTimestamp()), DatePattern.NORM_DATETIME_PATTERN);
                String stateDescription = AnchorStateUtils.getDescriptionByState(messageTemplate.getSendChannel(), simpleAnchorInfo.getState());

                sb.append(startTime).append(StrPool.C_COLON).append(stateDescription).append("==>");
            }

            for (String detail : sb.toString().split(StrPool.CRLF)) {
                if (CharSequenceUtil.isNotBlank(detail)) {
                    UserTimeLineVo.ItemsVO itemsVO = UserTimeLineVo.ItemsVO.builder()
                            .businessId(entry.getKey())
                            .sendType(EnumUtil.getEnumByCode(messageTemplate.getSendChannel(), ChannelType.class).getDescription())
                            .creator(messageTemplate.getCreator())
                            .title(messageTemplate.getName())
                            .detail(detail)
                            .build();
                    items.add(itemsVO);
                }
            }
        }
        return UserTimeLineVo.builder().items(items).build();
    }
}
