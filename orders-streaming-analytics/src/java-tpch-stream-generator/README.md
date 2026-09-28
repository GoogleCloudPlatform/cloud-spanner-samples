# Java TPCH Stream Generator

- A Kafka producer that publishes TPC-H Orders in the official pipe-delimited format.
- Supports flexible message constraints:
  - **Max messages:** Stop producing and exit after a specific message count.
  - **Target throughput:** Throttling in messages/second.
  - **Date filtering & relative windows:** Limit records to a date range (`--date-from`, `--date-to`) using ISO dates (`YYYY-MM-DD`) or relative expressions (e.g., `"1 month ago"`, `"today"`).
  - **Automatic date rebasing:** Dynamically scales historical TPC-H dates (1992–1998) into modern date windows automatically.

```bash
Usage: tpch-generator.jar [-hV] [--local] --bootstrap-servers=<bootstrapServers>
           [--date-from=<dateFromRaw>] [--date-to=<dateToRaw>]
           --max-messages=<maxMessages> --target-throughput=<targetThroughput>
           --topic=<topic>
TPC-H Orders Kafka Producer
      --bootstrap-servers=<bootstrapServers>
                        Kafka bootstrap servers
      --date-from=<dateFromRaw>
                        Start date range (e.g., '1995-01-01', '1 month ago',
                          '7 days ago')
      --date-to=<dateToRaw>
                        End date range (e.g., '1995-12-31', 'today'). Defaults
                          to today if date-from is specified.
  -h, --help            Show this help message and exit.
      --local           Use local Kafka without authentication
      --max-messages=<maxMessages>
                        Maximum number of messages to publish
      --target-throughput=<targetThroughput>
                        Target throughput in messages per second. Use 0 for
                          unlimited.
      --topic=<topic>   Kafka topic
  -V, --version         Print version information and exit.

```

---

### Date Filtering & Rebasing

The `--date-from` and `--date-to` options accept both **ISO dates** (`YYYY-MM-DD`) and **relative expressions**:

* **Relative formats:** `"1 day ago"`, `"2 weeks ago"`, `"1 month ago"`, `"5 years ago"`, `"today"`, `"yesterday"`.
* **Default behavior:** If `--date-from` is provided without `--date-to`, `--date-to` defaults to `today`.

#### How Date Processing Works Automatically:

1. **Native Filtering:** If your date range falls strictly within the TPC-H benchmark bounds (**`1992-01-01` to `1998-08-02**`), messages are filtered without altering the dataset timestamps.
2. **Automatic Rebasing:** If your requested date range extends outside the 1992–1998 window (such as relative ranges like `--date-from="1 month ago"`), the producer automatically projects and scales the historical TPC-H timestamps directly into your target window.

---

### Usage Docker

#### Relative Modern Date Window (Auto-Rebased)

```bash
docker build -t tpch-generator:local .

docker run --rm tpch-generator:local \
  --local \
  --bootstrap-servers=localhost:9092 \
  --max-messages=1000 \
  --target-throughput=100 \
  --topic=tpch-orders \
  --date-from="1 month ago" \
  --date-to="today"

```

#### Historical Date Range (Native TPC-H Filtering)

```bash
docker run --rm tpch-generator:local \
  --local \
  --bootstrap-servers=localhost:9092 \
  --max-messages=1000 \
  --target-throughput=100 \
  --topic=tpch-orders \
  --date-from="1995-01-01" \
  --date-to="1995-06-30"

```
