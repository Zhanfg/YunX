# YunX（云析）

网盘分享链接解析与高速下载的 Android 应用。粘贴分享链接即可浏览分享内容并下载文件。

> 增强版开发分支：`feat/yunx-enhanced-foundation`。

## Provider 状态

**完整集成（浏览分享 / 取链 / 下载，部分平台支持转存）：**

- 夸克网盘
- UC 网盘
- 迅雷网盘
- 百度网盘
- 123 云盘
- 139 网盘（和彩云）

**公开分享可直接下载：**

- Dropbox（官方 `dl=1` 下载模式）
- pCloud（官方 public-link API；支持多 CDN host）

**V3/V4 已识别，正在等待独立认证/API Provider 接入：**

- 国内：阿里云盘、天翼云盘、蓝奏云、115、PikPak、城通网盘
- 海外：Google Drive、OneDrive、MEGA、Box、MediaFire

“已识别”不会伪装成完整支持：输入这些链接时应用会明确显示对应平台及当前适配状态，不会错误落入夸克等旧 Provider。

> 不建议使用百度网盘进行高频解析/转存，可能触发平台风控。

## V1–V4 增强

- **下载线程上限**：设置页支持 `64 / 128`；这是用户允许的最大值，实际 worker 仍受 Provider/CDN 安全策略约束。
- **并发策略**：保留迅雷约 8 worker 的安全起始限制，并加入独立的自适应并发策略与回落规则。
- **动态重连**：分片及单流遇到 IO、HTTP 408/425/429/5xx 时退避重试；失败后的请求强制放弃旧 keep-alive 连接并创建新 Call。
- **恢复模型**：新增可刷新 `DownloadSource`、多 endpoint、URL 过期与恢复决策模型，为后续 Provider 重签名 / CDN failover 提供统一边界。
- **断点续传与完整性**：继续保留现有 part/seg 续传、Range 校验、合并后总大小校验。
- **Provider Registry**：国内/海外平台统一描述 region、认证方式、能力和 readiness，新增平台不再直接污染旧 `SharePlatform` 枚举。
- **公开下载 Provider**：Dropbox 与 pCloud 已接入真实解析入口，无需先登录现有六个平台。
- **Android 冷启动**：新增 day/night 与 Android 12+ splash 主题资源，修复深色模式启动白闪方向的问题。
- **工程维护**：CI 增加 lint、单测、Debug/Release 编译；清理本地 `workspace.json`；文档与 SDK 信息同步。

## 使用

1. 在「网盘」页登录需要认证的平台。
2. 在「解析」页粘贴分享链接（可带提取码）。
3. 已完整集成的平台可浏览分享内容并获取下载链接；Dropbox / pCloud 公开文件可直接进入下载流程。
4. 「下载」页查看进度，支持暂停 / 继续 / 删除 / 打开。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Room（凭证与下载任务持久化）
- OkHttp（网络请求 + 分片下载）
- KSP

## 构建

当前工程配置：`minSdk 23`、`compileSdk 36`、`targetSdk 34`、JDK 17。

```bash
git clone https://github.com/Zhanfg/YunX.git
cd YunX
git checkout feat/yunx-enhanced-foundation
```

用 Android Studio 打开项目即可构建。CI 工作流配置为执行 lint、单元测试、Debug APK 和 Release 编译验证。

## 免责声明

本项目仅供个人学习与技术交流，请勿用于侵犯版权、绕过付费限制或违反网盘服务条款的用途。下载内容版权归原作者所有，使用本项目产生的后果由使用者自行承担。

## 开源协议

本项目基于 [GNU AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html) 协议开源，详见根目录 [LICENSE](./LICENSE)。

## 关于协议兼容

部分网盘平台的解析来自公开接口、公开网页行为以及开源项目的协议研究。非官方接口可能随平台调整而失效，因此 Provider 实现需要独立维护并进行回归测试。
