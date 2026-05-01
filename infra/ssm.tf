locals {
  ssm_params = {
    "TENANTS_ACTIVE"                  = "REPLACE_ME"
    "TENANT_1_EMAIL"                  = "REPLACE_ME"
    "TENANT_1_ENABLED"                = "REPLACE_ME"
    "TENANT_1_SECTION"                = "REPLACE_ME"
    "TENANT_1_RENTAL_PRICE_MIN"       = "REPLACE_ME"
    "TENANT_1_RENTAL_PRICE_MAX"       = "REPLACE_ME"
    "TENANT_1_NUM_BEDS_MIN"           = "REPLACE_ME"
    "TENANT_1_NUM_BEDS_MAX"           = "REPLACE_ME"
    "TENANT_1_STORED_SHAPE_IDS"       = "REPLACE_ME"
    "AWS_REGION"                      = "eu-west-1"
    "DAFT_DYNAMO_ALERTS_TABLE"        = "daftiescanner_alerts"
    "DAFT_DYNAMO_SEEN_TABLE"          = "daftiescanner_seen"
    "DAFT_DYNAMO_CURSOR_TABLE"        = "daftiescanner_cursor"
  }
}

resource "aws_ssm_parameter" "app_config" {
  for_each = local.ssm_params

  name  = "${var.ssm_prefix}/${each.key}"
  type  = "String"
  value = each.value

  lifecycle {
    ignore_changes = [value]
  }

  tags = {
    Name = "daftiescanner-${lower(replace(each.key, "_", "-"))}"
  }
}
