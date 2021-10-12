PROJECT_NAME=resurface-aws-logger

start:
	@docker stop resurface || true
	@docker build -t resurfaceio-kinesis-consumer --no-cache .
	@docker-compose up --detach

stop:
	@docker-compose stop
	@docker-compose down --volumes --remove-orphans
	@docker image rmi -f resurfaceio-kinesis-consumer

bash:
	@docker exec -it aws-kds-consumer bash

logs:
	@docker logs -f aws-kds-consumer

restart:
	@docker-compose stop
	@docker-compose up --detach
