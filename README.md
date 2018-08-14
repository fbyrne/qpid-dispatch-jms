# qpid-dispatch-jms
Testing Apache Qpid Dispatcher with JMS Client

```bat
docker-compose build
docker-compose -d up

docker exec broker1 curl --user admin --pass admin -X PUT  -d '{"durable":true}' http://localhost:80/api/latest/queue/default/default/requests
docker exec broker2 curl --user admin --pass admin -X PUT  -d '{"durable":true}' http://localhost:80/api/latest/queue/default/default/requests
docker exec dispatcher qdstat -c
docker exec dispatcher qdstat --autolinks
```

```bat
docker-compose stop
docker-compose start
docker-compose restart
docker exec 
```