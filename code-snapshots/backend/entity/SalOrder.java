package com.caifan.business.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.caifan.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sal_order")
public class SalOrder extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long merchantId;

    private String saleNo;

    private Long customerId;

    /** 拼单团 id（普通销售单为空） */
    private Long groupId;

    /** 拼单成员 id（普通销售单为空） */
    private Long groupMemberId;

    /** 原价合计 */
    private BigDecimal totalAmount;

    /** 优惠金额 */
    private BigDecimal discount;

    /** 应收金额 = 原价 - 优惠 */
    private BigDecimal payable;

    /** 运费 */
    private BigDecimal freightAmount;

    /** FIFO 销售成本合计，开单时结转 */
    private BigDecimal costAmount;

    /** 0 未收，1 部分，2 已收 */
    private String payStatus;

    /** 0 正常，1 已退货，2 已取消 */
    private String status;

    private LocalDateTime saleDate;

    private String salesperson;

    /** 收货人 */
    private String receiverName;

    /** 收货电话 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;

    private String remark;

    private String cancelReason;

    private String cancelRemark;

    private String cancelOperator;

    private LocalDateTime cancelTime;

    /** 0 未退款，1 已模拟退款 */
    private String refundStatus;

    private BigDecimal refundAmount;

    private LocalDateTime refundTime;

    /** 明细 JSON 字符串，元素为 SalOrderItem */
    private String itemsJson;

    /** 关联分拣单号，非数据库字段，按需填充 */
    @TableField(exist = false)
    private String deliveryNo;

    /** 分拣/配送状态，非数据库字段，值同 DlvOrder.status */
    @TableField(exist = false)
    private String deliveryStatus;

    /** 签收来源，非数据库字段 */
    @TableField(exist = false)
    private String deliverySignType;

    /** 签收时间，非数据库字段 */
    @TableField(exist = false)
    private LocalDateTime deliverySignedTime;

    /** 当前是否允许客户提交损耗报备 */
    @TableField(exist = false)
    private Boolean claimAllowed;

    /** 当前商户配置的主动签收报损时限 */
    @TableField(exist = false)
    private Integer claimWindowHours;

    /** 主动签收订单的报损截止时间 */
    @TableField(exist = false)
    private LocalDateTime claimDeadline;

    /** 最近一次报损状态 */
    @TableField(exist = false)
    private String claimStatus;

    /** 最近一次报损状态文案 */
    @TableField(exist = false)
    private String claimStatusText;

    /** 订单明细，非数据库字段，按需填充 */
    @TableField(exist = false)
    private List<SalOrderItem> items;

    /** 客户端展示：当前订单是否允许取消。 */
    @TableField(exist = false)
    private Boolean canCancel;
}
