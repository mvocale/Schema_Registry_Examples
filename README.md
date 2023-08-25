# Schema_Registry_Examples
This repository contains usage examples of Confluent Schema Registry on Confluent Cloud infrastructure.

If you already have a Confluent Cloud environment with all the resources needed to test the examples you can walk through the subprojects and play with them.

Otherwise you can use the scripts provided in the folder _build-environment_ that will create all needed for you.

## Create environment
Before doing it you need to have installed the Confluent CLI and have credentials to access the Confluent Cloud.
Then, to build your Confluent Cloud environment, you simply need to set into the _build-environment_ folder and launch the command:

```
$ cd build-environment/src/main/resources/
$ ./create_environment.sh
```
This script will create, using Confluent CLI, all the resources needed to run the examples:
 - Environment;
 - Schema Registry (will be enabled and associated to your environment);
 - API Key and Secret for Schema Registry;
 - Kafka Cluster (BASIC type);
 - API Key and Secret for Kafka Cluster;
 - Service Account;
 - ksqlDB;

The script will also store the API Key and Secret into properties file that you will use to connect to Schema Registry and Kafka Cluster when you will execute the various examples.

## Destroy environment
All the examples are built to interact with Confluent Cloud. In order to contain your cost you should remove all the built resources at the end of your test.
You can easily do it running the command:

```
$ cd build-environment/src/main/resources/
$ ./destroy_environment.sh 
```
In this way all the resources created previously will be removed from Confluent Cloud
