locals {
  namespace = "DaftieScanner"
}

# SNS topic — receives all alarm notifications
resource "aws_sns_topic" "alerts" {
  name = "daftiescanner-alerts"
}

resource "aws_sns_topic_subscription" "alert_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

# Alarm: block_detected >= 1 in 5 minutes
resource "aws_cloudwatch_metric_alarm" "block_detected" {
  alarm_name          = "daftiescanner_block_detected"
  alarm_description   = "IP/session blocked by daft.ie — triggers EC2 IP recycle via Lambda"
  namespace           = local.namespace
  metric_name         = "block_detected"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
  ok_actions          = [aws_sns_topic.alerts.arn]
}

# Alarm: poll_errors >= 5 in 10 minutes
resource "aws_cloudwatch_metric_alarm" "poll_errors" {
  alarm_name          = "daftiescanner_poll_errors"
  alarm_description   = "Repeated poll errors — possible network or API change"
  namespace           = local.namespace
  metric_name         = "poll_errors"
  statistic           = "Sum"
  period              = 600
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}

# Alarm: poll silence — listings_found < 1 for 30 minutes
resource "aws_cloudwatch_metric_alarm" "poll_silence" {
  alarm_name          = "daftiescanner_poll_silence"
  alarm_description   = "No listings found for 30 minutes — possible silent block or crash"
  namespace           = local.namespace
  metric_name         = "listings_found"
  statistic           = "Sum"
  period              = 1800
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alerts.arn]
}
