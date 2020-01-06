//
//  MusicControlManager.h
//  RCTVideo
//
//  Created by Peng Cao  on 2019/8/15.
//  Copyright © 2019 Facebook. All rights reserved.
//
#import <React/RCTBridgeModule.h>
#import <React/RCTEventEmitter.h>
#import <MediaPlayer/MediaPlayer.h>

extern NSString *const kRemoteControlActionChangedNotification;

typedef NS_ENUM(NSInteger, RCTRemoteControlAction) {
    RCTRemoteControlActionPlay,
    RCTRemoteControlActionPause,
    RCTRemoteControlActionTogglePlayPause,
    RCTRemoteControlActionStop,
    RCTRemoteControlActionNextTrack,
    RCTRemoteControlActionPreviousTrack,
    RCTRemoteControlActionSeekForward,
    RCTRemoteControlActionSeekBackward,
};

@interface MusicControlManager : RCTEventEmitter <RCTBridgeModule>

@end
