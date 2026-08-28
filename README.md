# YunX（云析）

网盘分享链接解析与高速下载的 Android 应用。粘贴分享链接即可浏览分享内容并下载文件。

> 本 fork 正在维护增强版下载内核与 Provider 架构；开发分支为 `feat/yunx-enhanced-foundation`。

## 当前支持平台

**不建议使用百度网盘进行高频解析/转存，可能触发平台风控。**

- 夸克网盘
- UC 网盘
- 迅雷网盘
- 百度网盘
- 123 云盘
- 139 网盘（和彩云）

## 增强版方向

- **分享链接解析**：自动识别平台、分享 ID 与提取码
- **高速下载**：Range 分片并发 + 断点续传；用户线程上限提升到 128，实际 worker 会受 Provider/CDN 安全策略约束
- **下载恢复**：保留分片、任务请求头与分片规划，失败后可重试并继续下载
- **Provider 扩展**：逐步接入更多国内/海外网盘，并把认证、分享解析、取链和转存从 UI 解耦
- **临时转存清理**：百度/迅雷取链后清理；夸克保留到下载完成或删除任务后清理
- **登录**：夸克 / UC / 百度 / 139 使用 WebView Cookie；迅雷使用密码/短信；123 使用账号密码换取 JWT
- **认证备份**：使用用户口令派生密钥，以 AES-GCM 加密 Cookie/JWT 备份文件
- **剪贴板识别**：复制分享链接后回到应用，提示一键粘贴解析

## 截图

| 解析直链 | 分享解析 | 下载管理 |
|:---:|:---:|:---:|
| ![解析输入](images/Link.jpg) | ![文件列表](images/Parsing.jpg) | ![下载管理](images/Download.jpg) |

| 网盘登录 | 设置 | 关于 |
|:---:|:---:|:---:|
| ![网盘登录](images/Login.jpg) | ![设置](images/Setting.jpg) | ![关于](images/about.jpg) |

## 使用

1. 在「网盘」页登录需要认证的平台
2. 在「解析」页粘贴分享链接（可带提取码）
3. 浏览分享内容，点击文件获取下载直链
4. 「下载」页查看进度，支持暂停 / 继续 / 删除 / 打开

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

用 Android Studio 打开项目即可构建。CI 会执行 lint、单元测试、Debug APK 和 Release 编译验证。

## 免责声明

本项目仅供个人学习与技术交流，请勿用于侵犯版权、绕过付费限制或违反网盘服务条款的用途。下载内容版权归原作者所有，使用本项目产生的后果由使用者自行承担。

## 开源协议

本项目基于 [GNU AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html) 协议开源，详见根目录 [LICENSE](./LICENSE)。

## 关于协议兼容

部分网盘平台的解析来自公开接口、公开网页行为以及开源项目的协议研究。非官方接口可能随平台调整而失效，因此 Provider 实现需要独立维护并进行回归测试。
