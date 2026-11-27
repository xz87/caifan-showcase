import { BASE_URL } from './config'
import { token, clearLogin } from './auth'

let redirecting = false

function fail(message, reject, code, silent = false) {
  if (!silent) {
    uni.showToast({ title: message || '操作失败', icon: 'none' })
  }
  const error = new Error(message || 'request failed')
  error.code = code
  reject(error)
}

function statusMessage(statusCode, body) {
  if (statusCode === 404) {
    return '接口或资源不存在'
  }
  if (statusCode === 503) {
    return '服务暂时不可用，请稍后重试'
  }
  if (statusCode >= 500) {
    return '服务器异常，请稍后重试'
  }
  return body.msg || ('请求失败（HTTP ' + statusCode + '）')
}

export function request(method, path, data, options = {}) {
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
          fail(statusMessage(res.statusCode, body), reject, res.statusCode, options.silent)
          return
        }
        resolve(body.data)
      },
      fail() {
        fail('网络异常，请检查服务器连接', reject, undefined, options.silent)
      }
    })
  })
}

export const http = {
  get(path, options) {
    return request('GET', path, undefined, options)
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
        if (res.statusCode < 200 || res.statusCode >= 300 || body.code !== 200) {
          fail(statusMessage(res.statusCode, body) || '上传失败', reject, res.statusCode)
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
