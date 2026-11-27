const app = getApp();

function showFailure(title, message) {
  wx.showToast({ title: message || title, icon: 'none' });
}

function handleHttpStatus(statusCode, body) {
  if (statusCode === 404) {
    return '接口或资源不存在';
  }
  if (statusCode === 503) {
    return '服务暂时不可用，请稍后重试';
  }
  if (statusCode >= 500) {
    return '服务器异常，请稍后重试';
  }
  return body.msg || ('请求失败（HTTP ' + statusCode + '）');
}

function request(method, path, data, options) {
  const requestOptions = options || {};
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
        if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== 200) {
          if (!requestOptions.silent) {
            showFailure('请求失败', handleHttpStatus(res.statusCode, body));
          }
          reject(body);
          return;
        }
        resolve(body.data);
      },
      fail(err) {
        if (!requestOptions.silent) {
          showFailure('网络异常', '网络异常，请检查后端地址');
        }
        reject(err);
      }
    });
  });
}

module.exports = {
  get(path, options) {
    return request('GET', path, null, options);
  },
  post(path, data) {
    return request('POST', path, data);
  },
  del(path) {
    return request('DELETE', path);
  }
};
