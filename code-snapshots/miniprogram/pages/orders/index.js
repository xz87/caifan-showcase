const app = getApp();
const request = require('../../utils/request');
const assets = require('../../utils/assets');

const DLV = { 0: '待分拣', 1: '分拣中', 2: '已分拣', 3: '已配送', 5: '已签收' };
const PAY = { 0: '未付款', 1: '部分付款', 2: '已付清' };
const ORDER_STATUS = { 0: '正常', 1: '已退货', 2: '已取消' };

Page({
  data: {
    orders: [],
    loading: false,
    showPay: false,
    payQrUrl: assets.url('mall.payQr'),
    payOrderId: '',
    payAmount: '0.00',
    paying: false,
    showCancel: false,
    cancelOrderId: '',
    cancelReasonCode: 'NOT_NEEDED',
    cancelRemark: '',
    cancelling: false,
    penalty: null
  },

  onShow() {
    if (!app.token()) {
      wx.reLaunch({ url: '/pages/login/index' });
      return;
    }
    this.load();
    this.loadAssets();
  },

  async loadAssets() {
    try {
      const map = await assets.refresh();
      this.setData({ payQrUrl: map['mall.payQr'] || assets.url('mall.payQr') });
    } catch (e) {
      this.setData({ payQrUrl: assets.url('mall.payQr') });
    }
  },

  async load() {
    this.setData({ loading: true });
    try {
      const page = await request.get('/api/mall/orders?pageNum=1&pageSize=20');
      const orders = (page.records || []).map((o) => Object.assign({}, o, {
        dlvText: o.status === '2' ? '已取消' : (DLV[o.deliveryStatus] || '待分拣'),
        payText: PAY[o.payStatus] || '-',
        statusText: ORDER_STATUS[o.status] || '-',
        canSign: o.deliveryStatus === '3',
        canCancel: o.status === '0' && (!o.deliveryStatus || o.deliveryStatus === '0'),
        timeText: (o.saleDate || '').replace('T', ' ').substring(0, 16)
      }));
      let penalty = null;
      try {
        const status = await request.get('/api/mall/groups/status');
        if (status && status.penalty) {
          penalty = Object.assign({}, status.penalty, {
            endText: (status.penalty.endTime || '').replace('T', ' ').substring(5, 16)
          });
        }
      } catch (e) {
        // 拼单关闭或配置接口异常不阻断订单列表
      }
      this.setData({ orders, penalty });
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ loading: false });
    }
  },

  confirmSign(e) {
    const id = e.currentTarget.dataset.id;
    wx.showModal({
      title: '确认签收',
      content: '确认已经收到全部菜品？签收后状态不可回退。',
      success: async (res) => {
        if (!res.confirm) return;
        try {
          await request.post('/api/mall/orders/' + id + '/confirm-receipt');
          wx.showToast({ title: '已签收', icon: 'success' });
          this.load();
        } catch (e) {
          // request 已提示
        }
      }
    });
  },

  goMall() {
    wx.reLaunch({ url: '/pages/index/index' });
  },

  goClaim(e) {
    const { id, saleNo } = e.currentTarget.dataset;
    wx.navigateTo({
      url: '/pages/claim/index?orderId=' + id + '&saleNo=' + encodeURIComponent(saleNo || '')
    });
  },

  goStatement() {
    wx.navigateTo({ url: '/pages/statement/index' });
  },

  /** 再来一单：把原订单商品重新加入购物车 */
  async reorder(e) {
    const id = e.currentTarget.dataset.id;
    const order = this.data.orders.find((o) => String(o.id) === String(id));
    let items = (order && order.items) || [];
    if (!items.length) {
      items = await request.get('/api/mall/orders/' + id + '/items') || [];
    }
    if (!items.length) {
      wx.showToast({ title: '该订单暂无明细', icon: 'none' });
      return;
    }
    const cart = Object.assign({}, app.globalData.cart || {});
    items.forEach((i) => {
      cart[i.goodsId] = (cart[i.goodsId] || 0) + Number(i.qty || 0);
    });
    app.saveCart(cart);
    wx.showToast({ title: '已加入购物车', icon: 'success' });
    setTimeout(() => wx.reLaunch({ url: '/pages/index/index' }), 600);
  },

  goPay(e) {
    this.setData({
      showPay: true,
      payOrderId: e.currentTarget.dataset.id,
      payAmount: e.currentTarget.dataset.amount
    });
  },

  openCancel(e) {
    this.setData({
      showCancel: true,
      cancelOrderId: e.currentTarget.dataset.id,
      cancelReasonCode: 'NOT_NEEDED',
      cancelRemark: ''
    });
  },

  closeCancel() {
    if (this.data.cancelling) return;
    this.setData({ showCancel: false, cancelOrderId: '' });
  },

  chooseCancelReason(e) {
    this.setData({ cancelReasonCode: e.detail.value });
  },

  inputCancelRemark(e) {
    this.setData({ cancelRemark: e.detail.value });
  },

  async submitCancel() {
    const remark = (this.data.cancelRemark || '').trim();
    if (!remark) {
      wx.showToast({ title: '请填写退单备注', icon: 'none' });
      return;
    }
    if (this.data.cancelReasonCode === 'OTHER' && remark.length < 5) {
      wx.showToast({ title: '其他原因至少填写 5 个字', icon: 'none' });
      return;
    }
    this.setData({ cancelling: true });
    try {
      await request.post('/api/mall/orders/' + this.data.cancelOrderId + '/cancel', {
        reasonCode: this.data.cancelReasonCode,
        reason: remark
      });
      this.setData({ showCancel: false, cancelOrderId: '' });
      wx.showToast({ title: '订单已取消', icon: 'success' });
      this.load();
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ cancelling: false });
    }
  },

  closePay() {
    if (this.data.paying) return;
    this.setData({ showPay: false });
  },

  async confirmPay() {
    if (!this.data.payOrderId) return;
    this.setData({ paying: true });
    try {
      await request.post('/api/mall/pay', { orderId: this.data.payOrderId });
      this.setData({ showPay: false, payOrderId: '' });
      wx.showToast({ title: '支付成功', icon: 'success' });
      this.load();
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ paying: false });
    }
  }
});
