package com.google.firebase.functions.ktx

import okhttp3.Interceptor

/** Created by weiping on 2024/3/6 */
interface InterceptorFactory {
  fun create(bodyMap: Map<String, Any>?, bodyJSON: String?): Interceptor
}
