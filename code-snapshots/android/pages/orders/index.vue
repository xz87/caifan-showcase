<template>
  <view class="orders-page">
    <view class="status-spacer" />
    <view class="app-header">
      <view class="app-header-title">我的订单</view>
      <view class="header-action" @tap="goStatement">月度对账单</view>
    </view>

    <view v-if="penalty" class="penalty-banner">
      优惠暂停至 {{ penalty.endText }}，期间下单不享受折扣和免运优惠。
    </view>

    <view class="page-content">
      <view v-for="order in orders" :key="order.id" :class="['card', 'order-card', order.status === '2' ? 'cancelled' : '']">
        <view class="order-head">
          <view>
            <view class="sale-no">{{ order.saleNo }}</view>
            <view class="order-time">{{ order.timeText }}</view>
          </view>
          <view :class="['tag', order.status === '2' ? '' : 'ok']">{{ order.statusText }}</view>
        </view>
        <view class="order-items">
          <view v-for="item in order.items" :key="item.goodsId" class="order-item">
            <view class="order-item-name">{{ item.goodsName }}</view>
            <view class="order-item-qty">×{{ quantity(item.qty) }}</view>
            <view class="order-item-amount">¥{{ money(item.amount) }}</view>
          </view>
        </view>
        <view class="status-row">
          <view :class="['tag', order.payStatus === '2' ? 'ok' : 'warn']">{{ order.payText }}</view>
          <view :class="['tag', order.deliveryStatus === '3' || order.deliveryStatus === '5' ? 'ok' : 'warn']">
            {{ order.dlvText }}
          </view>
          <view class="order-total">合计 <text class="money">¥{{ money(order.payable) }}</text></view>
        </view>
        <view v-if="order.status === '2'" class="cancel-note">
          <view>退单原因：{{ order.cancelRemark || '-' }}</view>
          <view v-if="order.refundStatus === '1'">模拟退款：¥{{ money(order.refundAmount) }}</view>
        </view>
        <view v-if="order.claimStatusText || (order.canClaim && order.claimDeadlineText)" class="claim-note">
          <view v-if="order.claimStatusText">{{ order.claimStatusText }}</view>
          <view v-if="order.canClaim && order.claimDeadlineText" class="claim-deadline">
            请在 {{ order.claimDeadlineText }} 前提交
          </view>
        </view>
        <view class="order-actions">
          <button v-if="order.canSign" class="btn-primary btn-small" @tap="confirmSign(order)">确认签收</button>
          <button v-else-if="order.status === '0' && order.payStatus !== '2'"
                  class="btn-primary btn-small" @tap="openPay(order)">去支付</button>
          <button v-if="order.canClaim" class="btn-ghost btn-small" @tap="goClaim(order)">损耗报备</button>
          <button v-if="order.canCancel" class="btn-ghost btn-small" @tap="openCancel(order)">取消订单</button>
          <button v-if="order.status === '0'" class="btn-ghost btn-small" @tap="reorder(order)">再来一单</button>
        </view>
      </view>
      <view v-if="!loading && !orders.length" class="empty">暂无订单</view>
    </view>

    <view v-if="showPay" class="mask" />
    <view v-if="showPay" class="bottom-sheet pay-sheet">
      <view class="sheet-head">
        <view class="sheet-title">扫码支付</view>
        <view class="sheet-close" @tap="closePay">关闭</view>
      </view>
      <view class="pay-amount">¥{{ payAmount }}</view>
      <image class="pay-qr" :src="payQrUrl" mode="widthFix" show-menu-by-longpress />
      <view class="pay-hint">请完成付款后点击“确定支付”</view>
      <button class="btn-primary btn-block" :loading="paying" @tap="confirmPay">确定支付</button>
    </view>

    <view v-if="showCancel" class="mask" @tap="closeCancel" />
    <view v-if="showCancel" class="bottom-sheet cancel-sheet">
      <view class="sheet-head">
        <view class="sheet-title">取消订单</view>
        <view class="sheet-close" @tap="closeCancel">关闭</view>
      </view>
      <view class="cancel-tip">未开始分拣的订单可以取消，已支付订单会在后台记录模拟退款。</view>
      <radio-group class="reason-list" @change="chooseCancelReason">
        <label class="reason-item">
          <radio value="NOT_NEEDED" :checked="cancelReasonCode === 'NOT_NEEDED'" color="#e85d04" />
          <text>暂时不需要了</text>
        </label>
        <label class="reason-item">
          <radio value="TEMPORARY" :checked="cancelReasonCode === 'TEMPORARY'" color="#e85d04" />
          <text>临时有事</text>
        </label>
        <label class="reason-item">
          <radio value="OTHER" :checked="cancelReasonCode === 'OTHER'" color="#e85d04" />
          <text>其他原因</text>
        </label>
      </radio-group>
      <textarea
        v-model="cancelRemark"
        class="field-textarea"
        maxlength="200"
        :placeholder="cancelReasonCode === 'OTHER' ? '请填写至少 5 个字的原因' : '请补充退单备注'"
      />
      <button class="btn-primary btn-block" :loading="cancelling" @tap="submitCancel">确认取消</button>
    </view>
  </view>
</template>

<script>
import { http } from '../../utils/request'
import { requireLogin } from '../../utils/auth'
import { getCart, saveCart } from '../../utils/cart'
import { refreshAssets, assetUrl } from '../../utils/assets'
import { money, quantity, dateTime } from '../../utils/format'

const DLV = { '0': '待分拣', '1': '分拣中', '2': '已分拣', '3': '已配送', '4': '已取消', '5': '已签收' }
const PAY = { '0': '未付款', '1': '部分付款', '2': '已付清' }
const STATUS = { '0': '正常', '1': '已退货', '2': '已取消' }
const ORDER_POLL_INTERVAL = 8000

export default {
  data() {
    return {
      orders: [],
      loading: false,
      penalty: null,
      payQrUrl: assetUrl('mall.payQr', '/caifan/img/miniprogram/pay-qr.jpg'),
      showPay: false,
      payOrderId: '',
      payAmount: '0.00',
      paying: false,
      showCancel: false,
      cancelOrderId: '',
      cancelReasonCode: 'NOT_NEEDED',
      cancelRemark: '',
      cancelling: false
    }
  },
  onShow() {
    if (!requireLogin()) return
    this.load()
    this.loadAssets()
    this.startStatusPolling()
  },
  onHide() {
    this.stopStatusPolling()
  },
  onUnload() {
    this.stopStatusPolling()
  },
  async onPullDownRefresh() {
    try {
      await this.load()
    } finally {
      uni.stopPullDownRefresh()
    }
  },
  methods: {
    money,
    quantity,
    startStatusPolling() {
      this.stopStatusPolling()
      this.statusPollTimer = setInterval(() => {
        this.load({ silent: true })
      }, ORDER_POLL_INTERVAL)
    },
    stopStatusPolling() {
      if (this.statusPollTimer) {
        clearInterval(this.statusPollTimer)
        this.statusPollTimer = null
      }
    },
    async loadAssets() {
      const assets = await refreshAssets()
      if (assets['mall.payQr']) this.payQrUrl = assets['mall.payQr']
    },
    async load(options = {}) {
      const silent = !!options.silent
      if (this.loadingRequest) return this.loadingRequest
      this.loadingRequest = (async () => {
        if (!silent) this.loading = true
        try {
          const page = await http.get('/api/mall/orders?pageNum=1&pageSize=30', { silent })
          const orders = (page.records || []).map((order) => ({
            ...order,
            items: order.items || [],
            statusText: STATUS[order.status] || '-',
            payText: PAY[order.payStatus] || '-',
            dlvText: order.status === '2'
              ? '已取消'
              : (order.deliveryStatus === '5' && order.deliverySignType === 'AUTO'
                ? '已自动签收'
                : (DLV[order.deliveryStatus] || '待分拣')),
            canSign: order.status === '0' && order.deliveryStatus === '3',
            canClaim: !!order.claimAllowed,
            canCancel: order.status === '0' && (!order.deliveryStatus || order.deliveryStatus === '0'),
            claimDeadlineText: order.claimDeadline ? dateTime(order.claimDeadline, true) : '',
            claimStatusText: order.claimStatusText || '',
            timeText: dateTime(order.saleDate, true)
          }))
          const status = await http.get('/api/mall/groups/status', { silent }).catch(() => null)
          const penalty = status && status.penalty
            ? { ...status.penalty, endText: dateTime(status.penalty.endTime, true) }
            : null
          const signature = JSON.stringify({ orders, penalty })
          if (!silent || signature !== this.orderSignature) {
            this.orderSignature = signature
            this.orders = orders
            this.penalty = penalty
          }
        } catch (error) {
          // 首次加载由 request 提示；静默轮询不重复打断用户
        } finally {
          if (!silent) this.loading = false
        }
      })()
      try {
        return await this.loadingRequest
      } finally {
        this.loadingRequest = null
      }
    },
    confirmSign(order) {
      uni.showModal({
        title: '确认签收',
        content: '确认已经收到全部菜品？',
        success: async (result) => {
          if (!result.confirm) return
          try {
            await http.post('/api/mall/orders/' + order.id + '/confirm-receipt')
            uni.showToast({ title: '已签收', icon: 'success' })
            this.load()
          } catch (error) {
            // request 已提示
          }
        }
      })
    },
    openPay(order) {
      this.payOrderId = order.id
      this.payAmount = money(order.payable)
      this.showPay = true
    },
    closePay() {
      if (!this.paying) this.showPay = false
    },
    async confirmPay() {
      if (this.paying || !this.payOrderId) return
      this.paying = true
      try {
        await http.post('/api/mall/pay', { orderId: this.payOrderId })
        this.showPay = false
        uni.showToast({ title: '支付成功', icon: 'success' })
        this.load()
      } catch (error) {
        // request 已提示
      } finally {
        this.paying = false
      }
    },
    goClaim(order) {
      uni.navigateTo({
        url: '/pages/claim/index?orderId=' + order.id +
          '&saleNo=' + encodeURIComponent(order.saleNo || '') +
          '&claimAllowed=1' +
          '&deadline=' + encodeURIComponent(order.claimDeadlineText || '') +
          '&status=' + encodeURIComponent(order.claimStatusText || '')
      })
    },
    goStatement() {
      uni.navigateTo({ url: '/pages/statement/index' })
    },
    async reorder(order) {
      let items = order.items || []
      if (!items.length) {
        items = await http.get('/api/mall/orders/' + order.id + '/items')
      }
      if (!items.length) return uni.showToast({ title: '该订单暂无明细', icon: 'none' })
      const cart = getCart()
      items.forEach((item) => {
        cart[item.goodsId] = Number(cart[item.goodsId] || 0) + Number(item.qty || 0)
      })
      saveCart(cart)
      uni.showToast({ title: '已加入购物车', icon: 'success' })
      setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 500)
    },
    openCancel(order) {
      this.cancelOrderId = order.id
      this.cancelReasonCode = 'NOT_NEEDED'
      this.cancelRemark = ''
      this.showCancel = true
    },
    closeCancel() {
      if (!this.cancelling) this.showCancel = false
    },
    chooseCancelReason(event) {
      this.cancelReasonCode = event.detail.value
    },
    async submitCancel() {
      const remark = this.cancelRemark.trim()
      if (!remark) return uni.showToast({ title: '请填写退单备注', icon: 'none' })
      if (this.cancelReasonCode === 'OTHER' && remark.length < 5) {
        return uni.showToast({ title: '其他原因至少填写 5 个字', icon: 'none' })
      }
      this.cancelling = true
      try {
        await http.post('/api/mall/orders/' + this.cancelOrderId + '/cancel', {
          reasonCode: this.cancelReasonCode,
          reason: remark
        })
        this.showCancel = false
        uni.showToast({ title: '订单已取消', icon: 'success' })
        this.load()
      } catch (error) {
        // request 已提示
      } finally {
        this.cancelling = false
      }
    }
  }
}
</script>

<style scoped>
.orders-page {
  min-height: 100vh;
  background: #f4f7f5;
}

.penalty-banner {
  margin: 18rpx 24rpx 0;
  padding: 18rpx 20rpx;
  border: 1rpx solid #f5d9c4;
  border-radius: 14rpx;
  background: #fff7f1;
  color: #9b4c18;
  font-size: 22rpx;
  line-height: 1.55;
}

.order-card {
  margin-bottom: 18rpx;
  padding: 22rpx;
}

.order-card.cancelled {
  background: #fafbfa;
}

.order-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14rpx;
}

.sale-no {
  color: #25322a;
  font-size: 27rpx;
  font-weight: 700;
}

.order-time {
  margin-top: 5rpx;
  color: #8a928d;
  font-size: 20rpx;
}

.order-items {
  margin-top: 15rpx;
  padding: 10rpx 16rpx;
  border-radius: 12rpx;
  background: #f7f9f8;
}

.order-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 7rpx 0;
  color: #59655d;
  font-size: 23rpx;
}

.order-item-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-item-qty {
  color: #858e88;
  font-size: 21rpx;
}

.order-item-amount {
  min-width: 90rpx;
  color: #4b574f;
  text-align: right;
}

.status-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 16rpx;
}

.order-total {
  margin-left: auto;
  color: #68726b;
  font-size: 22rpx;
}

.order-total .money {
  margin-left: 5rpx;
  font-size: 29rpx;
}

.cancel-note {
  margin-top: 14rpx;
  padding: 13rpx 16rpx;
  border-radius: 10rpx;
  background: #f2f4f2;
  color: #727b75;
  font-size: 21rpx;
  line-height: 1.6;
}

.claim-note {
  margin-top: 14rpx;
  padding: 13rpx 16rpx;
  border-radius: 10rpx;
  background: #eef7f1;
  color: #356044;
  font-size: 21rpx;
  line-height: 1.6;
}

.claim-deadline {
  color: #7a857d;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12rpx;
  margin-top: 16rpx;
}

.pay-sheet {
  text-align: center;
}

.pay-amount {
  margin: 10rpx 0;
  color: #df5a08;
  font-size: 50rpx;
  font-weight: 800;
}

.pay-qr {
  width: 410rpx;
  margin: 8rpx auto 16rpx;
  border-radius: 16rpx;
}

.pay-hint,
.cancel-tip {
  margin-bottom: 20rpx;
  color: #7c857f;
  font-size: 22rpx;
  line-height: 1.55;
}

.reason-list {
  display: flex;
  flex-direction: column;
  margin-bottom: 18rpx;
}

.reason-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 17rpx 0;
  border-bottom: 1rpx solid #edf0ee;
  font-size: 25rpx;
}

.cancel-sheet .field-textarea {
  margin-bottom: 18rpx;
}
</style>
