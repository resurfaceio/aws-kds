PROJECT_NAME=resurface-aws-logger

start:
	@docker stop resurface || true
	@docker build -t aws-kds-consumer --no-cache .
	@docker-compose up --detach

stop:
	@docker-compose stop
	@docker-compose down --volumes --remove-orphans
	@docker image rmi -f aws-kds-consumer

bash:
	@docker exec -it aws-kds bash

logs:
	@docker logs -f aws-kds

restart:
	@docker-compose stop
	@docker-compose up --detach
