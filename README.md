# resurfaceio-aws-kds
Easily capture entire API requests and responses to your own [data lake](https://resurface.io/).

## Requirements

* docker
* an Amazon Web Services subscription might be required in order to use AWS Kinesis, AWS CloudWatch, and AWS API Gateway

## Ports Used

* 7700 - Resurface API Explorer & Trino database UI
* 7701 - Resurface microservice

## Setup

In order to run Resurface for AWS, some previous configuration is needed.

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

<a name="logging-from-aws-kinesis"/>

## Streaming data From AWS Kinesis to Resurface

- Set following the environment variables in your `.env` file:

| Variable | Set to |
|:---------|:-------|
|`KINESIS_STREAM_NAME`|Name of your Kinesis Data Stream instance. If you used our JSON template to deploy the stack, this should be `resurfaceio-kds-<<CloudFormation Stack ID>>`|
|`AWS_REGION`|Region where the Kinesis Data Stream is deployed.|
|`AWS_ACCESS_KEY_ID`|AWS Credentials|
|`AWS_SECRET_ACCESS_KEY`|AWS Credentials|
|`USAGE_LOGGERS_URL`|DB capture endpoint for your [Resurface instance](https://resurface.io/installation)|
|`USAGE_LOGGERS_RULES`|(**Optional**) Set of [rules](#protecting-user-privacy).<br />Only necessary if you want to exclude certain API calls from being logged.|

- (Optional) Build the container image

```bash
docker build -t aws-kds-consumer:1.0.1 .
```

- Run the container

```bash
docker run -d --name aws-kds --env-file .env resurfaceio/aws-kds-consumer:1.0.1
```

Or, if you built the image yourself in the previous step:

```bash
docker run -d --name aws-kds --env-file .env aws-kds-consumer:1.0.1
```

- Use your API as you always do. Go to the [API Explorer](https://resurface.io/docs#api-explorer) of your Resurface instance and verify that API Calls are being captured.

<a name="run-on-eks"/>

## Run Containers on Elastic Kubernetes Service (EKS)

Using [Helm](https://helm.sh/) you can deploy this listener application to your running cluster

```bash
helm upgrade -i resurface resurfaceio/resurface --namespace resurface \
--set consumer.azure.enabled=true \
--set consumer.aws.kdsname=KINESIS_STREAM_NAME \
--set consumer.aws.region=AWS_REGION \
--set consumer.aws.accesskeyid=AWS_ACCESS_KEY_ID \
--set consumer.aws.accesskeysecret=AWS_SECRET_ACCESS_KEY
```

<a name="run-locally"/>

## (Dev/Test) Run Containers Locally

Clone this repository to run the containers as an on-prem solution.
You will need to [install `docker-compose`](https://docs.docker.com/compose/install/) in addition to the requirements listed above.

```bash
git clone https://github.com/resurfaceio/aws-kds.git
cd aws-kds
make start
```

Additional commands:

```bash
make start     # rebuild and start containers
make bash      # open shell session
make logs      # follow container logs
make stop      # halt and remove containers
```

<a name="run-on-aws"/>

## (Dev/Test) Run Containers as AWS ECS/Fargate instances

Click down below to deploy both containers as EC2 Instances and run them as a cloud-based solution

[![Launch AWS Stack](https://s3.amazonaws.com/cloudformation-examples/cloudformation-launch-stack.png)]()

<a name="privacy"/>

## Protecting User Privacy

Loggers always have an active set of <a href="https://resurface.io/rules.html">rules</a> that control what data is logged
and how sensitive data is masked. All of the examples above apply a predefined set of rules (`include debug`),
but logging rules are easily customized to meet the needs of any application.

<a href="https://resurface.io/rules.html">Logging rules documentation</a>

---
<small>&copy; 2016-2022 <a href="https://resurface.io">Resurface Labs Inc.</a></small>
