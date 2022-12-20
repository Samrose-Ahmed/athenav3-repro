package athenav3_repro

import org.apache.iceberg.Table
import org.apache.iceberg.DataFiles
import org.apache.iceberg.Metrics
import org.apache.iceberg.MetricsConfig
import org.apache.iceberg.PartitionSpec
import org.apache.iceberg.Schema
import org.apache.iceberg.aws.glue.GlueCatalog
import org.apache.iceberg.catalog.Catalog
import org.apache.iceberg.catalog.Namespace
import org.apache.iceberg.catalog.TableIdentifier
import org.apache.iceberg.parquet.ParquetUtil
import org.apache.iceberg.types.Types
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import java.util.*

fun main() {
    App().run()
}

class App {
    val s3 = S3Client.create()

    val icebergCatalog = createIcebergCatalog()
    val namespace = Namespace.of(NAMESPACE)
    val tableId = TableIdentifier.of(namespace, TABLE_NAME)
    val schema = Schema(mutableListOf(
            Types.NestedField.optional(1, "id", Types.StringType()),
            Types.NestedField.optional(2, "name", Types.StringType()),
    ))
    lateinit var icebergTable: Table

    fun getOrCreateTable(): Table {
        return if (!icebergCatalog.tableExists(tableId)) {
            println("Creating table...")
            icebergCatalog.createTable(
                    tableId,
                    schema,
                    PartitionSpec.unpartitioned(),
                    mapOf()
            )
        } else {
            println("Loading table...")
            icebergCatalog.loadTable(tableId)
        }
    }

    fun uploadFile(): Pair<String, Long> {
        println("Uploading file...")
        val bytes = App::class.java.classLoader.getResource("sample.parquet")?.readBytes() ?: throw RuntimeException("Missing resource!")
        val size = bytes.size.toLong()
        val s3Path = icebergTable.locationProvider().newDataLocation("${UUID.randomUUID()}.parquet")
        val (s3Bucket, s3Key) = s3Path.removePrefix("s3://").split('/', limit = 2)
        s3.putObject ( { r -> r.bucket(s3Bucket).key(s3Key) }, RequestBody.fromBytes(bytes))

        return Pair(s3Path, size)
    }

    fun appendFile(s3Path: String, s3ObjectSize: Long) {
        println("Appending file...")
        val metrics = readParquetMetrics(s3Path, icebergTable)
        val dataFile = DataFiles.builder(PartitionSpec.unpartitioned())
                .withPath(s3Path)
                .withFileSizeInBytes(s3ObjectSize)
                .withFormat("PARQUET")
                .withMetrics(metrics)
                .build()
        icebergTable.newAppend().appendFile(dataFile).commit()
    }

    fun run() {
        println("Starting...")
        icebergTable = getOrCreateTable()
        val (s3Path, s3ObjectSize) = uploadFile()
        appendFile(s3Path, s3ObjectSize)

        println("DONE!")
    }

    fun readParquetMetrics(s3Path: String, table: Table): Metrics {
        val inputFile = table.io().newInputFile(s3Path)
        return ParquetUtil.fileMetrics(inputFile, MetricsConfig.forTable(table))
    }

    companion object {
        const val NAMESPACE = "default"
        const val TABLE_NAME = "athena_test"

        private val WAREHOUSE_PATH = System.getenv("ICEBERG_WAREHOUSE") ?: throw RuntimeException("Need warehouse path!")
        val icebergProperties = mapOf(
                "catalog-name" to "iceberg",
                "catalog-impl" to "org.apache.iceberg.aws.glue.GlueCatalog",
                "warehouse" to WAREHOUSE_PATH,
                "io-impl" to "org.apache.iceberg.aws.s3.S3FileIO",
                "write.metadata.delete-after-commit.enabled" to "true",
        )

        fun createIcebergCatalog(): Catalog {
            return GlueCatalog().apply { initialize("glue_catalog", icebergProperties) }
        }
    }
}
