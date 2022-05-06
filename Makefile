PROJECT_NAME=resurface-aws-logger

start:
	@docker stop resurface || true
	@docker build -t aws-kds-listener --no-cache .
	@docker-compose up --detach

stop:
	@docker-compose stop
	@docker-compose down --volumes --remove-orphans
	@docker image rmi -f aws-kds-listener

bash:
	@docker exec -it resurfaceio-kinesis-listener bash

logs:
	@docker logs -f resurfaceio-kinesis-listener

restart:
	@docker-compose stop
	@docker-compose up --detach
