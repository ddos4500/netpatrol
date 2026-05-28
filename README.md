# 网络设备自动巡检工具

一个面向 Windows 运维场景的 Java 桌面巡检程序第一版。

## 功能

- 导入 Excel `.xlsx` 设备清单
- 手动添加、编辑、删除设备
- 手动执行巡检
- 支持检测方式：
  - `PING`
  - `PING_TCP`
  - `TCP`
  - `HTTP`
  - `ONVIF`
  - `SNMP`
- 表格展示巡检结果
- 导出 PDF 巡检报告
- 大批量设备推荐导出 JSON / HTML / CSV 报告，速度明显快于 PDF
- 手动添加交换机端口速率监测项
- SNMP v1/v2c 读取标准 IF-MIB 端口速率
- 端口速率等于 `100Mbps` 时发送 MQTT JSON 告警
- 手动添加自定义 SNMP OID 读取项
- 支持多个每天固定时间执行的定时任务
- 支持多个间隔循环任务，每个任务可单独设置巡检内容和间隔分钟数
- 循环任务手动启动后一直运行，直到手动停止或程序退出
- 内置本地 Web JSON 报告服务，页面每 5 秒自动刷新最新结果
- 巡检历史按日期保存到本机用户目录
- SQLite 数据库存储设备、端口、任务、MQTT配置和历史JSON
- MQTT 心跳，每 10 分钟向手动配置的心跳 Topic 发送 JSON
- MQTT 多主题规则：完整JSON、deviceSummary、离线设备简化数组、掉百兆告警、心跳可分别推送到不同 Topic
- 可选 MySQL 存储后端，连接失败时自动回退到 SQLite
- 关闭窗口后隐藏到系统托盘，定时任务继续运行

## 运行要求

- Windows
- JDK 17 或更高版本
- Maven 3.9 或更高版本

当前项目使用 Maven 管理依赖：

- `snmp4j`
- `org.eclipse.paho.client.mqttv3`
- `org.xerial:sqlite-jdbc`
- `com.mysql:mysql-connector-j`

## 编译运行

```bat
build.bat
run.bat
```

`build.bat` 会调用 Maven 打包，生成：

```text
target\netpatrol-1.2.0.jar
```

## 端口速率监测

在“端口监测”页手动添加交换机端口：

```text
交换机名称
IP地址
区域
SNMP版本：1 或 2c
团体字
匹配方式：ifName / ifDescr / ifIndex
端口标识：例如 GigabitEthernet1/0/1，或 ifIndex 数字
告警速率Mbps：默认 100
备注
```

程序读取标准 IF-MIB，优先使用：

```text
ifHighSpeed
```

读取失败或无值时回退：

```text
ifSpeed
```

当端口速率等于 `100Mbps`，或等于你配置的告警速率时，定时任务每次发现都会发送 MQTT 告警。

## MQTT 告警

在“MQTT配置”页填写：

```text
服务器
端口
用户名
密码
告警/汇总Topic
心跳Topic
```

不使用 TLS。告警/汇总消息格式为 JSON，包含任务名、交换机名称、IP、区域、端口名、ifIndex、当前速率、告警阈值、告警时间等字段。
程序会复用 MQTT 长连接，配置变化或连接断开后才重新连接。

心跳消息每 10 分钟发送一次，格式为：

```json
{
  "event": "netpatrol_heartbeat",
  "status": "alive",
  "time": "yyyy-MM-dd HH:mm:ss"
}
```

## SQLite 数据库

数据库文件位置：

```text
主程序目录\data\netpatrol.db
```

当前会存储：

```text
设备列表
端口监测配置
自定义SNMP OID配置
定时任务配置
MQTT配置
MQTT主题规则
巡检历史JSON
```

旧版 `%USERPROFILE%\.netpatrol\netpatrol.db` 会在首次启动时自动复制到主程序目录下的 `data\netpatrol.db`。旧版 `%USERPROFILE%\.netpatrol\config.properties` 会在首次启动时自动迁移到 SQLite。

## MySQL 数据库

在“数据库配置”页可以选择：

```text
SQLite
MySQL
```

MySQL 连接字段：

```text
主机
端口
数据库名
用户名
密码
```

选择 MySQL 后，设备、端口、SNMP OID、定时任务、MQTT配置、MQTT主题规则和历史JSON会写入 MySQL。若 MySQL 连接失败，程序会回退到主程序目录下的 SQLite。

## 定时任务

在“定时任务”页可以添加两类任务：

```text
DAILY：每天固定时间执行
INTERVAL：手动启动后按间隔分钟循环执行，直到手动停止或程序退出
```

每个任务可以单独选择巡检内容：

```text
设备在线巡检
端口速率监测
自定义SNMP OID
```

循环任务默认间隔为 `5` 分钟。时间格式：

```text
HH:mm
```

关闭窗口时，程序会隐藏到系统托盘并继续运行定时任务。需要彻底退出时，在托盘图标菜单选择“退出”。

## JSON 报告服务

程序启动后会开启本地 Web 服务，默认地址：

```text
http://127.0.0.1:8765/
```

如果 8765 被占用，会依次尝试 8766 到 8775。网页内容为格式化 JSON，并每 5 秒自动刷新。也可以直接调用：

```text
http://127.0.0.1:8765/latest
http://127.0.0.1:8765/api/latest
```

服务监听所有网卡，可以通过内网地址访问，例如：

```text
http://192.168.x.x:8765/
```

如果内网地址无法访问，请检查 Windows 防火墙是否放行对应端口。

历史 JSON 按日期保存在：

```text
主程序目录\data\history
```

## MQTT 主题规则

在“MQTT主题规则”页可以为不同消息类型配置多个 Topic：

```text
FULL_JSON：完整 JSON 巡检报告
DEVICE_SUMMARY：只发送 deviceSummary 字段
OFFLINE_DEVICES：发送离线设备简化数组，只包含 name、ip、area、checkTime
PORT_ALERT：掉百兆告警
HEARTBEAT：心跳
```

如果没有配置对应规则，完整报告、掉百兆告警和心跳会继续使用“MQTT配置”页的旧 Topic 字段，兼容旧配置。

## 打包成 Windows exe

推荐安装完整 JDK 17 或更高版本，然后设置 `JAVA_HOME`。需要的是完整 JDK，不是 JRE，也不是 IntelliJ 自带的精简运行时。

### 方式一：免安装 exe 应用目录

```bat
package-app-image.bat
```

生成结果：

```text
dist\app-image\NetPatrol\NetPatrol.exe
```

把整个 `NetPatrol` 文件夹复制到其他 Windows 电脑即可运行，目标电脑不需要单独安装 Java。

### 方式二：安装包 exe

需要先安装 WiX Toolset，然后执行：

```bat
package-installer-exe.bat
```

生成结果在：

```text
dist\installer
```

## 快速生成模板

桌面软件内可以点击“导出Excel模板”。也可以在编译后执行：

```bat
java -cp out com.netpatrol.app.CreateTemplate 设备清单模板.xlsx
```

## Excel 字段

第一行必须是表头，支持以下字段：

```text
设备名称
设备类型
IP地址
所属区域
检测方式
检测端口
SNMP版本
SNMP团体字
HTTP地址
ONVIF端口
用户名
密码
备注
```

必填字段：

```text
设备名称
设备类型
IP地址
检测方式
```

设备类型建议：

```text
交换机
录像机
摄像头
其他
```

检测方式建议：

```text
PING
PING_TCP
TCP
HTTP
ONVIF
SNMP
```

## 说明

这是可运行的第一版骨架。为了适配当前环境，桌面界面使用 Java Swing，Excel 与 PDF 使用项目内置轻量实现。后续可以平滑升级为 JavaFX + Maven，并替换为 Apache POI、PDFBox、SNMP4J、ONVIF SDK 等成熟库。
