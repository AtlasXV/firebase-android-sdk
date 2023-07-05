package com.google.firebase.remoteconfig.ext;

import androidx.annotation.Nullable;

import com.google.firebase.remoteconfig.internal.ConfigContainer;

/**
 * weiping
 * 2023/7/6
 */
public interface ConfigFetchStrategy {
    public boolean needRefresh();

    public void intercept(@Nullable ConfigContainer fetchedConfigs);
}
