package com.caifan.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caifan.business.entity.DlvOrder;
import com.caifan.business.entity.DlvOrderItem;
import com.caifan.business.entity.SalOrder;
import com.caifan.business.entity.SalOrderItem;
import com.caifan.business.entity.Customer;
import com.caifan.business.entity.Goods;
import com.caifan.business.mapper.DlvOrderMapper;
import com.caifan.business.mapper.SalOrderMapper;
import com.caifan.business.mapper.CustomerMapper;
import com.caifan.business.mapper.GoodsMapper;
import com.caifan.business.support.SerialNoGenerator;
import com.caifan.business.support.TenantSupport;
import com.caifan.common.context.UserContext;
import com.caifan.common.exception.BizException;
import com.caifan.common.page.PageQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 分拣配送管理。
 */
@Service
@RequiredArgsConstructor
public class DlvOrderService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final DlvOrderMapper dlvOrderMapper;
    private final SalOrderMapper salOrderMapper;
    private final CustomerMapper customerMapper;
    private final GoodsMapper goodsMapper;
    private final SerialNoGenerator serialNo;
    private final ObjectMapper objectMapper;

    public Page<DlvOrder> page(PageQuery query) {
        LambdaQueryWrapper<DlvOrder> wrapper = new LambdaQueryWrapper<>();
        if (!TenantSupport.isPlatformAdmin()) {
            wrapper.eq(DlvOrder::getMerchantId, TenantSupport.merchantId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(DlvOrder::getDlvNo, query.getKeyword());
        }
        wrapper.orderByDesc(DlvOrder::getCreateTime);
        return dlvOrderMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    public List<DlvOrderItem> items(Long dlvOrderId) {
        DlvOrder order = dlvOrderMapper.selectById(dlvOrderId);
        if (order == null) {
            return new ArrayList<>();
        }
        return parseItems(order.getItemsJson());
    }

    public DlvOrder byId(Long dlvOrderId) {
        return dlvOrderId == null ? null : dlvOrderMapper.selectById(dlvOrderId);
    }

    /**
     * 按 id 批量取分拣单（带商户隔离），供 Excel 导出等只读场景使用。
     */
    public List<DlvOrder> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        if (uniqueIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DlvOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DlvOrder::getId, uniqueIds);
        if (!TenantSupport.isPlatformAdmin()) {
            wrapper.eq(DlvOrder::getMerchantId, TenantSupport.merchantId());
        }
        List<DlvOrder> found = dlvOrderMapper.selectList(wrapper);
        Map<Long, DlvOrder> byId = new HashMap<>();
        for (DlvOrder order : found) {
            byId.put(order.getId(), order);
        }
        List<DlvOrder> ordered = new ArrayList<>();
        for (Long id : uniqueIds) {
            DlvOrder order = byId.get(id);
            if (order != null) {
                ordered.add(order);
            }
        }
        return ordered;
    }

    /** 解析已取出的分拣单明细，避免导出时逐单回查。 */
    public List<DlvOrderItem> itemsOf(DlvOrder order) {
        return order == null ? new ArrayList<>() : parseItems(order.getItemsJson());
    }

    /**
     * 把分拣单号与分拣状态回填到销售单（非数据库字段），供销售列表与客户端订单展示，
     * 保证销售侧与分拣配送侧读到的是同一份状态。
     */
    public void fillDeliveryStatus(List<SalOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<Long> saleIds = new ArrayList<>();
        for (SalOrder order : orders) {
            if (order.getId() != null) {
                saleIds.add(order.getId());
            }
        }
        if (saleIds.isEmpty()) {
            return;
        }
        List<DlvOrder> dlvOrders = dlvOrderMapper.selectList(new LambdaQueryWrapper<DlvOrder>()
                .in(DlvOrder::getSaleOrderId, saleIds)
                .ne(DlvOrder::getStatus, "4")
                .orderByDesc(DlvOrder::getCreateTime));
        Map<Long, DlvOrder> latest = new HashMap<>();
        for (DlvOrder dlv : dlvOrders) {
            if (dlv.getSaleOrderId() != null) {
                latest.putIfAbsent(dlv.getSaleOrderId(), dlv);
            }
        }
        for (SalOrder order : orders) {
            DlvOrder dlv = order.getId() == null ? null : latest.get(order.getId());
            if (dlv == null) {
                continue;
            }
            order.setDeliveryNo(dlv.getDlvNo());
            order.setDeliveryStatus(dlv.getStatus());
            order.setDeliverySignType(dlv.getSignType());
            order.setDeliverySignedTime(dlv.getSignedTime());
        }
    }

    /**
     * 从销售单创建分拣任务。
     */
    @Transactional(rollbackFor = Exception.class)
    public DlvOrder createFromSale(Long saleOrderId) {
        SalOrder saleOrder = salOrderMapper.selectById(saleOrderId);
        if (saleOrder == null) {
            throw new BizException(6001, "销售单不存在");
        }
        if (!"0".equals(saleOrder.getStatus())) {
            throw new BizException(6008, "该销售单状态不允许分拣");
        }

        // 同一张销售单只允许存在一张未取消的分拣单，避免重复建单
        DlvOrder exist = dlvOrderMapper.selectOne(new LambdaQueryWrapper<DlvOrder>()
                .eq(DlvOrder::getSaleOrderId, saleOrderId)
                .ne(DlvOrder::getStatus, "4")
                .last("limit 1"));
        if (exist != null) {
            throw new BizException(8002, "该销售单已创建分拣单：" + exist.getDlvNo());
        }

        List<SalOrderItem> saleItems = parseSaleItems(saleOrder.getItemsJson());
        if (saleItems.isEmpty()) {
            throw new BizException(6004, "销售单无明细");
        }

        Customer customer = customerMapper.selectById(saleOrder.getCustomerId());
        String customerName = customer != null ? customer.getName() : null;

        List<DlvOrderItem> dlvItems = new ArrayList<>();
        BigDecimal totalQty = ZERO;
        for (SalOrderItem item : saleItems) {
            DlvOrderItem dlvItem = new DlvOrderItem();
            dlvItem.setGoodsId(item.getGoodsId());
            dlvItem.setGoodsName(item.getGoodsName());
            dlvItem.setQty(item.getQty());
            dlvItem.setBatchNo(item.getBatchNo());
            Goods goods = item.getGoodsId() == null ? null : goodsMapper.selectById(item.getGoodsId());
            dlvItem.setUnit(goods == null ? null : goods.getUnit());
            dlvItems.add(dlvItem);
            totalQty = totalQty.add(item.getQty());
        }

        DlvOrder dlvOrder = new DlvOrder();
        dlvOrder.setMerchantId(saleOrder.getMerchantId());
        dlvOrder.setDlvNo(serialNo.next("JL"));
        dlvOrder.setSaleOrderId(saleOrderId);
        dlvOrder.setSaleNo(saleOrder.getSaleNo());
        dlvOrder.setCustomerId(saleOrder.getCustomerId());
        dlvOrder.setCustomerName(customerName);
        dlvOrder.setTotalQty(totalQty);
        dlvOrder.setStatus("0");
        dlvOrder.setDlvDate(LocalDate.now());
        dlvOrder.setItemsJson(writeJson(dlvItems));
        dlvOrderMapper.insert(dlvOrder);

        return dlvOrder;
    }

    /**
     * 更新分拣状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public DlvOrder updateStatus(Long dlvOrderId, String status, String picker, String deliverer) {
        DlvOrder order = dlvOrderMapper.selectById(dlvOrderId);
        if (order == null) {
            throw new BizException(8001, "分拣单不存在");
        }
        if ("5".equals(status)) {
            throw new BizException(8006, "已签收必须由客户确认签收，员工端不能直接签收");
        }
        order.setStatus(status);
        if ("3".equals(status) && order.getDeliveredTime() == null) {
            order.setDeliveredTime(LocalDateTime.now());
        }
        if (StringUtils.hasText(picker)) {
            order.setPicker(picker);
        }
        if (StringUtils.hasText(deliverer)) {
            order.setDeliverer(deliverer);
        }
        dlvOrderMapper.updateById(order);
        return order;
    }

    /**
     * 客户主动签收。自动签收订单保持 AUTO，不能通过迟到的签收请求改成 MANUAL。
     */
    @Transactional(rollbackFor = Exception.class)
    public DlvOrder confirmManualSign(Long dlvOrderId) {
        DlvOrder order = dlvOrderMapper.selectById(dlvOrderId);
        if (order == null) {
            throw new BizException(8001, "分拣单不存在");
        }
        if ("5".equals(order.getStatus())) {
            if ("AUTO".equals(order.getSignType())) {
                throw new BizException(8005, "订单已自动签收，不能改为主动签收");
            }
            return order;
        }
        if (!"3".equals(order.getStatus())) {
            throw new BizException(8004, "当前状态不可签收，需先完成配送");
        }
        order.setStatus("5");
        order.setSignedTime(LocalDateTime.now());
        order.setSignType("MANUAL");
        dlvOrderMapper.updateById(order);
        return order;
    }

    /**
     * 批量自动签收已配送超过指定小时数的订单。
     */
    @Transactional(rollbackFor = Exception.class)
    public int autoSignOverdue(LocalDateTime now, int autoSignHours) {
        if (autoSignHours <= 0) {
            throw new IllegalArgumentException("autoSignHours must be positive");
        }
        LocalDateTime cutoff = now.minusHours(autoSignHours);
        return dlvOrderMapper.update(null, new LambdaUpdateWrapper<DlvOrder>()
                .eq(DlvOrder::getStatus, "3")
                .isNotNull(DlvOrder::getDeliveredTime)
                .le(DlvOrder::getDeliveredTime, cutoff)
                .set(DlvOrder::getStatus, "5")
                .set(DlvOrder::getSignedTime, now)
                .set(DlvOrder::getSignType, "AUTO")
                .set(DlvOrder::getUpdateTime, now));
    }

    private List<DlvOrderItem> parseItems(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DlvOrderItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<SalOrderItem> parseSaleItems(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SalOrderItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(8000, "明细序列化失败");
        }
    }
}
