@echo off
docker-compose up -d

timeout 20 > nul

docker exec -it broker1 curl -v --user admin:admin -X PUT -d \'{\"durable\":true}\' http://localhost/api/latest/queue/default/default/requests
docker exec -it broker2 curl -v --user admin:admin -X PUT -d \'{\"durable\":true}\' http://localhost/api/latest/queue/default/default/requests

docker exec -it dispatcher qdstat --links
