# 异环画质助手 · NteGfxAssistant

面向《异环》（Neverness to Everness）Android 版的 Root 级画质配置工具，以可视化界面替代手工编辑 Unreal Engine 配置文件。软件开源免费。

## 功能特性

- **多渠道支持**：官服 / 哔哩哔哩服 / 台服，自动识别安装状态并切换配置目录
- **画质参数可视化调节**：帧率、渲染分辨率、色彩分级等参数图形化配置，无需手改 ini
- **Root 文件操作**：兼容 KernelSU / Magisk 等 su 环境，配置写入、备份与回滚
- **配置管理**：配置导入导出、运行日志导出、恢复默认
- **可选联网能力**：公告、更新检查、匿名使用统计（服务端未配置时静默降级，不影响本地功能）
- **界面**：Material Design 3 + 极光主题、中英双语、明暗模式

## 构建

环境要求：JDK 17+、Android SDK（compileSdk 36）、Gradle 8.13

```bash
# 调试构建
gradle :app:assembleDebug

# 发布构建（需自行配置签名）
gradle :app:assembleRelease
```

签名信息通过根目录 `keystore.properties` 提供（不入库）：

```properties
storeFile=keystore/your-release.p12
storePassword=***
keyAlias=***
keyPassword=***
```

`tools/build-release.ps1` 提供完整的正式构建 + 签名证书校验 + 摘要计算流程。

## 本仓库不包含的内容

出于安全与合规考虑，以下内容**不随仓库分发**，请自行准备：

1. **Unreal Engine 配置文件**：`config-src/*.ini` 与 `app/src/main/assets/config/*.bin` 均未入库。
   完整的参数预设需自行准备 ini 文件放入 `config-src/`，再运行 `node tools/encrypt-config.mjs` 生成加密资源（密钥机制见脚本与 `ConfigCipher.kt`，请自行更换密钥）。缺失时应用仍可构建运行，仅画质配置模板为空。
2. **服务端地址与部署信息**：默认服务端地址为空串，公告 / 更新检查 / 匿名上报自动降级。自行部署后在 `core/prefs/AppPrefs.kt` 的 `DEFAULT_API_BASE_URL` 填入默认地址，或在应用内调试面板覆盖；如走明文 HTTP，需在 `res/xml/network_security_config.xml` 中放行对应域名。
3. **签名密钥**：`keystore/`、`keystore.properties`、`local.properties` 不入库，请使用自有密钥。

## 免责声明

- 本软件仅供测试与技术交流使用。受设备型号、系统环境及游戏版本差异影响，使用过程中可能出现设备或游戏异常，请在充分了解风险并做好备份后使用；继续使用即表示您已知悉并接受上述风险。
- 请勿在游戏官方社媒账号以任何形式提及本软件。因使用本软件造成的后果由使用者本人承担。
- 为防止安装包被恶意篡改，请仅从官方发布渠道下载构建产物。

## 开源许可

本项目基于 [MIT 协议](LICENSE) 发布，第三方依赖许可见应用内「开放源代码许可」。
