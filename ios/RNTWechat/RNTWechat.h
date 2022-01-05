#import <React/RCTEventEmitter.h>
#import "WXApi.h"

@interface RNTWechat : RCTEventEmitter <WXApiDelegate>

+ (void)init:(void (^)(NSString*, void (^)(UIImage*)))loadImage;

+ (BOOL)handleOpenURL:(UIApplication *)application openURL:(NSURL *)url options:(NSDictionary<NSString*, id> *)options;

+ (BOOL)handleOpenUniversalLink:(NSUserActivity *)userActivity;

@end
