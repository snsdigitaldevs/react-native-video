//
//  SSImageManager.h
//
//  Created by Hao Zhu on 2020/5/22.
//

#import <Foundation/Foundation.h>
#import "GlobalMacros.h"

NS_ASSUME_NONNULL_BEGIN

typedef void(^SSImageResponse)(UIImage * _Nullable image, NSError * _Nullable error);

@interface SSImageManager : NSObject

+ (instancetype)sharedManager;

- (void)downloadImageWithUrl:(NSString *)url completion:(SSImageResponse)completion;

@end

NS_ASSUME_NONNULL_END
