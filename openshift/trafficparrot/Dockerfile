FROM debian:buster

ARG TRAFFIC_PARROT_ZIP
ARG ACCEPT_LICENSE

RUN test $ACCEPT_LICENSE = true

# Extract the Traffic Parrot release file into /opt/trafficparrot
WORKDIR /opt

ADD $TRAFFIC_PARROT_ZIP trafficparrot.zip
RUN apt-get update && apt-get install unzip -y && unzip trafficparrot.zip && rm trafficparrot.zip && mv trafficparrot-*-jre-* trafficparrot

# Change the group of /opt/trafficparrot to the root group and set group permissions the same as user permissions
# This allows OpenShift to run the image as any user in the root group and retain the intended user permissions
# See https://docs.openshift.com/container-platform/4.1/openshift_images/create-images.html#use-uid_create-images for more details on this approach
RUN chgrp -R 0 /opt/trafficparrot && chmod -R g=u /opt/trafficparrot

# Traffic Parrot should run in the foreground so that signals are respected for graceful shutdown
# You can override properties that appear in trafficparrot.properties here as key=value arguments
WORKDIR /opt/trafficparrot

RUN mkdir /opt/trafficparrot-files

CMD [ \
    "./start-foreground.sh", \
    "trafficparrot.gui.http.port=18080", \
    "trafficparrot.virtualservice.http.port=18081", \
    "trafficparrot.virtualservice.http.management.port=18083", \
    "trafficparrot.virtualservice.trafficFilesRootUrl=file:/opt/trafficparrot-files" \
]
