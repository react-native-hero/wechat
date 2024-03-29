
#import <UIKit/UIKit.h>
#import <React/RCTConvert.h>
#import "RNTWechat.h"

@implementation RNTWechat

static RNTWechat *WECHAT_INSTANCE = nil;

static typeof(void (^)(NSString*, void (^)(UIImage*))) WECHAT_LOAD_IMAGE;

static NSString *ERROR_CODE_IMAGE_NOT_FOUND = @"1";

RCT_EXPORT_MODULE(RNTWechat);

+ (void)init:(void (^)(NSString*, void (^)(UIImage*)))loadImage {
    WECHAT_LOAD_IMAGE = loadImage;
}

+ (BOOL)handleOpenURL:(UIApplication *)application openURL:(NSURL *)url
options:(NSDictionary<NSString*, id> *)options {
    if (WECHAT_INSTANCE != nil) {
        return [WXApi handleOpenURL:url delegate:WECHAT_INSTANCE];
    }
    return NO;
}

+ (BOOL)handleOpenUniversalLink:(NSUserActivity *)userActivity {
    if (WECHAT_INSTANCE != nil) {
        return [WXApi handleOpenUniversalLink:userActivity delegate:WECHAT_INSTANCE];
    }
    return NO;
}

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (dispatch_queue_t)methodQueue {
    return dispatch_queue_create("com.github.reactnativehero.wechat", DISPATCH_QUEUE_SERIAL);
}

- (instancetype)init {
    if (self = [super init]) {
        if (WECHAT_INSTANCE) {
            WECHAT_INSTANCE = nil;
        }
        WECHAT_INSTANCE = self;
    }
    return self;
}

- (void)dealloc {
    WECHAT_INSTANCE = nil;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[
      @"pay_response",
      @"auth_response",
      @"message_response",
      @"open_mini_program_response",
      @"open_webview_response",
  ];
}

- (NSDictionary *)constantsToExport {
    return @{
        @"ERROR_CODE_IMAGE_NOT_FOUND": ERROR_CODE_IMAGE_NOT_FOUND,
    };
}

- (void)onReq:(BaseReq *)req {

}

- (void)onResp:(BaseResp *)resp {

    if (resp == nil) {
        return;
    }

    NSMutableDictionary *body = [[NSMutableDictionary alloc] init];

    // 改造一下返回结构，只有 code 和 msg 两个固定字段
    int code = 0;
    if (resp.errCode != WXSuccess) {
        code = resp.errCode;
    }

    body[@"code"] = @(code);
    body[@"msg"] = resp.errStr;

    // 微信支付
    if ([resp isKindOfClass:[PayResp class]]) {
        
        if (code == 0) {
            PayResp *r = (PayResp *)resp;
            NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
            data[@"returnKey"] = r.returnKey;
        }

        [self sendEventWithName:@"pay_response" body:body];
        
    }
    // 微信登录
    else if ([resp isKindOfClass:[SendAuthResp class]]) {

        if (code == 0) {
            SendAuthResp *r = (SendAuthResp *)resp;
            NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
            data[@"lang"] = r.lang;
            data[@"country"] = r.country;
            data[@"state"] = r.state;
            data[@"code"] = r.code;
            body[@"data"] = data;
        }

        [self sendEventWithName:@"auth_response" body:body];

    }
    // 微信分享
    else if ([resp isKindOfClass:[SendMessageToWXResp class]]) {

        if (code == 0) {
            SendMessageToWXResp *r = (SendMessageToWXResp *)resp;
            NSMutableDictionary *data = [[NSMutableDictionary alloc] init];
            data[@"lang"] = r.lang;
            data[@"country"] = r.country;
            body[@"data"] = data;
        }

        [self sendEventWithName:@"message_response" body:body];

    }
    // 打开小程序
    else if ([resp isKindOfClass:[WXLaunchMiniProgramResp class]]) {

        [self sendEventWithName:@"open_mini_program_response" body:body];

    }
    // 打开 webview
    else if ([resp isKindOfClass:[OpenWebviewResp class]]) {

        [self sendEventWithName:@"open_webview_response" body:body];

    }
    
}

RCT_EXPORT_METHOD(init:(NSDictionary*)options) {
    
    NSString *appId = [RCTConvert NSString:options[@"appId"]];
    NSString *universalLink = [RCTConvert NSString:options[@"universalLink"]];
    
    [WXApi registerApp:appId universalLink:universalLink];
    
}

RCT_EXPORT_METHOD(isInstalled:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    BOOL installed = [WXApi isWXAppInstalled];
    resolve(@{
        @"installed": @(installed)
    });
}

RCT_EXPORT_METHOD(isSupportOpenApi:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    BOOL supported = [WXApi isWXAppSupportApi];
    resolve(@{
        @"supported": @(supported)
    });
}

RCT_EXPORT_METHOD(open:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    BOOL success = [WXApi openWXApp];
    resolve(@{
        @"success": @(success)
    });
}

RCT_EXPORT_METHOD(pay:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {
    
    PayReq *req = [[PayReq alloc] init];
    
    NSNumber *timeStamp = @([[RCTConvert NSString:options[@"timeStamp"]] integerValue]);
    
    req.partnerId = [RCTConvert NSString:options[@"partnerId"]];
    req.prepayId = [RCTConvert NSString:options[@"prepayId"]];
    req.nonceStr = [RCTConvert NSString:options[@"nonceStr"]];
    req.timeStamp = timeStamp.unsignedIntValue;
    req.package = [RCTConvert NSString:options[@"package"]];
    req.sign = [RCTConvert NSString:options[@"sign"]];
    
    [WXApi sendReq:req completion:^(BOOL success) {
        resolve(@{
            @"success": @(success)
        });
    }];
    
}

RCT_EXPORT_METHOD(sendAuthRequest:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    SendAuthReq *req = [[SendAuthReq alloc] init];
    req.scope = [RCTConvert NSString:options[@"scope"]];
    req.state = [RCTConvert NSString:options[@"state"]];

    [WXApi sendReq:req completion:^(BOOL success) {
        resolve(@{
            @"success": @(success)
        });
    }];

}

// 分享文本
// scene 0-会话 1-朋友圈 2-收藏
RCT_EXPORT_METHOD(shareText:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
    req.bText = YES;
    req.text = [RCTConvert NSString:options[@"text"]];
    req.scene = [RCTConvert int:options[@"scene"]];

    [WXApi sendReq:req completion:^(BOOL success) {
        resolve(@{
            @"success": @(success)
        });
    }];

}

RCT_EXPORT_METHOD(shareImage:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(ERROR_CODE_IMAGE_NOT_FOUND, @"image is not found.", nil);
            return;
        }

        NSData *imageData = UIImagePNGRepresentation(image);

        WXImageObject *object = [WXImageObject object];
        object.imageData = imageData;

        WXMediaMessage *message = [WXMediaMessage message];
        // 分享图片不需要缩略图，最重要的是分享的图片通常比较大，会超过 32KB 限制
        // message.thumbData = imageData;
        message.mediaObject = object;

        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [RCTConvert int:options[@"scene"]];

        [WXApi sendReq:req completion:^(BOOL success) {
            resolve(@{
                @"success": @(success)
            });
        }];

    };

    NSString *url = [RCTConvert NSString:options[@"imageUrl"]];
    NSString *base64 = [RCTConvert NSString:options[@"imageBase64"]];
    
    if (base64.length > 0) {
        NSData *imageData = [[NSData alloc]
                    initWithBase64EncodedString:base64
                    options:NSDataBase64DecodingIgnoreUnknownCharacters];
        
        UIImage *image = [UIImage imageWithData:imageData];
        sendShareReq(image);
        
        return;
    }

    WECHAT_LOAD_IMAGE(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareAudio:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(ERROR_CODE_IMAGE_NOT_FOUND, @"thumbnail is not found.", nil);
            return;
        }

        NSData *imageData = UIImagePNGRepresentation(image);

        WXMusicObject *object = [WXMusicObject object];
        object.musicUrl = [RCTConvert NSString:options[@"pageUrl"]];
        object.musicLowBandUrl = object.musicUrl;
        object.musicDataUrl = [RCTConvert NSString:options[@"audioUrl"]];
        object.musicLowBandDataUrl = object.musicDataUrl;

        WXMediaMessage *message = [WXMediaMessage message];
        message.title = [RCTConvert NSString:options[@"title"]];
        message.description = [RCTConvert NSString:options[@"description"]];
        message.thumbData = imageData;
        message.mediaObject = object;

        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [RCTConvert int:options[@"scene"]];

        [WXApi sendReq:req completion:^(BOOL success) {
            resolve(@{
                @"success": @(success)
            });
        }];

    };

    NSString *url = [RCTConvert NSString:options[@"thumbnailUrl"]];

    WECHAT_LOAD_IMAGE(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareVideo:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(ERROR_CODE_IMAGE_NOT_FOUND, @"thumbnail is not found.", nil);
            return;
        }

        NSData *imageData = UIImagePNGRepresentation(image);

        WXVideoObject *object = [WXVideoObject object];
        object.videoUrl = [RCTConvert NSString:options[@"videoUrl"]];
        object.videoLowBandUrl = object.videoUrl;

        WXMediaMessage *message = [WXMediaMessage message];
        message.title = [RCTConvert NSString:options[@"title"]];
        message.description = [RCTConvert NSString:options[@"description"]];
        message.thumbData = imageData;
        message.mediaObject = object;

        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [RCTConvert int:options[@"scene"]];

        [WXApi sendReq:req completion:^(BOOL success) {
            resolve(@{
                @"success": @(success)
            });
        }];

    };

    NSString *url = [RCTConvert NSString:options[@"thumbnailUrl"]];

    WECHAT_LOAD_IMAGE(url, sendShareReq);

}

RCT_EXPORT_METHOD(sharePage:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(ERROR_CODE_IMAGE_NOT_FOUND, @"thumbnail is not found.", nil);
            return;
        }

        NSData *imageData = UIImagePNGRepresentation(image);

        WXWebpageObject *object = [WXWebpageObject object];
        object.webpageUrl = [RCTConvert NSString:options[@"pageUrl"]];

        WXMediaMessage *message = [WXMediaMessage message];
        message.title = [RCTConvert NSString:options[@"title"]];
        message.description = [RCTConvert NSString:options[@"description"]];
        message.thumbData = imageData;
        message.mediaObject = object;

        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        req.scene = [RCTConvert int:options[@"scene"]];

        [WXApi sendReq:req completion:^(BOOL success) {
            resolve(@{
                @"success": @(success)
            });
        }];

    };

    NSString *url = [RCTConvert NSString:options[@"thumbnailUrl"]];

    WECHAT_LOAD_IMAGE(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareMiniProgram:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(ERROR_CODE_IMAGE_NOT_FOUND, @"thumbnail is not found.", nil);
            return;
        }

        NSData *imageData = UIImagePNGRepresentation(image);

        WXMiniProgramObject *object = [WXMiniProgramObject object];
        // 兼容低版本的网页链接
        object.webpageUrl = [RCTConvert NSString:options[@"pageUrl"]];
        // 小程序的 userName
        object.userName = [RCTConvert NSString:options[@"mpName"]];
        // 小程序的页面路径
        object.path = [RCTConvert NSString:options[@"mpPath"]];
        // 小程序新版本的预览图二进制数据，6.5.9及以上版本微信客户端支持
        object.hdImageData = imageData;
        // 小程序的类型，默认正式版，1.8.1及以上版本开发者工具包支持分享开发版和体验版小程序
        // 0-正式版 1-开发版 2-体验版
        object.miniProgramType = [RCTConvert int:options[@"mpType"]];
        // 是否使用带 shareTicket 的分享
        object.withShareTicket = [RCTConvert BOOL:options[@"withShareTicket"]];

        WXMediaMessage *message = [WXMediaMessage message];
        message.title = [RCTConvert NSString:options[@"title"]];
        message.description = [RCTConvert NSString:options[@"description"]];
        // 兼容旧版本节点的图片，小于32KB，新版本优先
        // 使用 WXMiniProgramObject 的 hdImageData 属性
        message.thumbData = nil;
        message.mediaObject = object;

        SendMessageToWXReq *req = [[SendMessageToWXReq alloc] init];
        req.bText = NO;
        req.message = message;
        // 目前只支持会话
        req.scene = WXSceneSession;

        [WXApi sendReq:req completion:^(BOOL success) {
            resolve(@{
                @"success": @(success)
            });
        }];

    };

    NSString *url = [RCTConvert NSString:options[@"thumbnailUrl"]];

    WECHAT_LOAD_IMAGE(url, sendShareReq);

}

RCT_EXPORT_METHOD(openMiniProgram:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    WXLaunchMiniProgramReq *req = [WXLaunchMiniProgramReq object];
    
    // 拉起的小程序的 username
    req.userName = [RCTConvert NSString:options[@"mpName"]];
    // 拉起小程序页面的可带参路径，不填默认拉起小程序首页，
    // 对于小游戏，可以只传入 query 部分，来实现传参效果，如：传入 "?foo=bar"
    req.path = [RCTConvert NSString:options[@"mpPath"]];
    // 0-正式版 1-开发版 2-体验版
    req.miniProgramType = [RCTConvert int:options[@"mpType"]];

    [WXApi sendReq:req completion:^(BOOL success) {
        resolve(@{
            @"success": @(success)
        });
    }];
    
}


RCT_EXPORT_METHOD(openWebview:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    OpenWebviewReq *req = [OpenWebviewReq new];
    
    req.url = [RCTConvert NSString:options[@"url"]];
    
    [WXApi sendReq:req completion:^(BOOL success) {
        resolve(@{
            @"success": @(success)
        });
    }];
    
}

@end
