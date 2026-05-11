# solver-gto

[English README](./README.md)

这是一个基于 Spring Boot 和 Java 21 的德州扑克 Solver MVP，目前主要聚焦于单挑无上限德州扑克（Heads-up NLHE）的河牌圈求解，同时也提供了一个面向训练产品形态的 6 人桌练习 Demo。

项目当前分成两条主线：

- 数据库存储 + MyBatis 持久化的 Solver 主流程
- 面向前端交互演示的公开 API 与训练器 Demo

整体设计是“先做出能跑通、能演示、能继续扩展的骨架”，而不是一开始就伪装成完整商业级 Solver。

## 当前能力范围

- Spring Boot 后端应用
- MyBatis 访问 MySQL
- 按表名拆分 Mapper / Service
- 支持数据库建表初始化与样例任务求解
- 支持公开求解 API，直接传参数即可内存求解
- 支持基于公共牌、Hero 手牌、行动历史的范围推断求解
- 支持浏览器页面直接调用 API
- 支持一个参考 GTO Wizard 训练流程风格的 6-max 训练 Demo

## 技术栈

- Java 21
- Spring Boot
- MyBatis
- MySQL
- Maven
- 原生静态页面（`src/main/resources/static`）

## 启动前说明

项目默认数据库配置如下：

- host: `127.0.0.1`
- port: `3306`
- database: `solver-gto`
- user: `root`
- password: `jiang5368166`

如果你的本地 MySQL 配置不同，可以通过环境变量覆盖：

```bash
export SOLVER_GTO_DB_HOST=127.0.0.1
export SOLVER_GTO_DB_PORT=3306
export SOLVER_GTO_DB_NAME=solver-gto
export SOLVER_GTO_DB_USER=root
export SOLVER_GTO_DB_PASSWORD=jiang5368166
```

补充说明：

- 项目已经调整为“数据库不可用时也尽量允许应用启动”。
- 因此如果你只是想体验公开 API 或训练 Demo，没有立即连接 MySQL 也通常可以先把服务跑起来。
- 但数据库相关接口仍然需要可用的 MySQL 才能正常工作。

## 运行方式

启动 Spring Boot：

```bash
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

## 页面入口

### 1. 公开求解页面

启动后访问：

```text
http://localhost:8080/
```

这个页面可以：

- 直接填写范围参数并调用 `/api/public/solve`
- 在“基于行动历史推断”模式下调用 `/api/public/solve-from-history`

### 2. 训练 Demo 页面

启动后访问：

```text
http://localhost:8080/trainer-demo
```

这是一个参考 GTO Wizard Trainer 交互方式做出来的产品 Demo，重点是演示训练流程、打分逻辑和 UI 交互。

## 公开 API

### 1. 直接范围求解 API

接口：

```bash
POST /api/public/solve
```

示例请求：

```bash
curl -X POST http://localhost:8080/api/public/solve \
  -H 'Content-Type: application/json' \
  -d '{
    "gameType": "NLHE",
    "players": 2,
    "street": "RIVER",
    "pot": 100,
    "effectiveStack": 500,
    "board": ["As", "Ks", "Qh", "Jh", "2d"],
    "playerRange": "AQ+",
    "opponentRange": "AT+, KTs+, 66+",
    "betSizes": [50, 100, 200],
    "allinThreshold": 500
  }'
```

示例返回：

```json
{
  "actionFreq": {
    "CHECK": 0.2141,
    "BET_50": 0.3027,
    "BET_100": 0.2410,
    "BET_200": 0.1612,
    "ALLIN": 0.0810
  },
  "ev": {
    "CHECK": 9.8321,
    "BET_50": 11.1204,
    "BET_100": 10.7743,
    "BET_200": 10.1195,
    "ALLIN": 8.6610
  },
  "bestAction": "BET_50",
  "bestEv": 11.1204,
  "expl": 0.0184
}
```

说明：

- 当前公开求解接口主要支持 `NLHE + 2人 + RIVER`
- `actionFreq` 表示玩家整体范围在根节点上的动作频率分布
- `ev` 表示从根节点选择某个动作后、再按求解出的平均策略继续进行时的平均 EV
- `bestAction` 和 `bestEv` 分别表示当前最优动作和对应 EV
- `expl` 表示一个可用于理解 exploitability 的近似值

### 2. 基于行动历史推断的求解 API

接口：

```bash
POST /api/public/solve-from-history
```

适用场景：

- 已知公共牌
- 已知 Hero 手牌
- 已知双方行动过程
- 不知道对手精确范围

这个接口会先根据行动历史推断双方范围，再进行求解。

示例请求：

```bash
curl -X POST http://localhost:8080/api/public/solve-from-history \
  -H 'Content-Type: application/json' \
  -d '{
    "gameType": "NLHE",
    "players": 2,
    "street": "RIVER",
    "pot": 100,
    "effectiveStack": 500,
    "board": ["As", "Ks", "Qh", "Jh", "2d"],
    "heroHand": "AhQc",
    "heroPosition": "BB",
    "villainPosition": "BTN",
    "actionHistory": [
      "BTN open 2.5",
      "BB call",
      "flop BTN bet 33%",
      "BB call",
      "turn BTN bet 66%",
      "BB call",
      "river BTN jam"
    ],
    "betSizes": [50, 100, 200],
    "allinThreshold": 500
  }'
```

返回内容除了 `actionFreq`、`ev`、`bestAction`、`bestEv`、`expl` 外，还包括：

- `rootMode`：根节点模式，例如 `OPEN` 或 `FACING_BET`
- `inferredHeroRange`：推断出来的 Hero 范围
- `inferredOpponentRange`：推断出来的对手范围
- `assumptions`：本次推断所依赖的核心假设

## 训练 Demo

### 训练接口

后端接口：

- `POST /api/trainer/start`
- `POST /api/trainer/session/{sessionId}/action`

### Demo 支持的配置

- 6 人桌 NL 训练
- Hero 位置选择
- 对手人数选择
- 对手位置选择
- 起始阶段选择：`PREFLOP / FLOP / TURN / RIVER / RANDOM`
- 场景类型选择：
  - `随机`
  - `加注开池`
  - `面对加注`
  - `面对 3bet`
  - `面对 4bet`
  - `面对 5bet`
  - `面对加注-跟注`
  - `对抗挤压`
  - `面对溜入`
  - `面对隔离`
- 高级设置支持：
  - Hero 位置：`随机 / UTG / HJ / CO / BTN / SB / BB`
  - GTO 对手位置：`随机 / UTG / HJ / CO / BTN / SB / BB`
  - 相对位置：`随机 / IP / OOP`
- 手牌范围过滤
- 手牌类型过滤
- 听牌类型过滤
- 每个 Session 训练手数设置，默认 `10`

### 训练页面左侧统计

页面左侧会显示：

- 已训练手牌数
- 已执行行动数
- GTO 分数

细分统计包括：

- 最佳行动数
- 正确行动数
- 存疑的超低频行动数
- 错误行动数
- 巨大错误数

对应计分规则：

- 最佳行动：`+2`
- 正确行动：`+1`
- 存疑行动：`0`
- 错误行动：`-1`
- 巨大错误：`-2`

每次 Hero 做出动作后，系统会根据当前生成的“模拟 GTO 策略频率 + EV 档位”对该动作进行分类并累计分数。

### 重要说明

这个训练器 Demo 的定位是：

- 演示产品交互方式
- 演示训练 Session 的生成逻辑
- 演示 GTO 风格评分和反馈回路

它不是：

- 商业级 6-max 真 Solver
- 完整 CFR 树求解引擎
- 可替代真实 GTO Wizard 后端的数据系统

换句话说，这个 Demo 更接近“产品原型 + 可运行交互样机”，而不是“完整训练引擎”。

## 数据库相关接口

### 1. 初始化表结构

```bash
curl -X POST http://localhost:8080/api/schema/init
```

### 2. 注入一个样例河牌任务

```bash
curl -X POST http://localhost:8080/api/jobs/sample
```

### 3. 执行求解任务

```bash
curl -X POST http://localhost:8080/api/jobs/1/solve
```

示例返回：

```json
{
  "jobId": 1,
  "runId": 3,
  "status": "DONE",
  "iterations": 1200,
  "oopEv": 14.23,
  "ipEv": -14.23,
  "message": "OK",
  "strategyRowCount": 24
}
```

## 范围表达式说明

当前范围解析支持一些常见写法，例如：

- `AQ+`
- `KTs+`
- `ATo+`
- `66+`
- `AA`
- `AhKh`

同时已经兼容类似 `89+` 这种写法，并要求 rank 顺序为高到低，也就是 token 需要写成 `98+`、`T9+`、`A5s+` 这类格式，而不是低到高。

## 数据库表设计说明

本项目已经按表名拆分 Mapper / Service，便于后续扩展与维护。

数据库字段也已经补充中文注释，便于理解每个字段的业务含义。

如果你后续打算继续扩展，建议沿着下面的方式推进：

- 每张表单独一个 Mapper
- 每张表单独一个 Service
- DTO / Request / Response 与数据库实体解耦
- 求解逻辑、训练逻辑、范围推断逻辑分别拆分模块

## 项目定位与后续建议

这个项目当前更适合作为以下几类工作的起点：

- Heads-up 河牌 Solver MVP
- AI 德扑学习产品原型
- GTO 训练器的交互样机
- 手牌分析与弱点诊断系统的后端基础工程

如果后续要往你之前提到的 “Plan Shark” 方向演进，比较自然的下一步通常是：

1. 增加玩家手牌样本导入与聚合分析
2. 抽取玩家弱点标签，例如过度跟注、河牌 bluff 过少、面对 3bet 防守不足
3. 按弱点生成定向训练题组
4. 接入 AI 讲解模块，自动输出教学建议
5. 用真实解算数据替换当前训练 Demo 里的 mock 评分逻辑

## 一句话总结

如果你现在的目标是“先把一个能演示、能交互、能继续进化的 GTO 训练产品雏形跑起来”，这个项目已经具备一个不错的基础。
