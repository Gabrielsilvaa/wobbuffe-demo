#!/bin/bash
set -euo pipefail

DLQ_URL="$(awslocal sqs create-queue --queue-name wobbuffe-operations-dlq --query QueueUrl --output text)"
DLQ_ARN="$(awslocal sqs get-queue-attributes --queue-url "$DLQ_URL" --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)"
MAX_RECEIVE_COUNT="${WOBBUFFE_CONSUMER_MAX_RECEIVE_COUNT:-5}"
if ! [[ "$MAX_RECEIVE_COUNT" =~ ^[1-9][0-9]*$ ]]; then
  echo "WOBBUFFE_CONSUMER_MAX_RECEIVE_COUNT must be a positive integer" >&2
  exit 1
fi
REDRIVE_POLICY="$(printf '{"deadLetterTargetArn":"%s","maxReceiveCount":"%s"}' "$DLQ_ARN" "$MAX_RECEIVE_COUNT")"
QUEUE_URL="$(awslocal sqs create-queue --queue-name wobbuffe-operations --query QueueUrl --output text)"
ATTRIBUTES="$(python3 -c 'import json,sys; print(json.dumps({"RedrivePolicy": sys.argv[1]}))' "$REDRIVE_POLICY")"

awslocal sqs set-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attributes "$ATTRIBUTES"

# The worker overrides visibility on receive; this queue default is also safe
# for messages inspected or consumed by local tools during the demo.
awslocal sqs set-queue-attributes \
  --queue-url "$QUEUE_URL" \
  --attributes '{"VisibilityTimeout":"120"}'
