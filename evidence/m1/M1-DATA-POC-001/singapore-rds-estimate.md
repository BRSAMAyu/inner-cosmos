# Singapore RDS PostgreSQL minimum staging estimate

Price snapshot queried 2026-07-15 from the AWS public price list for `ap-southeast-1`, publication date `2026-07-10T19:40:47Z`. This is a planning estimate, not authorization to create resources.

| Item | Assumption | Public rate | Monthly estimate |
|---|---:|---:|---:|
| RDS PostgreSQL Single-AZ `db.t4g.micro` | 730 hours | USD 0.025/hour | USD 18.25 |
| gp3 storage | 20 GB minimum | USD 0.138/GB-month | USD 2.76 |
| Baseline total | before taxes, transfer, surplus CPU credits and backup overage | | **USD 21.01/month** |

Additional PostgreSQL backup storage is USD 0.095/GB-month. Automated backup storage up to the provisioned database storage is generally included while the instance is active; actual retention and snapshot usage must be checked before purchase. T4g surplus CPU credits are USD 0.075/vCPU-hour above baseline.

Operational assumptions:

- Region is Singapore (`ap-southeast-1`); production availability should use Multi-AZ even though this minimum staging estimate is Single-AZ.
- RDS storage encryption uses AWS KMS and covers storage, logs, automated backups, replicas and snapshots.
- Automated backups support point-in-time recovery with a configured retention window up to 35 days.
- pgvector version support must be rechecked against the selected RDS PostgreSQL engine patch before provisioning.

Official sources:

- AWS RDS for PostgreSQL pricing: https://aws.amazon.com/rds/postgresql/pricing/
- AWS public price list used for rates: https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/AmazonRDS/current/ap-southeast-1/index.json
- AWS Regions: https://docs.aws.amazon.com/global-infrastructure/latest/regions/aws-regions.html
- RDS encryption: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Overview.Encryption.html
- RDS backup retention: https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.BackupRetention.html

Human gate:

Creating RDS, selecting production Multi-AZ capacity, committing spend, choosing KMS ownership and setting final backup retention remain under `HG-PRODUCTION-ACCOUNTS`.
