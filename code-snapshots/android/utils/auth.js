const TOKEN_KEY = 'mall_token'
const CUSTOMER_KEY = 'mall_customer'

export function token() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function customer() {
  return uni.getStorageSync(CUSTOMER_KEY) || null
}

export function saveLogin(data) {
  uni.setStorageSync(TOKEN_KEY, data.token || '')
  uni.setStorageSync(CUSTOMER_KEY, data.customer || {})
}

export function saveCustomer(value) {
  uni.setStorageSync(CUSTOMER_KEY, value || {})
}

export function clearLogin() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(CUSTOMER_KEY)
  uni.removeStorageSync('mall_cart')
}

export function requireLogin() {
  if (token()) return true
  uni.reLaunch({ url: '/pages/login/index' })
  return false
}
