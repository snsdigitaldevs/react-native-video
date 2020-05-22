//
//  GlobalMacros.h
//
//  Created by Hao Zhu on 2020/5/22.
//

#ifndef GlobalMacros_h
#define GlobalMacros_h

#define dispatch_main_async_safe(block)\
if ([NSThread isMainThread]) {\
block();\
} else {\
dispatch_async(dispatch_get_main_queue(), block);\
}

#define dispatch_main_sync_safe(block)\
if ([NSThread isMainThread]) {\
block();\
} else {\
dispatch_sync(dispatch_get_main_queue(), block);\
}

#pragma mark - Print log
#ifdef DEBUG
#define NAILog(fmt, lvl, ...) NSLog((@"[%@] [Line%4d]%s" fmt @"\n\n"), lvl, __LINE__, __PRETTY_FUNCTION__, ##__VA_ARGS__)
#define NALog(fmt, ...) NSLog((@"[Line%4d]%s" fmt @"\n\n"), __LINE__, __PRETTY_FUNCTION__, ##__VA_ARGS__)
#else
#define NAILog(fmt, lvl, ...)
#define NALog(fmt, ...)
#endif

#endif /* GlobalMacros_h */
