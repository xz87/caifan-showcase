const app = getApp();
const request = require('../../utils/request');

Page({
  data: {
    orderId: '',
    saleNo: '',
    claimable: false,
    claimDeadlineText: '',
    claimStatusText: '',
    reason: '',
    images: [],
    videoUrl: '',
    submitting: false
  },

  onLoad(options) {
    const claimable = options.claimAllowed === '1';
    this.setData({
      orderId: options.orderId || '',
      saleNo: decodeURIComponent(options.saleNo || ''),
      claimable,
      claimDeadlineText: decodeURIComponent(options.deadline || ''),
      claimStatusText: decodeURIComponent(options.status || '')
    });
    if (!this.data.orderId || !claimable) {
      wx.showModal({
        title: '暂不可报损',
        content: '该订单当前不符合报损条件，请返回订单页刷新后重试。',
        showCancel: false,
        success: () => this.backToOrders()
      });
    }
  },

  backToOrders() {
    wx.navigateBack({
      fail: () => wx.reLaunch({ url: '/pages/orders/index' })
    });
  },

  input(e) {
    this.setData({ reason: e.detail.value });
  },

  chooseImages() {
    const remain = 3 - this.data.images.length;
    if (remain <= 0) {
      wx.showToast({ title: '最多 3 张图片', icon: 'none' });
      return;
    }
    wx.chooseMedia({
      count: remain,
      mediaType: ['image'],
      success: async (res) => {
        for (const file of res.tempFiles) {
          const url = await this.upload(file.tempFilePath);
          if (url) {
            this.setData({ images: this.data.images.concat(url) });
          }
        }
      }
    });
  },

  chooseVideo() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['video'],
      maxDuration: 60,
      success: async (res) => {
        const url = await this.upload(res.tempFiles[0].tempFilePath);
        if (url) this.setData({ videoUrl: url });
      }
    });
  },

  upload(filePath) {
    return new Promise((resolve) => {
      wx.uploadFile({
        url: app.globalData.baseUrl + '/api/mall/upload/claim',
        filePath,
        name: 'file',
        header: { Authorization: 'Bearer ' + app.token() },
        success: (r) => {
          try {
            const data = JSON.parse(r.data);
            if (data.code === 200) {
              resolve(data.data);
            } else {
              wx.showToast({ title: data.msg || '上传失败', icon: 'none' });
              resolve('');
            }
          } catch (e) {
            wx.showToast({ title: '上传失败', icon: 'none' });
            resolve('');
          }
        },
        fail: () => {
          wx.showToast({ title: '上传失败', icon: 'none' });
          resolve('');
        }
      });
    });
  },

  removeImage(e) {
    const idx = Number(e.currentTarget.dataset.idx);
    const images = this.data.images.slice();
    images.splice(idx, 1);
    this.setData({ images });
  },

  removeVideo() {
    this.setData({ videoUrl: '' });
  },

  async submit() {
    const { orderId, reason, images, videoUrl } = this.data;
    if (this.data.submitting) return;
    if (!orderId) {
      wx.showToast({ title: '缺少订单信息', icon: 'none' });
      return;
    }
    if (!this.data.claimable) {
      wx.showToast({ title: '该订单暂不可报损', icon: 'none' });
      return;
    }
    if (!reason.trim()) {
      wx.showToast({ title: '请填写损耗说明', icon: 'none' });
      return;
    }
    if (!images.length && !videoUrl) {
      wx.showToast({ title: '请上传图片或视频作为凭证', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    try {
      await request.post('/api/mall/claims', { orderId, reason, images, videoUrl });
      wx.showToast({ title: '已提交，等待审核', icon: 'success' });
      setTimeout(() => wx.navigateBack(), 900);
    } catch (e) {
      // request 已提示
    } finally {
      this.setData({ submitting: false });
    }
  }
});
