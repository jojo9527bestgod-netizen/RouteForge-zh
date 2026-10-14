# 开源许可、来源与致谢

本项目为免费的非官方 RouteForge 中文版。以下原作者及项目的版权不因本分支修改而转移。原始法律许可文本保持原文，中文说明用于解释来源与分发方式。

## RouteForge

- 上游：[agvmdsa/RouteForge](https://github.com/agvmdsa/RouteForge)
- 原作者版权：`Copyright (c) 2026 Anderson Gabriel`
- 修改基线：`35705af709437fbc1633407d38148c083f1cfacc`
- 许可：MIT，原声明见 [LICENSE](LICENSE) 和 [licenses/MIT-RouteForge.txt](licenses/MIT-RouteForge.txt)。
- 本分支改动由 jojo9527bestgod-netizen 维护，同样按 MIT 提供；改动内容见 [CHANGELOG.md](CHANGELOG.md)。

## BRouter

道路计算使用仓库中随附的 `brouter-1.7.10-ro.jar` 和 `car-fast.brf`。来源为 [abrensch/brouter](https://github.com/abrensch/brouter)，版权声明为 `Copyright (c) 2019 BRouter contributors`，采用 MIT 许可，全文见 [licenses/MIT-BRouter.txt](licenses/MIT-BRouter.txt)。

## SLF4J

运行时依赖包含 SLF4J API，采用 MIT 许可。原始二进制中的版权和许可已提取到 [licenses/MIT-SLF4J.txt](licenses/MIT-SLF4J.txt)，来源见 [SLF4J 许可说明](https://www.slf4j.org/license.html)。

## Apache 2.0 组件

AndroidX / Jetpack Compose、Kotlin、Kotlin Coroutines / Serialization、Ktor、Koin、OkHttp / Okio、osmdroid、Touchlab Stately、JSpecify、Guava 的相关组件按其各自 Apache 2.0 许可提供。

完整许可见 [licenses/Apache-2.0.txt](licenses/Apache-2.0.txt)。构建所用版本和许可依据见 [licenses/DEPENDENCIES.md](licenses/DEPENDENCIES.md)。二进制中附带的 LICENSE / NOTICE 等声明保留在 [licenses/third-party/](licenses/third-party/)，同时合并进 APK 内的许可文本。

对应项目由各自作者和贡献者维护。本分支未修改这些依赖库的实现；对 RouteForge 的修改另见更新记录。

## 地图数据和在线服务

OpenStreetMap 数据由 OpenStreetMap 贡献者提供，适用 [ODbL 数据许可及署名要求](https://www.openstreetmap.org/copyright)。应用保留地图角落中的可见署名。

地图服务和数据许可分别适用各自的条件：OSM France HOT 要求免费、非营利、流量适量及有效请求标识；公共地图和 Nominatim 搜索不用于批量抓取或压测。详见 [OSM France](https://www.openstreetmap.fr/usage/)、[Nominatim](https://operations.osmfoundation.org/policies/nominatim/) 和 [PRIVACY.md](PRIVACY.md)。

## APK 内的声明

源码文件 `app/src/main/assets/OPEN_SOURCE_NOTICES.txt` 合并以上说明、MIT / Apache 2.0 全文及提取的第三方声明。安装 APK 后可在“设置 → 开源许可与致谢”查看，不依赖在线网页。
