package com.qingtian.lcpes.modules.mq.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.util.StringUtil;
import com.qingtian.lcpes.base.Constants;
import com.qingtian.lcpes.base.exception.QTBusinessException;
import com.qingtian.lcpes.base.facade.BaseServiceImpl;
import com.qingtian.lcpes.base.util.RedisUtils;
import com.qingtian.lcpes.modules.mq.bo.MsgStatCountBO;
import com.qingtian.lcpes.modules.mq.entity.MqConfigEntity;
import com.qingtian.lcpes.modules.mq.entity.MqMsgEntity;
import com.qingtian.lcpes.modules.mq.entity.MqMsgHEntity;
import com.qingtian.lcpes.modules.mq.jmsg.MsgHeaderDto;
import com.qingtian.lcpes.modules.mq.jmsg.NodeInfo;
import com.qingtian.lcpes.modules.mq.jmsg.TablesDto;
import com.qingtian.lcpes.modules.mq.mapper.MqConfigMapper;
import com.qingtian.lcpes.modules.mq.mapper.MqMsgMapper;
import com.qingtian.lcpes.modules.mq.service.MqConfigService;
import com.qingtian.lcpes.modules.mq.service.MqMsgHService;
import com.qingtian.lcpes.modules.mq.service.MqMsgService;
import com.qingtian.lcpes.modules.mq.util.XmlUtil;
import com.qingtian.lcpes.modules.mq.vo.MsgStatCountVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jms.core.JmsOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * <p>
 * ????????? ???????????????
 * </p>
 *
 * @author zhangxq
 * @since 2022-09-01
 */
@Service
@Slf4j
public class MqMsgServiceImpl extends BaseServiceImpl<MqMsgMapper, MqMsgEntity> implements MqMsgService {
    private static final String PARAM_ERR_MSG = "????????????";
    private static final String INTERFACDE_CODE_NOT_EXISTS = "??????????????????";
    private static final String COLUMN_NOT_EXISTS = "???????????????";
    private static final String SYS_ERR = "????????????";
    private static final String SPLIT = "|";
    private static final String SENDER_IS_NULL = "???????????????";
    public static final String ERROR_INFO = "errorInfo";
    public static final String TRADE = "trade";

    @Autowired
    private MqConfigMapper configMapper;
    @Value("${itmp.sysNo}")
    private String sysNo;
    @Value("${itmp.sysName}")
    private String sysName;
    @Autowired
    private MqConfigService mqConfigService;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private RestTemplate restSendTemplate;
    @Autowired
    private RedisUtils redisUtils;
    @Resource
    private JmsOperations jmsOperations;
    @Autowired
    private MqMsgHService msgHService;
    @Resource
    ExecutorService receiveExecutorService;
    @Resource
    ExecutorService sendExecutorService;
    @Autowired
    private MqMsgMapper msgMapper;

    @Override
    public IPage<MqMsgEntity> listByPage(IPage<MqMsgEntity> page, MqMsgEntity entity) {
        List<MqMsgEntity> msgEntities = getBasicMapper().listByPage(page, entity);
        for (MqMsgEntity e : msgEntities) {
            LambdaQueryWrapper<MqConfigEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MqConfigEntity::getMsgId, e.getMsgId());
            List<MqConfigEntity> list = configMapper.selectList(wrapper);
            if (list.size() == 1) {
                e.setMsgName(list.get(0).getMsgName());
            }
            else {
                e.setMsgName(null);
            }
        }
        return page.setRecords(msgEntities);
    }

    @Override
    public MqMsgEntity doRecieveMsg(String msgJsonStr) {
        MqMsgEntity msgEntity = receiveMsg(msgJsonStr);
        receiveExecutorService.execute(() -> processRecieveMsg(msgEntity));
        return msgEntity;
    }

    /**
     * ?????????????????? - ??????
     *
     * @param msgJsonStr
     * @return
     */
    public MqMsgEntity receiveMsg(String msgJsonStr) {
        log.info("??????????????????: {}", msgJsonStr);

        String msgKey = "";
        boolean savedMsgId = false;
        MqMsgEntity msgEntity = null;

        try {
            // 1?????????????????????????????????NodeInfo??????
            NodeInfo nodeInfo = null;
            try {
                nodeInfo = JSON.parseObject(msgJsonStr, NodeInfo.class);
            }
            catch (JSONException jex) {
                log.error("??????????????????", jex);
            }
            if (nodeInfo == null || nodeInfo.getMessageHeader() == null || nodeInfo.getTables() == null) {
                //???????????????????????????????????????
                log.error("????????????: ??????-{}", msgJsonStr);
                String errMsgId = DigestUtils.md5DigestAsHex(msgJsonStr.getBytes());
                msgKey = Constants.REDIS_KEY_MESSAGE_ID + errMsgId;
                savedMsgId = redisUtils.redisTemplate.opsForValue().setIfAbsent(msgKey, msgJsonStr, 24, TimeUnit.HOURS);
                if (savedMsgId) saveExceptionMsg(msgJsonStr);
                return msgEntity;
            }

            // 2????????????????????????????????????
            MsgHeaderDto messageHeader = nodeInfo.getMessageHeader();
            //String messageId = messageHeader.getMessageId();
            String messageId = ("0").equalsIgnoreCase(messageHeader.getMessageId()) ? messageHeader.getUUID() : messageHeader.getMessageId();

            if (StringUtils.isEmpty(messageId)) {
                messageId = DigestUtils.md5DigestAsHex(msgJsonStr.getBytes());
                messageHeader.setMessageId(messageId);
            }
            msgKey = Constants.REDIS_KEY_MESSAGE_ID + messageId;
            savedMsgId = redisUtils.redisTemplate.opsForValue().setIfAbsent(msgKey, messageHeader.getMessageTypeId(), 24, TimeUnit.HOURS);
            if (!savedMsgId) {
                //???????????????
                log.info("redis?????????messageId-{}?????????????????????", messageId);
                return msgEntity;
            }

            // ???????????????db
            msgEntity = saveMsg(nodeInfo, msgJsonStr);
        }
        catch (Exception ex) {
            if (savedMsgId) {//?????????????????????redis??????
                redisUtils.del(msgKey);
            }
            throw new RuntimeException(ex);
        }
        return msgEntity;
    }

    /**
     * ?????????????????? - ????????????
     *
     * @param msgEntity
     * @return
     */
    public void processRecieveMsg(MqMsgEntity msgEntity) {
        try {
            if (msgEntity == null) {
                return;
            }
            //????????????
            Object msgPojo = this.getJMsgObj(msgEntity.getMsg());
            //???????????????
            String msgTypeId = msgEntity.getMsgId();
            MqConfigEntity msgConfig = this.getMsgConfig(msgTypeId);

            // ?????????????????????????????????url?????????????????????????????????    testUrl = http://localhost:2220/aaa/ccc    lb://server-name/aaa/ccc
            HttpHeaders headers = new HttpHeaders();
            MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
            headers.setContentType(type);
            HttpEntity<Object> requestEntity = new HttpEntity<>(msgPojo, headers);
            String url = "http://" + msgConfig.getServiceId() + msgConfig.getUri();
            boolean isSucc;
            ResponseEntity<JSONObject> responseEntity = null;
            String failReason;
            try {
                responseEntity = restTemplate.postForEntity(url, requestEntity, JSONObject.class);
                JSONObject retObj = responseEntity.getBody();
                log.info("???????????????????????? json = {}", retObj);
                isSucc = retObj.getString("retCode").equalsIgnoreCase("0");
                //failReason = isSucc ? "" : retObj.getString("retDesc");
                failReason = "";
                if (!isSucc) {
                    failReason = retObj.getString("retMessage");
                    if (StringUtil.isEmpty(failReason)) failReason = retObj.getString("errorMessage");
                }
            }
            catch (Exception ex) {
                log.error("????????????????????????????????????", ex);
                isSucc = false;
                failReason = ex.toString();
            }

            //??????????????????????????????????????????????????????????????????
            if (msgEntity.getMsgState().intValue() == 2) {
                msgEntity.setMsgNum(msgEntity.getMsgNum() == null ? 0l : (msgEntity.getMsgNum() + 1l));
                msgEntity.setRetryDt(LocalDateTime.now());
            }

            //????????????????????????
            if (isSucc) {
                // ????????????????????????
                msgEntity.setMsgState(3);
                msgEntity.setReceiveDt(LocalDateTime.now());
                msgEntity.setException("");
                this.updateById(msgEntity);
                log.info("?????????????????????????????????...");
            }
            else {
                msgEntity.setMsgState(2);
                msgEntity.setException(failReason);
                this.updateById(msgEntity);
                log.info("?????????????????????????????????...");
            }
        }
        catch (Exception ex) {
            updateFailedMessage(msgEntity, ex);
            log.error("??????????????????????????????", ex);
        }
    }

    @Override
    public MqMsgEntity doSendMsg(NodeInfo nodeInfo) {
        MqMsgEntity msgEntity = sendMsg(nodeInfo);
        sendExecutorService.execute(() -> processSendMsg(msgEntity));
        return msgEntity;
    }

    public MqMsgEntity sendMsg(NodeInfo nodeInfo) {
        log.info("????????????????????????: {}", nodeInfo);

        String msgKey = "";
        boolean savedMsgId = false;
        MqMsgEntity msgEntity = null;
        try {
            // 1????????????????????????
            MsgHeaderDto messageHeader = nodeInfo.getMessageHeader();
            ObjectMapper objectMapper = new ObjectMapper();
            String messageId = messageHeader.getMessageId();
            if (StringUtils.isEmpty(messageId)) {
                log.info("????????????messageId: {}", nodeInfo);
                String msgStr = objectMapper.writeValueAsString(nodeInfo);
                messageId = DigestUtils.md5DigestAsHex(msgStr.getBytes());
                messageHeader.setMessageId(messageId);
                messageHeader.setUUID(messageId);
            }

            SimpleDateFormat sim1 = new SimpleDateFormat("yyyyMMddhhmmss");
            if (StringUtils.isEmpty(messageHeader.getSendDate()))
                messageHeader.setSendDate(sim1.format(new Date()).substring(0, 8));
            if (StringUtils.isEmpty(messageHeader.getSendTime()))
                messageHeader.setSendTime(sim1.format(new Date()).substring(8));
            //nodeInfo.getMessageHeader().setFlag(9);
            if (StringUtils.isEmpty(messageHeader.getSender())) messageHeader.setSender(sysNo);
            if (StringUtils.isEmpty(messageHeader.getSvcName())) messageHeader.setSvcName(sysName);
            //nodeInfo.getMessageHeader().setMsg("");

            // 2????????????????????????????????????
            messageHeader.setMessageId(messageId);
            msgKey = Constants.REDIS_KEY_MESSAGE_ID + messageId;
            savedMsgId = redisUtils.redisTemplate.opsForValue().setIfAbsent(msgKey, messageHeader.getMessageTypeId(), 24, TimeUnit.HOURS);
            if (!savedMsgId) {
                //???????????????
                log.info("??????????????????messageId-{}?????????????????????", messageId);
                return msgEntity;
            }

            // 3???????????????
            String msgJson = objectMapper.writeValueAsString(nodeInfo);
            msgEntity = saveMsg(nodeInfo, msgJson);
        }
        catch (Exception ex) {
            if (savedMsgId) {//?????????????????????redis??????
                redisUtils.del(msgKey);
            }
            throw new RuntimeException(ex);
        }
        return msgEntity;
    }

    public void processSendMsg(MqMsgEntity msgEntity) {
        try {
            if (msgEntity == null) {
                return;
            }

            //???????????????
            String msgTypeId = msgEntity.getMsgId();
            MqConfigEntity msgConfig = this.getMsgConfig(msgTypeId);
            if (msgConfig == null) {
                throw new QTBusinessException("?????????????????? - " + msgTypeId);
            }

            boolean isSucc = false;
            String failReason = "";
            String msg = msgEntity.getMsg();
            if (msgConfig.getMsgType() == 3) {
                // http rest
                // ?????????????????????????????????url?????????????????????????????????    testUrl = http://localhost:2220/aaa/ccc
                HttpHeaders headers = new HttpHeaders();
                MediaType type = MediaType.parseMediaType("application/json; charset=UTF-8");
                headers.setContentType(type);
                if (msgConfig.getMsgId().substring(4, 8).equalsIgnoreCase("1020") || msgConfig.getMsgId().substring(4, 8).equalsIgnoreCase("1072")) {
                    //????????????: 1020  3pl:1072
                    String timeStr = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "001";
                    headers.add("sourceAppCode", msgConfig.getMsgId().substring(4, 8));//??????????????????
                    headers.add("serviceId", msgConfig.getMsgId());//?????????
                    headers.add("msgSendTime", timeStr);//?????????
                }
                HttpEntity<Object> requestEntity = new HttpEntity<>(msg, headers);
                String url = "http://" + msgConfig.getServiceId() + msgConfig.getUri();
                try {
                    if (msgConfig.getMsgId().substring(4, 8).equalsIgnoreCase("1020") || msgConfig.getMsgId().substring(4, 8).equalsIgnoreCase("1072")) {
                        ResponseEntity<String> responseEntity = restSendTemplate.postForEntity(url, requestEntity, String.class);
                        log.info("????????????ixbus?????? code = {}, body = {}", responseEntity.getStatusCode(), responseEntity.getBody());
                        isSucc = HttpStatus.OK == responseEntity.getStatusCode();
                    }
                    else {
                        ResponseEntity<JSONObject> responseEntity = restSendTemplate.postForEntity(url, requestEntity, JSONObject.class);
                        JSONObject retObj = responseEntity.getBody();
                        log.info("?????????????????? json = {}", retObj);
                        isSucc = retObj.getString("retCode").equalsIgnoreCase("0000000");
                        failReason = isSucc ? "" : retObj.getString("retDesc");
                    }
                }
                catch (Exception ex) {
                    log.error("????????????????????????", ex);
                    isSucc = false;
                    failReason = ex.toString();
                }
            }
            else if (msgConfig.getMsgType() == 1) {
                // ibm mq
                try {
                    //zk send
                    String queue = msgConfig.getQueue();
                    if (msgConfig.getMsgId().startsWith("S1125W")) {
                        NodeInfo nodeInfo = JSON.parseObject(msg, NodeInfo.class);
                        String xmlMsg = XmlUtil.node2Xml(nodeInfo);
                        queue = queue + nodeInfo.getMessageHeader().getSvcName();
                        log.error("sending zk message queue-{}, jsonmsg-{}, xmlmsg-{}", queue, msg, xmlMsg);
                        jmsOperations.convertAndSend(queue, xmlMsg);
                    }
                    else {
                        jmsOperations.convertAndSend(queue, msg);
                    }
                    isSucc = true;
                }
                catch (Exception ex) {
                    log.error("sending mq message failed", ex);
                    isSucc = false;
                    failReason = ex.toString();
                }
            }
            else {
                log.error("??????????????????????????????-{}", msg);
            }

            //??????????????????????????????????????????????????????????????????
            if (msgEntity.getMsgState().intValue() == 2) {
                msgEntity.setMsgNum(msgEntity.getMsgNum() == null ? 0l : (msgEntity.getMsgNum() + 1l));
                msgEntity.setRetryDt(LocalDateTime.now());
            }

            //????????????????????????
            if (isSucc) {
                // ????????????????????????
                msgEntity.setMsgState(3);
                msgEntity.setReceiveDt(LocalDateTime.now());
                msgEntity.setException("");
                this.updateById(msgEntity);
                log.info("?????????????????????????????????...");
            }
            else {
                msgEntity.setMsgState(2);
                msgEntity.setException(failReason);
                this.updateById(msgEntity);
                log.info("?????????????????????????????????...");
            }
        }
        catch (Exception ex) {
            updateFailedMessage(msgEntity, ex);
            log.error("????????????????????????", ex);
        }
    }

    public void processMsg(MqMsgEntity msgEntity) {
        String msgTypeId = msgEntity.getMsgId();

        if (Pattern.matches(sysNo, msgTypeId)) {
            // ??????????????????
            processSendMsg(msgEntity);
        }
        else {
            // ??????????????????????????????????????????
            processRecieveMsg(msgEntity);
        }
    }

    public MqMsgEntity manualSendRecvMsg(String jsonStr) {
        NodeInfo nodeInfo = JSON.parseObject(jsonStr, NodeInfo.class);
        String msgTypeId = nodeInfo.getMessageHeader().getMessageTypeId();

        MqMsgEntity msgEntity;
        if (Pattern.matches(sysNo, msgTypeId)) {
            // ??????????????????
            msgEntity = sendMsg(nodeInfo);
            processSendMsg(msgEntity);
        }
        else {
            // ??????????????????
            msgEntity = receiveMsg(jsonStr);
            processRecieveMsg(msgEntity);
        }
        return msgEntity;
    }

    @DSTransactional
    @Override
    public void archiveMsg(List<MqMsgEntity> msgEntityList) {
        // ????????????
        List<MqMsgHEntity> list = new ArrayList<>();
        List<Long> msgIds = new ArrayList<>();
        for (MqMsgEntity msgEntity : msgEntityList) {
            msgIds.add(msgEntity.getSid());
            list.add(copyToHMsg(msgEntity));
        }
        msgHService.saveBatch(list);

        // ????????????
        this.removeByIds(msgIds);
    }

    @Override
    public List<MsgStatCountVO> countByBO(MsgStatCountBO bo) {
        return msgMapper.countByBO(bo);
    }

    @Override
    public List<MsgStatCountVO> countMsgAndMsgHByBO(MsgStatCountBO bo) {
        List<MsgStatCountVO> msgCounts = this.countByBO(bo);
        List<MsgStatCountVO> msgHCounts = msgHService.countByBO(bo);

        List<MsgStatCountVO> all = new LinkedList<>();
        all.addAll(msgCounts);
        all.addAll(msgHCounts);

        return msgStatCountVOAggregation(all);
    }

    @Override
    public Long countMsgNumByBO(MsgStatCountBO bo) {
        Long res = msgMapper.countMsgNumByBO(bo);
        return res == null ? 0 : res;
    }

    @Override
    public Long countMsgNumByBOWithMsgH(MsgStatCountBO bo) {
        Long msgCount = this.countMsgNumByBO(bo);
        Long msgHCount = msgHService.countMsgNumByBO(bo);
        return msgCount + msgHCount;
    }

    /**
     * ????????????
     */
    private static List<MsgStatCountVO> msgStatCountVOAggregation(List<MsgStatCountVO> list) {
        HashMap<String, MsgStatCountVO> map = new HashMap<>();
        for (MsgStatCountVO vo : list) {
            String key = vo.getMsgSender() + "-" + vo.getMsgReceiver() + "-" + vo.getMsgState() + "-" + vo.getDateNum();
            MsgStatCountVO entry = map.get(key);
            if (entry == null) {
                map.put(key, vo);
            }
            else {
                entry.setCount(entry.getCount() + vo.getCount());
                map.put(key, entry);
            }
        }
        List<MsgStatCountVO> res = new LinkedList<>();
        for (String key : map.keySet()) {
            res.add(map.get(key));
        }
        return res;
    }

    public Object getJMsgObj(String msgJsonStr) {
        NodeInfo node = JSON.parseObject(msgJsonStr, NodeInfo.class);
        if (node == null) {
            throw new RuntimeException(PARAM_ERR_MSG);
        }
        MsgHeaderDto messageHeader = node.getMessageHeader();
        if (messageHeader == null) {
            throw new RuntimeException(PARAM_ERR_MSG);
        }
        else {
            // ??????????????????????????????????????????????????????????????????????????????????????????????????????json??????
            List<TablesDto> tables = node.getTables();
            // ?????????
            String msgTypeId = messageHeader.getMessageTypeId();

            MqConfigEntity msgConfig = this.getMsgConfig(msgTypeId);
            if (msgConfig == null) {

                throw new RuntimeException(INTERFACDE_CODE_NOT_EXISTS);
            }
            String classNamesStr = msgConfig.getClassName();
            String[] classNames = classNamesStr.split(",");
            //????????????????????????
            Map<String, String> mapInnerClassName = this.initClassNameMap(classNames);
            Object result;
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                // ????????????????????????????????????
                if (classNames.length == 1) {
                    List<Object> objs = new ArrayList<>();
                    Class<?> forName = Class.forName(classNames[0]);
                    List<Map<String, Object>> rows = tables.get(0).getRows();
                    for (Map<String, Object> map : rows) {
                        Object obj = objectMapper.readValue(JSON.toJSONString(map), forName);
                        setCommonParam(msgTypeId, obj);
                        objs.add(obj);
                    }
                    result = objs;
                }
                else {
                    // ????????????????????????class???????????????????????????????????????class
                    String outerClassName = classNames[0];
                    Class<?> forName = Class.forName(outerClassName);
                    Object outerObj = forName.newInstance();
                    setCommonParam(msgTypeId, outerObj);

                    for (TablesDto tablesDto : tables) {
                        String fieldName = tablesDto.getName().toLowerCase();
                        String className = mapInnerClassName.get(fieldName);
                        Class<?> fieldObj = Class.forName(className);
                        // ????????????????????????
                        Field declaredField = forName.getDeclaredField(fieldName);
                        if (declaredField == null) {

                            throw new RuntimeException(COLUMN_NOT_EXISTS);
                        }

                        List<Object> objList = new ArrayList<>();
                        tablesDto.getRows().forEach(row -> {
                            try {
                                objList.add(objectMapper.readValue(JSON.toJSONString(row), fieldObj));
                            }
                            catch (JsonProcessingException e) {
                                throw new RuntimeException(e);
                            }
                        });
                        PropertyDescriptor property = new PropertyDescriptor(declaredField.getName(), forName);
                        Method writeMethod = property.getWriteMethod();

                        writeMethod.invoke(outerObj, objList);
                    }

                    result = outerObj;
                }
                log.info("???????????????????????????-{}", result);
                return result;
            }
            catch (Exception e) {
                log.error("???????????????????????????", e);
                return new RuntimeException("??????????????????????????? - " + messageHeader.getMessageTypeId());
            }
        }
    }

    private void updateFailedMessage(MqMsgEntity msgEntity, Exception ex) {
        try {
            msgEntity.setException(ex.getMessage());
            msgEntity.setMsgState(2);
            msgEntity.setMsgNum(msgEntity.getMsgNum() == null ? 0l : (msgEntity.getMsgNum() + 1l));
            this.updateById(msgEntity);
        }
        catch (Exception exc) {
            log.error("update_message_failed_status exception", exc);
        }
    }

    private void setCommonParam(String msgId, Object obj) throws IntrospectionException, IllegalAccessException, InvocationTargetException {
        PropertyDescriptor propertyDescriptor = new PropertyDescriptor("messageTypeId", obj.getClass());
        Method writeMethod = propertyDescriptor.getWriteMethod();
        writeMethod.invoke(obj, msgId);
    }

    private Map<String, String> initClassNameMap(String[] classNames) {
        Map<String, String> fieldMap = new HashMap<>();
        int length = classNames.length;
        if (length <= 1) {
            return fieldMap;
        }
        boolean isingular = ((length - 1) % 2 == 1);
        fieldMap = new HashMap<>(isingular ? length : length - 1, 0.99f);
        for (int i = 1; i < length; i++) {
            String className = classNames[i].substring(classNames[i].lastIndexOf(".") + 1).toLowerCase();
            fieldMap.put(className, classNames[i]);
            /* ????????????????????????????????????????????????????????????????????????R1058112506??????body??????r1058112506body,circle??????r1058112506circle
             ?????????????????????????????????key
            */
            if (className.contains("body")) {
                fieldMap.put("body", classNames[i]);
            }
            else {
                fieldMap.put("circle", classNames[i]);
            }
        }
        return fieldMap;
    }

    private MqMsgHEntity copyToHMsg(MqMsgEntity msgEntity) {
        String msg = msgEntity.getMsg();
        Long sid = msgEntity.getSid();
        MqMsgHEntity msgHEntity = new MqMsgHEntity();
        msgHEntity.setMsgSid(sid);
        msgHEntity.setMsgId(msgEntity.getMsgId());
        msgHEntity.setMsg(msg);
        msgHEntity.setMsgState(msgEntity.getMsgState());
        msgHEntity.setMessageId(msgEntity.getMessageId());
        msgHEntity.setSendDt(msgEntity.getSendDt());
        msgHEntity.setReceiveDt(msgEntity.getReceiveDt());
        msgHEntity.setRetryDt(msgEntity.getRetryDt());
        msgHEntity.setException(msgEntity.getException());

        return msgHEntity;
    }

    private MqConfigEntity getMsgConfig(String msgTypeId) {
        QueryWrapper<MqConfigEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("msg_Id", msgTypeId);
        MqConfigEntity msgConfig = mqConfigService.getOne(wrapper, true);
        return msgConfig;
    }

    private MqMsgEntity saveMsg(NodeInfo nodeInfo, String msgJson) {
        // ??????????????????
        MsgHeaderDto messageHeader = nodeInfo.getMessageHeader();
        String messageTypeId = messageHeader.getMessageTypeId();
        String messageId = messageHeader.getMessageId();

        // ??????????????????
        MqMsgEntity msgEntity = new MqMsgEntity();
        msgEntity.setMsgId(messageTypeId);
        msgEntity.setMessageId(messageId);
        msgEntity.setMsg(msgJson);
        msgEntity.setSendDt(LocalDateTime.now());
        msgEntity.setMsgState(1);
        msgEntity.setMsgNum(0L);

        // ????????????
        boolean ret = this.save(msgEntity);
        return msgEntity;
    }

    private void saveExceptionMsg(String msg) {
        // ??????????????????
        MqMsgEntity msgEntity = new MqMsgEntity();
        msgEntity.setMessageId(DigestUtils.md5DigestAsHex(msg.getBytes()));
        msgEntity.setMsg(msg);
        msgEntity.setSendDt(LocalDateTime.now());
        msgEntity.setMsgState(2);
        msgEntity.setMsgNum(0L);

        // ????????????
        this.save(msgEntity);
    }
}
