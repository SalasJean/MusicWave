# MusicWave local observability

The default Compose stack exposes Grafana on `http://localhost:3000` (`admin` / `musicwave`), Prometheus on `http://localhost:9090`, Loki on `http://localhost:3100`, Tempo on `http://localhost:3200`, and OTLP on ports `4317` and `4318`.

Spring Boot writes one ECS JSON record per console line. The OpenTelemetry Collector tails Docker's JSON log files and sends those records to Loki; it also receives application traces over OTLP/HTTP and forwards them to Tempo. Prometheus scrapes both Spring Boot applications and RabbitMQ. Grafana provisions all three data sources and the **MusicWave Overview** dashboard without manual setup.

Telemetry is deliberately best effort. Neither API nor worker depends on the collector, Prometheus, Loki, Tempo, or Grafana becoming ready, and exporter timeouts are short. Durable PostgreSQL, outbox, inbox, RabbitMQ, Redis, and object-storage behavior remains authoritative.

Do not expose the development Grafana credentials or telemetry ports publicly. Override every credential and place the services behind authenticated TLS before VPS deployment.
