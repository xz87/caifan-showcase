<template>
  <view class="shop-page">
    <view class="status-spacer" />
    <view class="shop-header">
      <view class="header-copy">
        <view class="shop-brand">鲜蔬购菜</view>
        <view class="delivery-line">
          <text>{{ customerName || '正在登录' }}</text>
          <text class="dot">·</text>
          <text class="address-text">{{ addressSummary }}</text>
        </view>
      </view>
      <view class="header-address" @tap="goAddress">地址</view>
    </view>

    <view v-if="auditPending" class="audit-banner">
      采购商资质正在审核，审核通过后可享受采购价并提交订单
    </view>

    <scroll-view v-if="groupEnabled && notices.length" class="notice-scroll" scroll-x enable-flex>
      <view v-for="notice in notices" :key="notice.key" class="notice-card" @tap="goGroups">
        <view class="notice-kicker">{{ notice.kicker }}</view>
        <view class="notice-title">{{ notice.title }}</view>
        <view class="notice-desc">{{ notice.desc }}</view>
        <view class="notice-action">{{ notice.action }} ›</view>
      </view>
    </scroll-view>

    <view class="catalog">
      <view v-for="category in categories" :key="category.id" class="category-section">
        <view class="category-head">
          <view>
            <view class="category-title">{{ category.name }}</view>
            <view class="category-count">{{ category.goods.length }} 种商品</view>
          </view>
        </view>
        <view class="goods-grid">
          <view
            v-for="goods in category.goods"
            :key="goods.id"
            :class="['goods-card', lastAddedId === goods.id ? 'bump' : '']"
            @tap="addToCart(goods)"
          >
            <view class="image-shell">
              <image
                v-if="goods.imgUrl"
                class="goods-image"
                :src="goods.imgUrl"
                mode="aspectFill"
                lazy-load
                @error="handleImageError(goods)"
              />
              <view v-else class="image-placeholder">鲜</view>
              <view v-if="goods.qty > 0" class="cart-badge">{{ goods.displayQty }}</view>
            </view>
            <view class="goods-name">{{ goods.name }}</view>
            <view class="goods-meta">{{ goods.origin || '当日新鲜' }} · {{ goods.unit || '份' }}</view>
            <view class="goods-bottom">
              <view class="goods-price">
                <text class="currency">¥</text>{{ money(goods.salePrice) }}
                <text class="unit">/{{ goods.unit || '份' }}</text>
              </view>
              <view class="stepper" @tap.stop>
                <view class="stepper-btn" @tap.stop="changeQty(goods.id, -1)">−</view>
                <text class="stepper-value">{{ goods.displayQty }}</text>
                <view class="stepper-btn" @tap.stop="changeQty(goods.id, 1)">＋</view>
              </view>
            </view>
          </view>
        </view>
      </view>
      <view v-if="!loading && !categories.length" class="empty">暂无可售菜品</view>
    </view>

    <view v-if="cartTypes > 0" class="cart-bar">
      <view class="cart-summary">
        <view class="cart-count">{{ cartUnits }} 件 · {{ cartTypes }} 种</view>
        <view class="cart-total">合计 <text>¥{{ cartAmount }}</text></view>
      </view>
      <button class="checkout-button" @tap="openCheckout">去结算</button>
    </view>

    <view v-if="showCheckout" class="mask" @tap="closeCheckout" />
    <view v-if="showCheckout" class="bottom-sheet checkout-sheet">
      <view class="sheet-head">
        <view class="sheet-title">确认订单</view>
        <view class="sheet-close" @tap="closeCheckout">关闭</view>
      </view>
      <view class="checkout-total">
        <text>商品合计</text>
        <text class="money">¥{{ cartAmount }}</text>
      </view>
      <view v-if="freightTip" class="freight-tip">{{ freightTip }}</view>
      <scroll-view class="checkout-list" scroll-y>
        <view v-for="item in checkoutItems" :key="item.id" class="checkout-item">
          <view class="checkout-main">
            <view class="checkout-name">{{ item.name }}</view>
            <view class="checkout-price">¥{{ money(item.price) }} / {{ item.unit || '份' }}</view>
          </view>
          <view class="stepper">
            <view class="stepper-btn" @tap="changeQty(item.id, -1)">−</view>
            <text class="stepper-value">{{ quantity(item.qty) }}</text>
            <view class="stepper-btn" @tap="changeQty(item.id, 1)">＋</view>
          </view>
        </view>
      </scroll-view>

      <view class="address-head">
        <view class="section-title small-title">收货信息</view>
        <picker
          v-if="addresses.length"
          mode="selector"
          :range="addresses"
          range-key="label"
          :value="selectedAddressIndex"
          @change="onAddressChange"
        >
          <view class="address-picker">{{ selectedAddressLabel || '选择地址' }} ▾</view>
        </picker>
        <view v-else class="address-picker empty-address" @tap="goAddress">添加地址</view>
      </view>
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

    <view v-if="showPay" class="mask" />
    <view v-if="showPay" class="bottom-sheet pay-sheet">
      <view class="sheet-title">扫码支付</view>
      <view class="pay-amount">¥{{ payAmount }}</view>
      <image class="pay-qr" :src="payQrUrl" mode="widthFix" show-menu-by-longpress />
      <view class="pay-hint">请完成付款后点击“确定支付”</view>
      <button class="btn-primary btn-block" :loading="paying" @tap="confirmPay">确定支付</button>
      <button class="btn-ghost btn-block defer-button" @tap="deferPay">稍后支付</button>
    </view>
  </view>
</template>

<script>
import { http } from '../../utils/request'
import { customer, requireLogin, saveCustomer } from '../../utils/auth'
import { getCart, saveCart } from '../../utils/cart'
import { imageUrl, refreshAssets, assetUrl } from '../../utils/assets'
import { money, quantity } from '../../utils/format'

export default {
  data() {
    return {
      loading: false,
      customerName: '',
      auditPending: false,
      goods: [],
      categories: [],
      cart: {},
      cartTypes: 0,
      cartUnits: 0,
      cartAmount: '0.00',
      checkoutItems: [],
      lastAddedId: '',
      bumpTimer: null,
      addresses: [],
      selectedAddressIndex: 0,
      selectedAddressLabel: '',
      pickedAddressId: null,
      receiverName: '',
      receiverPhone: '',
      receiverAddress: '',
      groupEnabled: false,
      notices: [],
      freightTip: '',
      showCheckout: false,
      submitting: false,
      showPay: false,
      paying: false,
      payOrderId: '',
      payAmount: '0.00',
      payQrUrl: assetUrl('mall.payQr', '/caifan/img/miniprogram/pay-qr.jpg')
    }
  },
  computed: {
    addressSummary() {
      if (this.selectedAddressLabel) return this.selectedAddressLabel
      return '选择收货地址'
    }
  },
  onShow() {
    if (!requireLogin()) return
    this.cart = getCart()
    const picked = uni.getStorageSync('picked_address')
    if (picked) {
      uni.removeStorageSync('picked_address')
      this.pickedAddressId = picked.id
      this.showCheckout = this.cartTypes > 0
    }
    this.load()
  },
  methods: {
    money,
    quantity,
    async load() {
      this.loading = true
      try {
        const [customerData, goodsData, addressData, groupState] = await Promise.all([
          http.get('/api/mall/customer'),
          http.get('/api/mall/goods'),
          http.get('/api/mall/addresses').catch(() => []),
          this.loadGroupState()
        ])
        this.customerName = customerData ? customerData.name : ''
        if (customerData) saveCustomer(customerData)
        this.auditPending = customerData
          && customerData.customerType === 'PURCHASER'
          && String(customerData.auditStatus) !== '1'
        this.goods = (goodsData || []).map((item) => ({
          ...item,
          imgUrl: imageUrl(item.thumbUrl || item.imageUrl),
          fallbackImgUrl: imageUrl(item.imageUrl)
        }))
        this.addresses = (addressData || []).map((item) => ({
          ...item,
          label: item.receiverName + ' ' + item.receiverPhone
        }))
        const pickedIndex = this.pickedAddressId == null
          ? -1
          : this.addresses.findIndex((item) => String(item.id) === String(this.pickedAddressId))
        if (pickedIndex >= 0) {
          this.applyAddress(pickedIndex)
        } else {
          this.chooseDefaultAddress()
        }
        this.pickedAddressId = null
        this.groupEnabled = !!groupState.enabled
        this.notices = groupState.notices
        this.freightTip = groupState.freightTip
        this.render()
        this.loadAssets()
      } catch (error) {
        // request 已提示
      } finally {
        this.loading = false
      }
    },
    async loadGroupState() {
      const [status, groups, publicGroups, promotions] = await Promise.all([
        http.get('/api/mall/groups/status').catch(() => null),
        http.get('/api/mall/groups').catch(() => []),
        http.get('/api/mall/groups/public').catch(() => []),
        http.get('/api/mall/groups/active-promotions').catch(() => [])
      ])
      const enabled = !!(status && status.setting && status.setting.enabled)
      if (!enabled) return { enabled: false, notices: [], freightTip: '' }
      const notices = []
      ;(promotions || []).slice(0, 2).forEach((item) => {
        notices.push({
          key: 'promotion-' + item.id,
          kicker: '今日拼菜活动',
          title: item.title,
          desc: '最低 ¥' + money(item.minPromoPrice) + ' · ' + (item.itemCount || 0) + ' 样菜',
          action: '查看活动'
        })
      })
      const merged = [...(groups || []), ...(publicGroups || [])]
        .filter((item, index, all) => index === all.findIndex((row) =>
          String(row.id || row.groupNo) === String(item.id || item.groupNo)))
        .filter((item) => ['0', '1'].includes(String(item.status)))
      merged.slice(0, 3 - notices.length).forEach((group) => {
        const items = group.items || []
        notices.push({
          key: String(group.id || group.groupNo),
          kicker: '正在拼单',
          title: items.length ? items[0].goodsName + (items.length > 1 ? '等' + items.length + '样菜' : '') : '拼单菜品',
          desc: group.groupNo + ' · ' + (group.memberCount || 0) + '/' + (group.targetCount || 0) + ' 人',
          action: '去拼单'
        })
      })
      const current = customer() || {}
      const type = current.customerType === 'PURCHASER' ? 'PURCHASER' : 'RETAIL'
      const rule = (status.freightRules || []).find((item) => item.customerType === type)
      let freightTip = ''
      if (status.penalty) {
        freightTip = '优惠暂停期间仍收基础运费 ¥' + money(rule && rule.baseFee)
      } else if (rule) {
        freightTip = '基础运费 ¥' + money(rule.baseFee)
          + (Number(rule.freeThreshold || 0) > 0 ? '，满 ¥' + money(rule.freeThreshold) + ' 免运' : '')
      }
      return { enabled: true, notices, freightTip }
    },
    async loadAssets() {
      const assets = await refreshAssets()
      if (assets['mall.payQr']) this.payQrUrl = assets['mall.payQr']
    },
    chooseDefaultAddress() {
      if (!this.addresses.length) return
      let index = this.addresses.findIndex((item) => Number(item.isDefault) === 1)
      if (index < 0) index = 0
      this.applyAddress(index)
    },
    applyAddress(index) {
      const item = this.addresses[index]
      if (!item) return
      this.selectedAddressIndex = index
      this.selectedAddressLabel = item.label
      this.receiverName = item.receiverName
      this.receiverPhone = item.receiverPhone
      this.receiverAddress = item.address
    },
    onAddressChange(event) {
      this.applyAddress(Number(event.detail.value))
    },
    render() {
      const cart = this.cart || {}
      const groups = []
      const categoryMap = new Map()
      let cartTypes = 0
      let cartUnits = 0
      let total = 0
      const checkout = []
      this.goods.forEach((raw) => {
        const qty = Number(cart[raw.id] || 0)
        const goods = { ...raw, qty, displayQty: quantity(qty) }
        const categoryId = raw.categoryId || 0
        if (!categoryMap.has(categoryId)) {
          const group = { id: categoryId, name: raw.categoryName || '未分类', goods: [] }
          categoryMap.set(categoryId, group)
          groups.push(group)
        }
        categoryMap.get(categoryId).goods.push(goods)
        if (qty > 0) {
          cartTypes += 1
          cartUnits += qty
          total += qty * Number(raw.salePrice || 0)
          checkout.push({
            id: raw.id,
            name: raw.name,
            unit: raw.unit,
            price: raw.salePrice,
            qty
          })
        }
      })
      this.categories = groups
      this.checkoutItems = checkout
      this.cartTypes = cartTypes
      this.cartUnits = quantity(cartUnits)
      this.cartAmount = money(total)
    },
    addToCart(goods) {
      this.cart[goods.id] = Number(this.cart[goods.id] || 0) + 1
      saveCart(this.cart)
      this.render()
      this.lastAddedId = goods.id
      if (this.bumpTimer) clearTimeout(this.bumpTimer)
      this.bumpTimer = setTimeout(() => {
        this.lastAddedId = ''
      }, 320)
    },
    handleImageError(goods) {
      if (goods.fallbackImgUrl && goods.imgUrl !== goods.fallbackImgUrl) {
        goods.imgUrl = goods.fallbackImgUrl
      }
    },
    changeQty(id, delta) {
      const next = Number(this.cart[id] || 0) + Number(delta)
      if (next <= 0) delete this.cart[id]
      else this.cart[id] = next
      saveCart(this.cart)
      this.render()
      if (!this.cartTypes) this.showCheckout = false
    },
    async openCheckout() {
      if (!this.cartTypes) {
        uni.showToast({ title: '请先选择菜品', icon: 'none' })
        return
      }
      if (this.auditPending) {
        uni.showToast({ title: '采购商资质待审核', icon: 'none' })
        return
      }
      try {
        this.addresses = ((await http.get('/api/mall/addresses')) || []).map((item) => ({
          ...item,
          label: item.receiverName + ' ' + item.receiverPhone
        }))
        this.chooseDefaultAddress()
      } catch (error) {
        // request 已提示
      }
      this.showCheckout = true
    },
    closeCheckout() {
      if (!this.submitting) this.showCheckout = false
    },
    async submitOrder() {
      if (!this.receiverName.trim()) return uni.showToast({ title: '请填写收货人', icon: 'none' })
      if (!/^1\d{10}$/.test(this.receiverPhone)) return uni.showToast({ title: '请填写正确的手机号', icon: 'none' })
      if (!this.receiverAddress.trim()) return uni.showToast({ title: '请填写收货地址', icon: 'none' })
      const items = Object.keys(this.cart)
        .filter((id) => Number(this.cart[id]) > 0)
        .map((id) => ({ goodsId: id, qty: Number(this.cart[id]) }))
      if (!items.length) return uni.showToast({ title: '请先选择菜品', icon: 'none' })
      this.submitting = true
      try {
        const order = await http.post('/api/mall/order', {
          receiverName: this.receiverName,
          receiverPhone: this.receiverPhone,
          receiverAddress: this.receiverAddress,
          items
        })
        this.showCheckout = false
        this.showPay = true
        this.payOrderId = order.id
        this.payAmount = money(order.payable)
      } catch (error) {
        // request 已提示
      } finally {
        this.submitting = false
      }
    },
    async confirmPay() {
      if (!this.payOrderId || this.paying) return
      this.paying = true
      try {
        await http.post('/api/mall/pay', { orderId: this.payOrderId })
        this.cart = {}
        saveCart(this.cart)
        this.render()
        this.showPay = false
        uni.showToast({ title: '支付成功', icon: 'success' })
        setTimeout(() => uni.switchTab({ url: '/pages/orders/index' }), 600)
      } catch (error) {
        // request 已提示
      } finally {
        this.paying = false
      }
    },
    deferPay() {
      this.showPay = false
      uni.switchTab({ url: '/pages/orders/index' })
    },
    goAddress() {
      uni.navigateTo({ url: '/pages/address/index' })
    },
    goGroups() {
      uni.switchTab({ url: '/pages/groups/index' })
    }
  }
}
</script>

<style scoped>
.shop-page {
  min-height: 100vh;
  padding-bottom: 150rpx;
  background: #f4f7f5;
}

.shop-header {
  position: sticky;
  top: 0;
  z-index: 18;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 104rpx;
  padding: 14rpx 28rpx;
  border-bottom: 1rpx solid #e7ece8;
  background: rgba(255, 255, 255, 0.98);
}

.header-copy {
  flex: 1;
  min-width: 0;
}

.shop-brand {
  color: #213027;
  font-size: 34rpx;
  font-weight: 800;
}

.delivery-line {
  display: flex;
  align-items: center;
  gap: 7rpx;
  margin-top: 5rpx;
  color: #79827c;
  font-size: 21rpx;
}

.dot {
  color: #c0c6c2;
}

.address-text {
  max-width: 350rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-address {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #edf6f0;
  color: #247246;
  font-size: 23rpx;
  font-weight: 650;
}

.audit-banner {
  margin: 18rpx 24rpx 0;
  padding: 18rpx 20rpx;
  border: 1rpx solid #f5d9c4;
  border-radius: 14rpx;
  background: #fff7f1;
  color: #9b4c18;
  font-size: 23rpx;
  line-height: 1.55;
}

.notice-scroll {
  display: flex;
  width: 100%;
  padding: 22rpx 24rpx 4rpx;
  white-space: nowrap;
}

.notice-card {
  display: inline-flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 520rpx;
  margin-right: 16rpx;
  padding: 20rpx;
  border: 1rpx solid #f1dccd;
  border-radius: 16rpx;
  background: #fff9f4;
  vertical-align: top;
}

.notice-kicker {
  color: #db5d12;
  font-size: 20rpx;
  font-weight: 700;
}

.notice-title {
  margin-top: 8rpx;
  overflow: hidden;
  color: #3c2b20;
  font-size: 28rpx;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-desc {
  margin-top: 7rpx;
  overflow: hidden;
  color: #8b6953;
  font-size: 22rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notice-action {
  margin-top: 12rpx;
  color: #d9580b;
  font-size: 22rpx;
  font-weight: 650;
}

.catalog {
  padding: 8rpx 24rpx 30rpx;
}

.category-section {
  margin-top: 26rpx;
}

.category-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.category-title {
  color: #233029;
  font-size: 32rpx;
  font-weight: 800;
}

.category-count {
  margin-top: 4rpx;
  color: #8b938e;
  font-size: 20rpx;
}

.goods-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18rpx;
}

.goods-card {
  min-width: 0;
  padding: 14rpx;
  overflow: hidden;
  border: 1rpx solid #e8ece9;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 8rpx 22rpx rgba(36, 65, 46, 0.045);
}

.goods-card.bump {
  animation: card-bump 0.3s ease;
}

@keyframes card-bump {
  0% { transform: scale(1); }
  45% { transform: scale(1.025); }
  100% { transform: scale(1); }
}

.image-shell {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 0.82;
  overflow: hidden;
  border-radius: 12rpx;
  background: #edf3ee;
}

.goods-image {
  width: 100%;
  height: 100%;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: #74a183;
  font-size: 48rpx;
  font-weight: 800;
}

.cart-badge {
  position: absolute;
  top: 10rpx;
  right: 10rpx;
  min-width: 38rpx;
  height: 38rpx;
  padding: 0 9rpx;
  border-radius: 999rpx;
  background: #e85d04;
  color: #fff;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 38rpx;
  text-align: center;
}

.goods-name {
  margin-top: 13rpx;
  overflow: hidden;
  color: #26322b;
  font-size: 28rpx;
  font-weight: 750;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-meta {
  margin-top: 5rpx;
  overflow: hidden;
  color: #89918c;
  font-size: 20rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.goods-bottom {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-top: 12rpx;
}

.goods-price {
  flex: 1;
  min-width: 0;
  color: #df5a08;
  font-size: 29rpx;
  font-weight: 800;
  white-space: nowrap;
}

.currency {
  margin-right: 2rpx;
  font-size: 20rpx;
}

.unit {
  color: #8c948f;
  font-size: 18rpx;
  font-weight: 400;
}

.goods-bottom .stepper-btn {
  width: 42rpx;
  height: 42rpx;
  font-size: 27rpx;
}

.goods-bottom .stepper-value {
  min-width: 30rpx;
  font-size: 22rpx;
}

.cart-bar {
  position: fixed;
  right: 18rpx;
  bottom: 14rpx;
  left: 18rpx;
  z-index: 40;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 104rpx;
  padding: 14rpx 16rpx 14rpx 24rpx;
  border: 1rpx solid #e2e8e4;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 14rpx 38rpx rgba(28, 56, 38, 0.15);
}

.cart-summary {
  flex: 1;
  min-width: 0;
}

.cart-count {
  color: #758078;
  font-size: 21rpx;
}

.cart-total {
  margin-top: 4rpx;
  color: #28342c;
  font-size: 24rpx;
}

.cart-total text {
  color: #df5a08;
  font-size: 32rpx;
  font-weight: 800;
}

.checkout-button {
  height: 72rpx;
  margin: 0;
  padding: 0 34rpx;
  border-radius: 14rpx;
  background: #e85d04;
  color: #fff;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 72rpx;
}

.checkout-sheet {
  max-height: 92vh;
}

.checkout-total {
  display: flex;
  justify-content: space-between;
  color: #637068;
  font-size: 24rpx;
}

.freight-tip {
  margin-top: 8rpx;
  color: #8b938e;
  font-size: 21rpx;
}

.checkout-list {
  max-height: 330rpx;
  margin: 16rpx 0;
  border-top: 1rpx solid #edf0ee;
  border-bottom: 1rpx solid #edf0ee;
}

.checkout-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f2f4f2;
}

.checkout-item:last-child {
  border-bottom: 0;
}

.checkout-main {
  flex: 1;
  min-width: 0;
}

.checkout-name {
  overflow: hidden;
  color: #26322b;
  font-size: 27rpx;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.checkout-price {
  margin-top: 5rpx;
  color: #89918c;
  font-size: 21rpx;
}

.address-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin: 12rpx 0 16rpx;
}

.small-title {
  flex-shrink: 0;
  font-size: 26rpx;
}

.address-picker {
  max-width: 430rpx;
  padding: 10rpx 16rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: #edf6f0;
  color: #247246;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-address {
  background: #f2f4f2;
  color: #7f8782;
}

.pay-sheet {
  text-align: center;
}

.pay-amount {
  margin: 12rpx 0 10rpx;
  color: #df5a08;
  font-size: 50rpx;
  font-weight: 800;
}

.pay-qr {
  width: 410rpx;
  margin: 10rpx auto 16rpx;
  border-radius: 16rpx;
}

.pay-hint {
  margin-bottom: 22rpx;
  color: #7c857f;
  font-size: 22rpx;
}

.defer-button {
  margin-top: 14rpx;
}
</style>
