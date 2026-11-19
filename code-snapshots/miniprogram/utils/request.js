const app = getApp();

function request(method, path, data) {
  return new Promise((resolve, reject) => {
    const token = app.token();
    wx.request({
      url: app.globalData.baseUrl + path,
      method,
      data,
      header: token ? { Authorization: 'Bearer ' + token } : {},
      success(res) {
        if (res.statusCode === 401) {
          wx.removeStorageSync('mall_token');
          wx.reLaunch({ url: '/pages/login/index' });
          reject(res.data);
          return;
        }
        const body = res.data || {};
        if (body.code !== 200) {
          wx.showToast({ title: body.msg || '操作失败', icon: 'none' });
          reject(body);
          return;
        }
        resolve(body.data);
      },
      fail(err) {
        wx.showToast({ title: '网络异常，请检查后端地址', icon: 'none' });
        reject(err);
      }
    });
  });
}

module.exports = {
  get(path) {
    return request('GET', path);
  },
  post(path, data) {
    return request('POST', path, data);
  },
  del(path) {
    return request('DELETE', path);
  }
};
