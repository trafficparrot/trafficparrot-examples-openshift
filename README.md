# Using Traffic Parrot with OpenShift
These examples will show you how to use Traffic Parrot in an [OpenShift](https://www.openshift.com/) environment.

## Provision an OpenShift cluster
There are a number of ways you can do this, for example:
1. Locally using [Minishift](https://github.com/minishift/minishift)
1. Hosted in [Red Hat OpenShift Online](https://www.openshift.com/products/pricing/) (includes a free tier for personal use)
1. Hosted in [AWS](https://aws.amazon.com/quickstart/architecture/openshift/) (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))
1. Hosted on premise (requires a [Red Hat subscription](https://www.redhat.com/wapps/ugc/register.html))

## Install required tools
1. You will need the `oc` [client tool](https://www.okd.io/download.html#oc-platforms) to issue commands to the cluster
1. You will need [Docker](https://docs.docker.com/install/#supported-platforms) to be able to build and push custom Docker images to the registry

## Log in to the OpenShift cluster

### OpenShift Online
Find your login by clicking on "Command Line Tools in the web console:

![Alt text](images/openshift-command-line-tools.png?raw=true "Command Line Tools")

Then copy the token:

![Alt text](images/openshift-copy-token.png?raw=true "Command Line Tools")

And login to the console:
```
oc login https://api.<instance>.openshift.com --token=<token>
```

### Minishift
You can use the following commands to log in with the default system user:
```
oc login
system
admin
```

## Use Traffic Parrot to virtualize an OpenAPI specification as part of a CI/CD pipeline
We will work with the project `trafficparrot-test`

```
oc new-project trafficparrot-test
```

If you want to start again at any point, you can do:
```
oc delete project trafficparrot-test
```

Set a local variable that contains the cluster registry, for example:
* `CLUSTER_REGISTRY=registry.<instance>.openshift.com` for OpenShift online
* `CLUSTER_REGISTRY=$(minishift openshift registry)` for Minishift


### Create a Traffic Parrot image
Now let's log in to the registry so that we can build and push our Traffic Parrot image:
```
docker login -u developer -p $(oc whoami -t) ${CLUSTER_REGISTRY}
```

We build the image locally and tag it ready to be used in the cluster registry.

Note that you must set build arguments:
1. `TRAFFIC_PARROT_ZIP` is a HTTP location or a local file location. You can [download a trial copy](https://trafficparrot.com/download.html?src=trafficparrot-examples-openshift).
1. `ACCEPT_LICENSE` should be set to `true` if you accept the terms of the [LICENSE](LICENSE)

```
docker build \
--build-arg TRAFFIC_PARROT_ZIP=<fill this in> \
--build-arg ACCEPT_LICENSE=<fill this in> \
--tag trafficparrot:4.1.6 \
--file scripts/openshift/trafficparrot/Dockerfile .
```

Next, we tag and push to the cluster registry:
```
docker tag trafficparrot:4.1.6 ${CLUSTER_REGISTRY}/trafficparrot-test/trafficparrot-image
docker push ${CLUSTER_REGISTRY}/trafficparrot-test/trafficparrot-image
```

TODO this could all be done in the sample pipeline
### Spin up a Traffic Parrot app
First, we will generate a random name to use for the instance:
```
TRAFFIC_PARROT_ID=trafficparrot-${RANDOM}
```

Now we can use the `trafficparrot-image` that we created to create the app:
```
oc new-app --image-stream=trafficparrot-image --name=${TRAFFIC_PARROT_ID}
```

We will expose a service for the deployment, with the HTTP and HTTP management ports:
```
oc expose dc ${TRAFFIC_PARROT_ID} --port=18081,18083
```

For demo purposes, we will expose routes for these ports:
```
oc expose service ${TRAFFIC_PARROT_ID} --name=${TRAFFIC_PARROT_ID}-http --port=18081
oc expose service ${TRAFFIC_PARROT_ID} --name=${TRAFFIC_PARROT_ID}-http-management --port=18083
```
This is not required in the pipeline if everything is inside the cluster and can see the service.

### Use the REST API to import an OpenAPI specification
The HTTP management URL outside of the cluster is determined by the exposed route:
```
TRAFFIC_PARROT_HTTP_MANAGEMENT_URL=http://$(oc get route ${TRAFFIC_PARROT_ID}-http-management -o jsonpath='{.spec.host}')
```

We can upload an OpenAPI specification using the management API. In this example we are using `curl` but you can use any tool you want:
```
curl -F 'files[]=@scripts/openshift/finance/markit.yaml' ${TRAFFIC_PARROT_HTTP_MANAGEMENT_URL}/http/management/importMappings
```

The HTTP virtual service URL outside of the cluster is determined by the exposed route:
```
TRAFFIC_PARROT_HTTP_URL=http://$(oc get route ${TRAFFIC_PARROT_ID}-http -o jsonpath='{.spec.host}')
```

Then, we can check that the virtual service was created using the virtual service API. In this example we are using `curl` but you can use any tool you want:
```
curl -v ${TRAFFIC_PARROT_HTTP_URL}/MODApis/Api/v2/Quote/json
```

TODO this could all be done in the sample pipeline
### Spin the demo app up
TODO move to preamble
First we need the ability to build Java images:
```
oc create -f scripts/openshift/openjdk-s2i-imagestream.json
```

We will generate a random name to use for the instance:
```
DEMO_ID=finance-${RANDOM}
```

We also need to configure the demo app to point to the Traffic Parrot service we created. Note that we use the `TRAFFIC_PARROT_ID` as the DNS name of the service. We use a config map for this:
```
echo "finance-application.markit.url=http://${TRAFFIC_PARROT_ID}:18081/MODApis/Api/v2/Quote/json" > scripts/openshift/finance/finance-application.properties
oc create configmap ${DEMO_ID}-config --from-file=scripts/openshift/finance/finance-application.properties
```

Now we can tell OpenShift to build and deploy:
```
oc new-app --file=scripts/openshift/finance/template.json --param=APPLICATION_NAME=${DEMO_ID} --name=${DEMO_ID}
```

### Get the demo app to use Traffic Parrot
The demo app URL outside of the cluster is determined by the exposed route:
```
DEMO_URL=http://$(oc get route ${DEMO_ID} -o jsonpath='{.spec.host}')
```

Then, we can check that the demo application is able to talk to Traffic Parrot and return the mocked stock price:
```
curl -v ${DEMO_URL}/stock-quote-last-price
```

### Clean up
To clean up the demo app:
```
oc delete all -l "app=${DEMO_ID}"
oc delete configmap ${DEMO_ID}-config
```

To clean up Traffic Parrot:
```
oc delete all -l "app=${TRAFFIC_PARROT_ID}"
oc delete imagestream ${TRAFFIC_PARROT_ID}
```
