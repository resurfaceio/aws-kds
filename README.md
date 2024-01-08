# resurfaceio-aws-kds
Easily log API requests and responses to your own <a href="https://resurface.io">security data lake</a>.

[![License](https://img.shields.io/github/license/resurfaceio/aws-kds)](https://github.com/resurfaceio/aws-kds/blob/master/LICENSE)
[![Contributing](https://img.shields.io/badge/contributions-welcome-green.svg)](https://github.com/resurfaceio/aws-kds/blob/master/CONTRIBUTING.md)

## Contents

- [Deployment](#deployment)
- [Configuration](#configuration)
- [Running on EKS](#running-on-eks)
- [Protecting User Privacy](#protecting-user-privacy)

## Deployment

### Automatic deployment
Click the **Launch Stack** button below to deploy all necessary resources as a [CloudFormation stack](https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/stacks.html):

[![Launch AWS Stack](https://s3.amazonaws.com/cloudformation-examples/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home#/stacks/create/review?stackName=resurface-api-gateway&templateURL=https%3A%2F%2Fresurfacetemplates.s3.us-west-2.amazonaws.com%2Flogger-kinesis-stack.json)

This uses [a custom template](https://github.com/resurfaceio/iac-templates/blob/master/aws/logger-kinesis-stack.json) to create and deploy a [Kinesis Data Streams instance](https://docs.aws.amazon.com/streams/latest/dev/introduction.html), a [CloudWatch log group](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/Working-with-log-groups-and-streams.html) with a [subscription filter](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/SubscriptionFilters.html), and all the corresponding _IAM_ roles and policies to stream CloudWatch logs from your API Gateway instance to a Kinesis Data Stream.

Once the automatic deployment finishes, go to the **Outputs** section.
<details>
  <summary> Click to expand</summary>
  
  ![image](https://user-images.githubusercontent.com/7117255/172840506-63846434-9395-41e4-9534-b92161486b6b.png)
</details>

Copy the listed values and update the [required environment variables](#logging-from-aws-kinesis) accordingly.
<details>
  <summary>Click to expand</summary>
  
  ![image](https://user-images.githubusercontent.com/7117255/172839889-7c6859c9-ff63-46ab-ac48-768695b4ef00.png)
</details>

### Manual setup

If you would like to configure everything yourself using the AWS console instead, just follow Resurface's [Capturing from AWS API Gateway get-started guide](https://resurface.io/aws-get-started#manual-setup), where the entire process is documented in a step-by-step manner.

## Configuration

- Set following the environment variables in your `.env` file:

| Variable | Set to |
|:---------|:-------|
|`KINESIS_STREAM_NAME`|Name of your Kinesis Data Stream instance. If you used our JSON template to deploy the stack, this should be `resurfaceio-kds-<<CloudFormation Stack ID>>`|
|`AWS_REGION`|Region where the Kinesis Data Stream is deployed.|
|`AWS_ACCESS_KEY_ID`|AWS Credentials (**Optional** if running the container on AWS)|
|`AWS_SECRET_ACCESS_KEY`|AWS Credentials (**Optional** if running the container on AWS)|
|`USAGE_LOGGERS_URL`|DB capture endpoint for your [Resurface instance](https://resurface.io/installation)|
|`USAGE_LOGGERS_RULES`|(**Optional**) Set of [rules](#protecting-user-privacy).<br />Only necessary if you want to exclude certain API calls from being logged.|

- (Optional) Build the container image

```bash
docker build -t aws-kds-consumer:1.1.0 .
```

- Run the container

```bash
docker run -d --name aws-kds --env-file .env resurfaceio/aws-kds-consumer:1.1.0
```

Or, if you built the image yourself in the previous step:

```bash
docker run -d --name aws-kds --env-file .env aws-kds-consumer:1.1.0
```

- Use your API as you always do. Go to the [Resurface UI](https://resurface.io/docs#api-explorer) and verify that API Calls are being captured.

## Running on EKS

Using [Helm](https://helm.sh/) you can deploy this listener application to your running Elastic Kubernetes Service (EKS) cluster

```bash
helm upgrade -i resurface resurfaceio/resurface --namespace resurface \
--set consumer.aws.enabled=true \
--set consumer.aws.kdsname=KINESIS_STREAM_NAME \
--set consumer.aws.region=AWS_REGION
```

You will akso need to add the following permissions to the role attached to your EKS cluster

```json
"kinesis:DescribeStream",
"kinesis:GetRecords",
"kinesis:GetShardIterator",
"kinesis:ListShards",
"dynamodb:Scan",
"dynamodb:CreateTable",
"dynamodb:DescribeTable",
"dynamodb:GetItem",
"dynamodb:PutItem",
"dynamodb:UpdateItem",
"dynamodb:DeleteItem",
"cloudwatch:PutMetricData"
```

## Running on Kubernetes

If you are not running on EKS, you will need to pass your AWS credentials:

```bash
helm upgrade -i resurface resurfaceio/resurface --namespace resurface \
--set consumer.aws.enabled=true \
--set consumer.aws.kdsname=KINESIS_STREAM_NAME \
--set consumer.aws.region=AWS_REGION \
--set consumer.aws.accesskeyid=AWS_ACCESS_KEY_ID \
--set consumer.aws.accesskeysecret=AWS_SECRET_ACCESS_KEY
```

## Protecting User Privacy

Loggers always have an active set of <a href="https://resurface.io/rules.html">rules</a> that control what data is logged
and how sensitive data is masked. All of the examples above apply a predefined set of rules (`include debug`),
but logging rules are easily customized to meet the needs of any application.

<a href="https://resurface.io/rules.html">Logging rules documentation</a>

---
<small>&copy; 2016-2024 <a href="https://resurface.io">Graylog, Inc.</a></small>
