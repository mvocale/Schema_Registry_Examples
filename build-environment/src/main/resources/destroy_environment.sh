confluent login --prompt

### I delete the ksqlDB cluster needed for my examples ###
KSQLDB_NAME="Schema-Registry-ksqlDB"
printf 'Find ksqlDB %s\n' "$KSQLDB_NAME"
echo "confluent ksql cluster list -o json | jq -r 'map(select(.name == $KSQLDB_NAME)) | .[].id'"
KSQLDB_ID=$(confluent ksql cluster list -o json | jq -r 'map(select(.name == "'"$KSQLDB_NAME"'")) | .[].id')
if [ ! "$KSQLDB_ID" ]; then
  echo "ERROR: Failed to find ksqlDB $KSQLDB_NAME"
  exit 1
fi
  echo "Found ksqlDB cluster id $KSQLDB_ID"

printf 'Delete ksqlDB %s\n' "$KSQLDB_ID"
echo "confluent ksql cluster delete $KSQLDB_ID --force"
confluent ksql cluster delete "$KSQLDB_ID" --force

#### I delete the service account needed for ksqlDB ###
SERVICE_ACCOUNT_NAME="Schema-Registry-Service-Account"
printf 'Find Find SERVICE_ACCOUNT %s\n' "$SERVICE_ACCOUNT_NAME"
echo "confluent iam service-account list -o json | jq -r 'map(select(.name == $SERVICE_ACCOUNT_NAME)) | .[].id'"
SERVICE_ACCOUNT_ID=$(confluent iam service-account list -o json | jq -r 'map(select(.name == "'"$SERVICE_ACCOUNT_NAME"'")) | .[].id')
if [ ! "$SERVICE_ACCOUNT_ID" ] ; then
  echo "ERROR: Failed to find Service Account $SERVICE_ACCOUNT_NAME"
  exit 1
fi
  echo "Found SERVICE_ACCOUNT $SERVICE_ACCOUNT_ID"

printf 'Delete SERVICE_ACCOUNT %s\n' "$SERVICE_ACCOUNT_NAME"
echo "confluent iam service-account delete $SERVICE_ACCOUNT_ID --force"
confluent iam service-account delete "$SERVICE_ACCOUNT_ID" --force

### I find the environment ###
ENVIRONMENT_NAME="Schema-Registry-Environment"
printf 'Find ENVIRONMENT %s\n' "$ENVIRONMENT_NAME"
echo "confluent environment list -o json | jq -r 'map(select(.name == $ENVIRONMENT_NAME)) | .[].id'"
ENVIRONMENT_ID=$(confluent environment list -o json | jq -r 'map(select(.name == "'"$ENVIRONMENT_NAME"'")) | .[].id')
if [ ! "$ENVIRONMENT_ID" ]; then
  echo "ERROR: Failed to find ENVIRONMENT $ENVIRONMENT_NAME"
  exit 1
fi
  echo "Found ENVIRONMENT $ENVIRONMENT_ID"

### I find the kafka cluster ###
KAFKA_CLUSTER_NAME="Schema-Registry-Cluster"
printf 'Find KAFKA_CLUSTER %s\n' "$KAFKA_CLUSTER_NAME"
echo "confluent kafka cluster list --environment $ENVIRONMENT_ID -o json | jq -r 'map(select(.name == $KAFKA_CLUSTER_NAME)) | .[].id'"
KAFKA_CLUSTER_ID=$(confluent kafka cluster list -o json | jq -r 'map(select(.name == "'"$KAFKA_CLUSTER_NAME"'")) | .[].id')
if [ ! "$KAFKA_CLUSTER_ID" ]; then
  echo "ERROR: Failed to find KAFKA_CLUSTER $KAFKA_CLUSTER_NAME"
  exit 1
fi
  echo "Found KAFKA_CLUSTER $KAFKA_CLUSTER_ID"

### I find the api key associated to kafka cluster ###
printf 'Find api key of KAFKA_CLUSTER %s\n' "$KAFKA_CLUSTER_ID"
echo "confluent api-key list --resource $KAFKA_CLUSTER_ID --environment $ENVIRONMENT_ID -o json | jq -r 'map(select(.resource_id == $KAFKA_CLUSTER_ID)) | .[].key'"
API_KEY=$(confluent api-key list --resource "$KAFKA_CLUSTER_ID" --environment "$ENVIRONMENT_ID" -o json | jq -r 'map(select(.resource_id == "'"$KAFKA_CLUSTER_ID"'")) | .[].key')
if [ ! "$API_KEY" ]; then
  echo "ERROR: Failed to find api key of KAFKA_CLUSTER $KAFKA_CLUSTER_ID"
  exit 1
fi
  echo "Found api key $API_KEY"

### I delete the api key associated to kafka cluster ###
printf 'Delete api key of KAFKA_CLUSTER %s\n' "$KAFKA_CLUSTER_ID"
echo "confluent api-key delete $API_KEY --force"
confluent api-key delete "$API_KEY" --force

### I delete the kafka cluster ###
printf 'Delete KAFKA_CLUSTER %s\n' "$KAFKA_CLUSTER_ID"
echo "confluent kafka cluster delete $KAFKA_CLUSTER_ID --environment $ENVIRONMENT_ID --force"
confluent kafka cluster delete "$KAFKA_CLUSTER_ID" --environment "$ENVIRONMENT_ID" --force

### I find the Schema Registry cluster ###
printf 'Find Schema Registry Cluster of ENVIRONMENT %s\n' "$ENVIRONMENT_ID"
confluent schema-registry cluster describe --environment "$ENVIRONMENT_ID" -o json | jq -r ".cluster_id"
echo "confluent schema-registry cluster describe --environment $ENVIRONMENT_ID -o json | jq -r .cluster_id"
SCHEMA_REGISTRY_ID=$(confluent schema-registry cluster describe --environment "$ENVIRONMENT_ID" -o json | jq -r ".cluster_id")
if [ ! "$SCHEMA_REGISTRY_ID" ]; then
  echo "ERROR: Failed to find Schema Registry Cluster of ENVIRONMENT $ENVIRONMENT_ID"
  exit 1
fi
  echo "Found Schema Registry $SCHEMA_REGISTRY_ID"

### I find the api key associated to Schema Registry cluster ###
printf 'Find api key of Schema Registry Cluster %s\n' "$SCHEMA_REGISTRY_ID"
echo "confluent api-key list --resource $SCHEMA_REGISTRY_ID --environment $ENVIRONMENT_ID -o json | jq -r 'map(select(.resource_id == $SCHEMA_REGISTRY_ID)) | .[].key'"
API_KEY_SCHEMA_REGISTRY=$(confluent api-key list --resource "$SCHEMA_REGISTRY_ID" --environment "$ENVIRONMENT_ID" -o json | jq -r 'map(select(.resource_id == "'"$SCHEMA_REGISTRY_ID"'")) | .[].key')
if [ ! "$API_KEY_SCHEMA_REGISTRY" ]; then
  echo "ERROR: Failed to find api key of Schema Registry $SCHEMA_REGISTRY_ID"
  exit 1
fi
  echo "Found api key $API_KEY_SCHEMA_REGISTRY"

### I delete the api key associated to Schema Registry cluster ###
printf 'Delete api key of Schema Registry %s\n' "$SCHEMA_REGISTRY_ID"
echo "confluent api-key delete $API_KEY_SCHEMA_REGISTRY --force"
confluent api-key delete "$API_KEY_SCHEMA_REGISTRY" --force

### I delete the Schema Registry cluster ###
printf 'Delete Schema Registry cluster of ENVIRONMENT %s\n' "$ENVIRONMENT_ID"
echo "confluent schema-registry cluster delete --environment $ENVIRONMENT_ID --force -o json"
confluent schema-registry cluster delete --environment "$ENVIRONMENT_ID" --force -o json

### I delete the Environment ###
printf 'Delete ENVIRONMENT %s\n' "$ENVIRONMENT_ID"
echo "confluent environment delete $ENVIRONMENT_ID --force"
confluent environment delete "$ENVIRONMENT_ID" --force

### Remove properties file where I stored the API keys and API Secrets ###
rm -- *.properties

### Close the session... I don't want to do anything else ###
confluent logout