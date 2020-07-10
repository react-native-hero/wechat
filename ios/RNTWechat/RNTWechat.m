
#import <UIKit/UIKit.h>
#import <React/RCTConvert.h>
#import "RNTWechat.h"

RNTWechat *wechatInstance;

typeof(void (^)(NSString*, void (^)(UIImage*))) wechatLoadImage;

@implementation RNTWechat

RCT_EXPORT_MODULE(RNTWechat);

+ (void)init:(NSString *)appId universalLink:(NSString *)universalLink loadImage:(void (^)(NSString*, void (^)(UIImage*)))loadImage {
    wechatLoadImage = loadImage;
    [WXApi registerApp:appId universalLink:universalLink];
}

+ (BOOL)handleOpenURL:(UIApplication *)application openURL:(NSURL *)url
options:(NSDictionary<NSString*, id> *)options {
    if (wechatInstance != nil) {
        return [WXApi handleOpenURL:url delegate:wechatInstance];
    }
    return NO;
}

+ (BOOL)handleOpenUniversalLink:(NSUserActivity *)userActivity {
    if (wechatInstance != nil) {
        return [WXApi handleOpenUniversalLink:userActivity delegate:wechatInstance];
    }
    return NO;
}

+ (BOOL)requiresMainQueueSetup {
    return YES;
}

- (instancetype)init {
    if (self = [super init]) {
        if (wechatInstance) {
            wechatInstance = nil;
        }
        wechatInstance = self;
    }
    return self;
}

- (void)dealloc {
    wechatInstance = nil;
}

- (NSArray<NSString *> *)supportedEvents {
  return @[
      @"pay_response",
      @"auth_response",
      @"message_response",
  ];
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
    req.partnerId = [RCTConvert NSString:options[@"partnerId"]];
    req.prepayId = [RCTConvert NSString:options[@"prepayId"]];
    req.nonceStr = [RCTConvert NSString:options[@"nonceStr"]];
    req.timeStamp = [RCTConvert NSString:options[@"timeStamp"]].longLongValue;
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
            reject(@"1", @"image is not found.", nil);
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

    wechatLoadImage(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareAudio:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(@"1", @"thumbnail is not found.", nil);
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

    wechatLoadImage(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareVideo:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(@"1", @"thumbnail is not found.", nil);
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

    wechatLoadImage(url, sendShareReq);

}

RCT_EXPORT_METHOD(sharePage:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(@"1", @"thumbnail is not found.", nil);
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

    wechatLoadImage(url, sendShareReq);

}

RCT_EXPORT_METHOD(shareMiniProgram:(NSDictionary*)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject) {

    typeof(void(^)(UIImage *image)) sendShareReq = ^(UIImage *image){

        if (image == nil) {
            reject(@"1", @"thumbnail is not found.", nil);
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

    wechatLoadImage(url, sendShareReq);

}

@end
