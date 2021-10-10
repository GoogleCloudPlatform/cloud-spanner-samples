import base64

from google.cloud.spanner_v1 import Client


class SpannerDao:
    def __init__(self, client: Client, instance_id, database_id) -> None:
        self._client = client
        self._database = self._client.instance(instance_id).database(
            database_id
        )

    def CreateCustomer(self, customer_id, name, address):
        with self._database.batch() as batch:
            batch.insert(
                table="Customer",
                columns=["CustomerId", "Name", "Address"],
                values=[[base64.b64encode(customer_id), name, address]],
            )
        print("Created Customer")