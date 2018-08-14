FROM openjdk:8-jre-slim

RUN  apt-get update \
  && apt-get install -y wget \
                        curl \
  && rm -rf /var/lib/apt/lists/*

ENV QPID_VERSION=7.0.6
ENV QPID_HOME=/usr/local/qpid
ENV QPID_WORK=/var/lib/qpid/work
ENV PATH="${PATH}:${QPID_HOME}"
RUN mkdir -p $QPID_HOME && \
    mkdir -p $QPID_WORK
    
VOLUME $QPID_WORK

RUN wget -q http://www-eu.apache.org/dist/qpid/broker-j/$QPID_VERSION/binaries/apache-qpid-broker-j-$QPID_VERSION-bin.zip -O /tmp/apache-qpid-broker-j-$QPID_VERSION-bin.zip && \
   unzip -q /tmp/apache-qpid-broker-j-$QPID_VERSION-bin.zip -d /tmp/apache-qpid-broker-j-$QPID_VERSION-bin && \
   mv /tmp/apache-qpid-broker-j-$QPID_VERSION-bin/qpid-broker/${QPID_VERSION}/* ${QPID_HOME} && \
   rm -rf /tmp/apache-qpid-broker-j-$QPID_VERSION-bin.zip /tmp/apache-qpid-broker-j-$QPID_VERSION-bin

EXPOSE 5672
EXPOSE 80

#ENTRYPOINT ["ls", "$QPID_HOME"]
ENTRYPOINT "$QPID_HOME/bin/qpid-server" -prop "qpid.amqp_port=5672" -prop "qpid.http_port=80"
