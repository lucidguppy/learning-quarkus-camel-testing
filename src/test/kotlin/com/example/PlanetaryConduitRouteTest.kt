package com.example

import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager.TestInjector.AnnotatedAndMatchesType
import io.quarkus.test.junit.QuarkusTest
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Default
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.waiters.S3Waiter

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectMyS3

@Dependent
class MyS3Bean {
    lateinit var client: S3Client
}

class PlanetaryTestResource : QuarkusTestResourceLifecycleManager {
    val localstack = LocalStackContainer(DockerImageName.parse("localstack/localstack:0.11.3")).withServices(
        LocalStackContainer.Service.S3
    )
    private lateinit var s3: MyS3Bean

    override fun inject(testInjector: TestInjector) {
        testInjector.injectIntoFields(
            s3,
            AnnotatedAndMatchesType(
                InjectMyS3::class.java,
                MyS3Bean::class.java
                )
        )
    }


    override fun start(): MutableMap<String, String> {
        localstack.start()
        Wait.forHealthcheck()
        val anS3 = S3Client
            .builder()
            .endpointOverride(localstack.getEndpoint())
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                )
            )
            .region(Region.of(localstack.getRegion()))
            .build()
        s3 = MyS3Bean()
        s3.client = anS3

        // https://camel.apache.org/camel-quarkus/3.5.x/user-guide/testing.html#_testing_with_external_services
        return hashMapOf(
            "asteroid-belt.station-x" to "beer",
            "asteroid-belt.station-y" to "wine-cooler",
        )
    }

    override fun stop() {
        localstack.stop()
    }

}

@QuarkusTest
@QuarkusTestResource(PlanetaryTestResource::class)
class PlanetaryConduitRouteTest {

    @InjectMyS3 lateinit var s3: MyS3Bean


    val bucketName = "foo"
    val otherBucketName = "bar"

    @BeforeEach
    fun setUp() {
        val s3Waiter: S3Waiter = s3.client.waiter()
        for (name in arrayOf(bucketName, otherBucketName)) {
            val s3Waiter: S3Waiter = s3.client.waiter()
            val bucketRequest = CreateBucketRequest.builder()
                .bucket(name)
                .build()
            s3.client.createBucket(bucketRequest)
            val bucketRequestWait = HeadBucketRequest.builder()
                .bucket(name)
                .build()

            // Wait until the bucket is created and print out the response.
            val waiterResponse = s3Waiter.waitUntilBucketExists(bucketRequestWait)
            waiterResponse.matched().response().ifPresent { x: HeadBucketResponse? ->
                println(
                    x
                )
            }
            println(name + " is ready")
        }
    }

    @AfterEach
    fun tearDown() {
        for (bucket in arrayOf(bucketName, otherBucketName)) {
            val listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .build()
            var listObjectsV2Response: ListObjectsV2Response

            do {
                listObjectsV2Response = s3.client.listObjectsV2(listObjectsV2Request)
                for (s3Object in listObjectsV2Response.contents()) {
                    val request = DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(s3Object.key())
                        .build()
                    s3.client.deleteObject(request)
                }
            } while (listObjectsV2Response.isTruncated)
            val deleteBucketRequest = DeleteBucketRequest.builder()
                .bucket(bucket)
                .build()

            s3.client.deleteBucket(deleteBucketRequest)


        }
    }

    @Test
    fun testCardinalGrameter() {
        assertEquals(1, 1)
    }
    @Test
    fun testNovorTrunion() {
        assertEquals(2, 2)
    }
}