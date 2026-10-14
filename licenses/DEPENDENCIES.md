# 构建组件与许可记录

根据本次 Release 运行时依赖图和本机构建缓存中的 POM / 二进制许可声明整理，包含用于版本选择的平台和元数据模块。它们不一定全部以独立二进制出现在 APK 中。

随仓库分发的 BRouter JAR 另外采用 MIT 许可，见 MIT-BRouter.txt。Guava listenablefuture 的 Apache 2.0 许可来自其 guava-parent POM；SLF4J 的 MIT 许可来自二进制内的 META-INF/LICENSE.txt。

| 组件坐标 | 许可 | 分类 |
| --- | --- | --- |
| androidx.activity:activity-compose:1.13.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.activity:activity-ktx:1.13.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.activity:activity:1.13.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.annotation:annotation-experimental:1.4.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.annotation:annotation-jvm:1.10.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.annotation:annotation:1.10.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.appcompat:appcompat-resources:1.7.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.appcompat:appcompat:1.7.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.arch.core:core-common:2.2.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.arch.core:core-runtime:2.2.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.autofill:autofill:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.collection:collection-jvm:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.collection:collection-ktx:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.collection:collection:1.5.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.animation:animation-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.animation:animation-core-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.animation:animation-core:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.animation:animation:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.foundation:foundation-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.foundation:foundation-layout-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.foundation:foundation-layout:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.foundation:foundation:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.material3:material3-android:1.4.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.material3:material3:1.4.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.material:material-icons-core-android:1.7.8 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.material:material-icons-core:1.7.8 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.material:material-icons-extended-android:1.7.8 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.material:material-icons-extended:1.7.8 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.material:material-ripple-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.material:material-ripple:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.runtime:runtime-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.runtime:runtime-annotation-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.runtime:runtime-annotation:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.runtime:runtime-retain-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.runtime:runtime-retain:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.runtime:runtime-saveable-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.runtime:runtime-saveable:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.runtime:runtime:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-geometry-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-geometry:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-graphics-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-graphics:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-text-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-text:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-tooling-preview-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-tooling-preview:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-unit-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-unit:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui-util-android:1.12.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.compose.ui:ui-util:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose.ui:ui:1.12.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.compose:compose-bom:2026.08.00 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.concurrent:concurrent-futures:1.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.core:core-ktx:1.19.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.core:core-viewtree:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.core:core:1.19.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.cursoradapter:cursoradapter:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.customview:customview-poolingcontainer:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.customview:customview:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.drawerlayout:drawerlayout:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.emoji2:emoji2-views-helper:1.4.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.emoji2:emoji2:1.4.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.fragment:fragment-ktx:1.8.8 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.fragment:fragment:1.8.8 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.graphics:graphics-path:1.0.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.interpolator:interpolator:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-common-java8:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-common-jvm:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-common:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-livedata-core-ktx:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-livedata-core:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-livedata:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-process:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-runtime-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-runtime-compose-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-runtime-compose:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-runtime-ktx-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-runtime-ktx:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-runtime:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-viewmodel-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-viewmodel-compose-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate-android:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.lifecycle:lifecycle-viewmodel:2.11.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.loader:loader:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigation:navigation-common-android:2.10.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigation:navigation-common:2.10.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.navigation:navigation-compose-android:2.10.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigation:navigation-compose:2.10.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.navigation:navigation-runtime-android:2.10.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigation:navigation-runtime:2.10.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.navigationevent:navigationevent-android:1.1.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigationevent:navigationevent-compose-android:1.1.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.navigationevent:navigationevent-compose:1.1.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.navigationevent:navigationevent:1.1.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.profileinstaller:profileinstaller:1.4.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.resourceinspection:resourceinspection-annotation:1.0.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.savedstate:savedstate-android:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.savedstate:savedstate-compose-android:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.savedstate:savedstate-compose:1.5.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.savedstate:savedstate-ktx:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.savedstate:savedstate:1.5.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.startup:startup-runtime:1.2.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.tracing:tracing:1.2.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.vectordrawable:vectordrawable-animated:1.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.vectordrawable:vectordrawable:1.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.versionedparcelable:versionedparcelable:1.1.1 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.viewpager:viewpager:1.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.window:window-core-android:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| androidx.window:window-core:1.5.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| androidx.window:window:1.5.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| co.touchlab:stately-concurrency-jvm:2.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| co.touchlab:stately-concurrency:2.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| co.touchlab:stately-concurrent-collections-jvm:2.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| co.touchlab:stately-concurrent-collections:2.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| co.touchlab:stately-strict-jvm:2.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| co.touchlab:stately-strict:2.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| com.google.guava:listenablefuture:1.0 | Apache-2.0 | 有二进制构建记录 |
| com.squareup.okhttp3:okhttp-android:5.3.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| com.squareup.okhttp3:okhttp:5.3.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| com.squareup.okio:okio-jvm:3.17.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| com.squareup.okio:okio:3.17.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.insert-koin:koin-android:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-androidx-compose:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-compose-android:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-compose-viewmodel-android:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-compose-viewmodel:4.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.insert-koin:koin-compose:4.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.insert-koin:koin-core-jvm:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-core-viewmodel-android:4.1.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.insert-koin:koin-core-viewmodel:4.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.insert-koin:koin-core:4.1.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-client-content-negotiation-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-client-content-negotiation:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-client-core-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-client-core:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-client-okhttp-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-client-okhttp:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-events-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-events:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-http-cio-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-http-cio:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-http-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-http:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-io-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-io:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-network-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-network:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-serialization-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-serialization-kotlinx-json-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-serialization-kotlinx-json:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-serialization-kotlinx-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-serialization-kotlinx:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-serialization:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-sse-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-sse:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-utils-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-utils:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-websocket-serialization-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-websocket-serialization:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| io.ktor:ktor-websockets-jvm:3.5.2 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| io.ktor:ktor-websockets:3.5.2 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-common:2.11.0-beta01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-runtime:2.9.6 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0-beta01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.11.0-beta01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0-beta01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.savedstate:savedstate-compose:1.3.6 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.androidx.savedstate:savedstate:1.3.6 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.animation:animation-core:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.animation:animation:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.annotation-internal:annotation:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.collection-internal:collection:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.foundation:foundation-layout:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.foundation:foundation:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.runtime:runtime-saveable:1.11.0-rc01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.runtime:runtime:1.11.0-rc01 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui-geometry:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui-graphics:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui-text:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui-unit:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui-util:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.compose.ui:ui:1.8.0 | The Apache Software License, Version 2.0 | 平台或元数据 |
| org.jetbrains.kotlin:kotlin-stdlib-common:2.4.20 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlin:kotlin-stdlib:2.4.20 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.1 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.1 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-io-core:0.9.1 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-serialization-bom:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-serialization-json-io-jvm:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-serialization-json-io:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.11.0 | Apache-2.0 | 有二进制构建记录 |
| org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0 | Apache-2.0 | 平台或元数据 |
| org.jetbrains:annotations:23.0.0 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| org.jspecify:jspecify:1.0.0 | The Apache License, Version 2.0 | 有二进制构建记录 |
| org.osmdroid:osmdroid-android:6.1.20 | The Apache Software License, Version 2.0 | 有二进制构建记录 |
| org.slf4j:slf4j-api:2.0.18 | MIT | 有二进制构建记录 |
