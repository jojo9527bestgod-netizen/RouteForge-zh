# 隐私与第三方服务说明

本项目是免费的非官方 RouteForge 中文版，无广告、不收费。本分支没有新增自建后台或用户行为统计 SDK。

## 手机本地数据

- 起终点、途经点、地点及路线收藏和设置保存在手机本地。
- 道路计算使用本机 BRouter 和已下载路网。
- JSON / GPX 导入导出由用户选择文件及位置，文件可能包含具体地点坐标。
- 模拟定位通过安卓系统提供给其他应用，它们可能读取这些位置。
- 清除应用数据或卸载可能删除设置、路网和收藏。需保留的路线请先导出。
- 安卓系统自身的应用备份及迁移行为取决于手机系统和用户的备份设置，本应用沿用上游允许系统备份的配置。

## 网络请求

第三方服务可能接收到 IP 地址、请求时间、应用标识和对应功能所需内容：

| 功能 | 服务 | 请求内容 |
| --- | --- | --- |
| 默认备用地图 | OSM France HOT | 地图瓦片编号，可反映浏览的区域 |
| 其他地图样式 | OpenStreetMap / OpenTopoMap | 对应地图瓦片编号 |
| 手动地点搜索 | OpenStreetMap Nominatim | 用户提交的搜索文字；距离排序在手机本地完成 |
| 路网下载 | brouter.de | 所需区域的路网瓦片文件名称 |

输入文字本身不会逐字请求自动补全，点击搜索后才提交。请勿在公共地点搜索中输入私人身份信息。

维护者不控制第三方服务的日志、隐私政策或可用性。相关说明：

- [OpenStreetMap 隐私政策](https://osmfoundation.org/wiki/Privacy_Policy)
- [OpenStreetMap 数据版权与许可](https://www.openstreetmap.org/copyright)
- [OSM France 使用条件](https://www.openstreetmap.fr/usage/)
- [Nominatim 使用条件](https://operations.osmfoundation.org/policies/nominatim/)
- [OpenStreetMap 公共瓦片使用条件](https://operations.osmfoundation.org/policies/tiles/)
- [BRouter](https://brouter.de/)
- [OpenTopoMap](https://opentopomap.org/about)

## 反馈与公开资料

GitHub Issues 和发布页对外可见。分享截图、日志或轨迹前，请移除家庭地址、设备标识等敏感内容。维护者发布的材料不包含手机的个人收藏、现场轨迹或签名密钥。
