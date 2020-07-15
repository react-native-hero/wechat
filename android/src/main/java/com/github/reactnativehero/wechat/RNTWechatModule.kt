package com.github.reactnativehero.wechat

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.tencent.mm.opensdk.constants.Build
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelmsg.*
import com.tencent.mm.opensdk.modelpay.PayReq
import com.tencent.mm.opensdk.modelpay.PayResp
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import java.io.ByteArrayOutputStream
import java.util.*

class RNTWechatModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), IWXAPIEventHandler {

    companion object {

        private lateinit var wechatApi: IWXAPI

        private var wechatAppId = ""

        private var wechatModule: RNTWechatModule? = null

        private var wechatLoadImage: ((String, (Bitmap?) -> Unit) -> Unit)? = null

        private const val ERROR_CODE_IMAGE_NOT_FOUND = "1"
        private const val ERROR_CODE_IMAGE_RECYCLED = "2"

        fun init(app: Application, appId: String, loadImage: (String, (Bitmap?) -> Unit) -> Unit) {

            wechatLoadImage = loadImage

            // 通过 WXAPIFactory 工厂，获取 IWXAPI 的实例
            wechatApi = WXAPIFactory.createWXAPI(app, appId, true)

            // 将应用的 appId 注册到微信
            wechatAppId = appId
            wechatApi.registerApp(appId)

            // 建议动态监听微信启动广播进行注册到微信
            app.registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    wechatApi.registerApp(appId)
                }
            }, IntentFilter(ConstantsAPI.ACTION_REFRESH_WXAPP))

        }

        fun handleIntent(intent: Intent) {
            wechatModule?.let {
                wechatApi.handleIntent(intent, it)
            }
        }

    }

    override fun getName(): String {
        return "RNTWechat"
    }

    override fun getConstants(): Map<String, Any>? {

        val constants: MutableMap<String, Any> = HashMap()

        constants["ERROR_CODE_IMAGE_NOT_FOUND"] = ERROR_CODE_IMAGE_NOT_FOUND
        constants["ERROR_CODE_IMAGE_RECYCLED"] = ERROR_CODE_IMAGE_RECYCLED

        return constants

    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        wechatModule = null
    }

    override fun initialize() {
        super.initialize()
        wechatModule = this
    }

    @ReactMethod
    fun isInstalled(promise: Promise) {

        val map = Arguments.createMap()
        map.putBoolean("installed", wechatApi.isWXAppInstalled)

        promise.resolve(map)

    }

    @ReactMethod
    fun isSupportOpenApi(promise: Promise) {

        val map = Arguments.createMap()
        map.putBoolean("supported", wechatApi.wxAppSupportAPI >= Build.OPENID_SUPPORTED_SDK_INT)

        promise.resolve(map)

    }

    @ReactMethod
    fun open(promise: Promise) {

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.openWXApp())

        promise.resolve(map)

    }

    @ReactMethod
    fun pay(options: ReadableMap, promise: Promise) {

        val req = PayReq()
        req.appId = wechatAppId
        req.partnerId = options.getString("partnerId")
        req.prepayId = options.getString("prepayId")
        req.nonceStr = options.getString("nonceStr")
        req.timeStamp = options.getString("timeStamp")
        req.packageValue = options.getString("package")
        req.sign = options.getString("sign")

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.sendReq(req))

        promise.resolve(map)

    }

    @ReactMethod
    fun sendAuthRequest(options: ReadableMap, promise: Promise) {

        val req = SendAuth.Req()
        req.scope = options.getString("scope")

        if (options.hasKey("state")) {
            req.state = options.getString("state")
        }

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.sendReq(req))

        promise.resolve(map)

    }

    @ReactMethod
    fun shareText(options: ReadableMap, promise: Promise) {

        val obj = WXTextObject()
        obj.text = options.getString("text")

        val msg = WXMediaMessage(obj)
        msg.description = options.getString("text")

        val req = SendMessageToWX.Req()
        req.transaction = createUUID()
        req.message = msg
        req.scene = options.getInt("scene")

        // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
        if (options.hasKey("openId")) {
            req.userOpenId = options.getString("openId")
        }

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.sendReq(req))

        promise.resolve(map)

    }

    @ReactMethod
    fun shareImage(options: ReadableMap, promise: Promise) {

        fun sendShareReq(bitmap: Bitmap?) {

            if (bitmap == null) {
                promise.reject(ERROR_CODE_IMAGE_NOT_FOUND, "image is not found.")
                return
            }

            if (bitmap.isRecycled) {
                promise.reject(ERROR_CODE_IMAGE_RECYCLED, "image is recycled.")
                return
            }

            val obj = WXImageObject(bitmap)

            val msg = WXMediaMessage(obj)
            // 分享图片不需要缩略图，最重要的是分享的图片通常比较大，会超过 32KB 限制

            val req = SendMessageToWX.Req()
            req.transaction = createUUID()
            req.message = msg
            req.scene = options.getInt("scene")

            // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
            if (options.hasKey("openId")) {
                req.userOpenId = options.getString("openId")
            }

            val map = Arguments.createMap()
            map.putBoolean("success", wechatApi.sendReq(req))

            promise.resolve(map)

        }

        val url = options.getString("imageUrl")!!

        wechatLoadImage?.invoke(url) {
            sendShareReq(it)
        }

    }

    @ReactMethod
    fun shareAudio(options: ReadableMap, promise: Promise) {

        fun sendShareReq(bitmap: Bitmap?) {

            if (bitmap == null) {
                promise.reject(ERROR_CODE_IMAGE_NOT_FOUND, "thumbnail is not found.")
                return
            }

            if (bitmap.isRecycled) {
                promise.reject(ERROR_CODE_IMAGE_RECYCLED, "thumbnail is recycled.")
                return
            }

            val obj = WXMusicObject()
            obj.musicUrl = options.getString("pageUrl")
            obj.musicLowBandUrl = obj.musicUrl
            obj.musicDataUrl = options.getString("audioUrl")
            obj.musicLowBandDataUrl = obj.musicDataUrl

            val msg = WXMediaMessage(obj)
            msg.title = options.getString("title")
            msg.description = options.getString("description")
            msg.thumbData = bitmap2ByteArray(bitmap)

            val req = SendMessageToWX.Req()
            req.transaction = createUUID()
            req.message = msg
            req.scene = options.getInt("scene")

            // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
            if (options.hasKey("openId")) {
                req.userOpenId = options.getString("openId")
            }

            val map = Arguments.createMap()
            map.putBoolean("success", wechatApi.sendReq(req))

            promise.resolve(map)

        }

        val url = options.getString("thumbnailUrl")!!

        wechatLoadImage?.invoke(url) {
            sendShareReq(it)
        }

    }

    @ReactMethod
    fun shareVideo(options: ReadableMap, promise: Promise) {

        fun sendShareReq(bitmap: Bitmap?) {

            if (bitmap == null) {
                promise.reject(ERROR_CODE_IMAGE_NOT_FOUND, "thumbnail is not found.")
                return
            }

            if (bitmap.isRecycled) {
                promise.reject(ERROR_CODE_IMAGE_RECYCLED, "thumbnail is recycled.")
                return
            }

            val obj = WXVideoObject()
            obj.videoUrl = options.getString("videoUrl")
            obj.videoLowBandUrl = obj.videoUrl

            val msg = WXMediaMessage(obj)
            msg.title = options.getString("title")
            msg.description = options.getString("description")
            msg.thumbData = bitmap2ByteArray(bitmap)

            val req = SendMessageToWX.Req()
            req.transaction = createUUID()
            req.message = msg
            req.scene = options.getInt("scene")

            // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
            if (options.hasKey("openId")) {
                req.userOpenId = options.getString("openId")
            }

            val map = Arguments.createMap()
            map.putBoolean("success", wechatApi.sendReq(req))

            promise.resolve(map)

        }

        val url = options.getString("thumbnailUrl")!!

        wechatLoadImage?.invoke(url) {
            sendShareReq(it)
        }

    }

    @ReactMethod
    fun sharePage(options: ReadableMap, promise: Promise) {

        fun sendShareReq(bitmap: Bitmap?) {

            if (bitmap == null) {
                promise.reject(ERROR_CODE_IMAGE_NOT_FOUND, "thumbnail is not found.")
                return
            }

            if (bitmap.isRecycled) {
                promise.reject(ERROR_CODE_IMAGE_RECYCLED, "thumbnail is recycled.")
                return
            }

            val obj = WXWebpageObject()
            obj.webpageUrl = options.getString("pageUrl")

            val msg = WXMediaMessage(obj)
            msg.title = options.getString("title")
            msg.description = options.getString("description")
            msg.thumbData = bitmap2ByteArray(bitmap)

            val req = SendMessageToWX.Req()
            req.transaction = createUUID()
            req.message = msg
            req.scene = options.getInt("scene")

            // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
            if (options.hasKey("openId")) {
                req.userOpenId = options.getString("openId")
            }

            val map = Arguments.createMap()
            map.putBoolean("success", wechatApi.sendReq(req))

            promise.resolve(map)

        }

        val url = options.getString("thumbnailUrl")!!

        wechatLoadImage?.invoke(url) {
            sendShareReq(it)
        }

    }

    @ReactMethod
    fun shareMiniProgram(options: ReadableMap, promise: Promise) {

        fun sendShareReq(bitmap: Bitmap?) {

            if (bitmap == null) {
                promise.reject(ERROR_CODE_IMAGE_NOT_FOUND, "thumbnail is not found.")
                return
            }

            if (bitmap.isRecycled) {
                promise.reject(ERROR_CODE_IMAGE_RECYCLED, "thumbnail is recycled.")
                return
            }

            val obj = WXMiniProgramObject()
            obj.webpageUrl = options.getString("pageUrl")
            obj.userName = options.getString("mpName")
            obj.path = options.getString("mpPath")
            obj.miniprogramType = options.getInt("mpType")
            obj.withShareTicket = options.getBoolean("withShareTicket")

            val msg = WXMediaMessage(obj)
            msg.title = options.getString("title")
            msg.description = options.getString("description")
            msg.thumbData = bitmap2ByteArray(bitmap)

            val req = SendMessageToWX.Req()
            req.transaction = createUUID()
            req.message = msg
            // 目前只支持会话
            req.scene = SendMessageToWX.Req.WXSceneSession

            // 这个参数貌似是新版 SDK 加的，以前用的版本没传过这个参数
            if (options.hasKey("openId")) {
                req.userOpenId = options.getString("openId")
            }

            val map = Arguments.createMap()
            map.putBoolean("success", wechatApi.sendReq(req))

            promise.resolve(map)

        }

        val url = options.getString("thumbnailUrl")!!

        wechatLoadImage?.invoke(url) {
            sendShareReq(it)
        }

    }

    override fun onReq(baseReq: BaseReq?) {

    }

    override fun onResp(baseResp: BaseResp?) {

        if (baseResp == null) {
            return
        }

        val map = Arguments.createMap()

        var code = 0
        if (baseResp.errCode != BaseResp.ErrCode.ERR_OK) {
          code = baseResp.errCode
        }

        map.putInt("code", code)
        map.putString("msg", baseResp.errStr)

        when (baseResp) {
            is PayResp -> {
                if (code == 0) {
                    val data = Arguments.createMap()
                    data.putString("returnKey", baseResp.returnKey)
                    map.putMap("data", data)
                }
                sendEvent("pay_response", map)
            }
            is SendAuth.Resp -> {
                if (code == 0) {
                    val data = Arguments.createMap()
                    data.putString("code", baseResp.code)
                    data.putString("state", baseResp.state)
                    data.putString("url", baseResp.url)
                    data.putString("lang", baseResp.lang)
                    data.putString("country", baseResp.country)
                    map.putMap("data", data)
                }
                sendEvent("auth_response", map)
            }
            is SendMessageToWX.Resp -> {
                // 没啥新属性...
                sendEvent("message_response", map)
            }
            else -> {

            }
        }

    }

    private fun sendEvent(eventName: String, params: WritableMap) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                .emit(eventName, params)
    }

    private fun createUUID(): String {
        return UUID.randomUUID().toString()
    }

    private fun bitmap2ByteArray(bitmap: Bitmap): ByteArray {

        val output = ByteArrayOutputStream()
        bitmap.compress(CompressFormat.PNG, 100, output)
        bitmap.recycle()

        val result: ByteArray = output.toByteArray()
        try {
            output.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return result

    }

}
