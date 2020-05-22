//
//  SSImageManager.m
//
//  Created by Hao Zhu on 2020/5/22.
//

#import "SSImageManager.h"
#import <CommonCrypto/CommonDigest.h>

@interface SSImageManager ()

@property (nonatomic, strong) NSCache *imageCache;
@property (strong, nonatomic) NSString *diskCachePath;
@property (nonatomic, strong) dispatch_queue_t ioQueue;

@end

@implementation SSImageManager

+ (instancetype)sharedManager {
  static SSImageManager *instance;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    instance = [[self alloc] init];
  });
  return instance;
}

- (instancetype)init {
  self = [super init];
  if (self) {
    self.imageCache = [[NSCache alloc] init];
    self.imageCache.name = @"com.ss.imageCache";
    // local cache path
    NSString *filePath = [NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, YES) firstObject];
    self.diskCachePath = [filePath stringByAppendingPathComponent:@"com.ss.imageCache"];
    // create a background thread to load image
    self.ioQueue = dispatch_queue_create("com.ss.imageCache", DISPATCH_QUEUE_SERIAL);
  }
  return self;
}

- (void)downloadImageWithUrl:(NSString *)url completion:(SSImageResponse)completion {
  if (url.length <= 0) {
    NSError *error = [[NSError alloc] initWithDomain:NSURLErrorDomain code:NSURLErrorBadURL userInfo:nil];
    completion(nil, error);
    NALog(@"image url is invalid.");
    return;
  }
  // Get image from local cache
    NSString *imageUrlKey = [self md5String:url];
  [self imageCacheForkey:imageUrlKey completion:^(UIImage * _Nullable cacheImage, NSError * _Nullable error) {
    if (cacheImage) {
      completion(cacheImage, nil);
      NALog(@"Get image from cache.");
      return;
    }
    __weak typeof(self) weakSelf = self;
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:url]];
    request.HTTPMethod = @"GET";
    NSURLSession *session = [NSURLSession sharedSession];
    NSURLSessionDataTask *task = [session dataTaskWithRequest:request completionHandler:^(NSData * _Nullable data, NSURLResponse * _Nullable response, NSError * _Nullable error) {
      UIImage *image = nil;
      if (data) {
        image = [UIImage imageWithData:data];
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (image) {
          [strongSelf storeImage:image forKey:imageUrlKey];
          NALog(@"stored image in cache.");
        }
      }
      dispatch_async(dispatch_get_main_queue(), ^{
        completion(image, error);
      });
    }];
    [task resume];
  }];
  
}

/**
 * Get image from cache
 * @param key encrypted key for image url
 */
- (void)imageCacheForkey:(NSString *)key completion:(SSImageResponse)completion {
  if (key.length <= 0) {
    NSError *error = [[NSError alloc] initWithDomain:NSURLErrorDomain code:NSURLErrorBadURL userInfo:nil];
    completion(nil, error);
    NALog(@"image cache key is invalid.");
    return;
  }
  UIImage *cacheImage = [self.imageCache objectForKey:key];
  if (cacheImage) {
    NALog(@"Get image from memory cache.");
    completion(cacheImage, nil);
    return;
  }
  // load image from disk
  dispatch_async(self.ioQueue, ^{
    NSString *imagePath = [self.diskCachePath stringByAppendingPathComponent:key];
    NSData *data = [NSData dataWithContentsOfFile:imagePath];
    if (data) {
      UIImage *diskImage = [UIImage imageWithData:data];
      if (diskImage) {
        // store disk image to memory
        [self.imageCache setObject:diskImage forKey:key];
        completion(diskImage, nil);
        NALog(@"Get image from disk cache.");
      }
    } else {
      // load image from network
      completion(nil, nil);
    }
  });
}

/**
 * Store image to cache
 */
- (void)storeImage:(UIImage *)image forKey:(NSString *)key {
  if (!image || key.length <= 0) {
    return;
  }
  // store to memory
  [self.imageCache setObject:image forKey:key];
  dispatch_async(self.ioQueue, ^{
    NSData *imageData = UIImagePNGRepresentation(image);
    if (!imageData) {
      return;
    }
    if (![[NSFileManager defaultManager] fileExistsAtPath:self.diskCachePath]) {
      [[NSFileManager defaultManager] createDirectoryAtPath:self.diskCachePath
                                withIntermediateDirectories:YES
                                                 attributes:nil
                                                      error:nil];
    }
    // store to disk
    NSString *imagePath = [self.diskCachePath stringByAppendingPathComponent:key];
    [[NSFileManager defaultManager] createFileAtPath:imagePath contents:imageData attributes:nil];
    NALog(@"stored image both in memory and disk cache.");
  });
}

- (NSString *)md5String:(NSString *)str {
  if (str == nil || str.length == 0) {
    return nil;
  }
  const char *value = [str UTF8String];
  unsigned char outputBuffer[CC_MD5_DIGEST_LENGTH];
  CC_MD5(value, (CC_LONG)strlen(value), outputBuffer);
  
  NSMutableString *outputString = [[NSMutableString alloc] initWithCapacity:CC_MD5_DIGEST_LENGTH * 2];
  for(NSInteger count = 0; count < CC_MD5_DIGEST_LENGTH; count++){
    [outputString appendFormat:@"%02x",outputBuffer[count]];
  }
  return outputString;
}

@end
