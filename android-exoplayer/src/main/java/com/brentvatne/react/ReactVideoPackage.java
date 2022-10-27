package com.brentvatne.react;

import androidx.annotation.NonNull;

import com.brentvatne.exoplayer.ReactExoplayerViewManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.JavaScriptModule;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.brentvatne.media.NativeMediaModule;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ReactVideoPackage implements ReactPackage {

    @NonNull
    @Override
    public List<NativeModule> createNativeModules(@NonNull ReactApplicationContext reactContext) {
        return Collections.singletonList(new NativeMediaModule(reactContext));
    }

    // Deprecated RN 0.47	
    public List<Class<? extends JavaScriptModule>> createJSModules() {	
        return Collections.emptyList();	
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Arrays.<ViewManager>asList(new ReactExoplayerViewManager());
    }
}
