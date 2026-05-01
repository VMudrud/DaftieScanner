data "archive_file" "ip_recycle" {
  type        = "zip"
  source_file = "${path.module}/lambda/ip_recycle.py"
  output_path = "${path.module}/lambda/ip_recycle.zip"
}

# IAM role for Lambda
resource "aws_iam_role" "ip_recycle_lambda" {
  name = "daftiescanner-ip-recycle-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "ip_recycle_ec2" {
  name = "daftiescanner-ip-recycle-ec2"
  role = aws_iam_role.ip_recycle_lambda.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ec2:StopInstances", "ec2:StartInstances", "ec2:DescribeInstances"]
      Resource = "arn:aws:ec2:${var.aws_region}:*:instance/${var.instance_id}"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ip_recycle_basic_exec" {
  role       = aws_iam_role.ip_recycle_lambda.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Lambda function
resource "aws_lambda_function" "ip_recycle" {
  function_name    = "daftiescanner-ip-recycle"
  role             = aws_iam_role.ip_recycle_lambda.arn
  filename         = data.archive_file.ip_recycle.output_path
  source_code_hash = data.archive_file.ip_recycle.output_base64sha256
  handler          = "ip_recycle.handler"
  runtime          = "python3.12"
  timeout          = 120 # stop+wait+start can take ~60s

  environment {
    variables = {
      INSTANCE_ID = var.instance_id
    }
  }
}

# Allow SNS to invoke the Lambda
resource "aws_lambda_permission" "sns_invoke" {
  statement_id  = "AllowSNSInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.ip_recycle.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.alerts.arn
}

# SNS subscription — only trigger Lambda on block_detected alarm
resource "aws_sns_topic_subscription" "ip_recycle_lambda" {
  topic_arn     = aws_sns_topic.alerts.arn
  protocol      = "lambda"
  endpoint      = aws_lambda_function.ip_recycle.arn

  filter_policy = jsonencode({
    AlarmName = [{ prefix = "daftiescanner_block_detected" }]
  })
}
