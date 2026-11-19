<template>
  <view class="groups-page">
    <view class="status-spacer" />
    <view class="app-header">
      <view class="app-header-title">拼单买菜</view>
      <view v-if="groupStatusLoaded && groupEnabled" class="app-header-sub">一起买，更划算</view>
    </view>

    <view v-if="groupStatusLoaded && !groupEnabled" class="closed-banner">
      <view class="closed-title">拼单功能暂时关闭</view>
      <view class="closed-text">已有拼单仍可完成下单和支付，新开团将在后台开启后恢复。</view>
    </view>

    <view v-if="penalty" class="penalty-banner">
      优惠暂停至 {{ penalty.endText }}，期间按普通零售价和基础运费结算。
    </view>

    <view class="page-content">
      <template v-if="!groupStatusLoaded || groupEnabled">
        <view class="card create-panel">
          <view class="panel-head">
            <view>
              <view class="section-title">发起拼单</view>
              <view class="section-note">可多选菜品，并设置每样菜的默认数量</view>
            </view>
            <view class="selected-count">已选 {{ selectedGoods.length }}/20</view>
          </view>
          <scroll-view class="goods-picker" scroll-y>
            <checkbox-group @change="onGoodsChange">
              <label v-for="goods in goods" :key="goods.id" :class="['pick-row', selectedGoodsIds.includes(String(goods.id)) ? 'selected' : '']">
                <checkbox :value="String(goods.id)" :checked="selectedGoodsIds.includes(String(goods.id))" color="#e85d04" />
                <view class="pick-main">
                  <view class="pick-title-row">
                    <text class="pick-name">{{ goods.name }}</text>
                    <text class="pick-price">{{ goods.priceTag }} ¥{{ goods.groupPriceText }}/{{ goods.unit || '份' }}</text>
                  </view>
                  <view v-if="selectedGoodsIds.includes(String(goods.id))" class="pick-qty-row" @tap.stop>
                    <text>默认数量</text>
                    <view class="stepper">
                      <view class="stepper-btn" @tap.stop="changePickQty(goods.id, -1)">−</view>
                      <input
                        class="pick-qty-input"
                        type="digit"
                        :value="goods.pickQty"
                        @input="inputPickQty(goods.id, $event)"
                      />
                      <view class="stepper-btn" @tap.stop="changePickQty(goods.id, 1)">＋</view>
                      <text class="pick-unit">{{ goods.unit || '份' }}</text>
                    </view>
                  </view>
                </view>
              </label>
            </checkbox-group>
          </scroll-view>
          <view class="target-row">
            <view>
              <view class="field-label">成团人数</view>
              <view class="section-note">支持 2 至 10 人</view>
            </view>
            <view class="target-stepper">
              <view class="stepper-btn" @tap="changeTarget(-1)">−</view>
              <text class="target-value">{{ targetCount }}</text>
              <view class="stepper-btn" @tap="changeTarget(1)">＋</view>
            </view>
          </view>
          <button class="btn-primary btn-block" :loading="creating" @tap="createGroup">创建多菜品拼单</button>
        </view>

        <view class="card join-panel">
          <view class="section-title">加入拼单</view>
          <view class="join-row">
            <input v-model="joinCode" class="field-input join-input" placeholder="输入拼单码" />
            <button class="btn-secondary join-button" :loading="joining" @tap="joinGroup">加入</button>
          </view>
        </view>
      </template>

      <view v-if="publicGroups.length" class="public-section">
        <view class="section-row">
          <view class="section-title">正在拼单</view>
          <view class="section-note">左右滑动查看更多</view>
        </view>
        <scroll-view class="public-scroll" scroll-x enable-flex>
          <view v-for="group in publicGroups" :key="group.id" class="public-card">
            <view class="public-head">
              <view class="public-name">{{ group.displayName }}</view>
              <view class="tag warn">{{ group.memberCount }}/{{ group.targetCount }} 人</view>
            </view>
            <view v-for="item in group.items" :key="item.goodsId" class="public-item">
              {{ item.goodsName }} · ¥{{ money(item.groupPrice) }}/{{ item.unit || '份' }}
            </view>
            <view class="public-meta">{{ group.leaderName || '拼友' }} · 截止 {{ group.expireText }}</view>
            <button class="btn-secondary btn-small public-button" @tap="copyCode(group.groupNo)">复制拼单码</button>
          </view>
        </scroll-view>
      </view>

      <view class="my-section">
        <view class="section-row">
          <view class="section-title">我的拼单</view>
          <view class="section-note">{{ groups.length }} 个记录</view>
        </view>
        <view v-for="group in groups" :key="group.id" class="card group-card">
          <view class="group-head">
            <view>
              <view class="group-name">{{ group.displayName }}</view>
              <view class="group-no">{{ group.groupNo }}</view>
            </view>
            <view :class="['tag', group.status === '1' ? 'ok' : (group.status === '0' ? 'warn' : '')]">
              {{ group.memberStatus === '2' ? '已退出' : group.statusText }}
            </view>
          </view>
          <view class="group-items">
            <view v-for="item in group.items" :key="item.goodsId" class="group-item">
              <view class="group-item-main">
                <text>{{ item.goodsName }} · {{ quantity(item.qty) }}{{ item.unit }}</text>
                <text v-if="item.priceSource === 'OFFICIAL'" class="tag ok source-tag">今日官方价</text>
                <text v-else-if="item.priceSource === 'GROUP_MANUAL'" class="tag warn source-tag">团内改价</text>
              </view>
              <text class="money">¥{{ money(item.groupPrice) }}/{{ item.unit || '份' }}</text>
            </view>
          </view>
          <view class="group-progress">
            <view class="progress-text">{{ group.memberCount }}/{{ group.targetCount }} 人 · 截止 {{ group.expireText }}</view>
            <button class="mini-copy" @tap="copyCode(group.groupNo)">复制拼单码</button>
          </view>
          <button v-if="group.status === '1' && !group.ordered && group.memberStatus !== '2'"
                  class="btn-primary group-action" @tap="openOrder(group)">去下单</button>
          <view v-if="group.ordered" class="ordered-tip">已下单，请到订单页完成支付</view>
          <button v-if="group.memberStatus !== '2' && group.status !== '2'"
                  class="btn-ghost group-action" @tap="exitGroup(group)">退出拼单</button>
        </view>
        <view v-if="!groups.length" class="empty">还没有参与拼单</view>
      </view>
    </view>

    <view v-if="showOrder" class="mask" @tap="closeOrder" />
    <view v-if="showOrder" class="bottom-sheet order-sheet">
      <view class="sheet-head">
        <view>
          <view class="sheet-title">拼单下单</view>
          <view class="section-note">{{ current.groupNo }}</view>
        </view>
        <view class="sheet-close" @tap="closeOrder">关闭</view>
      </view>
      <scroll-view class="order-goods" scroll-y>
        <view v-for="(item, index) in current.items" :key="item.goodsId" class="order-goods-row">
          <view class="order-goods-main">
            <view class="order-goods-name">{{ item.goodsName }}</view>
            <view class="order-goods-price">¥{{ money(item.groupPrice) }}/{{ item.unit || '份' }}</view>
          </view>
          <view class="stepper">
            <view class="stepper-btn" @tap="changeOrderQty(index, -1)">−</view>
            <input
              class="order-qty-input"
              type="digit"
              :value="item.qty"
              @input="inputOrderQty(index, $event)"
            />
            <view class="stepper-btn" @tap="changeOrderQty(index, 1)">＋</view>
          </view>
        </view>
      </scroll-view>
      <view class="order-summary">
        已选 {{ selectedOrderCount }} 种 · 商品合计
        <text class="money">¥{{ groupOrderTotal }}</text>
      </view>
      <view v-if="freightTip" class="freight-tip">{{ freightTip }}</view>
      <view class="field">
        <text class="field-label">收货人</text>
        <input v-model="receiverName" class="field-input" placeholder="请输入收货人" />
      </view>
      <view class="field">
        <text class="field-label">收货电话</text>
        <input v-model="receiverPhone" class="field-input" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="field">
        <text class="field-label">收货地址</text>
        <input v-model="receiverAddress" class="field-input" placeholder="请输入详细地址" />
      </view>
      <button class="btn-primary btn-block" :loading="submitting" @tap="submitOrder">提交订单</button>
    </view>
  </view>
</template>

<script>
import { http } from '../../utils/request'
import { customer, requireLogin } from '../../utils/auth'
import { money, quantity, dateTime } from '../../utils/format'

const STATUS_TEXT = { '0': '拼单中', '1': '已成团', '2': '已结束' }

function normalizeItems(group) {
  const source = Array.isArray(group.items) && group.items.length
    ? group.items
    : [{
        goodsId: group.goodsId,
        goodsName: group.goodsName,
        unit: group.unit,
        groupPrice: group.groupPrice,
        qty: 1
      }]
  return source.filter((item) => item && item.goodsId).map((item) => ({
    ...item,
    qty: Number(item.qty || 1)
  }))
}

function displayName(items) {
  if (!items.length) return '拼单菜品'
  return items.length > 1 ? items[0].goodsName + '等' + items.length + '样菜' : items[0].goodsName
}

export default {
  data() {
    return {
      groups: [],
      publicGroups: [],
      goods: [],
      selectedGoodsIds: [],
      targetCount: 3,
      joinCode: '',
      groupEnabled: false,
      groupStatusLoaded: false,
      penalty: null,
      freightTip: '',
      creating: false,
      joining: false,
      showOrder: false,
      current: {},
      selectedOrderCount: 0,
      groupOrderTotal: '0.00',
      receiverName: '',
      receiverPhone: '',
      receiverAddress: '',
      submitting: false
    }
  },
  computed: {
    selectedGoods() {
      return this.goods.filter((item) => this.selectedGoodsIds.includes(String(item.id)))
    }
  },
  onShow() {
    if (requireLogin()) this.load()
  },
  methods: {
    money,
    quantity,
    async load() {
      try {
        const [myGroups, publicGroups, goods, addresses, promotions, status] = await Promise.all([
          http.get('/api/mall/groups'),
          http.get('/api/mall/groups/public').catch(() => []),
          http.get('/api/mall/goods'),
          http.get('/api/mall/addresses').catch(() => []),
          http.get('/api/mall/groups/active-promotions').catch(() => []),
          http.get('/api/mall/groups/status').catch(() => null)
        ])
        const groupStatus = status || {}
        this.groupEnabled = !!(groupStatus.setting && groupStatus.setting.enabled)
        this.groupStatusLoaded = true
        this.penalty = groupStatus.penalty
          ? { ...groupStatus.penalty, endText: dateTime(groupStatus.penalty.endTime, true) }
          : null
        this.groups = (myGroups || []).map((group) => {
          const items = normalizeItems(group)
          return {
            ...group,
            items,
            displayName: displayName(items),
            statusText: STATUS_TEXT[group.status] || '已结束',
            expireText: dateTime(group.expireTime, true)
          }
        })
        this.publicGroups = (publicGroups || []).map((group) => {
          const items = normalizeItems(group)
          return {
            ...group,
            items,
            displayName: displayName(items),
            expireText: dateTime(group.expireTime, true)
          }
        })
        const official = {}
        ;(groupStatus.prices || []).forEach((item) => {
          official[item.goodsId] = Number(item.price)
        })
        const promotionsMap = {}
        ;(promotions || []).forEach((promotion) => {
          ;(promotion.items || []).forEach((item) => {
            const price = Number(item.promoPrice)
            if (!promotionsMap[item.goodsId] || price < promotionsMap[item.goodsId]) {
              promotionsMap[item.goodsId] = price
            }
          })
        })
        this.goods = (goods || []).map((item) => {
          const regular = Number(item.groupPrice != null ? item.groupPrice : item.salePrice)
          const promo = promotionsMap[item.id]
          const chosen = official[item.id] || (promo && promo < regular ? promo : regular)
          return {
            ...item,
            pickQty: 1,
            groupPriceText: money(chosen),
            priceTag: official[item.id] ? '今日价' : (promo ? '活动价' : '拼单价')
          }
        })
        const currentCustomer = customer() || {}
        const type = currentCustomer.customerType === 'PURCHASER' ? 'PURCHASER' : 'RETAIL'
        const rule = (groupStatus.freightRules || []).find((item) => item.customerType === type)
        if (this.penalty) {
          this.freightTip = '优惠暂停：按普通零售价 + 基础运费 ¥' + money(rule && rule.baseFee)
        } else if (rule) {
          this.freightTip = '基础运费 ¥' + money(rule.baseFee)
            + (Number(rule.freeThreshold || 0) > 0 ? '，满 ¥' + money(rule.freeThreshold) + ' 免运' : '')
        }
        const defaultAddress = (addresses || []).find((item) => Number(item.isDefault) === 1)
          || (addresses || [])[0]
        if (defaultAddress && !this.receiverName) {
          this.receiverName = defaultAddress.receiverName
          this.receiverPhone = defaultAddress.receiverPhone
          this.receiverAddress = defaultAddress.address
        }
      } catch (error) {
        // request 已提示
      }
    },
    onGoodsChange(event) {
      this.selectedGoodsIds = (event.detail.value || []).map(String)
    },
    findGoods(id) {
      return this.goods.find((item) => String(item.id) === String(id))
    },
    changePickQty(id, delta) {
      const goods = this.findGoods(id)
      if (!goods) return
      goods.pickQty = Math.max(0.1, Number(goods.pickQty || 1) + Number(delta))
    },
    inputPickQty(id, event) {
      const goods = this.findGoods(id)
      if (goods) goods.pickQty = event.detail.value
    },
    changeTarget(delta) {
      this.targetCount = Math.max(2, Math.min(10, Number(this.targetCount || 3) + Number(delta)))
    },
    async createGroup() {
      if (this.creating) return
      const items = this.selectedGoods.map((item) => ({
        goodsId: item.id,
        qty: Number(item.pickQty)
      }))
      if (!items.length) return uni.showToast({ title: '请至少选择一个菜品', icon: 'none' })
      if (items.some((item) => !item.qty || item.qty <= 0)) {
        return uni.showToast({ title: '请填写正确的菜品数量', icon: 'none' })
      }
      this.creating = true
      try {
        const group = await http.post('/api/mall/groups', {
          items,
          targetCount: Number(this.targetCount)
        })
        this.selectedGoodsIds = []
        this.goods.forEach((item) => {
          item.pickQty = 1
        })
        uni.showModal({
          title: '拼单已创建',
          content: '把拼单码发给朋友：' + group.groupNo,
          showCancel: false
        })
        this.load()
      } catch (error) {
        // request 已提示
      } finally {
        this.creating = false
      }
    },
    async joinGroup() {
      if (this.joining) return
      if (!this.joinCode.trim()) return uni.showToast({ title: '请输入拼单码', icon: 'none' })
      this.joining = true
      try {
        await http.post('/api/mall/groups/join', { groupNo: this.joinCode.trim() })
        this.joinCode = ''
        uni.showToast({ title: '已加入拼单', icon: 'success' })
        this.load()
      } catch (error) {
        // request 已提示
      } finally {
        this.joining = false
      }
    },
    copyCode(code) {
      uni.setClipboardData({
        data: code,
        success: () => {
          this.joinCode = code
          uni.showToast({ title: '拼单码已复制', icon: 'success' })
        }
      })
    },
    exitGroup(group) {
      uni.showModal({
        title: '退出拼单',
        content: '退出后会释放名额并保留历史记录；如已生成销售单，请先取消订单。',
        success: async (result) => {
          if (!result.confirm) return
          try {
            await http.post('/api/mall/groups/' + group.id + '/exit')
            uni.showToast({ title: '已退出拼单', icon: 'success' })
            this.load()
          } catch (error) {
            // request 已提示
          }
        }
      })
    },
    openOrder(group) {
      const current = {
        ...group,
        items: group.items.map((item) => ({ ...item, qty: Number(item.qty || 1) }))
      }
      this.current = current
      this.updateOrderSummary()
      this.showOrder = true
    },
    closeOrder() {
      if (!this.submitting) this.showOrder = false
    },
    changeOrderQty(index, delta) {
      const items = this.current.items.map((item, idx) =>
        idx === index ? { ...item, qty: Math.max(0, Number(item.qty || 0) + Number(delta)) } : item)
      this.current = { ...this.current, items }
      this.updateOrderSummary()
    },
    inputOrderQty(index, event) {
      const items = this.current.items.map((item, idx) =>
        idx === index ? { ...item, qty: event.detail.value } : item)
      this.current = { ...this.current, items }
      this.updateOrderSummary()
    },
    updateOrderSummary() {
      let count = 0
      let total = 0
      ;(this.current.items || []).forEach((item) => {
        if (Number(item.qty) > 0) {
          count += 1
          total += Number(item.qty) * Number(item.groupPrice || 0)
        }
      })
      this.selectedOrderCount = count
      this.groupOrderTotal = money(total)
    },
    async submitOrder() {
      if (this.submitting || !this.current.id) return
      const items = (this.current.items || [])
        .filter((item) => Number(item.qty) > 0)
        .map((item) => ({ goodsId: item.goodsId, qty: Number(item.qty) }))
      if (!items.length) return uni.showToast({ title: '请选择至少一个菜品', icon: 'none' })
      if (!this.receiverName.trim() || !/^1\d{10}$/.test(this.receiverPhone) || !this.receiverAddress.trim()) {
        return uni.showToast({ title: '请填写完整收货信息', icon: 'none' })
      }
      this.submitting = true
      try {
        await http.post('/api/mall/groups/' + this.current.id + '/order', {
          items,
          receiverName: this.receiverName,
          receiverPhone: this.receiverPhone,
          receiverAddress: this.receiverAddress
        })
        this.showOrder = false
        uni.showToast({ title: '下单成功，请支付', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/orders/index' }), 600)
      } catch (error) {
        // request 已提示
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.groups-page {
  min-height: 100vh;
  background: #f4f7f5;
}

.closed-banner,
.penalty-banner {
  margin: 20rpx 24rpx 0;
  padding: 20rpx;
  border-radius: 14rpx;
}

.closed-banner {
  border: 1rpx solid #e2e7e4;
  background: #fff;
}

.closed-title {
  color: #36423a;
  font-size: 28rpx;
  font-weight: 750;
}

.closed-text {
  margin-top: 6rpx;
  color: #79827c;
  font-size: 22rpx;
  line-height: 1.55;
}

.penalty-banner {
  border: 1rpx solid #f5d9c4;
  background: #fff7f1;
  color: #9b4c18;
  font-size: 23rpx;
  line-height: 1.55;
}

.create-panel,
.join-panel {
  padding: 24rpx;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.selected-count {
  color: #d9580b;
  font-size: 21rpx;
}

.goods-picker {
  max-height: 520rpx;
  border: 1rpx solid #e8ece9;
  border-radius: 14rpx;
}

.pick-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 18rpx;
  border-bottom: 1rpx solid #eff2f0;
  background: #fff;
}

.pick-row:last-child {
  border-bottom: 0;
}

.pick-row.selected {
  background: #fff9f3;
}

.pick-main {
  flex: 1;
  min-width: 0;
}

.pick-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.pick-name {
  overflow: hidden;
  color: #27332b;
  font-size: 26rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pick-price {
  flex-shrink: 0;
  color: #d9580b;
  font-size: 21rpx;
}

.pick-qty-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
  padding-top: 12rpx;
  border-top: 1rpx dashed #ecd8ca;
  color: #7c685b;
  font-size: 21rpx;
}

.pick-qty-input {
  width: 74rpx;
  height: 44rpx;
  border-radius: 8rpx;
  background: #f4f6f4;
  font-size: 23rpx;
  text-align: center;
}

.pick-unit {
  color: #89918c;
  font-size: 20rpx;
}

.target-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin: 22rpx 0;
}

.target-stepper {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.target-value {
  min-width: 54rpx;
  color: #26322b;
  font-size: 30rpx;
  font-weight: 750;
  text-align: center;
}

.join-panel {
  margin-top: 18rpx;
}

.join-row {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.join-input {
  flex: 1;
}

.join-button {
  flex-shrink: 0;
  width: 150rpx;
  height: 76rpx;
  line-height: 76rpx;
}

.public-section,
.my-section {
  margin-top: 28rpx;
}

.public-scroll {
  display: flex;
  width: 100%;
  white-space: nowrap;
}

.public-card {
  display: inline-flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 500rpx;
  margin-right: 16rpx;
  padding: 22rpx;
  border: 1rpx solid #e6ebe7;
  border-radius: 16rpx;
  background: #fff;
  vertical-align: top;
}

.public-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.public-name {
  overflow: hidden;
  color: #243129;
  font-size: 28rpx;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.public-item {
  margin-top: 9rpx;
  overflow: hidden;
  color: #5f6b63;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.public-meta {
  margin-top: 12rpx;
  color: #89918c;
  font-size: 20rpx;
}

.public-button {
  margin-top: 16rpx;
}

.group-card {
  margin-bottom: 18rpx;
  padding: 22rpx;
}

.group-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.group-name {
  color: #25322a;
  font-size: 29rpx;
  font-weight: 750;
}

.group-no {
  margin-top: 5rpx;
  color: #8a928d;
  font-size: 20rpx;
}

.group-items {
  margin-top: 14rpx;
  padding: 12rpx 16rpx;
  border-radius: 12rpx;
  background: #f7f9f8;
}

.group-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  padding: 6rpx 0;
  color: #5e6962;
  font-size: 22rpx;
}

.group-item-main {
  display: flex;
  align-items: center;
  gap: 8rpx;
  min-width: 0;
}

.source-tag {
  font-size: 18rpx;
}

.group-progress {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  margin-top: 14rpx;
}

.progress-text {
  color: #7c857f;
  font-size: 21rpx;
}

.mini-copy {
  margin: 0;
  padding: 0;
  background: transparent;
  color: #d9580b;
  font-size: 21rpx;
  line-height: 1.4;
}

.group-action {
  margin-top: 14rpx;
}

.ordered-tip {
  margin-top: 14rpx;
  color: #277648;
  font-size: 22rpx;
}

.order-goods {
  max-height: 360rpx;
  margin: 18rpx 0;
  border-top: 1rpx solid #edf0ee;
  border-bottom: 1rpx solid #edf0ee;
}

.order-goods-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f1f3f2;
}

.order-goods-row:last-child {
  border-bottom: 0;
}

.order-goods-main {
  flex: 1;
  min-width: 0;
}

.order-goods-name {
  overflow: hidden;
  color: #26322b;
  font-size: 26rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-goods-price {
  margin-top: 5rpx;
  color: #d9580b;
  font-size: 21rpx;
}

.order-qty-input {
  width: 74rpx;
  height: 44rpx;
  border-radius: 8rpx;
  background: #f4f6f4;
  font-size: 23rpx;
  text-align: center;
}

.order-summary {
  margin-bottom: 6rpx;
  color: #69746c;
  font-size: 23rpx;
}

.freight-tip {
  margin-bottom: 18rpx;
  color: #8a928d;
  font-size: 21rpx;
}
</style>
