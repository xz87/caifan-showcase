package com.caifan.admin.controller;

import com.caifan.admin.mall.MallCustomer;
import com.caifan.admin.mall.MallTokenService;
import com.caifan.business.entity.Customer;
import com.caifan.business.service.MallService;
import com.caifan.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

/**
 * 小程序与 Android 用户端认证：手机号 + 密码登录、注册和 Bearer Token 退出。
 */
@RestController
@RequestMapping("/api/mall/auth")
@RequiredArgsConstructor
public class MallAuthController {

    private final MallService mallService;
    private final MallTokenService mallTokenService;

    /**
     * 小程序与 Android 登录：手机号 + 密码，返回 Bearer Token。
     */
    @PostMapping("/token-login")
    public Result<Map<String, Object>> tokenLogin(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        Customer customer = mallService.loginByPhoneAndPassword(phone, password);
        MallCustomer mallCustomer = toMallCustomer(customer);
        String token = mallTokenService.issue(mallCustomer);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("customer", mallCustomer);
        return Result.ok(data, "登录成功");
    }

    /**
     * 小程序与 Android 散客注册。
     */
    @PostMapping("/token-register")
    public Result<Map<String, Object>> tokenRegister(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String password = body.get("password");
        String name = body.get("name");
        String email = body.get("email");
        Customer customer = mallService.register(phone, password, name, email);
        MallCustomer mallCustomer = toMallCustomer(customer);
        String token = mallTokenService.issue(mallCustomer);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("customer", mallCustomer);
        return Result.ok(data, "注册成功");
    }

    /**
     * 小程序与 Android 退出：吊销 Token。
     */
    @PostMapping("/token-logout")
    public Result<Void> tokenLogout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            mallTokenService.revoke(authorization.substring(7).trim());
        }
        return Result.ok();
    }

    /**
     * 采购商注册：公司名称 + 申请资料图片，提交后待商户审核。
     */
    @PostMapping("/token-register-purchaser")
    public Result<Map<String, Object>> tokenRegisterPurchaser(@RequestBody Map<String, String> body) {
        Customer customer = mallService.registerPurchaser(
                body.get("phone"), body.get("password"),
                body.get("companyName"), body.get("docUrl"));
        MallCustomer mallCustomer = toMallCustomer(customer);
        String token = mallTokenService.issue(mallCustomer);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("customer", mallCustomer);
        return Result.ok(data, "注册成功，等待商户审核");
    }

    private MallCustomer toMallCustomer(Customer customer) {
        return new MallCustomer(
                customer.getId(), customer.getName(), customer.getPhone(),
                customer.getMerchantId() == null ? 1L : customer.getMerchantId(),
                customer.getCustomerType(), customer.getAuditStatus());
    }
}
