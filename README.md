# Plasmo Musicshare

通过 Plasmo Voice 向附近玩家共享电脑音乐，同时保留正常麦克风说话。支持指定程序抓取、人声音效、独立人声与音乐混响，以及本机试听。

当前版本：**1.2.19**。Windows 客户端、Fabric Minecraft **1.21.11**、Plasmo Voice **2.1.17 及以上的 2.x**；可选 Talking Heads **1.1.3**。无需 Soundboard 或单独安装 TarsosDSP。

## 下载与安装

从 [Releases](https://github.com/Nathan489138/Plasmo-Musicshare/releases) 下载 `Plasmo-Musicshare-1.2.19+mc1.21.11-pv2.x.jar`，放入客户端 `mods`。手动移除旧版 Musicshare，避免重复加载；保留 Fabric API 和 Plasmo Voice。服务器需提供 Plasmo Voice 服务，其他玩家需安装 Plasmo Voice 才能听到。

## 快速使用

1. 在 Plasmo Voice 设置中选好麦克风和输出耳机。
2. 打开“音乐共享”，开启共享并调整发送音量，最高 **500%**。
3. 如需排除 KOOK，点击“扫描程序”，在“选择程序”中勾选播放器、点击“应用”，使用“只抓取指定程序”。
4. 在“音乐音效”调整人声预设和独立混响，在“本机试听”检查效果。

程序列表手动扫描，正常播放时不反复扫描全部程序；已选程序退出后会后台退避检查并恢复采集。空选择或目标未运行不会退回全局抓取。全局模式保留，分享除当前 Minecraft 进程树之外的声音。

同一浏览器中的音乐网页和 KOOK 网页不能按标签分开抓取，请使用不同浏览器程序或独立 KOOK 客户端。

## 快捷键与命令

| 操作 | 默认绑定 |
| --- | --- |
| 开关音乐共享 | F9，按一次开启，再按一次关闭 |
| 屏蔽人声 | F8，按住开启，松开恢复 |

可在游戏控制设置重新绑定，游戏内统一提示“快捷键”。人声屏蔽达到感应阈值时播放屏蔽音，低于阈值时静音，音乐独立。保留 `/music`、`/music on`、`/music off`、`/music status`。

## 音效与试听

人声预设：**原声、回声、机器人声、小孩、反派、水下低通、自定义**。内置 TarsosDSP，未集成 AI 变声。

- 小孩：温和升调；反派：低沉人声加回响；水下低通：闷沉音色。
- 变调、机器人和水下预设带平滑电平补偿，减少变声后音量过小；主观响度仍需试听判断。
- 保留镶边、延迟、变调、滤波、时间拉伸面板。人声和音乐各有独立混响：小房间、录音棚、舞台、大厅。
- 本机试听支持无延迟、1～10 秒延迟，使用 Plasmo Voice 选定的耳机。试听时暂停对外发送，关闭后恢复。

“强制话筒开启”默认关闭。开启后低于阈值的共享音乐也持续发送，不绕过真人麦克风激活、静音或服务器权限。持续发送可能使远端喇叭常亮；本机图标和 Talking Heads 仍遵循感应阈值。

## 兼容与验证

已通过 Plasmo Voice **2.1.17**、**2.2.0-beta.1** 的 Fabric/Knot 加载与集成检查，后者同时加载 Talking Heads **1.1.3**。音频、混音、门限、屏蔽、试听、进程选择/恢复及配置迁移回归检查通过。真实设备听感、完整 GUI 视觉和多人服务器仍需用户实测。

加载时检查已覆盖的 Plasmo Voice 接口，检测到不兼容会停用 Musicshare 并提示；无法保证未来所有 2.x 内部行为兼容。Minecraft 其他版本与 Plasmo Voice 3.x 尚未适配。

## 源码与反馈

- [更新记录](CHANGELOG.md)
- [详细参数、配置与验证说明](docs/TECHNICAL.md)
- [问题反馈](https://github.com/Nathan489138/Plasmo-Musicshare/issues)：请提供版本、复现步骤和相关日志，提交前移除私人信息。

构建入口 `build.ps1`，集成检查 `test-load.ps1`。需要 JDK 21 或以上、脚本所需的 Fabric/Minecraft 依赖缓存和 `deps` 中的 Plasmo Voice 依赖；具体路径见脚本。源码包不含游戏、依赖缓存或用户配置。

## 许可

**GPL-3.0-or-later**，详见 [LICENSE](LICENSE)。内置 TarsosDSP core 2.5，第三方许可见 [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md)。Plasmo Voice 和 Talking Heads 是外部依赖，不随模组分发。
