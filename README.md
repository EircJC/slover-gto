# solver-gto

[中文文档](./README.zh-CN.md)

This is a Spring Boot based Java 21 solver MVP for heads-up no-limit hold'em river spots.

It is intentionally scoped so it can actually run locally:

- Inputs are stored in MySQL tables.
- The solver reads one job, runs CFR on the configured action tree, and writes strategy rows back to MySQL.
- Current scope: 2 players, fixed river board, fixed action tree, weighted combo ranges.

## MySQL connection

Defaults:

- host: `127.0.0.1`
- port: `3306`
- database: `solver-gto`
- user: `root`
- password: `jiang5368166`

If your local MySQL uses a different socket or port, override with environment variables:

```bash
export SOLVER_GTO_DB_HOST=127.0.0.1
export SOLVER_GTO_DB_PORT=3306
export SOLVER_GTO_DB_NAME=solver-gto
export SOLVER_GTO_DB_USER=root
export SOLVER_GTO_DB_PASSWORD=jiang5368166
```

## Run

Start the Spring Boot application:

```bash
mvn spring-boot:run
```

Default HTTP port is `8080`.

## API

### Public solve API

This API solves an ad-hoc spot directly in memory and does not require you to pre-create a database job.

Endpoint:

```bash
POST /api/public/solve
```

Example request:

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

Example response:

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

Notes:

- Current public API scope is `NLHE + 2 players + RIVER`.
- Range notation currently supports common forms such as `AQ+`, `KTs+`, `ATo+`, `66+`, `AA`, and exact combos like `AhKh`.
- Returned `actionFreq` is the aggregated root-node strategy frequency for the player range.
- Returned `ev` is the average EV of taking each root action and then continuing with the solved average strategy.

### History-based API

This mode accepts only your exact hand plus the action history, then infers both ranges before solving.

Endpoint:

```bash
POST /api/public/solve-from-history
```

Example request:

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

This endpoint returns:

- `actionFreq`, `ev`, `bestAction`, `bestEv`, `expl`
- `rootMode`, which is either `OPEN` or `FACING_BET`
- `inferredHeroRange` and `inferredOpponentRange`
- `assumptions`, which list the main model assumptions used by the range inferrer

### Web page

After starting the app, open:

```text
http://localhost:8080/
```

The page lets you fill in the same request fields in a browser and calls `/api/public/solve` for you.
Use the mode selector on the page to switch between direct range solving and history-based inference.

### Trainer demo

The 6-max trainer demo page is available at:

```text
http://localhost:8080/trainer-demo
```

Trainer demo APIs:

- `POST /api/trainer/start`
- `POST /api/trainer/session/{sessionId}/action`

This demo supports:

- 6-max NL trainer table
- Hero position selection
- Opponent count and position selection
- Start stage selection: preflop / flop / turn / river / random
- Scenario families such as open raise, facing raise, facing 3bet, facing 4bet, facing 5bet, raise-call, squeeze, limp, and isolation
- Relative position and hand archetype filters
- Session-based scoring with `+2 / +1 / 0 / -1 / -2`

Important note:

- This is a trainer-style product demo with a mock GTO scoring engine and generated spots.
- It is designed to demonstrate the product interaction loop and UI flow, not to replace a production 6-max solver.

### Database-backed APIs

Initialize the schema:

```bash
curl -X POST http://localhost:8080/api/schema/init
```

Seed a sample river spot:

```bash
curl -X POST http://localhost:8080/api/jobs/sample
```

Run a job:

```bash
curl -X POST http://localhost:8080/api/jobs/1/solve
```

Example response:

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

## Scope notes

This MVP solves a river subgame only. It is the right foundation for a real solver, but it is not yet a full preflop-to-river engine. The input/output schema is designed so the tree abstraction can be expanded later without changing the solver contract.
# slover-gto
