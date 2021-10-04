PROJECT_NAME=resurface-aws-logger

start:
	@docker stop resurface || true
	@docker build -t kinesis-resurface-consumer --no-cache .
	@docker-compose up --detach

stop:
	@docker-compose stop
	@docker-compose down --volumes --remove-orphans
	@docker image rmi -f kinesis-resurface-consumer

bash:
	@docker exec -it kds-resurface-consumer bash

logs:
	@docker logs -f kds-resurface-consumer

restart:
	@docker-compose stop
	@docker-compose up --detach
