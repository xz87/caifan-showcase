package com.caifan.business.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.caifan.business.dto.ClaimForm;
import com.caifan.business.dto.OrderLine;
import com.caifan.business.dto.ReshipForm;
import com.caifan.business.dto.SaleForm;
import com.caifan.business.entity.DlvOrder;
import com.caifan.business.entity.InvAdjust;
import com.caifan.business.entity.LossClaim;
import com.caifan.business.entity.SalClaimSetting;
import com.caifan.business.entity.SalOrder;
import com.caifan.business.entity.SalOrderItem;
import com.caifan.business.mapper.DlvOrderMapper;
import com.caifan.business.mapper.InvAdjustMapper;
import com.caifan.business.mapper.LossClaimMapper;
import com.caifan.business.mapper.SalClaimSettingMapper;
import com.caifan.business.mapper.SalOrderMapper;
import com.caifan.business.support.SerialNoGenerator;
import com.caifan.business.support.TenantSupport;
import com.caifan.common.exception.BizException;
import com.caifan.common.page.PageQuery;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 损耗报备：客户签收后 48 小时内提交证据，商户审核，通过后可生成 0 元补发单。
 */
@Service
@RequiredArgsConstructor
public class LossClaimService {

    private static final int DEFAULT_CLAIM_WINDOW_HOURS = 14;

    private final LossClaimMapper claimMapper;
    private final SalOrderMapper orderMapper;
    private final DlvOrderMapper dlvOrderMapper;
    private final InvAdjustMapper adjustMapper;
    private final SaleService saleService;
    private final DlvOrderService dlvOrderService;
    private final SerialNoGenerator serialNo;
    private final ObjectMapper objectMapper;
    private final SalClaimSettingMapper settingMapper;

    @Transactional(rollbackFor = Exception.class)
    public LossClaim submit(Long partyId, Long merchantId, ClaimForm form) {
        if (form == null || form.getOrderId() == null) {
            throw new BizException(6001, "缺少订单信息");
        }
        if (!StringUtils.hasText(form.getReason())) {
            throw new BizException(6014, "请填写损耗说明");
        }
        if (form.getImages() != null && form.getImages().size() > 3) {
            throw new BizException(6014, "最多上传 3 张图片");
        }
        SalOrder order = orderMapper.selectById(form.getOrderId());
        if (order == null || !partyId.equals(order.getCustomerId())) {
            throw new BizException(2003, "无权操作该订单");
        }
        DlvOrder dlv = dlvOrderMapper.selectOne(new LambdaQueryWrapper<DlvOrder>()
                .eq(DlvOrder::getSaleOrderId, order.getId())
                .eq(DlvOrder::getStatus, "5")
                .orderByDesc(DlvOrder::getCreateTime)
                .last("limit 1"));
        if (dlv == null) {
            throw new BizException(6011, "订单尚未签收，暂时不能报备");
        }
        if ("AUTO".equals(dlv.getSignType())) {
            throw new BizException(6021, "自动签收订单不支持报损");
        }
        if (dlv.getSignedTime() == null) {
            throw new BizException(6011, "订单尚未签收，暂时不能报备");
        }
        int claimWindowHours = claimWindowHours(merchantId);
        if (dlv.getSignedTime().isBefore(LocalDateTime.now().minusHours(claimWindowHours))) {
            throw new BizException(6012,
                    "签收已超过报损时限（" + claimWindowHours + " 小时），请联系商家处理");
        }
        Long active = claimMapper.selectCount(new LambdaQueryWrapper<LossClaim>()
                .eq(LossClaim::getOrderId, order.getId())
                .in(LossClaim::getStatus, "0", "1"));
        if (active > 0) {
            throw new BizException(6013, "该订单已有处理中的报备");
        }

        LossClaim claim = new LossClaim();
        claim.setMerchantId(merchantId);
        claim.setPartyId(partyId);
        claim.setOrderId(order.getId());
        claim.setSaleNo(order.getSaleNo());
        claim.setReason(form.getReason());
        claim.setImagesJson(writeJson(form.getImages() == null ? new ArrayList<>() : form.getImages()));
        claim.setVideoUrl(form.getVideoUrl());
        claim.setStatus("0");
        claimMapper.insert(claim);
        return claim;
    }

    public int claimWindowHours(Long merchantId) {
        SalClaimSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<SalClaimSetting>()
                .eq(SalClaimSetting::getMerchantId, merchantId)
                .last("limit 1"));
        if (setting == null || setting.getClaimWindowHours() == null) {
            return DEFAULT_CLAIM_WINDOW_HOURS;
        }
        return setting.getClaimWindowHours();
    }

    @Transactional(rollbackFor = Exception.class)
    public int saveClaimWindowHours(Long merchantId, int hours) {
        if (hours < 1 || hours > 72) {
            throw new BizException(6022, "报损时限必须在 1 到 72 小时之间");
        }
        SalClaimSetting setting = settingMapper.selectOne(new LambdaQueryWrapper<SalClaimSetting>()
                .eq(SalClaimSetting::getMerchantId, merchantId)
                .last("limit 1"));
        if (setting == null) {
            setting = new SalClaimSetting();
            setting.setMerchantId(merchantId);
            setting.setClaimWindowHours(hours);
            settingMapper.insert(setting);
        } else {
            setting.setClaimWindowHours(hours);
            settingMapper.updateById(setting);
        }
        return hours;
    }

    /**
     * 给客户端订单补充报损资格、截止时间和最近一次报损状态。
     */
    public void fillClaimEligibility(List<SalOrder> orders, Long merchantId) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        int windowHours = claimWindowHours(merchantId);
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = orders.stream()
                .map(SalOrder::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, LossClaim> latestClaims = new LinkedHashMap<>();
        if (!orderIds.isEmpty()) {
            List<LossClaim> claims = claimMapper.selectList(new LambdaQueryWrapper<LossClaim>()
                    .in(LossClaim::getOrderId, orderIds)
                    .orderByDesc(LossClaim::getCreateTime));
            for (LossClaim claim : claims) {
                latestClaims.putIfAbsent(claim.getOrderId(), claim);
            }
        }
        for (SalOrder order : orders) {
            order.setClaimAllowed(false);
            order.setClaimDeadline(null);
            order.setClaimStatus(null);
            order.setClaimStatusText(null);
            LossClaim latest = latestClaims.get(order.getId());
            if (latest != null) {
                order.setClaimStatus(latest.getStatus());
                if ("0".equals(latest.getStatus())) {
                    order.setClaimStatusText("报损待审核");
                    continue;
                }
                if ("1".equals(latest.getStatus())) {
                    order.setClaimStatusText("报损已通过");
                    continue;
                }
                if ("3".equals(latest.getStatus())) {
                    order.setClaimStatusText("已补发");
                    continue;
                }
            }
            if (!"5".equals(order.getDeliveryStatus())) {
                continue;
            }
            if ("AUTO".equals(order.getDeliverySignType())) {
                order.setClaimStatusText("已自动签收，不可报损");
                continue;
            }
            if (order.getDeliverySignedTime() == null) {
                continue;
            }
            LocalDateTime deadline = order.getDeliverySignedTime().plusHours(windowHours);
            order.setClaimDeadline(deadline);
            if (now.isAfter(deadline)) {
                order.setClaimStatusText("已超过报损时限");
                continue;
            }
            order.setClaimAllowed(true);
            if (latest != null && "2".equals(latest.getStatus())) {
                order.setClaimStatusText("已驳回，可重新报备");
            }
        }
    }

    public List<LossClaim> listMine(Long partyId, Long merchantId) {
        return claimMapper.selectList(new LambdaQueryWrapper<LossClaim>()
                .eq(LossClaim::getPartyId, partyId)
                .eq(LossClaim::getMerchantId, merchantId)
                .orderByDesc(LossClaim::getCreateTime));
    }

    public Page<LossClaim> page(PageQuery query) {
        LambdaQueryWrapper<LossClaim> wrapper = new LambdaQueryWrapper<>();
        if (!TenantSupport.isPlatformAdmin()) {
            wrapper.eq(LossClaim::getMerchantId, TenantSupport.merchantId());
        }
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(LossClaim::getSaleNo, query.getKeyword())
                    .or().like(LossClaim::getReason, query.getKeyword()));
        }
        wrapper.orderByAsc(LossClaim::getStatus).orderByDesc(LossClaim::getCreateTime);
        return claimMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, boolean pass, String remark, String auditor) {
        LossClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw new BizException(6015, "报备单不存在");
        }
        if (!"0".equals(claim.getStatus())) {
            throw new BizException(6016, "该报备单已处理");
        }
        claim.setStatus(pass ? "1" : "2");
        claim.setAuditRemark(remark);
        claim.setAuditBy(auditor);
        claim.setAuditTime(LocalDateTime.now());
        claimMapper.updateById(claim);
    }

    /**
     * 审核通过后生成 0 元补发单，并自动创建分拣单；库存照常 FIFO 扣减，不计销售额。
     */
    @Transactional(rollbackFor = Exception.class)
    public SalOrder reship(Long id, ReshipForm reshipForm, String auditor) {
        LossClaim claim = claimMapper.selectById(id);
        if (claim == null) {
            throw new BizException(6015, "报备单不存在");
        }
        if (!"1".equals(claim.getStatus())) {
            throw new BizException(6017, "只有审核通过的报备才能补发");
        }
        SalOrder origin = orderMapper.selectById(claim.getOrderId());
        if (origin == null) {
            throw new BizException(6001, "原销售单不存在");
        }
        List<SalOrderItem> originItems = parseItems(origin.getItemsJson());
        if (originItems.isEmpty()) {
            throw new BizException(6004, "原销售单没有明细");
        }
        Map<Long, BigDecimal> originQty = new HashMap<>();
        for (SalOrderItem item : originItems) {
            originQty.put(item.getGoodsId(), item.getQty());
        }

        List<OrderLine> lines = new ArrayList<>();
        if (reshipForm != null && reshipForm.getItems() != null && !reshipForm.getItems().isEmpty()) {
            for (OrderLine req : reshipForm.getItems()) {
                if (req.getGoodsId() == null || req.getQty() == null
                        || req.getQty().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal max = originQty.get(req.getGoodsId());
                if (max == null) {
                    throw new BizException(6018, "补发商品不在原订单中");
                }
                if (req.getQty().compareTo(max) > 0) {
                    throw new BizException(6019, "补发数量不能超过原订单数量");
                }
                OrderLine line = new OrderLine();
                line.setGoodsId(req.getGoodsId());
                line.setQty(req.getQty());
                line.setPrice(BigDecimal.ZERO);
                lines.add(line);
            }
        } else {
            // 未传明细时按原单全量补发，兼容旧调用
            for (SalOrderItem item : originItems) {
                OrderLine line = new OrderLine();
                line.setGoodsId(item.getGoodsId());
                line.setQty(item.getQty());
                line.setPrice(BigDecimal.ZERO);
                lines.add(line);
            }
        }
        if (lines.isEmpty()) {
            throw new BizException(6020, "请填写补发数量");
        }

        SaleForm form = new SaleForm();
        form.setCustomerId(origin.getCustomerId());
        form.setReceiverName(origin.getReceiverName());
        form.setReceiverPhone(origin.getReceiverPhone());
        form.setReceiverAddress(origin.getReceiverAddress());
        form.setPayNow(true);
        form.setPayMethod("RESHIP");
        form.setReship(true);
        form.setRemark("损耗补发：" + claim.getSaleNo());
        form.setItems(lines);
        SalOrder reship = saleService.create(form);
        dlvOrderService.createFromSale(reship.getId());

        // 补发成本转为损耗记录：库存已由补发单 FIFO 扣减，这里只做损耗账；
        // 同时清零补发单销售成本，避免销售成本与损耗重复计算。
        List<SalOrderItem> reshipItems = parseItems(reship.getItemsJson());
        for (SalOrderItem item : reshipItems) {
            BigDecimal cost = item.getCostAmount() == null ? BigDecimal.ZERO : item.getCostAmount();
            InvAdjust adjust = new InvAdjust();
            adjust.setMerchantId(claim.getMerchantId());
            adjust.setAdjustNo(serialNo.next("SY"));
            adjust.setAdjustType(InvAdjust.TYPE_LOSS);
            adjust.setGoodsId(item.getGoodsId());
            adjust.setBatchNo(item.getBatchNo());
            adjust.setQty(item.getQty());
            adjust.setAmount(cost.setScale(2, RoundingMode.HALF_UP));
            adjust.setReason("损耗补发：" + claim.getSaleNo());
            adjust.setAdjustDate(LocalDate.now());
            adjust.setStatus("0");
            adjust.setOperator(auditor);
            adjustMapper.insert(adjust);
            item.setCostAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        }
        reship.setCostAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        reship.setItemsJson(writeJson(reshipItems));
        orderMapper.updateById(reship);

        claim.setStatus("3");
        claim.setReshipOrderId(reship.getId());
        claim.setReshipSaleNo(reship.getSaleNo());
        claim.setAuditBy(auditor);
        claimMapper.updateById(claim);
        return reship;
    }

    /** 管理员补发弹窗用：返回原销售单明细。 */
    public List<SalOrderItem> originItems(Long claimId) {
        LossClaim claim = claimMapper.selectById(claimId);
        if (claim == null) {
            throw new BizException(6015, "报备单不存在");
        }
        SalOrder origin = orderMapper.selectById(claim.getOrderId());
        return origin == null ? new ArrayList<>() : parseItems(origin.getItemsJson());
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

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
