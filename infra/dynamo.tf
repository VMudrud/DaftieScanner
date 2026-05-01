resource "aws_dynamodb_table" "seen" {
  name         = "daftiescanner_seen"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "tenantId"
  range_key    = "listingId"

  attribute {
    name = "tenantId"
    type = "S"
  }

  attribute {
    name = "listingId"
    type = "N"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  tags = {
    Name = "daftiescanner-seen"
  }
}

resource "aws_dynamodb_table" "cursor" {
  name         = "daftiescanner_cursor"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "tenantId"

  attribute {
    name = "tenantId"
    type = "S"
  }

  tags = {
    Name = "daftiescanner-cursor"
  }
}

resource "aws_dynamodb_table" "alerts" {
  name         = "daftiescanner_alerts"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "alertKey"

  attribute {
    name = "alertKey"
    type = "S"
  }

  ttl {
    attribute_name = "ttl"
    enabled        = true
  }

  tags = {
    Name = "daftiescanner-alerts"
  }
}
