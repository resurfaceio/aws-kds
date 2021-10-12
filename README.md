# resurfaceio-aws-kds
Easily log API requests and responses to your own [system of record](https://resurface.io/).

## Requirements

* docker
* docker-compose
* an Amazon Web Services subscription might be required in order to use AWS Kinesis and AWS API Gateway

## Ports Used

* 4002 - Resurface API Explorer
* 4001 - Resurface microservice
* 4000 - Trino database UI

## Setup

In order to run Resurface for AWS, some previous configuration is needed. Click the **Launch Stack** button below to deploy all necessary resources as a _CloudFormation stack_:

[![Launch AWS Stack](https://s3.amazonaws.com/cloudformation-examples/cloudformation-launch-stack.png)](https://console.aws.amazon.com/cloudformation/home#/stacks/create/review?stackName=resurface-api-gateway&templateURL=https%3A%2F%2Fresurfacetemplates.s3.us-west-2.amazonaws.com%2Fresurfacestack.json)

This creates and deploys a _Kinesis Data Stream_ instance, a _CloudWatch_ log group with a subscription filter, and all the corresponding _IAM_ roles and policies.

## Deploy to AWS

Click down below to deploy both containers as EC2 Instances and run them as a cloud-based solution

// Coming soon!

## Deploy Locally

Clone this repository to run the containers as an on-prem solution

```
make start     # rebuild and start containers
make bash      # open shell session
make logs      # follow container logs
make stop      # halt and remove containers
```

<a name="logging_from_aws_kinesis"/>

## Logging From AWS Kinesis

- If you are running the containers locally, you need to set following the environment variables in the [`.env`](https://github.com/resurfaceio/azure-eh/blob/master/.env) file to their corresponding values before doing `make start`:

| Variable                    | Set to                                                                                                                                          |
|:----------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------|
|`KINESIS_STREAM_NAME`        |Name of your Kinesis Data Stream instance. If you used our JSON template to deploy the stack, |
|`AWS_REGION`                 |Partition number configured in `policy.xml`. Should be `"0"` by default                                                                          |
|`USAGE_LOGGERS_URL`          |(**Optional**) Resurface database connection URL.<br />Only necessary if your [Resurface instance](https://resurface.io/installation) uses a different connection URL than the one provided by default   |
|`USAGE_LOGGERS_RULES`        |(**Optional**) Set of [rules](#protecting-user-privacy).<br />Only necessary if you want to exclude certain API calls from being logged.         |

- Use your API as you always do. Enjoy! 

<a name="privacy"/>

## Protecting User Privacy

Loggers always have an active set of <a href="https://resurface.io/rules.html">rules</a> that control what data is logged
and how sensitive data is masked. All of the examples above apply a predefined set of rules (`include debug`),
but logging rules are easily customized to meet the needs of any application.

<a href="https://resurface.io/rules.html">Logging rules documentation</a>

---
<small>&copy; 2016-2021 <a href="https://resurface.io">Resurface Labs Inc.</a></small>
