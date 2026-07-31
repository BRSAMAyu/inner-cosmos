mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
}

run "commercial_sg_secure_plan" {
  command = plan

  override_resource {
    target          = aws_sqs_queue.dead_letter
    override_during = plan
    values = {
      arn = "arn:aws:sqs:ap-southeast-1:123456789012:inner-cosmos-prod-dlq"
    }
  }

  override_resource {
    target          = aws_s3_bucket.objects
    override_during = plan
    values = {
      id  = "inner-cosmos-production-objects-mock"
      arn = "arn:aws:s3:::inner-cosmos-production-objects-mock"
    }
  }

  override_resource {
    target          = aws_kms_key.platform
    override_during = plan
    values = {
      arn = "arn:aws:kms:ap-southeast-1:123456789012:key/00000000-0000-0000-0000-000000000000"
    }
  }

  variables {
    operator_access_principal_arn = "arn:aws:iam::123456789012:role/inner-cosmos-platform-admin"
    redis_auth_token              = "MockOnly-Redis-Token-32-Characters"
    alarm_topic_arns              = ["arn:aws:sns:ap-southeast-1:123456789012:inner-cosmos-alerts"]
  }

  assert {
    condition     = var.aws_region == "ap-southeast-1"
    error_message = "commercial-sg must be fixed to the Singapore region."
  }

  assert {
    condition = (
      aws_eks_cluster.this.vpc_config[0].endpoint_private_access
      && !aws_eks_cluster.this.vpc_config[0].endpoint_public_access
    )
    error_message = "EKS must default to a private API endpoint."
  }

  assert {
    condition = (
      aws_db_instance.postgres.multi_az
      && aws_db_instance.postgres.storage_encrypted
      && aws_db_instance.postgres.deletion_protection
      && !aws_db_instance.postgres.publicly_accessible
      && aws_db_instance.postgres.backup_retention_period >= 7
    )
    error_message = "RDS must be private, encrypted, Multi-AZ, backed up and deletion-protected."
  }

  assert {
    condition = (
      aws_elasticache_replication_group.valkey.multi_az_enabled
      && aws_elasticache_replication_group.valkey.automatic_failover_enabled
      && aws_elasticache_replication_group.valkey.transit_encryption_enabled
      && aws_elasticache_replication_group.valkey.at_rest_encryption_enabled
      && aws_elasticache_replication_group.valkey.replicas_per_node_group >= 2
    )
    error_message = "Valkey must be encrypted and span at least three nodes with automatic failover."
  }

  assert {
    condition     = jsondecode(aws_sqs_queue.events.redrive_policy).deadLetterTargetArn == aws_sqs_queue.dead_letter.arn
    error_message = "The production event queue must route exhausted messages to its DLQ."
  }

  assert {
    condition = (
      length(aws_sqs_queue_policy.transport) == 2
      && aws_s3_bucket_policy.objects.bucket == aws_s3_bucket.objects.id
    )
    error_message = "SQS and S3 must attach policies that deny insecure transport."
  }

  assert {
    condition = (
      aws_s3_bucket_public_access_block.objects.block_public_acls
      && aws_s3_bucket_public_access_block.objects.block_public_policy
      && aws_s3_bucket_public_access_block.objects.ignore_public_acls
      && aws_s3_bucket_public_access_block.objects.restrict_public_buckets
      && aws_s3_bucket_versioning.objects.versioning_configuration[0].status == "Enabled"
    )
    error_message = "Object storage must block public access and retain versions."
  }

  assert {
    condition = (
      aws_launch_template.eks_nodes.metadata_options[0].http_tokens == "required"
      && !aws_launch_template.eks_nodes.network_interfaces[0].associate_public_ip_address
      && aws_launch_template.eks_nodes.block_device_mappings[0].ebs[0].encrypted
    )
    error_message = "EKS nodes must use IMDSv2, private networking and encrypted root volumes."
  }

  assert {
    condition = (
      aws_eks_pod_identity_association.runtime.namespace == "inner-cosmos"
      && aws_eks_pod_identity_association.runtime.service_account == "inner-cosmos"
    )
    error_message = "Runtime AWS access must use EKS Pod Identity for the scoped service account."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.dead_letter.alarm_actions) > 0
    error_message = "Production alarms must have at least one owner-provided action target."
  }

  assert {
    condition     = aws_cloudwatch_log_group.eks.kms_key_id == aws_kms_key.platform.arn
    error_message = "EKS control-plane logs must use the platform KMS key."
  }
}
