package com.github.reactnativehero.wechat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Base64
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.tencent.mm.opensdk.constants.Build
import com.tencent.mm.opensdk.constants.ConstantsAPI
import com.tencent.mm.opensdk.modelbase.BaseReq
import com.tencent.mm.opensdk.modelbase.BaseResp
import com.tencent.mm.opensdk.modelbiz.OpenWebview
import com.tencent.mm.opensdk.modelbiz.WXLaunchMiniProgram
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

        private var wechatModule: RNTWechatModule? = null

        private var wechatLoadImage: ((String, (Bitmap?) -> Unit) -> Unit)? = null

        private const val ERROR_CODE_IMAGE_NOT_FOUND = "1"
        private const val ERROR_CODE_IMAGE_RECYCLED = "2"

        @JvmStatic fun init(loadImage: (String, (Bitmap?) -> Unit) -> Unit) {
            wechatLoadImage = loadImage
        }

        fun handleIntent(intent: Intent) {
            wechatModule?.handleIntent(intent)
        }

    }

    private var wechatAppId: String? = ""
    private lateinit var wechatApi: IWXAPI

    override fun getName(): String {
        return "RNTWechat"
    }

    override fun getConstants(): Map<String, Any>? {

        val constants: MutableMap<String, Any> = HashMap()

        constants["ERROR_CODE_IMAGE_NOT_FOUND"] = ERROR_CODE_IMAGE_NOT_FOUND
        constants["ERROR_CODE_IMAGE_RECYCLED"] = ERROR_CODE_IMAGE_RECYCLED

        return constants

    }

    override fun invalidate() {
        super.invalidate()
        wechatModule = null
    }

    override fun initialize() {
        super.initialize()
        wechatModule = this
    }

    fun handleIntent(intent: Intent) {
        wechatApi.handleIntent(intent, this)
    }

    @ReactMethod
    fun init(options: ReadableMap) {

        val context = reactApplicationContext

        wechatAppId = options.getString("appId")

        // 通过 WXAPIFactory 工厂，获取 IWXAPI 的实例
        wechatApi = WXAPIFactory.createWXAPI(context, wechatAppId, true)

        // 将应用的 appId 注册到微信
        wechatApi.registerApp(wechatAppId)

        // 建议动态监听微信启动广播进行注册到微信
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                wechatApi.registerApp(wechatAppId)
            }
        }, IntentFilter(ConstantsAPI.ACTION_REFRESH_WXAPP), ContextCompat.RECEIVER_EXPORTED)

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

        val url = options.getString("imageUrl")
        val base64 = options.getString("imageBase64")

        url?.let {
            wechatLoadImage?.invoke(it) { image ->
                sendShareReq(image)
            }
        }

        base64?.let {
            val bytes = Base64.decode(it, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            sendShareReq(bitmap)
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
            // 小程序限制图片为 128KB
            msg.thumbData = bitmap2ByteArray(bitmap, 131072)

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

    @ReactMethod
    fun openMiniProgram(options: ReadableMap, promise: Promise) {

        val req: WXLaunchMiniProgram.Req = WXLaunchMiniProgram.Req()
        req.userName = options.getString("mpName")
        req.path = options.getString("mpPath")
        req.miniprogramType = options.getInt("mpType")

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.sendReq(req))

        promise.resolve(map)

    }

    @ReactMethod
    fun openWebview(options: ReadableMap, promise: Promise) {

        val req: OpenWebview.Req = OpenWebview.Req()

        req.url = options.getString("url")

        val map = Arguments.createMap()
        map.putBoolean("success", wechatApi.sendReq(req))

        promise.resolve(map)

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
                sendEvent("message_response", map)
            }
            is WXLaunchMiniProgram.Resp -> {
                sendEvent("open_mini_program_response", map)
            }
            is OpenWebview.Resp -> {
                sendEvent("open_webview_response", map)
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

    // 默认限制图片为 32KB
    private fun bitmap2ByteArray(bitmap: Bitmap, limit: Int = 32768): ByteArray {

        var output = ByteArray(0)
        var outputWidth = bitmap.width
        var outputHeight = bitmap.height

        val ratio = outputWidth.toFloat() / outputHeight.toFloat()
        val decreaseWidth = outputWidth < outputHeight

        while (outputWidth > 0 && outputHeight > 0) {
            val localBitmap = createBitmap(outputWidth, outputHeight, Bitmap.Config.RGB_565)
            val localCanvas = Canvas(localBitmap)
            val byteArrayOutputStream = ByteArrayOutputStream()

            localCanvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), Rect(0, 0, outputWidth, outputHeight), null)
            localBitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
            localBitmap.recycle()

            val outputSize = byteArrayOutputStream.size()

            // 微信限制了图片尺寸
            if (outputSize < limit) {
                if (outputSize > 0) {
                    output = byteArrayOutputStream.toByteArray()
                }
                byteArrayOutputStream.close()
                break
            }
            else {
                byteArrayOutputStream.close()
                if (decreaseWidth) {
                    outputWidth -= getDecreaseOffset(outputWidth)
                    outputHeight = (outputWidth.toFloat() / ratio).toInt()
                }
                else {
                    outputHeight -= getDecreaseOffset(outputHeight)
                    outputWidth = (outputHeight.toFloat() * ratio).toInt()
                }
            }
        }

        return output

    }

    private fun getDecreaseOffset(size: Int): Int {
        if (size > 4000) {
            return 2000
        }
        else if (size > 3000) {
            return 1000
        }
        else if (size > 2000) {
            return 500
        }
        else if (size > 1500) {
            return 300
        }
        else if (size > 1000) {
            return 200
        }
        else if (size > 500) {
            return 50
        }
        else if (size > 300) {
            return 30
        }

        return 10
    }

}
