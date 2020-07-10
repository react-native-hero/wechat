#import <React/RCTEventEmitter.h>
#import "WXApi.h"

@interface RNTWechat : RCTEventEmitter <WXApiDelegate>

+ (void)init:(NSString *)appId universalLink:(NSString *)universalLink loadImage:(void (^)(NSString*, void (^)(UIImage*)))loadImage;

+ (BOOL)handleOpenURL:(UIApplication *)application openURL:(NSURL *)url options:(NSDictionary<NSString*, id> *)options;

+ (BOOL)handleOpenUniversalLink:(NSUserActivity *)userActivity;

@end
