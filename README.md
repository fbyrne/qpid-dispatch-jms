# qpid-dispatch-jms
Testing Apache Qpid Dispatcher with JMS Client

Instructions
1. Start the docker containers
```sh
docker-compose build
docker-compose -d up
```
2. Create the requests queue on broker1 (http://localhost:18080) and broker2 (http://localhost:28080).  Creation of the queues using the method below is not working.  I just create using the web management console.
```sh
docker exec broker1 curl --user admin:admin -X PUT  -d '{"durable":true}' http://localhost:80/api/latest/queue/default/default/requests
docker exec broker2 curl --user admin:admin -X PUT  -d '{"durable":true}' http://localhost:80/api/latest/queue/default/default/requests
```
3. Check the links are ok
```
docker exec dispatcher qdstat -c
docker exec dispatcher qdstat --autolinks
```
4. Build and run the test
```sh
>M2_HOME=/usr/local/maven/maven-3.5.0
>PATH=$PATH:$M2_HOME/bin
>JAVA_HOME=/usr/local/java/jdk1.8.0_131-x64
>export M2_HOME PATH JAVA_HOME
>cd jms-test
>mvn test -e
```
5. View the logs
```sh
docker-compose logs -f -tail 100
```
6. Update the dispatcher configuration
```sh
docker exec -it dispatcher bash
vi /var/lib/qdrouter/etc/qdrouter/qdrouter-custom.conf
exit
docker-compose restart dispatcher
```
