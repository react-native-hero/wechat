# @react-native-hero/wechat

封装微信 SDK，支持 `微信登录`、`微信支付`、`微信分享`。

## Getting started

Install the library using either Yarn:

```
yarn add @react-native-hero/wechat
```

or npm:

```
npm install --save @react-native-hero/wechat
```

## Link

- React Native v0.60+

For iOS, use `cocoapods` to link the package.

run the following command:

```
$ cd ios && pod install
```

For android, the package will be linked automatically on build.

- React Native <= 0.59

run the following command to link the package:

```
$ react-native link @react-native-hero/wechat
```

## Setup

### iOS

在 [微信开放平台](https://open.weixin.qq.com/) 获取 `appId`，并配置 `universalLink`。

在 `Xcode` 中，选择你的工程设置项，选中 `TARGETS` 一栏，在 `Info` 标签栏的 `URL Types` 添加 `URL Scheme` 为你所注册的 `appId`。

![](https://res.wx.qq.com/op_res/ohBULcCbr3PPan9SwnrNM6fEr-4kGDn98NenybClk1-fZE2rRYqU6xJCyVIMoFo9)

在 `Xcode` 中，选择你的工程设置项，选中 `TARGETS` 一栏，在 `Info` 标签栏的 `LSApplicationQueriesSchemes` 添加 `weixin` 和 `weixinULAPI`。

![](https://res.wx.qq.com/op_res/jck8iqKH85F0BaUWOT3GsSNmuGiOajiC-0bUWehibxED9c4JCauEun6UAZFh3HdO)

接下来，配置 `universalLink`，这有点麻烦。

首先在苹果开发者帐号里获取 `Team ID`，然后生成一份如下格式的 `json`：

```json
{
  "applinks": {
    "apps": [],
    "details": [
      {
        "appID":"<team id>.<bundle id>",
        "paths": ["*"]
      }
    ]
  }
}
```

举个例子，如果你的 `Team ID` 是 `N123`，`Bundle ID` 是 `com.abd.prod`，那么 `appID` 应该是 `N123.com.abd.prod`。

准备好 `json` 之后，确保通过 `https://www.abc.com/apple-app-site-association` 能打开这份 `json`。

到此服务器的配置完成，接下来配置 `Xcode`，首先打开苹果 `Certificates, Identifiers & Profiles`，选中 `Identifiers` 菜单，再选中你要使用的 `Identifier`，确保它勾选了 `Associated Domains` 项，如下：

![image](https://user-images.githubusercontent.com/2732303/76764453-d3f28900-67cf-11ea-8c4e-ac704fe41191.png)

然后回到主菜单，选中 `Profiles` 菜单，确保你要使用的 `Profile` 包含了 `Associated Domains`，如果当前还未包含，则点击编辑，保存，然后重新下载一个，并导入 `Xcode`。

在 `Xcode` 中，选择你的工程设置项，选中 `TARGETS` 一栏，在 `Signing & Capabilities` 标签栏点击 `+ Capability`，添加 `Associated Domains`，并填入你的 `universalLink`，格式是 `applinks:<domain name>`，注意域名不要包含 `https`，如下：

![image](https://user-images.githubusercontent.com/2732303/76765267-19638600-67d1-11ea-955e-ef637e108b50.png)


最后，修改 `AppDelegate.m`：

```oc
// 导入库
#import <RNTWechat.h>

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  ...
  [RNTWechat init:^(NSString *url, void (^ onComplete)(UIImage *)) {

    // 加载网络图片
    // 加载成功后调用 onComplete

  }];
}

// 添加此方法
- (BOOL)application:(UIApplication *)application openURL:(NSURL *)url
            options:(NSDictionary<NSString*, id> *)options {
  return [RNTWechat handleOpenURL:application openURL:url options:options];
}

// 添加此方法
- (BOOL)application:(UIApplication *)application continueUserActivity:(NSUserActivity *)userActivity restorationHandler:(void(^)(NSArray<id<UIUserActivityRestoring>> * __nullable restorableObjects))restorationHandler {
  return [RNTWechat handleOpenUniversalLink:userActivity];
}
```

### Android

在 [微信开放平台](https://open.weixin.qq.com/) 获取 `appId`。

在你的包名相应目录下新建一个 `wechat` 目录，并在该 `wechat` 目录下新增一个 `NotifyActivity` 类，该类继承自 `Activity`。

```kotlin
package your-package-name.wechat

import android.app.Activity
import android.os.Bundle
import com.github.reactnativehero.wechat.RNTWechatModule

class NotifyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RNTWechatModule.handleIntent(this.intent)
        finish()
    }

}
```

在 `manifest` 文件的 `application` 节点下新增一个 `activity` 和两个 `activity-alias`，里面加上 `exported` 和 `launchMode` 属性，其中 `exported` 设置为 `true`， `launchMode` 设置为 `singleTask`：

```xml
<activity
    android:name=".wechat.NotifyActivity"
    android:label="@string/app_name"
    android:theme="@android:style/Theme.Translucent.NoTitleBar"
    android:exported="true"
    android:launchMode="singleTask" />

<activity-alias
    android:name="${applicationId}.wxapi.WXEntryActivity"
    android:exported="true"
    android:targetActivity=".wechat.NotifyActivity" />

<!-- 不要微信支付可以删掉下面这个 -->
<activity-alias
    android:name="${applicationId}.wxapi.WXPayEntryActivity"
    android:exported="true"
    android:targetActivity=".wechat.NotifyActivity" />
```

最后，在 `MainApplication` 的 `onCreate` 方法进行初始化：

```kotlin
override fun onCreate() {

   RNTWechatModule.init { url, onComplete ->

      // 加载网络图片
      // 加载成功后调用 onComplete

  }

}
```

## Usage

```js
import {
  SCOPE,
  SCENE,
  MP_TYPE,
  init,
  sendAuthRequest,
  open,
  openMiniProgram,
  pay,
  shareText,
  shareImage,
  shareAudio,
  shareVideo,
  sharePage,
  shareMiniProgram,
} from '@react-native-hero/wechat'

// 必须先调用初始化方法，才能调用后面的方法
init({
  appId: '微信 app id',
  universalLink: 'ios 的 universalLink，注意包含 https://'
})

// 微信登录
sendAuthRequest({
  scope: SCOPE.USER_INFO,
})
.then(response => {
  response.data.code
})
.catch(() => {
  // 登录失败
})

// 打开微信
open()

// 打开微信小程序
openMiniProgram({
  // 小程序的 userName
  // 获取方式：登录小程序管理后台-设置-基本设置-帐号信息
  mpName: '',
  // 小程序的页面路径
  mpPath: '',
  // 小程序的类型
  mpType: MP_TYPE.PROD,
})

// 打开微信网页
openWebview({
  url: 'https://baidu.com',
})

// 微信支付（一般透传后端传来的参数，不用管它是什么意思）
pay({
  partnerId: 'partnerId',
  prepayId: 'prepayId',
  nonceStr: 'nonceStr',
  timeStamp: 'timeStamp',
  package: 'package',
  sign: 'sign',
})
.then(response => {
  // 财付通返回给商家的信息（好像也没什么用...）
  response.data.returnKey
})
.catch(() => {
  // 支付失败
})

// 分享文本
shareText({
  text: 'xxxxx',
  // 通过 scene 控制分享到 会话、朋友圈、收藏
  scene: SCENE.SESSION,
})
.then(() => {
  // 分享成功
})
.catch(() => {
  // 分享失败
})

// 分享图片
shareImage({
  imageUrl: 'https://xxx',
  scene: SCENE.SESSION,
})

// 分享 base64 图片
// 前面的 data:image/png;base64, 可要可不要，随便外面怎么传
shareImage({
  imageBase64: 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgA....',
  scene: SCENE.SESSION,
})

// 分享音频
shareAudio({
  // 音频网页地址
  pageUrl: 'https://xxx',
  // 音频地址
  audioUrl: 'https://xxx',
  // 缩略图地址
  thumbnailUrl: 'https://xxx',
  title: '',
  description: '',
  scene: SCENE.SESSION,
})

// 分享视频
shareVideo({
  // 视频地址
  videoUrl: 'https://xxx',
  // 缩略图地址
  thumbnailUrl: 'https://xxx',
  title: '',
  description: '',
  scene: SCENE.SESSION,
})

// 分享网页
sharePage({
  // 网页地址
  pageUrl: 'https://xxx',
  // 缩略图地址
  thumbnailUrl: 'https://xxx',
  title: '',
  description: '',
  scene: SCENE.SESSION,
})

// 分享小程序
shareMiniProgram({
  // 兼容低版本的网页链接
  pageUrl: 'https://xxx',
  // 缩略图地址
  thumbnailUrl: 'https://xxx',
  // 小程序的 userName
  // 获取方式：登录小程序管理后台-设置-基本设置-帐号信息
  mpName: '',
  // 小程序的页面路径
  mpPath: '',
  // 小程序的类型
  mpType: MP_TYPE.PROD,
  // 是否使用带 shareTicket 的分享
  withShareTicket: true,
  title: '',
  description: '',
  scene: SCENE.SESSION,
})
```
