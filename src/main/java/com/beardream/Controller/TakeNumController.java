package com.beardream.Controller;

import com.beardream.Utils.Constants;
import com.beardream.Utils.Json;
import com.beardream.Utils.ResultUtil;
import com.beardream.Utils.TextUtil;
import com.beardream.dao.BusinessMapper;
import com.beardream.dao.NumberMapper;
import com.beardream.dao.UserMapper;
import com.beardream.enums.ResultEnum;
import com.beardream.exception.UserException;
import com.beardream.model.Business;
import com.beardream.model.Number;
import com.beardream.model.Result;
import com.beardream.model.User;
import com.beardream.service.TakeNumService;
import io.swagger.annotations.Api;
import me.chanjar.weixin.common.exception.WxErrorException;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpTemplateMsgServiceImpl;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateData;
import me.chanjar.weixin.mp.bean.template.WxMpTemplateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;

/**
 * Created by laxzh on 2017/5/6.
 * 取号控制器
 */
@RestController
@RequestMapping("/api/mobile/takeNum")
@Api(value = "取号服务",description = "提供RESTful风格API的取号的操作")
public class TakeNumController {

    private final static Logger mLogger = LoggerFactory.getLogger("TakeNumController.class");

    @Value("${TemplateId}")
    private String mTemplateId;

    @Value("${push_message}")
    private String mPushMessage;

    @Autowired
    private TakeNumService mTakeNumService;

    @Autowired
    private WxMpService mWxMpService;

    @Autowired
    private UserMapper mUserMapper;

    @Autowired
    private BusinessMapper mBusinessMapper;

    @Autowired
    private NumberMapper mNumberMapper;

    // 取号
    @GetMapping("/takeNum")
    public Result takeNum(Number number, HttpSession session){
        if (session.getAttribute(Constants.USER) == null){
            //未登录
            throw new UserException(ResultEnum.Logout);
        }
        User user = Json.fromJson((String) session.getAttribute(Constants.USER), User.class);

        if (!TextUtil.isEmpty(number.getBusinessId()) && !TextUtil.isEmpty(number.getPeopleNum())){
            return ResultUtil.error(-1,"请选择商家并填写人数后又提交");
        }
        number.setUserId(user.getUserId());
        number.setIsExpired((byte) 0); // 设置为没有过期
        Result num = mTakeNumService.takeNum(number);
        if (num.getCode() != -1)
            return ResultUtil.success(num.getData());
        else
            return ResultUtil.error(-1,num.getMsg());
    }

    // 获取用户所取到对应商家的号  eg:用户已取号，获取他的取号码，以及他需要等待多少桌
    @GetMapping("/getNum")
    public Result getNum(Number number, HttpSession session){
        if (session.getAttribute(Constants.USER) == null){
            //未登录
            throw new UserException(ResultEnum.Logout);
        }
        User user = Json.fromJson((String) session.getAttribute(Constants.USER), User.class);

        if (!TextUtil.isEmpty(number.getBusinessId())){
            return ResultUtil.error(-1,"请选择商家后又提交");
        }

        number.setUserId(user.getUserId());
        number.setIsExpired((byte) 0);
        Result num = mTakeNumService.getCurrentNum(number);

        if (num.getCode() == -1)
            return ResultUtil.error(-1,num.getMsg());
        else
            return ResultUtil.success(num.getData());
    }

    // 获取当前排到第几号   eg:用户已取号，获取当前排到第几号
    @GetMapping("/getCurrent")
    public Result getCurrentNum(Number number, HttpSession session){

        if (!TextUtil.isEmpty(number.getBusinessId())){
            return ResultUtil.error(-1,"请选择商家后又提交");
        }

        number.setIsExpired((byte) 0);
        Result num = mTakeNumService.refreshNum(number);

        if (num.getCode() == -1)
            return ResultUtil.error(-1,num.getMsg());
        else
            return ResultUtil.success(num.getData());
    }

    // 叫号 （商家调用）  eg:商家将推送消息给当前号
    @GetMapping("/callNum")
    public Result callNum(Number number, HttpSession session){

        // 传过来一个商家id和number，将该条记录is_expire设置为 0
        if (number != null && !TextUtil.isEmpty(number.getBusinessId()) && !TextUtil.isEmpty(number.getNumber())){
            return ResultUtil.error(-1,"请选择商家并携带number后又提交");
        }

        // 根据number，businessId，is_expire来查找被叫号的顾客
        Result result = mTakeNumService.callNum(number);

        number.setIsExpired((byte) 0);
        number = mNumberMapper.findBySelective(number).get(0);

        if (result.getCode() == -1) {
            return ResultUtil.error(-1,result.getMsg());
        }else {
            // 对过号（被叫号的）顾客进行消息推送
            User user = (User) result.getData();
            pushMsg(number.getNumber(), number.getNumber(), user.getUserId(), number.getPeopleNum(), number.getBusinessId());
            return ResultUtil.success(result.getMsg());
        }
    }

    // 过号 （商家调用）  设置这个号过期，并推送消息给剩下的所有号，通知他们队列已更新
    @GetMapping("/passNum")
    public Result passNum(Number number, HttpSession session){
        // 传过来一个商家id和number，将该条记录is_expire设置为 0
        if (number != null && !TextUtil.isEmpty(number.getBusinessId()) && !TextUtil.isEmpty(number.getNumber())){
            return ResultUtil.error(-1,"请选择商家并携带number后又提交");
        }

        // 要被过号的这个号应该是没有过期的
        number.setIsExpired((byte) 1);
        Result result = mTakeNumService.passNum(number);

        if (result.getCode() == -1) {
            return ResultUtil.error(-1,result.getMsg());
        }else {
            // 当过了一个号时，应该取得剩下所有号的集合，集合中装的信息有（number,openid，currentNum）
            List<Number> remainLists = (List<Number>) result.getData();
            if (remainLists.size() == 0){
                // 说明后面没有号了,不需要推送消息
                return ResultUtil.success("设置成功");
            }
            // 设置当前号是队列的第一个元素（因为队列已经过排序，因此第一个就是队列中对前面的号）
            int curretNum = remainLists.get(0).getNumber();

            // 对队列中的其他号进行推送
            for (Number remainList : remainLists) {
                pushMsg(remainList.getNumber(), curretNum, remainList.getUserId(), remainList.getPeopleNum(), remainList.getBusinessId());
            }

            return ResultUtil.success(result.getMsg());
        }
    }

    // 微信推送消息过号
    public void pushMsg(int takeNum, int CurrentNum, int userId, int peopleNum, int businessId){

        // 根据userId查找openid
        User user = mUserMapper.selectByPrimaryKey(userId);

        Business business = mBusinessMapper.selectByPrimaryKey(businessId);
        if (!TextUtil.isEmpty(user.getOpenid())){
            mLogger.error("推送微信消息失败,原因是={}","用户openid为空");
            return;
        }

        String tableType = null;
        if (peopleNum <= 4){
            tableType = "小桌";
        }
        if (peopleNum >= 5 && peopleNum <= 6){
            tableType = "中桌";
        }
        if (peopleNum > 6){
            tableType = "大桌";
        }

        WxMpTemplateMessage templateMessage = new WxMpTemplateMessage();
        try {
            templateMessage.setToUser(user.getOpenid());
            templateMessage.setTemplateId(mTemplateId);
            templateMessage.setUrl("");
            templateMessage.getData().add(new WxMpTemplateData("first", "Hey! 小蕨叫你来吃饭啦~","#003371"));
            templateMessage.getData().add(new WxMpTemplateData("keyword1", business.getName() + "","#003371"));
//            templateMessage.getData().add(new WxMpTemplateData("keyword2", CurrentNum + "","#003371")); // 当前排到的号
            templateMessage.getData().add(new WxMpTemplateData("keyword2", takeNum + "","#003371"));
            templateMessage.getData().add(new WxMpTemplateData("keyword3",tableType + "","#44cef6"));
            templateMessage.getData().add(new WxMpTemplateData("keyword4",takeNum - CurrentNum + "","#00056"));
            templateMessage.getData().add(new WxMpTemplateData("remark","小蕨随时为你服务哦~","#00056"));
            mWxMpService.getTemplateMsgService().sendTemplateMsg(templateMessage);
        } catch (WxErrorException e) {
            e.printStackTrace();
            mLogger.error("推送微信消息失败,原因是={}",e.getMessage());
        }
    }
}
