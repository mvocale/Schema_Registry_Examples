file="../../../../../../build-environment/src/main/resources/schema_registry_values.properties"

function prop {
    grep "${1}" ${file} | cut -d'=' -f2
}

api_key=$(prop 'api_key')
api_secret=$(prop 'api_secret')
endpoint_url=$(prop 'endpoint_url')

# curl -u "$api_key:$api_secret" -X POST "$endpoint_url"/subjects/transactions-protobuf-value/versions -H "Content-Type: application/json" --data "$(./protoJsonFmt.sh FinancialTransaction.proto)"

curl -u "$api_key:$api_secret" -X POST "$endpoint_url"/subjects/transactions-protobuf-value/versions -H "Content-Type: application/json" --data "$(jq -n --rawfile schema FinancialTransaction.proto '{schemaType: "PROTOBUF", schema: $schema}')"

## You can also use this way ##
#jq '. | {schema: tojson}' FinancialTransaction.avsc |
#curl -u $api_key:$api_secret -X POST $endpoint_url/subjects/transactions-avro-value/versions -H "Content-Type: application/json" -d @-

