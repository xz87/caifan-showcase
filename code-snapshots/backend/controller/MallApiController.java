package com.caifan.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caifan.admin.mall.MallCustomer;
import com.caifan.admin.mall.MallSession;
import com.caifan.business.dto.SaleForm;
import com.caifan.business.dto.CancelOrderForm;
import com.caifan.business.entity.FileAsset;
import com.caifan.business.entity.Goods;
import com.caifan.business.entity.SalOrder;
import com.caifan.business.entity.SalOrderItem;
import com.caifan.business.service.MallService;
import com.caifan.common.page.PageQuery;
import com.caifan.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 客户端订菜 API：商品浏览、自主下单、我的订单、支付。
 */
@RestController
@RequestMapping("/api/mall")
@RequiredArgsConstructor
public class MallApiController {

    private final MallService mallService;

    /**
     * 当前客户信息。
     */
    @GetMapping("/customer")
    public Result<MallCustomer> customer(HttpServletRequest request) {
        MallCustomer session = MallSession.current(request);
        com.caifan.business.entity.Customer db = mallService.getCustomer(session.getCustomerId());
        if (db == null) {
            return Result.fail(2001, "客户不存在");
        }
        return Result.ok(new MallCustomer(
                db.getId(), db.getName(), db.getPhone(),
                db.getMerchantId() == null ? 1L : db.getMerchantId(),
                db.getCustomerType(), db.getAuditStatus()));
    }

    /**
     * 可订购商品列表（含库存，不含进价）。
     */
    @GetMapping("/goods")
    public Result<List<Goods>> goods(HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.goods(customer.getCustomerId(), customer.getMerchantId()));
    }

    /**
     * 小程序动态图片、音频等媒体资源，URL 和元数据来自数据库。
     */
    @GetMapping("/assets")
    public Result<List<FileAsset>> assets(HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.assets(customer.getMerchantId()));
    }

    /**
     * 自主下单。
     */
    @PostMapping("/order")
    public Result<SalOrder> placeOrder(@RequestBody SaleForm form, HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        SalOrder order = mallService.placeOrder(customer.getCustomerId(), form);
        return Result.ok(order, "下单成功");
    }

    /**
     * 我的订单（分页）。
     */
    @GetMapping("/orders")
    public Result<Page<SalOrder>> myOrders(PageQuery query, HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.myOrders(customer.getCustomerId(), query));
    }

    /**
     * 订单明细。
     */
    @GetMapping("/orders/{id}/items")
    public Result<List<SalOrderItem>> orderItems(@PathVariable Long id, HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.orderItems(customer.getCustomerId(), id));
    }

    /**
     * 客户确认收货：分拣单由「已配送」推进到「已签收」，商家端同步可见。
     */
    @PostMapping("/orders/{id}/confirm-receipt")
    public Result<com.caifan.business.entity.DlvOrder> confirmReceipt(@PathVariable Long id,
                                                                    HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.confirmReceipt(customer.getCustomerId(), id), "已确认签收");
    }

    /** 客户取消未分拣订单；已支付订单在同一次事务内记录模拟退款。 */
    @PostMapping("/orders/{id}/cancel")
    public Result<SalOrder> cancelOrder(@PathVariable Long id, @RequestBody CancelOrderForm form,
                                        HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        return Result.ok(mallService.cancelOrder(customer.getCustomerId(), id, form), "订单已取消");
    }

    /**
     * 支付。测试/模拟支付：直接标记已收款；真实支付宝接入后替换为下单跳转逻辑。
     */
    @PostMapping("/pay")
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        MallCustomer customer = MallSession.current(request);
        Long orderId = body.get("orderId");
        mallService.markPaid(customer.getCustomerId(), orderId);
        return Result.ok(Map.of("orderId", orderId, "status", "PAID"), "支付成功");
    }
}
