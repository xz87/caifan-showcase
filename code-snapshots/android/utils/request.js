import { BASE_URL } from './config'
import { token, clearLogin } from './auth'

let redirecting = false

function fail(message, reject) {
  uni.showToast({ title: message || '操作失败', icon: 'none' })
  reject(new Error(message || 'request failed'))
}

export function request(method, path, data) {
  return new Promise((resolve, reject) => {
    const currentToken = token()
    uni.request({
      url: BASE_URL + path,
      method,
      data,
      timeout: 20000,
      header: currentToken ? { Authorization: 'Bearer ' + currentToken } : {},
      success(res) {
        if (res.statusCode === 401) {
          clearLogin()
          if (!redirecting) {
            redirecting = true
            uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
            setTimeout(() => {
              redirecting = false
              uni.reLaunch({ url: '/pages/login/index' })
            }, 600)
          }
          reject(res.data || { code: 401 })
          return
        }
        const body = res.data || {}
        if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== 200) {
          fail(body.msg || ('请求失败（HTTP ' + res.statusCode + '）'), reject)
          return
        }
        resolve(body.data)
      },
      fail() {
        fail('网络异常，请检查服务器连接', reject)
      }
    })
  })
}

export const http = {
  get(path) {
    return request('GET', path)
  },
  post(path, data) {
    return request('POST', path, data)
  },
  delete(path) {
    return request('DELETE', path)
  }
}

export function upload(path, filePath) {
  return new Promise((resolve, reject) => {
    const currentToken = token()
    uni.uploadFile({
      url: BASE_URL + path,
      filePath,
      name: 'file',
      header: currentToken ? { Authorization: 'Bearer ' + currentToken } : {},
      success(res) {
        let body = {}
        try {
          body = JSON.parse(res.data || '{}')
        } catch (e) {
          fail('上传响应解析失败', reject)
          return
        }
        if (res.statusCode === 401) {
          clearLogin()
          uni.reLaunch({ url: '/pages/login/index' })
          reject(body)
          return
        }
        if (body.code !== 200) {
          fail(body.msg || '上传失败', reject)
          return
        }
        resolve(body.data)
      },
      fail() {
        fail('上传失败', reject)
      }
    })
  })
}
