# RadBroadcast ——  沉浸音频广播（Paper 1.20.1）

服务端按**区域 / 指定玩家 / 全员**向装有客户端 Mod 的玩家广播音频直链（MP3），
用于怪物喊话、警报、环境音等沉浸式演出。纯客户端解码播放，**不占用 MC 声音通道**，
可与游戏内其他音效/音乐叠加。

## 组件

| 端 | 文件 | 说明 |
| --- | --- | --- |
| 服务端 | `RadBroadcast-1.0.0.jar` | Paper 1.20.1 插件（放 `plugins/`） |
| 客户端 | `radbroadcast-client-1201-1.0.0.jar` | 1.20.1 Fabric 客户端 Mod（放 `mods/`，需 Fabric API） |

服务端与客户端通过 Fabric 插件消息通道 **`radb:play`** 通信。

## 安装

1. **服务端**（Paper 1.20.1）：`RadBroadcast-1.0.0.jar` 放进 `plugins/`，重启。
   - 可选：PlaceholderAPI（支持 `%占位符%`）、Multiverse-Core（支持世界别名）——不装也能用。
2. **客户端**：每位需要听到广播的玩家，在 1.20.1 Fabric 环境（Fabric Loader 0.15+、Fabric API 0.92+、Java 17+）装
   `radbroadcast-client-1201-1.0.0.jar`。没装 Mod 的玩家自动忽略，不影响服务器。
3. 需要 **OP**（或 `radbroadcast.use` 权限）才能执行 `/rad`。

## 命令

```
/rad play <x> <y> <z> <世界> <范围> <音频直链>       向世界内该点指定范围的玩家广播
/rad play <玩家|UUID|@a|@p|@r> <音频直链>           指定玩家播放（可 PAPI 占位符）
/rad play all <音频直链>                             全体在线玩家播放
```

示例：
```
/rad play 100 64 -200 map_world 50 https://cdn.example.com/alarm.mp3
/rad play %player_name% https://cdn.example.com/boss.mp3      (PAPI 占位符)
/rad play Steve https://cdn.example.com/hello.mp3
/rad play all https://cdn.example.com/announce.mp3
```

> 占位符说明：装了 PlaceholderAPI 后自动展开。由**玩家**执行命令时以该玩家为上下文
> （`%player_name%` 就是他自己）；由**控制台/MM 执行**时取第一位在线玩家为上下文，
> 因此推荐让命令带真实目标（见下）。

## MythicMobs 集成（怪物喊话）

怪物执行 skill 时，**`command` 机制会先解析 `<caster.*>` 占位符**，直接调用插件命令即可：

```yaml
BroadcastRoar:
  Skills:
  - command{c="rad play <caster.world> <caster.l.x> <caster.l.y> <caster.l.z> 20 https://cdn.example.com/roar.mp3"} @self
  - sound{s=SILENCE} @self   # (可选)让怪自己不“喊空”
```

含义：怪物所在位置 20 格内的玩家播放 `roar.mp3`。Skill 自带冷却，你的 MM 版本已有冷却配置的话，
一条 skill 触发一次播一次，正好符合需求。你原本的 vanilla `execute ... run` 写法同样可用。

**给特定怪物目标玩家播放**（如果想让被攻击者“单独听”），用：
```yaml
  - command{c="rad play <target.uuid> https://cdn.example.com/targeted.mp3"} @trigger
```

## 常见问题

- **听不到声音**：① 确认该玩家客户端装了 Mod + Fabric API；② 看服务端控制台是否打印
  `→ 播放指令已发送 xxx`；③ 音频直链在本机浏览器能直接打开、支持 https、格式为 MP3；
  ④ 区域指令的“世界名”要和服务端实际世界名一致（装了 Multiverse 也支持别名）。
- **权限**：非 OP 需要 `radbroadcast.use`。
- **音质/延迟**：首次加载网络缓冲约 0.3~1s；想秒开可用小体积 mp3（<1MB）。

## 构建

- 插件：`gradle build`（JDK 17，依赖 io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT）
- 客户端：Fabric Loom 1.7.4 / Gradle 8.10 / JDK 17+，源码见 `radbroadcast-client-src.zip`
