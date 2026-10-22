# TimedDataCollection 功能与接口清单

> 本文档统计项目现有的定时任务、查询服务、外部接口、取数口径与存储结构。
>
> **维护约定：以后每新增一个功能（定时任务 / 查询服务 / 对外接口 / 数据口径 / 配置项），都必须同步更新本文档，并在文末「变更记录」中追加一条。**

- 最后更新时间：2026-10-20
- 技术栈：Spring Boot + RestTemplate + JdbcTemplate
- Web 端口：`8080`
- 数据库：MySQL `jdbc:mysql://localhost:3306/zgdata`（`application.properties` 中保留 SQLite 切换配置）
- 时区：`ZoneId.systemDefault()`，部署环境应为 `Asia/Shanghai`

---

## 1. 代码结构

| 包 | 内容 |
|---|---|
| `controller` | 对外 HTTP 接口：手动触发采集、单次查询 |
| `scheduler` | 定时任务与限速器 |
| `service` | 外部数据查询服务、入库服务 |
| `dto` | 外部接口响应映射对象 |
| `demos` | Spring Boot 示例 Controller，与业务无关 |

启动类 `TimedDataCollectionApplication` 使用 `@EnableScheduling`，并注册全局 `RestTemplate` Bean。

---

## 2. 外部接口

项目共调用 3 个外部接口。鉴权信息统一放在 `application.properties`，本文不重复记录密钥。

### 2.1 能源指标采集接口

- 配置前缀：`timedata.api.url`
- 请求方式：`POST`
- 调用方：`DataCollectionScheduler`
- 请求体：

```json
{
  "timeDivision": "time_division,0,D",
  "dataTime": 1767196800000,
  "ids": ["xcenbb_01_wym_00_00_12", "..."]
}
```

- 说明：
  - 日数据 `timeDivision = time_division,0,D`，月数据 `time_division,0,M`。
  - `ids` 默认从 `ids.properties` 读取（内置 54 个 ID）；jar 同目录下的外部 `ids.properties` 优先。
- 响应结构（`CollectResponseDTO`）：

```json
{
  "status": true,
  "message": "...",
  "code": "...",
  "traceId": "...",
  "data": {
    "<dataId>": {
      "id": "...",
      "valueBigDecimal": 12.34,
      "value": "...",
      "valueTime": "...",
      "valueTimeDate": "...",
      "valueUpdateTime": "...",
      "dataQuality": "...",
      "timeDivision": "..."
    }
  }
}
```

### 2.2 筛选材料查询接口

- 配置前缀：`filtermaterial.api.url`
- 请求方式：`GET`
- 鉴权参数：`serviceId`、`serviceSecret`
- 业务参数：`queryStarttime`、`queryEndtime`（格式 `yyyy-MM-dd HH:mm:ss`）
- 调用方：`FilterMaterialQueryService`
- 响应结构（只读取 `data.data[]`）：

```json
{
  "data": {
    "data": [
      {
        "id": 1,
        "matCode": "...",
        "matName": "...",
        "fpWgtDev": "B401BC",
        "netWgt": 100.0,
        "strtWgt": 0.0,
        "curWgt": 0.0,
        "starttime": "...",
        "endtime": "...",
        "...": "其余字段见 FilterMaterialItemDTO"
      }
    ]
  }
}
```

### 2.3 焦炭材料流转历史查询接口

- 配置前缀：`cokematerial.api.url`
- 请求方式：`POST`
- 鉴权参数（URL Query）：`serviceId`、`serviceSecret`
- 调用方：`CokeMaterialQueryService`、`SinteredCokeQueryService`、`IronCokeQueryService`、`BlastFurnacePciQueryService`
- 通用请求体（各服务只改 `endPosList` / `startPosList` / `matCodeList`）：

```json
{
  "endPosList": [],
  "endTime": 1767283199000,
  "fullQuery": true,
  "sourceTypeList": ["SubFlow", "Sign", "ExtCar"],
  "startPosList": [],
  "split": false,
  "matCodeList": [],
  "startTime": 1767196800000,
  "statusList": ["Completed", "Start", "End"],
  "limit": 50000,
  "meteringEquipmentCodeList": ["RA_PDCa_B401", "RA_PDCa_B402"],
  "bizCode": "common",
  "strictCheckReset": true
}
```

- 响应结构（只读取 `data[]`）：

```json
{
  "data": [
    {
      "id": "...",
      "transportWeight": 100.0,
      "matCode": "...",
      "matName": "...",
      "startPos": "...",
      "startPosDescr": "...",
      "endPos": "...",
      "endPosDescr": "...",
      "meteringEquipmentCode": "...",
      "meteringEquipmentDescr": "...",
      "startTime": "...",
      "endTime": "...",
      "sourceType": "...",
      "status": "...",
      "...": "其余字段见 CokeMaterialItemDTO"
    }
  ]
}
```

---

## 3. 查询服务

所有服务均位于 `service` 包。

| 服务类 | 用途 | 上游接口 | 输出 |
|---|---|---|---|
| `FilterMaterialQueryService` | 筛选材料净重汇总 | 2.2 | `Map<类型名, netWgt 合计>` |
| `CokeMaterialQueryService` | 焦炭材料流转重量汇总 | 2.3 | `transportWeight` 合计 |
| `SinteredCokeQueryService` | 烧结焦炭重量汇总 | 2.3 | `transportWeight` 合计 |
| `SinteredCoalQueryService` | 烧结煤重量汇总 | 2.3 | `transportWeight` 合计 |
| `IronCokeQueryService` | 炼铁焦炭重量汇总 | 2.3 | 两组查询的 `transportWeight` 合计之和 |
| `BlastFurnacePciQueryService` | 高炉喷煤重量汇总 | 2.3 | 按物料编码分别返回 `transportWeight` 合计 |
| `DataStorageService` | 原始指标入库 | - | 写 `day` / `month` 表 |
| `JmStorageService` | 合计指标入库 | - | 写 `jm_day` / `jm_month` 表 |

### 3.1 FilterMaterialQueryService

- 请求条件：时间范围 + `materialType`。
- 过滤逻辑：
  1. 取 `data.data[]`；
  2. 只保留 `fpWgtDev` 为 `B401BC` 或 `B402BC` 的记录；
  3. 按 `matName` 关键字筛选并累加 `netWgt`。
- `materialType=1`（焦炭）：`matName` 含「焦炭」→ `{焦炭: 合计}`。
- `materialType=2`（喷煤）：`matName` 含「高挥发」→ `烟煤`；含「低硫」→ `无烟煤`。

### 3.2 CokeMaterialQueryService（焦炭）

- 查询条件：
  - `startPosList = ["CK1_LC_JC_A1", "CK1_LC_JC_B1"]`
  - `meteringEquipmentCodeList = ["CK1_PDCa_J15"]`
  - `endPosList = []`、`matCodeList = []`
- 计算：汇总所有记录的 `transportWeight`。

### 3.3 SinteredCokeQueryService（烧结焦炭）

- 查询条件：
  - `endPosList = ["ST1_LC_RP"]`
  - `meteringEquipmentCodeList = ["RA_PDCa_B401", "RA_PDCa_B402"]`
  - 不限制 `matCodeList`
- 计算：汇总所有记录的 `transportWeight`。
- 说明：「烧结焦炭」身份由查询条件决定，返回后不再按 `matCode` / `matName` 二次校验。

### 3.4 SinteredCoalQueryService（烧结煤）

- 查询条件：
  - `endPosList = ["ST1_LC_RP"]`
  - `matCodeList = ["1230000008"]`
  - `meteringEquipmentCodeList = ["RA_PDCa_B401", "RA_PDCa_B402"]`
- 计算：汇总所有记录的 `transportWeight`。
- 说明：与烧结焦炭使用同一终点、同一计量设备，靠 `matCode=1230000008` 区分。

### 3.5 IronCokeQueryService（炼铁焦炭）

- 同一接口发两次请求，再相加：
  - 第 1 组：`matCodeList = ["SDZJ202601130001", "SDZJ202601130002"]`
  - 第 2 组：`matCodeList = ["5310001223"]`
- 两次请求均使用 `meteringEquipmentCodeList = ["RA_PDCa_B401", "RA_PDCa_B402"]`、`endPosList = []`。
- 任一组抛异常则整体失败，避免写入不完整合计。

### 3.6 BlastFurnacePciQueryService（高炉喷煤）

- 共同条件：
  - `endPosList = ["BF1_LC_PM1", "BF1_LC_PM2", "BF1_LC_PM3", "BF1_LC_PM4", "BF1_LC_PM5"]`
  - `meteringEquipmentCodeList = ["RA_PDCa_B401", "RA_PDCa_B402"]`
- 高炉高挥发喷煤：`matCode = 1220000008`
- 高炉低硫贫瘦喷煤：`matCode = 1220000015`
- 两种数据分别独立查询、独立入库，互不相加。

---

## 4. 定时任务

cron 为 Spring 6 位格式：`秒 分 时 日 月 周`。默认调度线程池为单线程，任务实际串行执行。

| 定时任务类 | cron | 执行时间 | 回采范围 | 写入表 |
|---|---|---|---|---|
| `DataCollectionScheduler#scheduledCollect` | `0 30 0 * * ?` | 每天 00:30 | 昨天起往前 `timedata.collect.days=365` 天 | `day` |
| `DataCollectionScheduler#scheduledMonthlyCollect` | `0 30 0 1 * ?` | 每月 1 号 00:30 | 昨月起往前 `timedata.collect.months=12` 个月 | `month` |
| `DayDataSumScheduler#scheduledCollectDaySum` | `0 5 0 * * ?` | 每天 00:05 | 昨天起往前 365 天 | `jm_day` |
| `MonthDataSumScheduler#scheduledCollectMonthSum` | `0 0 0 1 * ?` | 每月 1 号 00:00 | 昨月起往前 12 个月 | `jm_month` |
| `CollectRateLimiter#pause` | - | 每次采集之间 | 固定延迟 `timedata.collect.delay-ms=200` 毫秒 | - |

行为说明：

- 所有回采任务逐日/逐月执行，已存在记录覆盖写入；单日/单月失败只记日志，不中断整体回采。
- `DataCollectionScheduler` 采集的是原始指标（按 `ids.properties` 的 ID），普通采集 `isOld=0`，老 ID 接口采集 `isOld=1`。
- `DayDataSumScheduler` 每天 8 项指标：焦炭、烟煤、无烟煤、烧结焦炭、烧结煤、炼铁焦炭、高炉高挥发喷煤、高炉低硫贫瘦喷煤。
- `MonthDataSumScheduler` 不是一次请求查整月，而是逐日采集后累加，再以当月 1 号 0 点时间戳入库。
- 月任务每天执行 8 项指标采集（共 9 次上游 HTTP 请求），月任务逐日循环，整体耗时较长。

---

## 5. 对外 HTTP 接口

| 方法 | 路径 | 用途 | 参数 |
|---|---|---|---|
| GET | `/update/time/{time}` | 手动采集某天原始日数据 → `day` | `time`：当天 0 点毫秒时间戳 |
| GET | `/update/timemonth/{time}` | 手动采集某月原始月数据 → `month` | `time`：当月 1 号 0 点毫秒时间戳 |
| GET | `/update/time/old/{time}/{oldId}` | 老 ID 日数据采集 → `day`，`isOld=1` | `time`、`oldId` |
| GET | `/update/timemonth/old/{time}/{oldId}` | 老 ID 月数据采集 → `month`，`isOld=1` | `time`、`oldId` |
| GET | `/jm/day/{time}` | 手动采集某天合计数据 → `jm_day` | `time`：当天 0 点毫秒时间戳 |
| GET | `/jm/month/{time}` | 手动采集某月合计数据 → `jm_month` | `time`：当月 1 号 0 点毫秒时间戳 |
| GET | `/cokematerial/totalWeight` | 查询焦炭材料 `transportWeight` 合计（不写库） | `startTime`、`endTime`（毫秒） |
| GET | `/filtermaterial/netWeight` | 查询筛选材料 `netWgt` 合计（不写库） | `startTime`、`endTime`、`materialType`（1=焦炭，2=喷煤） |

以上接口统一返回：

```json
{
  "success": true,
  "message": "...",
  "...": "业务字段"
}
```

---

## 6. 取数口径

| 入库 `type` | 日数据计算方式 | 说明 |
|---|---|---|
| `焦炭` | 筛选材料 `materialType=1` 的「焦炭」`netWgt` + 焦炭材料查询 `transportWeight` | 两路相加 |
| `烟煤` | 筛选材料 `materialType=2` 中 `matName` 含「高挥发」的 `netWgt` | 上游"高挥发"映射为烟煤 |
| `无烟煤` | 筛选材料 `materialType=2` 中 `matName` 含「低硫」的 `netWgt` | 上游"低硫"映射为无烟煤 |
| `烧结焦炭` | 焦炭材料查询（`endPos=ST1_LC_RP`，设备 `RA_PDCa_B401/B402`）`transportWeight` | 不限制物料编码 |
| `烧结煤` | 焦炭材料查询（`endPos=ST1_LC_RP`，`matCode=1230000008`，设备 `RA_PDCa_B401/B402`）`transportWeight` | 与烧结焦炭同终点，靠物料编码区分 |
| `炼铁焦炭` | 焦炭材料查询两组 `matCodeList` 各自合计后相加 | 两组分别请求 |
| `高炉高挥发喷煤` | 焦炭材料查询（`endPos=BF1_LC_PM1~PM5`，`matCode=1220000008`）`transportWeight` | 独立入库 |
| `高炉低硫贫瘦喷煤` | 焦炭材料查询（`endPos=BF1_LC_PM1~PM5`，`matCode=1220000015`）`transportWeight` | 独立入库 |

时间范围约定：

- 日数据：`startTime` = 当天 00:00:00.000，`endTime` = 当天 23:59:59.000。
- 月数据：逐日按上述范围采集后累加。

---

## 7. 数据库表结构

所有表由代码 `CREATE TABLE IF NOT EXISTS` 自动创建，表名/字段名使用 `timedata.db.quote-char`（MySQL 为 `` ` ``）。

### 7.1 `day` / `month`（原始指标）

| 字段 | 类型 | 说明 |
|---|---|---|
| `selectTime` | BIGINT | 查询时间戳（日/月 0 点），联合主键 |
| `readableTime` | VARCHAR(255) | 可读时间 |
| `dataId` | VARCHAR(255) | 指标 ID，联合主键 |
| `id` | VARCHAR(255) | 上游数据 ID |
| `valueBigDecimal` | DECIMAL(30,12) | 数值 |
| `value` | VARCHAR(255) | 原始值 |
| `valueTime` | VARCHAR(255) | 数据时间 |
| `valueTimeDate` | VARCHAR(255) | 数据日期 |
| `valueUpdateTime` | VARCHAR(255) | 更新时间 |
| `dataQuality` | VARCHAR(50) | 数据质量 |
| `timeDivision` | VARCHAR(255) | 时间维度 |
| `isOld` | TINYINT | 是否老 ID 数据，覆盖时取新旧较大值，只增不减 |

主键：`(selectTime, dataId)`。

### 7.2 `jm_day` / `jm_month`（合计指标）

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | VARCHAR(50) | 指标类型（见第 6 节），联合主键 |
| `total` | DOUBLE | 合计值 |
| `time` | BIGINT | 时间戳，联合主键 |
| `readableTime` | VARCHAR(255) | 可读时间 |

主键：`(time, type)`。写入逻辑为先查后插/更新，已存在则覆盖 `total`。

---

## 8. 配置项

`src/main/resources/application.properties`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | `8080` | 服务端口 |
| `spring.datasource.*` | MySQL `zgdata` | 数据源，可切换 SQLite |
| `timedata.db.quote-char` | `` ` `` | SQL 标识符引号 |
| `timedata.api.url` | 能源指标接口 | 原始指标采集 |
| `timedata.time-division` | `time_division,0,D` | 日数据时间维度 |
| `timedata.collect.days` | `365` | 日回采天数 |
| `timedata.collect.months` | `12` | 月回采月数 |
| `timedata.collect.delay-ms` | `200` | 采集间隔 |
| `filtermaterial.api.*` | - | 筛选材料接口地址与鉴权 |
| `cokematerial.api.*` | - | 焦炭材料流转接口地址与鉴权 |
| `ids.properties` | 内置 54 个 ID | jar 同目录外部文件优先 |

---

## 9. 回填脚本

位于 `src/main/resources/`，需先启动应用（默认 `http://localhost:8080`）：

| 脚本 | 作用 | 调用接口 |
|---|---|---|
| `backfill_data.py` | 回填指定日期范围原始日/月数据 | `/update/time/{ts}`、`/update/timemonth/{ts}` |
| `backfill_old_data.py` | 回填老 ID 日/月数据（`isOld=1`） | `/update/time/old/{ts}/{oldId}`、`/update/timemonth/old/{ts}/{oldId}` |
| `backfill_jm_data.py` | 回填指定日期范围合计日/月数据 | `/jm/day/{ts}`、`/jm/month/{ts}` |

---

## 10. 已知行为与注意事项

- 查询服务的空响应或 `data` 不是数组时返回 `0`，接口异常可能被表现为"数据为 0"。
- `焦炭` 为两路上游数据相加，口径变化时需同时确认两路数据。
- 原始采集任务（00:30）与合计任务（00:05）各自独立回采，互不依赖。
- 默认调度器为单线程，长耗时回采任务会串行占用调度线程。

---

## 11. 变更记录

| 日期 | 变更内容 |
|---|---|
| 2026-10-20 | 初版：统计现有 3 个外部接口、5 个查询服务、4 个定时任务、8 个业务 HTTP 接口、7 项指标口径及表结构 |
| 2026-10-20 | 新增 `SinteredCoalQueryService`（烧结煤）：`endPos=ST1_LC_RP`、`matCode=1230000008`、设备 `RA_PDCa_B401/B402`；已接入日/月合计定时任务，入库类型 `烧结煤` |
