package com.qingtian.lcpes.modules.authpre.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.nimbusds.jose.JWSObject;
import com.qingtian.lcpes.base.util.IPUtils;
import com.qingtian.lcpes.modules.authpre.entity.AcUserEntity;
import com.qingtian.lcpes.modules.authpre.service.AcUserService;
import com.qingtian.lcpes.modules.authpre.service.AuthpreService;
import com.qingtian.lcpes.modules.authpre.util.AesUtil;
import com.qingtian.lcpes.modules.authpre.vo.LoginUser;
import com.qingtian.lcpes.modules.sys.vo.SysLogVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class AuthpreServiceImpl implements AuthpreService {

    public static final String AES_KEY = "12345678901234561234567890123456";
    @Value("${qingtian.auth.address}")
    private String authAddress;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private AcUserService acUserService;

    @Value("${qingtian.auth.token}")
    private String authTokenVal;

    @Override
    public String login(String loginId, String password, HttpServletResponse response) throws IOException {
        //????????????url
        //dev:
        String url = authAddress + "/ac/login/dologin.action";
        //String url = "http://10.88.246.110:9001/ac/login/doLoginForNewPortal.action";

        //pro:
        //String url = "http://10.188.57.73/ac/login/dologin.action";
        //String url = "http://10.188.57.73/ac/login/doLoginForNewPortal.action";

        //????????????
        password = AesUtil.decryptStr(password, AES_KEY);
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("username", loginId);
        postMethod.addParameter("password", password);
        // LOGGER.info("++++++++++??????url:+++++++++++++++++" + url + "?username=" + loginId + "&password=" + password);
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        // LOGGER.info("-------httpStatus:-------:" + httpStatus);
        String str = postMethod.getResponseBodyAsString();
        // LOGGER.info("-------jsonstr-------:" + str);
        JSONObject jsonobj = JSON.parseObject(str);

        String res = jsonobj.getString("success");
        if (res.equalsIgnoreCase("false")) {
            setLoginLog(loginId, 2);
            return null;
        }
        else {
            //??????token ,key?????? token:10011810
            this.stringRedisTemplate.opsForValue().set("authtoken:" + loginId, str, 60, TimeUnit.MINUTES);

            //?????????????????????redis
            acUserService.setBizToRedis(loginId);

            //??????token
            //this.getTokenFromRedis(loginId);

            this.setTokenToCookies(loginId, response);
            setLoginLog(loginId, 1);

        }
        //??????????????????
        /* ?????????????????????????????????????????????
        if (!stringRedisTemplate.hasKey("user:" + loginId)) {
            return this.getUserInfo(loginId, httpClient);
        } else {
            return stringRedisTemplate.opsForValue().get("user:" + loginId);
        }*/
        //return this.getUserInfo(loginId, httpClient);
        return str;

    }


    @Override
    public String splogin(String loginSrc, String loginId, String password) throws IOException {
        //????????????url
        //dev:
        //String url = authAddress + "/ac/login/dologin.action";
        String url = authAddress + "/ac/login/doLoginForNewPortal.action";
        //pro:
        //String url = "http://10.188.57.73/ac/login/dologin.action";
        //String url = "http://10.188.57.73/ac/login/doLoginForNewPortal.action";

        //????????????
        password = AesUtil.decryptStr(password, AES_KEY);
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("username", loginSrc);
        postMethod.addParameter("password", password);
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        // LOGGER.info("-------httpStatus:-------:" + httpStatus);
        String str = postMethod.getResponseBodyAsString();
        // LOGGER.info("-------jsonstr-------:" + str);
        JSONObject jsonobj = JSON.parseObject(str);

        String res = jsonobj.getString("success");
        if (res.equalsIgnoreCase("false")) {
            setLoginLog(loginSrc, 2);
            return null;
        }
        else {
            String tokenStr = jsonobj.getJSONObject("data").getJSONObject("userBean").getString("token");
            //??????token ,key?????? token:10011810
            this.stringRedisTemplate.opsForValue().set("authtoken:" + loginId, tokenStr, 60, TimeUnit.MINUTES);

            //?????????????????????redis
            acUserService.setBizToRedis(loginId);

            setLoginLog(loginId, 1);

        }
        //??????????????????
        /* ?????????????????????????????????????????????
        if (!stringRedisTemplate.hasKey("user:" + loginId)) {
            return this.getUserInfo(loginId, httpClient);
        } else {
            return stringRedisTemplate.opsForValue().get("user:" + loginId);
        }*/
        //return this.getUserInfo(loginId, httpClient);
        return str;

    }

    /*
     * ??????????????????
     * @param loginId
     * @param flag 1??????  2??????
     */
    void setLoginLog(String loginId, Integer flag) {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String url = request.getRequestURL().toString() + "?" + request.getQueryString();
        String ip = IPUtils.getIp(request);

        //??????????????????
        SysLogVO sysLogVO = new SysLogVO();
        // ????????????????????????
        long startTime = System.currentTimeMillis();
        sysLogVO.setUserId(loginId);//????????????id
        sysLogVO.setLogType("1");//????????????
        sysLogVO.setLogContent("????????????");
        sysLogVO.setOperateType("??????");
        sysLogVO.setRequestUrl(url);
        sysLogVO.setIp(ip);
        startTime = System.currentTimeMillis();//??????????????????
        sysLogVO.setCreatedDt(LocalDateTime.now());//??????????????????
        // ???????????????
        // LOGGER.info("???????????????{}"+sysLogVO.getUserId());
        sysLogVO.setResultMsg(1 == flag ? "??????" : "??????");
        sysLogVO.setCostTime(BigDecimal.valueOf(System.currentTimeMillis() - startTime));//????????????
    }

    /*
     * ??????????????????
     * @param loginId
     * @param flag 1??????  2??????
     */
    void setLogoutLog(String loginId, Integer flag) {

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String url = request.getRequestURL().toString() + "?" + request.getQueryString();
        String ip = IPUtils.getIp(request);

        //??????????????????
        SysLogVO sysLogVO = new SysLogVO();
        //????????????????????????
        long startTime = System.currentTimeMillis();
        sysLogVO.setUserId(loginId);//????????????id
        sysLogVO.setLogType("1");//????????????
        sysLogVO.setLogContent("????????????");
        sysLogVO.setOperateType("??????");
        sysLogVO.setRequestUrl(url);
        sysLogVO.setIp(ip);
        startTime = System.currentTimeMillis();//??????????????????
        sysLogVO.setCreatedDt(LocalDateTime.now());//??????????????????
        sysLogVO.setResultMsg(1 == flag ? "??????" : "??????");
        //??????????????????
        long endTime = System.currentTimeMillis();
        //????????????
        Long costTime = ((endTime - startTime));
        sysLogVO.setCostTime(BigDecimal.valueOf(costTime));//????????????
    }

    @Override
    public String getLoginId(HttpServletRequest request) {
        String token = request.getHeader("AuthUserToken");
        try {
            JWSObject jwsObject = JWSObject.parse(token);
            net.minidev.json.JSONObject jsonObject = jwsObject.getPayload().toJSONObject();
            JSONObject jo = JSON.parseObject(jsonObject.toString());
            String sub = jo.getString("sub");
            if (sub.length() <= 12) {
                return sub;
            }
            JSONObject joLoginId = JSON.parseObject(sub);
            return joLoginId.getString("loginId");
        }
        catch (ParseException ex) {
            ex.printStackTrace();
            return null;
        }

    }

    @Override
    public String getTokenFromRedis(String loginId) {
        String tokenStr = stringRedisTemplate.opsForValue().get("authtoken:" + loginId);
        if (isJSON2(tokenStr)) {
            JSONObject jsonobj = JSON.parseObject(tokenStr);
            String res = jsonobj.getString("data");
            JSONObject jsonobjt = JSON.parseObject(res);
            String token = jsonobjt.getString("token");
            return token;
        }
        else {
            return tokenStr;
        }
    }

    public static boolean isJSON2(String str) {
        boolean result = false;
        try {
            Object obj = JSON.parse(str);
            result = true;
        }
        catch (Exception e) {
            result = false;
        }
        return result;
    }


    @Override
    public LoginUser getUserInfo(String loginId) {
        String userInfo = stringRedisTemplate.opsForValue().get("user:" + loginId);
        JSONObject jsonobj = JSON.parseObject(userInfo);
        try {
            String data = jsonobj.getString("data");
            LoginUser loginUser = new LoginUser();
            AcUserEntity acUser = this.acUserService.getAcUserByLoginId(loginId);
            if (acUser != null) {
                loginUser.setUserSid(acUser.getSid());
            }
            loginUser.setLoginId(loginId);
            loginUser.setOrgCode(JSON.parseObject(data).getString("orgCode"));
            loginUser.setOrgName(JSON.parseObject(data).getString("orgName"));
            loginUser.setPositionCode(JSON.parseObject(data).getString("positionCode"));
            loginUser.setPositionName(JSON.parseObject(data).getString("positionName"));
            loginUser.setUsername(JSON.parseObject(data).getString("username"));
            return loginUser;
        }
        catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

    }

    @Override
    public String getAuthInfoFromRedis(String loginId, String sysId) throws IOException {
        //?????????????????????url
        //pro:
        //String url = "http://10.188.57.73/ac/ac-user/getUserInfoAndResourceAssignTree.action";
        //dev:
        String url = authAddress + "/ac/ac-user/getUserInfoAndResourceAssignTree.action";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("usercode", loginId);
        postMethod.addParameter("appCode", sysId);
        //Authorization ????????????????????????
        postMethod.setRequestHeader("Authorization", this.getTokenFromRedis(loginId));
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        // LOGGER.info("-------???????????????????????????-------");
        // LOGGER.info("-------httpStatus-------:" + httpStatus);
        //????????????????????????????????????
        String str = postMethod.getResponseBodyAsString();
        //stringRedisTemplate.opsForValue().set("user:" + username, str,30, TimeUnit.MINUTES);
        //????????????????????? ,key?????? user:10011810
        stringRedisTemplate.opsForValue().set("user:" + loginId, str);


        //??????auth??????????????????itmp???
        if (authTokenVal.equalsIgnoreCase("YES")) {
            acUserService.asynUserInfo(loginId);
        }

        return stringRedisTemplate.opsForValue().get("user:" + loginId);
    }

    @Override
    public String getUserBtnAuth(String loginId) throws IOException {
        //????????????????????????url
        //pro:
        //String url = "http://10.188.57.73/ac/ac-resource/getUserAssignedResourceTree.action";
        //dev:
        String url = authAddress + "/ac/ac-resource/getUserAssignedResourceTree.action";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("loginId", loginId);
        postMethod.addParameter("appCode", "TMP");
        //resType : 2 ????????????
        postMethod.addParameter("resType", "2");
        //Authorization ????????????????????????
        postMethod.setRequestHeader("Authorization", this.getTokenFromRedis(loginId));
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        String str = postMethod.getResponseBodyAsString();
        //stringRedisTemplate.opsForValue().set("user:" + username, str,30, TimeUnit.MINUTES);
        //???????????????????????? ,key?????? user:btn: 10011810
        stringRedisTemplate.opsForValue().set("user:btn:" + loginId, str);

        return stringRedisTemplate.opsForValue().get("user:btn:" + loginId);
    }

    @Override
    public String getUserPageAuth(String loginId) throws IOException {
        //????????????????????????url
        //pro:
        //String url = "http://10.188.57.73/ac/ac-resource/getUserAssignedResourceTree.action";
        //dev:
        String url = authAddress + "/ac/ac-resource/getUserAssignedResourceTree.action";
        HttpClient httpClient = new HttpClient();
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("loginId", loginId);
        postMethod.addParameter("appCode", "TMP");
        //resType : 2 ????????????
        postMethod.addParameter("resType", "1");
        //Authorization ????????????????????????
        postMethod.setRequestHeader("Authorization", this.getTokenFromRedis(loginId));
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        String str = postMethod.getResponseBodyAsString();
        //stringRedisTemplate.opsForValue().set("user:" + username, str,30, TimeUnit.MINUTES);
        //???????????????????????? ,key?????? user:page: 10011810
        stringRedisTemplate.opsForValue().set("user:page:" + loginId, str);

        return stringRedisTemplate.opsForValue().get("user:page:" + loginId);
    }

    @Override
    public Boolean logout() {
        Boolean result = false;
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String loginId = getLoginId(request);
        try {
            if (stringRedisTemplate.hasKey("authtoken:" + loginId)) {
                stringRedisTemplate.delete("authtoken:" + loginId);
            }

            setLogoutLog(loginId, 1);
            result = true;
        }
        catch (Exception e) {
            setLogoutLog(loginId, 2);
        }
        return result;
    }

    @Override
    public void reset(String loginId) {
        if (stringRedisTemplate.hasKey("authtoken:" + loginId)) {
            stringRedisTemplate.expire("authtoken:" + loginId, 60, TimeUnit.MINUTES);
        }
    }


    void setTokenToCookies(String loginId, HttpServletResponse response) {
        String token = this.getTokenFromRedis(loginId);
        Cookie e = new Cookie("AuthUserToken", token);
        //token??????cookie
        response.addCookie(e);
        Cookie u = new Cookie("LoginId", loginId);
        //loginId??????cookie
        response.addCookie(u);

    }


    /**
     * ??????????????????????????????????????????
     * ?????????redis
     *
     * @param username
     * @param httpClient
     * @return
     * @throws IOException
     */
    public String getUserInfo(String username, HttpClient httpClient) throws IOException {
        //?????????????????????url
        //pro:
        //String url = "http://10.188.57.73/ac/ac-user/getUserInfoAndResourceAssignTree.action";
        //dev:
        String url = authAddress + "/ac/ac-user/getUserInfoAndResourceAssignTree.action";
        PostMethod postMethod = new PostMethod(url);
        postMethod.addParameter("usercode", username);
        postMethod.addParameter("appCode", "TMP");
        //?????????????????????
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
        //??????????????????
        postMethod.getParams().setParameter(HttpMethodParams.SO_TIMEOUT, 5000);
        int httpStatus = httpClient.executeMethod(postMethod);
        // LOGGER.info("-------???????????????????????????-------");
        // LOGGER.info("-------httpStatus-------:" + httpStatus);
        //????????????????????????????????????
        String str = postMethod.getResponseBodyAsString();
        //stringRedisTemplate.opsForValue().set("user:" + username, str,30, TimeUnit.MINUTES);
        //????????????????????? ,key?????? user:10011810
        stringRedisTemplate.opsForValue().set("user:" + username, str);
        return stringRedisTemplate.opsForValue().get("user:" + username);
    }

}
