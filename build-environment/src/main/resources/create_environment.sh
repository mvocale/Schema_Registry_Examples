confluent login --prompt

### Create the environment needed for my examples ###
ENVIRONMENT_NAME="Schema-Registry-Environment"
printf 'Create a new Confluent Cloud environment %s\n' "$ENVIRONMENT_NAME"
echo "confluent environment create $ENVIRONMENT_NAME -o json"
if ! OUTPUT=$(confluent environment create $ENVIRONMENT_NAME -o json) ; then
  echo "ERROR: Failed to create environment $ENVIRONMENT_NAME"
  exit 1
fi
echo "$OUTPUT" | jq .

ENVIRONMENT_ID=$(echo "$OUTPUT" | jq -r ".id")
printf 'Specify %s as the active environment\n' "$ENVIRONMENT_ID"
echo "confluent environment use $ENVIRONMENT_ID"
confluent environment use "$ENVIRONMENT_ID"

### Create and set the Schema Registry environment needed for my examples ###
printf 'Create a new Schema Registry environment\n'
echo "confluent schema-registry cluster enable --cloud aws --geo eu --package essentials --environment ""$ENVIRONMENT_ID"" -o json"
if ! OUTPUT=$(confluent schema-registry cluster enable --cloud "aws" --geo "eu" --package "essentials" --environment "$ENVIRONMENT_ID" -o json) ; then
  echo "ERROR: Failed to create schema registry."
  exit 1
fi
  echo "$OUTPUT" | jq .

SCHEMA_REGISTRY_ID=$(echo "$OUTPUT" | jq -r ".id")

### I store the API key and secret into a properties file that I used to populate the maven settings needed to interact with Schema Registry ###
confluent api-key create --resource "$SCHEMA_REGISTRY_ID" --description "Schema Registry credentials" --output json | jq -r 'to_entries[]|"\(.key)=\(.value)"' > schema_registry_api_key_secret.properties

### I create the Kafka cluster needed for my examples ###
KAFKA_CLUSTER_NAME="Schema-Registry-Cluster"
printf 'Create a new Confluent Cloud Kafka Cluster %s\n' "$KAFKA_CLUSTER_NAME"
echo "confluent kafka cluster create $KAFKA_CLUSTER_NAME --cloud aws --region eu-south-1 --environment $ENVIRONMENT_ID"
if ! OUTPUT=$(confluent kafka cluster create $KAFKA_CLUSTER_NAME --cloud "aws" --region "eu-south-1" --environment "$ENVIRONMENT_ID" -o json) ; then
  echo "ERROR: Failed to create Kafka Cluster $KAFKA_CLUSTER_NAME"
  exit 1
fi
  echo "$OUTPUT" | jq .

KAFKA_CLUSTER_ID=$(echo "$OUTPUT" | jq -r ".id")
confluent kafka cluster use "$KAFKA_CLUSTER_ID"

### I store the API key and secret into a properties file that I used to populate the maven settings needed to interact with Kafka Cluster  ###
confluent api-key create --resource "$KAFKA_CLUSTER_ID" --description "Schema Registry Kafka Cluster credentials" --output json | jq -r 'to_entries[]|"\(.key)=\(.value)"' > kafka_cluster_api_key_secret.properties

### I create the service account needed for ksqlDB ###
SERVICE_ACCOUNT_NAME="Schema-Registry-Service-Account"
CCLOUD_EMAIL=$(confluent prompt -f '%u')
printf 'Create Service Account %s\n' "$SERVICE_ACCOUNT_NAME"
echo "confluent iam service-account create $SERVICE_ACCOUNT_NAME --description SA for Schema Registry Examples run by $CCLOUD_EMAIL -o json"
if ! OUTPUT=$(confluent iam service-account create "$SERVICE_ACCOUNT_NAME" --description "SA for Schema Registry Examples run by $CCLOUD_EMAIL"  -o json) ; then
  echo "ERROR: Failed to create Service Account $SERVICE_ACCOUNT_NAME"
  exit 1
fi
  echo "$OUTPUT" | jq .

SERVICE_ACCOUNT_ID=$(echo "$OUTPUT" | jq -r ".id")

### I propagate the role binding ###
confluent iam rbac role-binding create --principal User:"$SERVICE_ACCOUNT_ID" --role EnvironmentAdmin --environment "$ENVIRONMENT_ID" -o json
printf 'Waiting for role-binding to propagate\n'
sleep 30

### I create the ksqlDB cluster needed for my examples ###
KSQLDB_NAME="Schema-Registry-ksqlDB"
printf 'Create ksqlDB %s\n' "$KSQLDB_NAME"
echo "confluent ksql cluster create $KSQLDB_NAME --credential-identity $SERVICE_ACCOUNT_ID --csu 1 --cluster $KAFKA_CLUSTER_ID --environment $ENVIRONMENT_ID -o json"
if ! OUTPUT=$(confluent ksql cluster create "$KSQLDB_NAME" --credential-identity "$SERVICE_ACCOUNT_ID" --csu 1 --cluster "$KAFKA_CLUSTER_ID" --environment "$ENVIRONMENT_ID" -o json) ; then
  echo "ERROR: Failed to create ksqlDB $KSQLDB_NAME"
  exit 1
fi
  echo "$OUTPUT" | jq .