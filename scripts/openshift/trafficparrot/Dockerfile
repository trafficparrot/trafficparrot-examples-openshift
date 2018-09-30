FROM openjdk:8u181-jre

ARG TRAFFIC_PARROT_ZIP
ARG ACCEPT_LICENSE

RUN test $ACCEPT_LICENSE = true

WORKDIR /opt

ADD $TRAFFIC_PARROT_ZIP trafficparrot.zip
RUN unzip trafficparrot.zip && rm trafficparrot.zip && mv trafficparrot-enterprise-release-* trafficparrot
WORKDIR /opt/trafficparrot

# create standard mappings directories so that they are read-write even if e.g. a config map is mounted
RUN mkdir mappings
RUN mkdir __files
RUN mkdir grpc-mappings
RUN mkdir proto-bin
RUN mkdir proto

# https://docs.openshift.com/container-platform/3.10/creating_images/guidelines.html#use-uid
RUN chgrp -R 0 /opt/trafficparrot && chmod -R g+rwX /opt/trafficparrot

CMD ["./start-foreground.sh", "trafficparrot.gui.http.port=18080", "trafficparrot.virtualservice.http.port=18081", "trafficparrot.virtualservice.http.management.port=18083"]
