package com.google.firebase.functions

import okhttp3.Interceptor

/** Created by weiping on 2024/3/6 */
public interface InterceptorFactory {
    public fun create(bodyMap: Map<String?, Any?>?, bodyJSON: String?): Interceptor
}
