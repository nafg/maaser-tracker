# Maaser Tracker

A full-stack application for tracking financial transactions and calculating Maaser (Jewish tithing) obligations.

## Overview

Maaser Tracker helps users manage their tithing obligations by:
- Connecting to bank accounts through Plaid API
- Tracking income and expenses across multiple accounts
- Automatically identifying income that requires tithing
- Calculating Maaser obligations (10% of eligible income)
- Tracking Maaser payments and maintaining a running balance

## Architecture

The project follows a modern full-stack architecture:

```
maaser-tracker/
├── jvm/                # Backend (Scala/Http4s)
│   └── src/main/scala/maasertracker/server/
│       ├── PlaidHttp4sServer.scala  # Http4s server implementation
│       └── package.scala            # Server utilities & configurations
├── js/                 # Frontend (Scala.js)
│   └── src/main/scala/maasertracker/js/
│       └── TransactionsView.scala   # UI components
├── shared/             # Shared code between frontend and backend
│   └── src/main/scala/maasertracker/
│       ├── Codecs.scala  # JSON codec implementations
│       └── models.scala  # Data models
├── migrations/         # Database migration scripts
│   └── src/main/resources/db/migration/
│       └── V*.sql        # Flyway migration scripts
└── docker/             # Docker-related files
```

### Technology Stack

#### Backend (JVM)
- Scala 2.13
- Http4s (HTTP server)
- Circe (JSON processing)
- Slick (Database access)
- Flyway (Database migrations)
- Plaid Java API (Financial data)
- Neon Postgres (Database)

#### Frontend (JS)
- Scala.js (Scala to JavaScript compilation)
- ScalaJS-React (React bindings)
- Ant Design (UI components via antd-scalajs-react)
- Monocle (Optics for state management)
- Webpack (Asset bundling)

#### Development/Deployment
- SBT (Build tool)
- Tmuxp (Development environment)

## Database Schema

The application uses Neon Postgres with the following schema:

### Tables

Database schema is defined in SQL migration files located in `migrations/src/main/resources/db/migration/`. The project uses a custom code generation approach via source generators defined in `build.sbt`:

- SQL schema definitions are automatically transformed into:
  - Case classes (e.g., `MatchRuleRow`) in `shared/.jvm/target/scala-2.13/src_managed/main/models.scala`
  - Slick entity table modules (e.g., `MatchRuleTable`) in `jvm/target/scala-2.13/src_managed/main/Tables.scala`

The main database tables include:

#### match_rule
Defines rules for categorizing transactions.
```sql
CREATE TABLE match_rule (
    id                BIGSERIAL PRIMARY KEY,
    kind              TEXT NOT NULL,
    is_transaction_id TEXT NULL,
    is_description    TEXT NULL,
    is_institution    TEXT NULL,
    is_category       TEXT NULL,
    min_amount        DECIMAL NULL,
    max_amount        DECIMAL NULL
);
```

#### initial_balance
Stores the starting Maaser balance.
```sql
CREATE TABLE initial_balance (
    id     BIGSERIAL PRIMARY KEY,
    "date" DATE NOT NULL,
    amount DECIMAL NOT NULL
);
```

#### plaid_institution
Stores information about financial institutions.
```sql
CREATE TABLE plaid_institution (
    id             BIGSERIAL PRIMARY KEY,
    institution_id TEXT NOT NULL,
    name           TEXT NOT NULL
);
```

#### plaid_item
Stores Plaid access tokens for connected bank accounts.
```sql
CREATE TABLE plaid_item (
    id           BIGSERIAL PRIMARY KEY,
    item_id      TEXT NOT NULL,
    access_token TEXT NOT NULL,
    institution  BIGINT NOT NULL REFERENCES plaid_institution (id)
);
```

## Running the Application

### Prerequisites
- JDK 11+
- SBT
- Node.js and npm

### Setup and Running

1. **Database Configuration**:
   The application uses [Neon](https://neon.tech/) - a serverless PostgreSQL database service.
   Configuration is specified in `jvm/src/main/resources/application.conf`:
   ```
   db.slick {
     properties = {
       databaseName = maasertracker
       serverName = "<neon-server-address>"
       portNumber = 5432
       user = "<username>"
       password = "<password>"
       sslmode = require
     }
   }
   ```
   You'll need to set up your own Neon database or modify the configuration to use a different database.

2. **Run the Backend**:
   ```bash
   sbt ~jvm/reStart
   ```
   This starts the backend server on port 9090 with hot-reloading enabled.

3. **Run the Frontend**:
   ```bash
   sbt '~; js/fastOptJS/startWebpackDevServer; js/fastOptJS/webpack'
   ```
   This starts the webpack dev server on port 8081.

4. **Combined Command (Alternative)**:
   ```bash
   sbt dev
   ```
   This runs both the backend and frontend with a single command.

### Using tmuxp (Recommended for developers)

The project includes a tmuxp configuration for easy development:

```bash
tmuxp load .tmuxp.yaml
```

This will:
- Start a new tmux session with multiple panes
- Launch the backend server with hot-reloading
- Start the frontend webpack dev server

The application will be accessible at:
- Frontend: http://localhost:8081
- Backend API: http://localhost:9090

## Features

- **Bank Connection**: Connect to bank accounts via Plaid
- **Transaction Categorization**: Automatically categorize transactions
- **Income Tracking**: Track income that requires Maaser (tithing)
- **Maaser Calculation**: Calculate 10% obligation on eligible income
- **Balance Maintenance**: Keep track of Maaser obligations and payments
- **Downloadable Reports**: Export transaction data

## Plaid Integration

The application uses Plaid to connect to bank accounts and retrieve transaction data. It supports:
- Account linking via Plaid Link
- Transaction history retrieval
- Real-time transaction updates

## License

[Add appropriate license information here]
