output "profile" {
  value       = "commercial-sg"
  description = "Evidence boundary: this stack is never the AWS Academy profile."
}

output "region" {
  value       = var.aws_region
  description = "Commercial data-plane region."
}

output "vpc_id" {
  value = aws_vpc.this.id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "eks_cluster_name" {
  value = aws_eks_cluster.this.name
}

output "eks_cluster_endpoint" {
  value     = aws_eks_cluster.this.endpoint
  sensitive = true
}

output "application_image_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "postgres_endpoint" {
  value     = aws_db_instance.postgres.endpoint
  sensitive = true
}

output "postgres_master_secret_arn" {
  value     = aws_db_instance.postgres.master_user_secret[0].secret_arn
  sensitive = true
}

output "redis_primary_endpoint" {
  value     = aws_elasticache_replication_group.valkey.primary_endpoint_address
  sensitive = true
}

output "event_queue_url" {
  value     = aws_sqs_queue.events.url
  sensitive = true
}

output "event_dead_letter_queue_url" {
  value     = aws_sqs_queue.dead_letter.url
  sensitive = true
}

output "object_bucket_name" {
  value = aws_s3_bucket.objects.id
}

output "runtime_role_arn" {
  value = aws_iam_role.runtime.arn
}

output "runtime_secret_arns" {
  value     = { for name, secret in aws_secretsmanager_secret.runtime : name => secret.arn }
  sensitive = true
}
