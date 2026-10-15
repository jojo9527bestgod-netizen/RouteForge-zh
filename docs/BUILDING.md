# 源码构建说明

## 环境

当前已验证的构建组合：

- JDK 25 运行自带的 Gradle 9.7.1。
- JDK 21 供模块 Java / Kotlin 工具链使用。
- Android SDK Platform 37.0 和 Build Tools 36.0.0。
- 安卓最低版本为 API 26（Android 8.0）。

从可信的官方渠道安装工具，使用仓库的 Gradle Wrapper。依赖版本见 `gradle/libs.versions.toml`，首次构建需要联网。

设置 `JAVA_HOME` 为 JDK 25、`ANDROID_HOME` 为安卓 SDK 路径，并确保 Gradle 能发现 JDK 21。必要时通过 `org.gradle.java.installations.paths` 指定本机 JDK 路径，将本机配置放在本机 Gradle 设置中，不提交到仓库。

## 构建

在仓库根目录执行：

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

Windows 使用 `gradlew.bat`。若工具拒绝含中文的目录，可添加 `-Pandroid.overridePathCheck=true`，或克隆到英文路径。

Debug 输出在 `app/build/outputs/apk/debug/`，使用本机调试签名。Release 输出在 `app/build/outputs/apk/release/`，仓库不包含维护者发布密钥，需要自行签名。签名不同的 APK 不能直接互相覆盖。

## 回归检查

```sh
./gradlew :core:domain:test :feature:addresssearch:domain:test :feature:addresssearch:data:testDebugUnitTest :feature:addresssearch:presentation:testDebugUnitTest :feature:simulation:domain:test :feature:simulation:data:testDebugUnitTest :feature:simulation:presentation:testDebugUnitTest :feature:routing:domain:test :feature:routing:data:testDebugUnitTest :feature:routing:presentation:testDebugUnitTest
```

检查覆盖地点搜索与坐标解析、路线、下载完整性、模拟控制和收藏。测试通过不能替代搜索服务可达性、地图访问、后台播放和导航兼容性真机验证。

## 许可与发布

应用内许可文本在 `app/src/main/assets/OPEN_SOURCE_NOTICES.txt`。修改依赖时请同步核对 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)、[licenses/](../licenses/) 及应用内声明。

`LICENSE` 保留上游 MIT 版权，公开版包名为 `com.routeforge.zh`。签名密钥、密码、令牌、`local.properties`、工具缓存和手机现场记录不进入公开仓库。
