package com.beardream.Controller;

import com.beardream.Utils.ResultUtil;
import com.beardream.Utils.TextUtil;
import com.beardream.dao.BusinessMapper;
import com.beardream.dao.RoleMapper;
import com.beardream.ioc.PermissionMethod;
import com.beardream.ioc.PermissionModule;
import com.beardream.model.*;
import com.beardream.service.BusinessService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * Created by laxzh on 2017/5/6.
 * 商家控制器
 */
@RestController
@RequestMapping("/api/business")
@Api(value = "商家服务",description = "提供RESTful风格API的商家的增删改查服务")
public class BuisnessController {
    @Autowired
    private  BusinessMapper businessMapper;

    @Autowired
    private BusinessService businessService;

    @ApiOperation("获取单个商家信息")
    @GetMapping
    public Result get(Business
                                  business, BindingResult bindingResult) {
            System.out.println(business.getBusinessId());
            return ResultUtil.success(businessMapper.findBySelective(business));
        }

    @ApiOperation("添加商家")
    @PostMapping
    public Result post(Business business){
        int result;
        if (business == null)
            return ResultUtil.error(-1,"没有参数");
        List<Business> b = businessMapper.findBySelective(business);
        if (b.size()>0)
            return  ResultUtil.error(-1,"添加失败，商家已存在");
        business.setAddTime(new Date());
        result = businessMapper.insertSelective(business);
        if (result > 0 )
            return  ResultUtil.success("添加成功");
        else
            return  ResultUtil.error(-1,"添加失败");
    }

    @ApiOperation("删除商家")
    @DeleteMapping
    public Result delete(Business business){
        int result;
        result = businessMapper.deleteByPrimaryKey(business.getBusinessId());
        if (result>0)
            return ResultUtil.success("删除成功");
        else
            return ResultUtil.error(-1,"删除失败");
    }

    @ApiOperation("更新商家")
    @PutMapping
    public Result put(Business business){
        int result;
        System.out.println(business.getBusinessId());
        business.setAddTime(new Date());
        result = businessMapper.updateByPrimaryKeySelective(business);
        if (result>0)
            return ResultUtil.success("修改成功");
        else
            return  ResultUtil.error(-1,"修改失败");
    }

    @ApiOperation("分页查询商家推荐")
    @GetMapping("/recommend")
    public Result getPage(Business business, Tag tag,Dish dish, @RequestParam(value = "pageNum", defaultValue = "1",required = false)  int pageNum, @RequestParam(value = "pageSize", defaultValue = "10",required = false)  int pageSize, BindingResult bindingResult){
        if (!TextUtil.isEmpty(pageNum) || !TextUtil.isEmpty(pageSize)){
            return ResultUtil.error(-1,"pageNum,pageNum不能为空！");
        }
        if (businessService.getPage(business, pageNum,pageSize)!=null)
            return ResultUtil.success(businessService.getPage(business, pageNum,pageSize));
        else
            return ResultUtil.error(-1,"系统错误");
    }
}
