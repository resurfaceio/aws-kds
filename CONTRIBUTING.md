# Contributing
## Run Containers Locally

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

## Run Containers as AWS ECS/Fargate instances

Click down below to deploy both containers as EC2 Instances and run them as a cloud-based solution

[![Launch AWS Stack](https://s3.amazonaws.com/cloudformation-examples/cloudformation-launch-stack.png)]()
