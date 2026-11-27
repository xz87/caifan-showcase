<template>
  <view class="claim-page">
    <view class="status-spacer" />
    <view class="app-header">
      <view class="back-button" @tap="goBack">‹</view>
      <view class="app-header-title">损耗报备</view>
      <view class="app-header-sub">{{ saleNo }}</view>
    </view>

    <view class="page-content">
      <view v-if="claimDeadlineText || claimStatusText" class="claim-context">
        <view v-if="claimDeadlineText" class="context-line">
          <text class="context-label">报损截止</text>
          <text>{{ claimDeadlineText }}</text>
        </view>
        <view v-if="claimStatusText" class="context-line">
          <text class="context-label">当前状态</text>
          <text>{{ claimStatusText }}</text>
        </view>
      </view>

      <view class="notice-strip">
        <text class="notice-mark">说明</text>
        <text>请上传能看清损耗情况的图片或视频。商户审核后会按实际情况补发缺失商品。</text>
      </view>

      <view class="card form-card">
        <view class="section-title">损耗说明</view>
        <textarea
          v-model="reason"
          class="field-textarea reason-input"
          maxlength="500"
          placeholder="请说明哪些菜品出现损耗、数量以及实际情况"
        />

        <view class="section-row media-head">
          <view>
            <view class="section-title">图片凭证</view>
            <view class="section-note">最多 3 张</view>
          </view>
          <button class="btn-secondary btn-small" :loading="uploadingImage" @tap="chooseImages">添加图片</button>
        </view>
        <view class="image-grid">
          <view v-for="(image, index) in images" :key="image" class="image-item">
            <image class="evidence-image" :src="imageUrl(image)" mode="aspectFill" @tap="previewImage(index)" />
            <view class="remove-media" @tap="removeImage(index)">×</view>
          </view>
          <view v-if="!images.length" class="image-empty">尚未添加图片</view>
        </view>

        <view class="section-row media-head video-head">
          <view>
            <view class="section-title">视频凭证</view>
            <view class="section-note">最长 60 秒，可选</view>
          </view>
          <button class="btn-secondary btn-small" :loading="uploadingVideo" @tap="chooseVideo">
            {{ videoUrl ? '重新上传' : '添加视频' }}
          </button>
        </view>
        <view v-if="videoUrl" class="video-file">
          <view>
            <view class="video-title">视频已上传</view>
            <view class="video-url">{{ videoUrl }}</view>
          </view>
          <view class="video-remove" @tap="videoUrl = ''">移除</view>
        </view>

        <button class="btn-primary btn-block submit-button" :loading="submitting" :disabled="!claimable" @tap="submit">
          提交报备
        </button>
      </view>
    </view>
  </view>
</template>

<script>
import { http, upload } from '../../utils/request'
import { requireLogin } from '../../utils/auth'
import { imageUrl } from '../../utils/assets'

export default {
  data() {
    return {
      orderId: '',
      saleNo: '',
      claimable: false,
      claimDeadlineText: '',
      claimStatusText: '',
      reason: '',
      images: [],
      videoUrl: '',
      uploadingImage: false,
      uploadingVideo: false,
      submitting: false
    }
  },
  onLoad(options) {
    if (!requireLogin()) return
    this.orderId = options.orderId || ''
    this.saleNo = decodeURIComponent(options.saleNo || '')
    this.claimable = options.claimAllowed === '1'
    this.claimDeadlineText = decodeURIComponent(options.deadline || '')
    this.claimStatusText = decodeURIComponent(options.status || '')
    if (!this.orderId || !this.claimable) {
      uni.showModal({
        title: '暂不可报损',
        content: '该订单当前不符合报损条件，请返回订单页刷新后重试。',
        showCancel: false,
        success: () => this.backToOrders()
      })
    }
  },
  methods: {
    imageUrl,
    goBack() {
      this.backToOrders()
    },
    backToOrders() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack()
      } else {
        uni.reLaunch({ url: '/pages/orders/index' })
      }
    },
    async chooseImages() {
      const remain = 3 - this.images.length
      if (remain <= 0) return uni.showToast({ title: '最多上传 3 张图片', icon: 'none' })
      try {
        const result = await uni.chooseImage({
          count: remain,
          sizeType: ['compressed'],
          sourceType: ['album', 'camera']
        })
        this.uploadingImage = true
        for (const path of result.tempFilePaths || []) {
          const url = await upload('/api/mall/upload/claim', path)
          if (url) this.images.push(url)
        }
      } catch (error) {
        // request 已提示
      } finally {
        this.uploadingImage = false
      }
    },
    async chooseVideo() {
      try {
        const result = await uni.chooseVideo({
          sourceType: ['album', 'camera'],
          compressed: true,
          maxDuration: 60
        })
        const path = result.tempFilePath
        if (!path) return
        this.uploadingVideo = true
        this.videoUrl = await upload('/api/mall/upload/claim', path)
      } catch (error) {
        // request 已提示
      } finally {
        this.uploadingVideo = false
      }
    },
    previewImage(index) {
      uni.previewImage({
        urls: this.images.map((item) => imageUrl(item)),
        current: index
      })
    },
    removeImage(index) {
      this.images.splice(index, 1)
    },
    async submit() {
      if (this.submitting) return
      if (!this.orderId) return uni.showToast({ title: '缺少订单信息', icon: 'none' })
      if (!this.claimable) return uni.showToast({ title: '该订单暂不可报损', icon: 'none' })
      if (!this.reason.trim()) return uni.showToast({ title: '请填写损耗说明', icon: 'none' })
      if (!this.images.length && !this.videoUrl) {
        return uni.showToast({ title: '请上传图片或视频凭证', icon: 'none' })
      }
      this.submitting = true
      try {
        await http.post('/api/mall/claims', {
          orderId: this.orderId,
          reason: this.reason,
          images: this.images,
          videoUrl: this.videoUrl
        })
        uni.showToast({ title: '已提交，等待审核', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 700)
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
.claim-page {
  min-height: 100vh;
  background: #f4f7f5;
}

.back-button {
  width: 44rpx;
  color: #445048;
  font-size: 48rpx;
  font-weight: 300;
  line-height: 1;
}

.form-card {
  margin-top: 20rpx;
  padding: 24rpx;
}

.claim-context {
  margin-bottom: 16rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid #dce9df;
  border-radius: 14rpx;
  background: #eef7f1;
}

.context-line {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  color: #355c42;
  font-size: 22rpx;
  line-height: 1.6;
}

.context-line + .context-line {
  margin-top: 5rpx;
}

.context-label {
  flex-shrink: 0;
  color: #718078;
}

.reason-input {
  margin-top: 15rpx;
}

.media-head {
  margin-top: 24rpx;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
}

.image-item {
  position: relative;
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 12rpx;
  background: #edf2ee;
}

.evidence-image {
  width: 100%;
  height: 100%;
}

.remove-media {
  position: absolute;
  top: 7rpx;
  right: 7rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38rpx;
  height: 38rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.58);
  color: #fff;
  font-size: 27rpx;
}

.image-empty {
  grid-column: 1 / -1;
  padding: 30rpx;
  border: 1rpx dashed #dbe2dd;
  border-radius: 12rpx;
  color: #8a928d;
  font-size: 22rpx;
  text-align: center;
}

.video-head {
  margin-top: 26rpx;
}

.video-file {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 16rpx;
  border-radius: 12rpx;
  background: #f4f7f5;
}

.video-title {
  color: #2c3a31;
  font-size: 24rpx;
  font-weight: 650;
}

.video-url {
  max-width: 470rpx;
  margin-top: 5rpx;
  overflow: hidden;
  color: #8a928d;
  font-size: 19rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-remove {
  flex-shrink: 0;
  color: #b94343;
  font-size: 22rpx;
}

.submit-button {
  margin-top: 28rpx;
}
</style>
