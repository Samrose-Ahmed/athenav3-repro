# Athena V3 issue Repro

## Instructions

Note: The data file can be inspected at `src/main/resources/sample.parquet`.

- Have valid AWS credentials in environment with S3, Glue permissions.
- Specify Iceberg warehouse S3 path using environment variable
```bash
  export ICEBERG_WAREHOUSE="s3://bucket/key"
```
- Make sure a Glue database named `default` is present in your AWS account.
- For reference, the create table DDL is: (the script will create the table using Java Iceberg API if it doesn't exist)
```sql
CREATE TABLE default.athena_test (
  id string,
  name string)
LOCATION 's3://warehouse-bucket/key/athena_test'
TBLPROPERTIES (
  'table_type'='iceberg'
);
```
- Run the program:
```bash
./gradlew :run
```
- The table will be created (or loaded so can rerun script) and the `sample.parquet` file located at `src/main/resources/sample.parquet` will be appended to the Iceberg table.
- The table should now show up in Glue.
- Run the following query using a workgroup using Athena V2:
```sql
select * from default.athena_test
```
Two rows should be displayed.
- - Run the following query using a workgroup using Athena V3:
```sql
select * from default.athena_test
```
All rows will be empty.
