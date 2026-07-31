variable "project_name" {
  description = "Lowercase name prefix for commercial Singapore resources."
  type        = string
  default     = "inner-cosmos"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,24}$", var.project_name))
    error_message = "project_name must be 3-25 lowercase alphanumeric or hyphen characters."
  }
}

variable "environment" {
  description = "Commercial environment name."
  type        = string
  default     = "production"

  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "environment must be staging or production."
  }
}

variable "aws_region" {
  description = "Commercial Singapore region. Academy uses a separate, pre-provisioned us-east-1 profile."
  type        = string
  default     = "ap-southeast-1"

  validation {
    condition     = var.aws_region == "ap-southeast-1"
    error_message = "commercial-sg must remain in ap-southeast-1."
  }
}

variable "availability_zones" {
  description = "Three Singapore availability zones, supplied explicitly so plans are deterministic."
  type        = list(string)
  default     = ["ap-southeast-1a", "ap-southeast-1b", "ap-southeast-1c"]

  validation {
    condition = length(var.availability_zones) == 3 && alltrue([
      for zone in var.availability_zones : startswith(zone, "ap-southeast-1")
    ])
    error_message = "Exactly three ap-southeast-1 availability zones are required."
  }
}

variable "vpc_cidr" {
  description = "Commercial VPC CIDR."
  type        = string
  default     = "10.40.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "One public ingress/NAT subnet per availability zone."
  type        = list(string)
  default     = ["10.40.0.0/24", "10.40.1.0/24", "10.40.2.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) == 3
    error_message = "Exactly three public subnet CIDRs are required."
  }
}

variable "private_subnet_cidrs" {
  description = "One private application/data subnet per availability zone."
  type        = list(string)
  default     = ["10.40.16.0/20", "10.40.32.0/20", "10.40.48.0/20"]

  validation {
    condition     = length(var.private_subnet_cidrs) == 3
    error_message = "Exactly three private subnet CIDRs are required."
  }
}

variable "kubernetes_version" {
  description = "EKS minor version; upgrade deliberately after workload and add-on compatibility review."
  type        = string
  default     = "1.35"
}

variable "operator_access_principal_arn" {
  description = "Owner-approved IAM role granted EKS cluster administrator access. Never hard-code an account identity."
  type        = string

  validation {
    condition     = can(regex("^arn:aws:iam::[0-9]{12}:role/.+", var.operator_access_principal_arn))
    error_message = "operator_access_principal_arn must be an IAM role ARN."
  }
}

variable "cluster_endpoint_public_access" {
  description = "Allow a public EKS API endpoint only for an explicitly approved, CIDR-restricted operator path."
  type        = bool
  default     = false
}

variable "cluster_endpoint_public_access_cidrs" {
  description = "Approved operator CIDRs when cluster_endpoint_public_access is true."
  type        = list(string)
  default     = []

  validation {
    condition = !var.cluster_endpoint_public_access || (
      length(var.cluster_endpoint_public_access_cidrs) > 0
      && alltrue([for cidr in var.cluster_endpoint_public_access_cidrs : cidr != "0.0.0.0/0"])
    )
    error_message = "A public EKS endpoint requires at least one restricted CIDR and forbids 0.0.0.0/0."
  }
}

variable "node_instance_types" {
  description = "Allowed on-demand managed-node instance types."
  type        = list(string)
  default     = ["m7i.large"]
}

variable "node_min_size" {
  type    = number
  default = 3
}

variable "node_desired_size" {
  type    = number
  default = 3
}

variable "node_max_size" {
  type    = number
  default = 9

  validation {
    condition     = var.node_min_size >= 3 && var.node_desired_size >= var.node_min_size && var.node_max_size >= var.node_desired_size
    error_message = "Commercial nodes must start with at least three instances and min <= desired <= max."
  }
}

variable "database_name" {
  type    = string
  default = "innercosmos"
}

variable "database_master_username" {
  type    = string
  default = "innercosmos_admin"
}

variable "database_instance_class" {
  type    = string
  default = "db.m7g.large"
}

variable "database_allocated_storage_gib" {
  type    = number
  default = 100

  validation {
    condition     = var.database_allocated_storage_gib >= 100
    error_message = "Commercial RDS storage must be at least 100 GiB."
  }
}

variable "database_backup_retention_days" {
  type    = number
  default = 14

  validation {
    condition     = var.database_backup_retention_days >= 7 && var.database_backup_retention_days <= 35
    error_message = "RDS backup retention must be between 7 and 35 days."
  }
}

variable "redis_node_type" {
  type    = string
  default = "cache.r7g.large"
}

variable "redis_auth_token" {
  description = "Operator-injected 16-128 character Valkey token. It is sensitive and must be supplied outside Git."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.redis_auth_token) >= 32 && length(var.redis_auth_token) <= 128
    error_message = "redis_auth_token must contain 32-128 characters."
  }
}

variable "deletion_protection" {
  description = "Protect commercial stateful resources from accidental deletion."
  type        = bool
  default     = true
}

variable "kubernetes_namespace" {
  description = "Namespace used by the commercial Kustomize overlay and EKS Pod Identity."
  type        = string
  default     = "inner-cosmos"
}

variable "kubernetes_service_account" {
  description = "Runtime service account associated with the least-privilege commercial role."
  type        = string
  default     = "inner-cosmos"
}

variable "alarm_topic_arns" {
  description = "Owner-provisioned SNS topics for production alarms. Empty actions are not accepted."
  type        = list(string)

  validation {
    condition = length(var.alarm_topic_arns) > 0 && alltrue([
      for arn in var.alarm_topic_arns : can(regex("^arn:aws:sns:ap-southeast-1:[0-9]{12}:.+", arn))
    ])
    error_message = "Provide at least one ap-southeast-1 SNS topic ARN for production alarms."
  }
}

variable "tags" {
  description = "Additional non-sensitive resource tags."
  type        = map(string)
  default     = {}
}
