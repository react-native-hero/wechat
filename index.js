
import { NativeEventEmitter, NativeModules, Platform } from 'react-native'

const { RNTWechat } = NativeModules

const eventEmitter = new NativeEventEmitter(RNTWechat)

let resolveAuth
let rejectAuth
let resolvePay
let rejectPay
let resolveMessage
let rejectMessage
let resolveOpenMiniProgram
let rejectOpenMiniProgram
let resolveWebview
let rejectWebview

eventEmitter.addListener('auth_response', function (data) {
  if (data.code === 0) {
    if (resolveAuth) {
      resolveAuth(data)
      resolveAuth = rejectAuth = undefined
    }
  }
  else if (rejectAuth) {
    rejectAuth(data)
    resolveAuth = rejectAuth = undefined
  }
})

eventEmitter.addListener('pay_response', function (data) {
  if (data.code === 0) {
    if (resolvePay) {
      resolvePay(data)
      resolvePay = rejectPay = undefined
    }
  }
  else if (rejectPay) {
    rejectPay(data)
    resolvePay = rejectPay = undefined
  }
})

eventEmitter.addListener('message_response', function (data) {
  if (data.code === 0) {
    if (resolveMessage) {
      resolveMessage(data)
      resolveMessage = rejectMessage = undefined
    }
  }
  else if (rejectMessage) {
    rejectMessage(data)
    resolveMessage = rejectMessage = undefined
  }
})

eventEmitter.addListener('open_mini_program_response', function (data) {
  if (data.code === 0) {
    if (resolveOpenMiniProgram) {
      resolveOpenMiniProgram(data)
      resolveOpenMiniProgram = rejectOpenMiniProgram = undefined
    }
  }
  else if (rejectOpenMiniProgram) {
    rejectOpenMiniProgram(data)
    resolveOpenMiniProgram = rejectOpenMiniProgram = undefined
  }
})

eventEmitter.addListener('open_webview_response', function (data) {
  if (data.code === 0) {
    if (resolveWebview) {
      resolveWebview(data)
      resolveWebview = rejectWebview = undefined
    }
  }
  else if (rejectWebview) {
    rejectWebview(data)
    resolveWebview = rejectWebview = undefined
  }
})

function shareMessage(promise) {
  return new Promise((resolve, reject) => {
    promise.then(data => {
      if (data.success) {
        resolveMessage = resolve
        rejectMessage = reject
      }
      else {
        reject(data)
      }
    })
  })
}

export const CODE = Platform.select({
  ios: {
    IMAGE_NOT_FOUND: RNTWechat.ERROR_CODE_IMAGE_NOT_FOUND,
  },
  android: {
    IMAGE_NOT_FOUND: RNTWechat.ERROR_CODE_IMAGE_NOT_FOUND,
    IMAGE_RECYCLED: RNTWechat.ERROR_CODE_IMAGE_RECYCLED,
  }
})

export const SCOPE = {
  USER_INFO: 'snsapi_userinfo',
}

export const SCENE = {
  // 分享给朋友
  SESSION: 0,
  // 分享到朋友圈
  TIMELINE: 1,
  // 分享到收藏
  FAVORITE: 2,
}

// 小程序类型
export const MP_TYPE = {
  // 正式版
  PROD: 0,
  // 测试版
  TEST: 1,
  // 预览版
  PREVIEW: 2,
}

/**
 * 初始化
 */
export function init(options) {
  return RNTWechat.init(options)
}

/**
 * 检查微信是否已被用户安装
 */
export function isInstalled() {
  return RNTWechat.isInstalled()
}

/**
 * 判断当前微信的版本是否支持 Open Api
 */
export function isSupportOpenApi() {
  return RNTWechat.isSupportOpenApi()
}

/**
 * 打开微信
 */
export function open() {
  return RNTWechat.open()
}

/**
 * 打开微信小程序
 */
export function openMiniProgram(options) {
  return new Promise((resolve, reject) => {
    RNTWechat
      .openMiniProgram(options)
      .then(data => {
        if (data.success) {
          resolveOpenMiniProgram = resolve
          rejectOpenMiniProgram = reject
        }
        else {
          reject(data)
        }
      })
  })
}

/**
 * 打开微信网页
 */
 export function openWebview(options) {
  return new Promise((resolve, reject) => {
    RNTWechat
      .openWebview(options)
      .then(data => {
        if (data.success) {
          resolveWebview = resolve
          rejectWebview = reject
        }
        else {
          reject(data)
        }
      })
  })
}

/**
 * 微信支付
 */
export function pay(options) {
  return new Promise((resolve, reject) => {
    RNTWechat
      .pay(options)
      .then(data => {
        if (data.success) {
          resolvePay = resolve
          rejectPay = reject
        }
        else {
          reject(data)
        }
      })
  })
}

/**
 * 微信登录
 */
export function sendAuthRequest(options) {
  return new Promise((resolve, reject) => {
    RNTWechat
      .sendAuthRequest(options)
      .then(data => {
        if (data.success) {
          resolveAuth = resolve
          rejectAuth = reject
        }
        else {
          reject(data)
        }
      })
  })
}

/**
 * 分享文本
 */
export function shareText(options) {
  return shareMessage(
    RNTWechat.shareText(options)
  )
}

/**
 * 分享图片
 */
export function shareImage(options) {
  return shareMessage(
    RNTWechat.shareImage(options)
  )
}

/**
 * 分享音频
 */
export function shareAudio(options) {
  return shareMessage(
    RNTWechat.shareAudio(options)
  )
}

/**
 * 分享视频
 */
export function shareVideo(options) {
  return shareMessage(
    RNTWechat.shareVideo(options)
  )
}

/**
 * 分享网页
 */
export function sharePage(options) {
  return shareMessage(
    RNTWechat.sharePage(options)
  )
}

/**
 * 分享小程序
 */
export function shareMiniProgram(options) {
  return shareMessage(
    RNTWechat.shareMiniProgram(options)
  )
}

