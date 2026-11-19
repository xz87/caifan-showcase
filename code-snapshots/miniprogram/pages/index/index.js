const app = getApp();
const request = require('../../utils/request');
const config = require('../../config');
const assets = require('../../utils/assets');

function imageUrl(path) {
  if (!path) return '';
  if (/^https?:\/\//.test(path)) return path;
  return config.assetOrigin + path;
}

Page({
  data: {
    baseUrl: app.globalData.baseUrl,
    categories: [],
    checkoutItems: [],
    cartCount: 0,
    cartAmount: '0.00',
    showCheckout: false,
    receiverName: '',
    receiverPhone: '',
    receiverAddress: '',
    addressList: [],
    selectedAddrIndex: 0,
    selectedAddrLabel: '',
    auditPending: false,
    groupEnabled: false,
    groupNotices: [{
      key: 'default',
      title: '一起拼菜更划算',
      desc: '发起拼单或输入拼单码，满员后享拼单价',
      action: '去拼菜'
    }],
    groupNoticeMore: '',
    freightTip: '',
    lastAddedId: '',
    submitting: false,
    showPay: false,
    payQrUrl: assets.url('mall.payQr'),
    payOrderId: '',
    payAmount: '0.00',
    paying: false
  },

  onShow() {
    if (!app.token()) {
      wx.reLaunch({ url: '/pages/login/index' });
      return;
    }
    this.refreshCustomer();
    this.loadGoods();
    this.loadAssets();
    this.loadGroupNotice();
    this.applyAddress();
  },

  async loadAssets() {
    try {
      const map = await assets.refresh();
      this.setData({ payQrUrl: map['mall.payQr'] || assets.url('mall.payQr') });
    } catch (e) {
      this.setData({ payQrUrl: assets.url('mall.payQr') });
    }
  },

  async refreshCustomer() {
    try {
      const c = await request.get('/api/mall/customer');
      if (c) {
        wx.setStorageSync('mall_customer', c);
        this.setData({
          auditPending: c.customerType === 'PURCHASER' && String(c.auditStatus) !== '1'
        });
      }
    } catch (e) {
      // 登录态失效由 request 统一处理
    }
  },

  async loadGroupNotice() {
    try {
      const res = await Promise.all([
        request.get('/api/mall/groups').catch(() => []),
        request.get('/api/mall/groups/public').catch(() => []),
        request.get('/api/mall/groups/active-promotions').catch(() => []),
        request.get('/api/mall/groups/status').catch(() => null)
      ]);
      const status = res[3] || {};
      const enabled = !!(status.setting && status.setting.enabled);
      if (!enabled) {
        this.setData({ groupEnabled: false, groupNotices: [], groupNoticeMore: '' });
        return;
      }
      const mine = res[0] || [];
      const publicGroups = res[1] || [];
      const groups = mine.concat(publicGroups).filter((group, index, all) => {
        return index === all.findIndex((item) => String(item.id || item.groupNo) === String(group.id || group.groupNo));
      });
      const promotions = res[2] || [];
      const customer = wx.getStorageSync('mall_customer') || {};
      const customerType = customer.customerType === 'PURCHASER' ? 'PURCHASER' : 'RETAIL';
      const rule = (status.freightRules || []).find((item) => item.customerType === customerType);
      let freightTip = '';
      if (status.penalty) {
        freightTip = '优惠暂停期间仍收基础运费 ¥' + Number(rule ? rule.baseFee : 0).toFixed(2);
      } else if (rule) {
        freightTip = '基础运费 ¥' + Number(rule.baseFee || 0).toFixed(2)
          + (Number(rule.freeThreshold || 0) > 0 ? '，满 ¥' + Number(rule.freeThreshold).toFixed(2) + ' 免运' : '');
      }
      const relevant = groups.filter((g) => String(g.status) === '0'
        || (String(g.status) === '1' && !g.ordered));
      if (!relevant.length && !promotions.length) {
        this.setData({
          groupNotices: [{
            key: 'default',
            title: '一起拼菜更划算',
            desc: '发起拼单或输入拼单码，满员后享拼单价',
            action: '去拼菜'
          }],
          groupNoticeMore: '',
          groupEnabled: true,
          freightTip
        });
        return;
      }

      const promotionNotices = promotions.slice(0, 2).map((promotion) => ({
        key: 'promotion-' + promotion.id,
        title: promotion.title,
        desc: (promotion.description || '拼菜活动进行中')
          + ' · ' + promotion.itemCount + ' 样菜 · 最低 ¥'
          + Number(promotion.minPromoPrice || 0).toFixed(2)
          + ' · 截止 ' + (promotion.endTime || '').replace('T', ' ').substring(5, 16),
        action: '去拼菜'
      }));
      const groupNotices = relevant.slice(0, Math.max(0, 3 - promotionNotices.length)).map((group) => {
        const items = Array.isArray(group.items) && group.items.length
          ? group.items
          : [{
              goodsId: group.goodsId,
              goodsName: group.goodsName,
              unit: group.unit,
              groupPrice: group.groupPrice
            }];
        const first = items[0] || {};
        const goodsName = first.goodsName || '菜品';
        const price = Number(first.groupPrice != null ? first.groupPrice : group.groupPrice || 0).toFixed(2);
        const unit = first.unit || group.unit || '份';
        const open = String(group.status) === '0';
        return {
          key: String(group.id || group.groupNo),
          title: items.length > 1
            ? goodsName + '等' + items.length + '样菜' + (open ? '正在拼单' : '拼单已成团')
            : goodsName + (open ? '正在拼单' : '拼单已成团'),
          desc: open
            ? '¥' + price + '/' + unit + ' 起 · 共 ' + items.length + ' 样菜 · 已拼 '
              + (group.memberCount || 0) + '/' + (group.targetCount || 0)
              + ' 人 · 拼单码 ' + group.groupNo
            : '共 ' + items.length + ' 样菜 · 拼单已成团，等待你下单',
          action: open ? '查看' : '去下单'
        };
      });
      const notices = promotionNotices.concat(groupNotices);
      const remaining = Math.max(0, promotions.length - 2)
        + Math.max(0, relevant.length - groupNotices.length);
      this.setData({
        groupNotices: notices,
        groupEnabled: true,
        freightTip,
        groupNoticeMore: remaining > 0
          ? '还有 ' + remaining + ' 个拼单活动，查看全部 ›'
          : ''
      });
    } catch (e) {
      // 拼单通告失败时保留默认引导，不影响选购
    }
  },

  async applyAddress(force) {
    let list = [];
    try {
      list = (await request.get('/api/mall/addresses')) || [];
    } catch (e) {
      list = [];
    }
    const decorated = list.map((a) => Object.assign({}, a, {
      label: a.receiverName + ' ' + a.receiverPhone
    }));

    const picked = wx.getStorageSync('picked_address');
    if (picked) {
      wx.removeStorageSync('picked_address');
      const idx = decorated.findIndex((a) => String(a.id) === String(picked.id));
      const target = idx >= 0 ? decorated[idx] : picked;
      this.setData({
        addressList: decorated,
        selectedAddrIndex: idx >= 0 ? idx : 0,
        selectedAddrLabel: target.receiverName + ' ' + target.receiverPhone,
        receiverName: target.receiverName,
        receiverPhone: target.receiverPhone,
        receiverAddress: target.address
      });
      return;
    }
    if (!force && (this.data.receiverName || this.data.receiverPhone || this.data.receiverAddress)) {
      this.setData({ addressList: decorated });
      return;
    }

    const defIndex = Math.max(0, decorated.findIndex((a) => Number(a.isDefault) === 1));
    const def = decorated[defIndex];
    if (def) {
      this.setData({
        addressList: decorated,
        selectedAddrIndex: defIndex,
        selectedAddrLabel: def.receiverName + ' ' + def.receiverPhone,
        receiverName: def.receiverName,
        receiverPhone: def.receiverPhone,
        receiverAddress: def.address
      });
    } else {
      this.setData({ addressList: [], selectedAddrLabel: '' });
    }
  },

  onAddressChange(e) {
    const idx = Number(e.detail.value);
    const item = this.data.addressList[idx];
    if (!item) return;
    this.setData({
      selectedAddrIndex: idx,
      selectedAddrLabel: item.receiverName + ' ' + item.receiverPhone,
      receiverName: item.receiverName,
      receiverPhone: item.receiverPhone,
      receiverAddress: item.address
    });
  },

  useDefaultAddress() {
    this.applyAddress(true);
  },

  async loadGoods() {
    try {
      const goods = await request.get('/api/mall/goods');
      this.goods = goods || [];
      this.render(this.goods);
    } catch (e) {
      // request 已提示
    }
  },

  render(goods) {
    const cart = app.globalData.cart || {};
    const map = {};
    const order = [];
    goods.forEach((g) => {
      const key = g.categoryId || 0;
      if (!map[key]) {
        map[key] = { id: key, name: g.categoryName || '未分类', goods: [] };
        order.push(key);
      }
      map[key].goods.push(Object.assign({}, g, {
        qty: cart[g.id] || 0,
        imgUrl: imageUrl(g.thumbUrl || g.imageUrl),
        originalImgUrl: imageUrl(g.imageUrl)
      }));
    });

    let count = 0;
    let total = 0;
    const checkoutItems = [];
    goods.forEach((g) => {
      const qty = cart[g.id] || 0;
      if (qty > 0) {
        count += 1;
        total += qty * Number(g.salePrice || 0);
        checkoutItems.push({
          id: g.id,
          name: g.name,
          unit: g.unit,
          price: g.salePrice,
          qty
        });
      }
    });

    this.setData({
      categories: order.map((k) => map[k]),
      checkoutItems,
      cartCount: count,
      cartAmount: total.toFixed(2)
    });
  },

  onGoodsImageError(event) {
    const id = event.currentTarget.dataset.id;
    const categories = this.data.categories.map((category) => ({
      ...category,
      goods: category.goods.map((goods) => {
        if (String(goods.id) !== String(id)
            || !goods.originalImgUrl
            || goods.imgUrl === goods.originalImgUrl) {
          return goods;
        }
        return { ...goods, imgUrl: goods.originalImgUrl };
      })
    }));
    this.setData({ categories });
  },

  changeQty(e) {
    const { id, delta } = e.currentTarget.dataset;
    const cart = Object.assign({}, app.globalData.cart || {});
    const next = (cart[id] || 0) + Number(delta);
    if (next <= 0) {
      delete cart[id];
    } else {
      cart[id] = next;
    }
    app.saveCart(cart);
    this.render(this.goods || []);
    const remain = Object.keys(cart).reduce((sum, key) => sum + cart[key], 0);
    if (!remain && this.data.showCheckout) {
      this.setData({ showCheckout: false });
    }
  },

  /** 点整张商品卡加一件：首次写入 cart[id]，之后只做累加 */
  addToCart(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    const cart = Object.assign({}, app.globalData.cart || {});
    cart[id] = (cart[id] || 0) + 1;
    app.saveCart(cart);
    this.render(this.goods || []);
    this.setData({ lastAddedId: id });
    if (this._bumpTimer) {
      clearTimeout(this._bumpTimer);
    }
    this._bumpTimer = setTimeout(() => this.setData({ lastAddedId: '' }), 350);
  },

  /** 阻断加减号区域的点击冒泡，避免触发整卡加购 */
  noop() {},

  async openCheckout() {
    if (this.data.auditPending) {
      wx.showToast({ title: '采购商资质待审核，暂不能下单', icon: 'none' });
      return;
    }
    if (!this.data.cartCount) {
      wx.showToast({ title: '请先选择菜品', icon: 'none' });
      return;
    }
    // 每次打开结算都重新拉一次默认地址，避免页面缓存导致地址不更新
    await this.applyAddress(true);
    this.setData({ showCheckout: true });
  },

  closeCheckout() {
    if (this.data.submitting) return;
    this.setData({ showCheckout: false });
  },

  input(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  async submit() {
    const { receiverName, receiverPhone, receiverAddress } = this.data;
    if (!receiverName) {
      wx.showToast({ title: '请填写收货人', icon: 'none' });
      return;
    }
    if (!/^1\d{10}$/.test(receiverPhone)) {
      wx.showToast({ title: '请填写正确的收货电话', icon: 'none' });
      return;
    }
    if (!receiverAddress) {
      wx.showToast({ title: '请填写收货地址', icon: 'none' });
      return;
    }

    const cart = app.globalData.cart || {};
    const items = Object.keys(cart).map((id) => ({ goodsId: id, qty: cart[id] }));
    if (!items.length) {
      wx.showToast({ title: '请先选择菜品', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });
    try {
      const order = await request.post('/api/mall/order', {
        receiverName,
        receiverPhone,
        receiverAddress,
        items
      });
      this.setData({
        showCheckout: false,
        showPay: true,
        payOrderId: order.id,
        payAmount: order.payable || this.data.cartAmount
      });
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ submitting: false });
    }
  },

  async confirmPay() {
    if (!this.data.payOrderId) return;
    this.setData({ paying: true });
    try {
      await request.post('/api/mall/pay', { orderId: this.data.payOrderId });
      app.saveCart({});
      this.setData({ showPay: false, payOrderId: '' });
      wx.showToast({ title: '支付成功', icon: 'success' });
      setTimeout(() => wx.reLaunch({ url: '/pages/orders/index' }), 800);
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ paying: false });
    }
  },

  deferPay() {
    this.setData({ showPay: false });
    wx.reLaunch({ url: '/pages/orders/index' });
  },

  goOrders() {
    wx.reLaunch({ url: '/pages/orders/index' });
  },

  goAddress() {
    wx.navigateTo({ url: '/pages/address/index' });
  },

  goGroups() {
    wx.navigateTo({ url: '/pages/groups/index' });
  },

  /** 退出登录只保留在首页，订单页的“首页”只做返回 */
  logout() {
    request.post('/api/mall/auth/token-logout')
      .catch(() => {})
      .then(() => {
        wx.removeStorageSync('mall_token');
        wx.removeStorageSync('mall_customer');
        app.saveCart({});
        wx.reLaunch({ url: '/pages/login/index' });
      });
  }
});
