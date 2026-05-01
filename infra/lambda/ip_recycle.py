"""
Lambda: daftiescanner-ip-recycle
Triggered by SNS → CloudWatch alarm (block_detected).
Stops and starts the EC2 instance to obtain a new public IP.
"""
import os
import json
import boto3

INSTANCE_ID = os.environ["INSTANCE_ID"]
ec2 = boto3.client("ec2")


def handler(event, context):
    for record in event.get("Records", []):
        message = json.loads(record["Sns"]["Message"])
        alarm_name = message.get("AlarmName", "")
        new_state = message.get("NewStateValue", "")

        if "block_detected" in alarm_name and new_state == "ALARM":
            print(f"Block detected alarm fired. Recycling EC2 IP for instance {INSTANCE_ID}")
            ec2.stop_instances(InstanceIds=[INSTANCE_ID])
            waiter = ec2.get_waiter("instance_stopped")
            waiter.wait(InstanceIds=[INSTANCE_ID])
            ec2.start_instances(InstanceIds=[INSTANCE_ID])
            print(f"Instance {INSTANCE_ID} restarted — new public IP will be assigned")
        else:
            print(f"Ignoring SNS message: alarm={alarm_name} state={new_state}")
