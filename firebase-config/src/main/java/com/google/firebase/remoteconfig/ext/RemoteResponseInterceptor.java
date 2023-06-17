package com.google.firebase.remoteconfig.ext;

import androidx.annotation.Nullable;

import com.google.firebase.remoteconfig.internal.ConfigContainer;

/**
 * weiping
 * 2023/6/16
 */
public interface RemoteResponseInterceptor {
    public void intercept(@Nullable ConfigContainer fetchedConfigs);
}
