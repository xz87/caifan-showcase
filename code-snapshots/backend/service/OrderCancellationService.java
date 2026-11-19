package com.caifan.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.caifan.business.dto.BatchAllocation;
import com.caifan.business.dto.CancelOrderForm;
import com.caifan.business.entity.DlvOrder;
import com.caifan.business.entity.GroupSetting;
import com.caifan.business.entity.OrderCancel;
import com.caifan.business.entity.SalGroup;
import com.caifan.business.entity.SalGroupMember;
import com.caifan.business.entity.SalOrder;
import com.caifan.business.entity.SalOrderItem;
import com.caifan.business.mapper.CustomerMapper;
import com.caifan.business.mapper.DlvOrderMapper;
import com.caifan.business.mapper.FinSettlementMapper;
import com.caifan.business.mapper.InvBatchMapper;
import com.caifan.business.mapper.InvStockMapper;
import com.caifan.business.mapper.OrderCancelMapper;
import com.caifan.business.mapper.SalGroupMapper;
import com.caifan.business.mapper.SalGroupMemberMapper;
import com.caifan.business.mapper.SalOrderMapper;
import com.caifan.business.support.TenantSupport;
import com.caifan.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 销售单取消：未分拣前精确回补 FIFO 批次、库存、赊账和结算，并保留完整审计。
 * 历史订单若缺少 batchAllocations，不做猜测性回补，交由库存调整处理。
 */
@Service
@RequiredArgsConstructor
public class OrderCancellationService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final SalOrderMapper orderMapper;
    private final InvBatchMapper batchMapper;
    private final InvStockMapper stockMapper;
    private final CustomerMapper customerMapper;
    private final FinSettlementMapper settlementMapper;
    private final DlvOrderMapper dlvOrderMapper;
    private final OrderCancelMapper cancelMapper;
    private final SalGroupMapper groupMapper;
    private final SalGroupMemberMapper memberMapper;
    private final GroupConfigService groupConfigService;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public SalOrder cancel(Long customerId, Long orderId, CancelOrderForm form,
                           String operatorType, String operator, boolean customerResponsible) {
        SalOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(6001, "订单不存在");
        }
        if ("CUSTOMER".equals(operatorType) && !order.getCustomerId().equals(customerId)) {
            throw new BizException(2003, "无权取消该订单");
        }
        if ("2".equals(order.getStatus())) {
            return order;
        }
        if (!"0".equals(order.getStatus())) {
            throw new BizException(6110, "当前订单状态不可取消");
        }
        validateReason(form);
        ensureNotPicked(orderId);

        List<SalOrderItem> items = parseItems(order.getItemsJson());
        if (items.isEmpty()) {
            throw new BizException(6111, "订单明细异常，不能自动取消");
        }
        Map<Long, BigDecimal> goodsQty = new HashMap<>();
        for (SalOrderItem item : items) {
            if (item.getBatchAllocations() == null || item.getBatchAllocations().isEmpty()) {
                throw new BizException(6112,
                        "历史订单缺少批次分配快照，请通过库存调整处理，禁止自动回补");
            }
            for (BatchAllocation allocation : item.getBatchAllocations()) {
                if (allocation.getBatchId() == null || allocation.getQty() == null
                        || allocation.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BizException(6112, "批次分配快照异常，不能自动取消");
                }
                if (batchMapper.restore(allocation.getBatchId(), allocation.getQty()) == 0) {
                    throw new BizException(6112, "批次回补失败，请检查库存数据");
                }
            }
            goodsQty.merge(item.getGoodsId(), item.getQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> entry : goodsQty.entrySet()) {
            stockMapper.increase(IdWorker.getId(), order.getMerchantId(), entry.getKey(), entry.getValue());
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        boolean paid = "2".equals(order.getPayStatus());
        BigDecimal refundAmount = paid ? TenantSupport.nvl(order.getPayable()) : BigDecimal.ZERO;

        settlementMapper.closeCancelledSale(order.getSaleNo());
        if (!paid && TenantSupport.nvl(order.getPayable()).compareTo(BigDecimal.ZERO) > 0) {
            customerMapper.addCreditUsed(order.getCustomerId(), TenantSupport.nvl(order.getPayable()).negate());
        }
        cancelDelivery(orderId);

        String snapshot = json(order);
        order.setStatus("2");
        order.setCancelReason(form.getReasonCode());
        order.setCancelRemark(form.getReason().trim());
        order.setCancelOperator(StringUtils.hasText(operator) ? operator : operatorType);
        order.setCancelTime(now);
        order.setRefundStatus(paid ? "1" : "0");
        order.setRefundAmount(refundAmount);
        order.setRefundTime(paid ? now : null);
        orderMapper.updateById(order);

        OrderCancel record = new OrderCancel();
        record.setMerchantId(order.getMerchantId());
        record.setOrderId(order.getId());
        record.setSaleNo(order.getSaleNo());
        record.setCustomerId(order.getCustomerId());
        record.setReasonCode(form.getReasonCode());
        record.setReasonText(form.getReason().trim());
        record.setResponsible(customerResponsible);
        record.setRefundStatus(paid ? "1" : "0");
        record.setRefundAmount(refundAmount);
        record.setOperatorType(operatorType);
        record.setOperator(StringUtils.hasText(operator) ? operator : operatorType);
        record.setCancelTime(now);
        record.setSnapshotJson(snapshot);
        cancelMapper.insert(record);

        releaseGroupMember(order, now);
        if (customerResponsible) {
            issuePenaltyIfNeeded(order, now);
        }
        groupConfigService.audit(order.getMerchantId(), "CANCEL", order.getId(), "CANCEL",
                snapshot, json(order));
        return order;
    }

    public boolean canCancel(SalOrder order, String deliveryStatus) {
        if (order == null || !"0".equals(order.getStatus())) {
            return false;
        }
        return !StringUtils.hasText(deliveryStatus)
                || "0".equals(deliveryStatus)
                || "4".equals(deliveryStatus);
    }

    private void ensureNotPicked(Long orderId) {
        DlvOrder delivery = dlvOrderMapper.selectOne(new LambdaQueryWrapper<DlvOrder>()
                .eq(DlvOrder::getSaleOrderId, orderId)
                .ne(DlvOrder::getStatus, "4")
                .orderByDesc(DlvOrder::getCreateTime)
                .last("limit 1"));
        if (delivery != null && !"0".equals(delivery.getStatus())) {
            throw new BizException(6113, "订单已开始分拣，不能取消，请走损耗补发");
        }
    }

    private void cancelDelivery(Long orderId) {
        dlvOrderMapper.update(null, new LambdaUpdateWrapper<DlvOrder>()
                .eq(DlvOrder::getSaleOrderId, orderId)
                .eq(DlvOrder::getStatus, "0")
                .set(DlvOrder::getStatus, "4")
                .set(DlvOrder::getUpdateTime, LocalDateTime.now(BUSINESS_ZONE)));
    }

    private void releaseGroupMember(SalOrder order, LocalDateTime now) {
        if (order.getGroupMemberId() == null) {
            return;
        }
        SalGroupMember member = memberMapper.selectById(order.getGroupMemberId());
        if (member == null || "2".equals(member.getStatus())) {
            return;
        }
        member.setStatus("2");
        member.setExitTime(now);
        memberMapper.updateById(member);

        SalGroup group = order.getGroupId() == null ? null : groupMapper.selectById(order.getGroupId());
        if (group == null) {
            return;
        }
        group.setMemberCount(Math.max(0, (group.getMemberCount() == null ? 1 : group.getMemberCount()) - 1));
        if ("1".equals(group.getStatus()) && group.getMemberCount() < group.getTargetCount()) {
            group.setStatus("0");
        }
        if (order.getCustomerId().equals(group.getLeaderPartyId())) {
            List<SalGroupMember> active = memberMapper.selectList(new LambdaQueryWrapper<SalGroupMember>()
                    .eq(SalGroupMember::getGroupId, group.getId())
                    .eq(SalGroupMember::getStatus, "0")
                    .orderByAsc(SalGroupMember::getCreateTime));
            if (!active.isEmpty()) {
                group.setLeaderPartyId(active.get(0).getPartyId());
            }
        }
        groupMapper.updateById(group);
    }

    private void issuePenaltyIfNeeded(SalOrder order, LocalDateTime now) {
        GroupSetting setting = groupConfigService.setting(order.getMerchantId());
        long count = cancelMapper.selectCount(new LambdaQueryWrapper<OrderCancel>()
                .eq(OrderCancel::getMerchantId, order.getMerchantId())
                .eq(OrderCancel::getCustomerId, order.getCustomerId())
                .eq(OrderCancel::getResponsible, true)
                .ge(OrderCancel::getCancelTime, now.minusDays(Math.max(1, setting.getPenaltyWindowDays()))));
        if (count >= setting.getPenaltyThreshold()) {
            groupConfigService.issuePendingPenalty(
                    order.getMerchantId(), order.getCustomerId(), (int) count);
        }
    }

    private void validateReason(CancelOrderForm form) {
        if (form == null || !StringUtils.hasText(form.getReasonCode())) {
            throw new BizException(6114, "请选择退单原因");
        }
        if (!List.of("NOT_NEEDED", "TEMPORARY", "OTHER", "MERCHANT").contains(form.getReasonCode())) {
            throw new BizException(6114, "退单原因不合法");
        }
        if (!StringUtils.hasText(form.getReason())) {
            throw new BizException(6114, "请填写退单备注");
        }
        if ("OTHER".equals(form.getReasonCode()) && form.getReason().trim().length() < 5) {
            throw new BizException(6114, "其他原因备注至少填写 5 个字");
        }
    }

    private List<SalOrderItem> parseItems(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SalOrderItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(6115, "取消审计序列化失败");
        }
    }
}
