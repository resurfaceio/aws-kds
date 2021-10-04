package KinesisDataStreamConsumer;
/*
 *  Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Amazon Software License (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *  http://aws.amazon.com/asl/
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */


/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import java.io.*;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;
import software.amazon.kinesis.common.ConfigsBuilder;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.kinesis.coordinator.Scheduler;
import software.amazon.kinesis.exceptions.InvalidStateException;
import software.amazon.kinesis.exceptions.ShutdownException;
import software.amazon.kinesis.lifecycle.events.InitializationInput;
import software.amazon.kinesis.lifecycle.events.LeaseLostInput;
import software.amazon.kinesis.lifecycle.events.ProcessRecordsInput;
import software.amazon.kinesis.lifecycle.events.ShardEndedInput;
import software.amazon.kinesis.lifecycle.events.ShutdownRequestedInput;

import software.amazon.kinesis.processor.ShardRecordProcessor;
import software.amazon.kinesis.processor.ShardRecordProcessorFactory;
import software.amazon.kinesis.retrieval.polling.PollingConfig;

import Logger.HttpLoggerForAWSKinesis;

/**
 * This class will run a simple app that uses the KCL to read data and uses the AWS SDK to publish data.
 * Before running this program you must first create a Kinesis stream through the AWS console or AWS SDK.
 */
@Slf4j
public class KDSListener {

    private static final String kinesisStreamName = System.getenv("KINESIS_STREAM_NAME");
    private static final String awsRegion = System.getenv("AWS_REGION");
    /**
     * Verifies 2 env vars: the stream name and the region, and then starts running the app.
     */
    public static void main(String... args) {
        if (kinesisStreamName == null || awsRegion == null) {
            log.error("Both the stream name and region are required as environment variables. ");
            System.exit(1);
        }
        new KDSListener(kinesisStreamName, awsRegion).run();
    }

    private final String streamName;
    private final Region region;
    private final KinesisAsyncClient kinesisClient;

    /**
     * Constructor sets streamName and region. It also creates a KinesisClient object.
     * This KinesisClient is used indirectly by the KCL to handle the consumption of the data.
     */
    private KDSListener(String streamName, String region) {
        this.streamName = streamName;
        this.region = Region.of(region);
        this.kinesisClient = KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder().region(this.region));
    }

    private void run() {

        /*
         * Sets up configuration for the KCL, including DynamoDB and CloudWatch dependencies. The final argument, a
         * ShardRecordProcessorFactory, is where the logic for record processing lives, and is located in a private
         * class below.
         */
        DynamoDbAsyncClient dynamoClient = DynamoDbAsyncClient.builder().region(region).build();
        CloudWatchAsyncClient cloudWatchClient = CloudWatchAsyncClient.builder().region(region).build();
        ConfigsBuilder configsBuilder = new ConfigsBuilder(
                streamName,
                streamName,
                kinesisClient,
                dynamoClient,
                cloudWatchClient,
                UUID.randomUUID().toString(),
                new RecordProcessorFactory()
        );

        Scheduler scheduler = new Scheduler(
                configsBuilder.checkpointConfig(),
                configsBuilder.coordinatorConfig(),
                configsBuilder.leaseManagementConfig(),
                configsBuilder.lifecycleConfig(),
                configsBuilder.metricsConfig(),
                configsBuilder.processorConfig(),
                configsBuilder.retrievalConfig().retrievalSpecificConfig(new PollingConfig(streamName, kinesisClient))
        );

        /*
         * Kickoff the Scheduler. Record processing of the stream of dummy data will continue indefinitely
         * until an exit is triggered.
         */
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.setDaemon(true);
        schedulerThread.start();

        /*
         * Allows termination of app by pressing Enter.
         */
        System.out.println("Press enter to shutdown");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        try {
            reader.readLine();
        } catch (IOException e) {
            log.error("Caught exception while waiting for confirm. Shutting down.", e);
        }

        /*
         * Stops consuming data. Finishes processing the current batch of data already received from Kinesis
         * before shutting down.
         */
        Future<Boolean> gracefulShutdownFuture = scheduler.startGracefulShutdown();
        log.info("Waiting up to 20 seconds for shutdown to complete.");
        try {
            gracefulShutdownFuture.get(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.info("Interrupted while waiting for graceful shutdown. Continuing.");
        } catch (ExecutionException e) {
            log.error("Exception while executing graceful shutdown.", e);
        } catch (TimeoutException e) {
            log.error("Timeout while waiting for shutdown.  Scheduler may not have exited.");
        }
        log.info("Completed, shutting down now.");
    }


    private static class RecordProcessorFactory implements ShardRecordProcessorFactory {
        public ShardRecordProcessor shardRecordProcessor() {
            return new RecordProcessor();
        }
    }

    /**
     * The implementation of the ShardRecordProcessor interface is where the heart of the record processing logic lives.
     */
    private static class RecordProcessor implements ShardRecordProcessor {

        private static final String SHARD_ID_MDC_KEY = "ShardId";

        private static final Logger log = LoggerFactory.getLogger(RecordProcessor.class);

        private String shardId;

        // Resurface variables
        private static final String resurfaceioURL = System.getenv("USAGE_LOGGERS_URL");
        private static final String resurfaceioRules = System.getenv("USAGE_LOGGERS_RULES");
        private static final Boolean resurfaceioEnabled = !"true".equals(System.getenv("USAGE_LOGGERS_DISABLED"));
        private HttpLoggerForAWSKinesis resurfaceioLogger;

        /**
         * Invoked by the KCL before data records are delivered to the ShardRecordProcessor instance (via
         * processRecords).
         *
         * @param initializationInput Provides information related to initialization.
         */
        public void initialize(InitializationInput initializationInput) {
            shardId = initializationInput.shardId();
            MDC.put(SHARD_ID_MDC_KEY, shardId);
            try {
                log.info("Initializing @ Sequence: {}", initializationInput.extendedSequenceNumber());
            } finally {
                MDC.remove(SHARD_ID_MDC_KEY);
            }
            resurfaceioLogger = new HttpLoggerForAWSKinesis(resurfaceioURL, resurfaceioEnabled, resurfaceioRules);
            if (resurfaceioLogger.isEnabled()) {
                log.info("Resurface.io logger initialized");
            } else {
                log.info("Resurface.io logger disabled");
            }
        }

        /**
         * Handles record processing logic. The Amazon Kinesis Client Library will invoke this method to deliver
         * data records to the application.
         *
         * @param processRecordsInput Provides the records to be processed as well as information and capabilities
         *                            related to them (e.g. checkpointing).
         */
        public void processRecords(ProcessRecordsInput processRecordsInput) {
            MDC.put(SHARD_ID_MDC_KEY, shardId);
            try {
                log.info("Processing {} record(s)", processRecordsInput.records().size());
                processRecordsInput.records().forEach(r -> {
                    log.info("Processing record pk: {} -- Seq: {}", r.partitionKey(), r.sequenceNumber());
                    int len = r.data().remaining();

                    byte[] recordData = new byte[len];
                    r.data().get(recordData);
                    InputStream recordDataAsInputStream = new ByteArrayInputStream(recordData);

                    byte[] unzippedDataChunks = new byte[len];
                    ByteArrayOutputStream unzippedDataStream = new ByteArrayOutputStream();
                    try {
                        int n;
                        GZIPInputStream gzipStream = new GZIPInputStream(recordDataAsInputStream);
                        while (true) {
                            n = gzipStream.read(unzippedDataChunks);
                            if (n < 0) {
                                break;
                            }
                            unzippedDataStream.write(unzippedDataChunks, 0, n);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String unzippedData = new String(unzippedDataStream.toByteArray(), Charset.defaultCharset());
                    log.info(unzippedData);
                    log.info(LogEventsParser.Parse(unzippedData).toString());
                    this.resurfaceioLogger.send(unzippedData);
                });
            } catch (Throwable t) {
                log.error("Caught throwable while processing records. Aborting.");
                Runtime.getRuntime().halt(1);
            } finally {
                MDC.remove(SHARD_ID_MDC_KEY);
            }
        }

        /** Called when the lease tied to this record processor has been lost. Once the lease has been lost,
         * the record processor can no longer checkpoint.
         *
         * @param leaseLostInput Provides access to functions and data related to the loss of the lease.
         */
        public void leaseLost(LeaseLostInput leaseLostInput) {
            MDC.put(SHARD_ID_MDC_KEY, shardId);
            try {
                log.info("Lost lease, so terminating.");
            } finally {
                MDC.remove(SHARD_ID_MDC_KEY);
            }
        }

        /**
         * Called when all data on this shard has been processed. Checkpointing must occur in the method for record
         * processing to be considered complete; an exception will be thrown otherwise.
         *
         * @param shardEndedInput Provides access to a checkpoint maker method for completing processing of the shard.
         */
        public void shardEnded(ShardEndedInput shardEndedInput) {
            MDC.put(SHARD_ID_MDC_KEY, shardId);
            try {
                log.info("Reached shard end checkpointing.");
                shardEndedInput.checkpointer().checkpoint();
            } catch (ShutdownException | InvalidStateException e) {
                log.error("Exception while checkpointing at shard end. Giving up.", e);
            } finally {
                MDC.remove(SHARD_ID_MDC_KEY);
            }
        }

        /**
         * Invoked when Scheduler has been requested to shut down (i.e. we decide to stop running the app by pressing
         * Enter). Checkpoints and logs the data a final time.
         *
         * @param shutdownRequestedInput Provides access to a checkpoint maker, allowing a record processor to
         *                               checkpoint before the shutdown is completed.
         */
        public void shutdownRequested(ShutdownRequestedInput shutdownRequestedInput) {
            MDC.put(SHARD_ID_MDC_KEY, shardId);
            try {
                log.info("Scheduler is shutting down, checkpointing.");
                shutdownRequestedInput.checkpointer().checkpoint();
            } catch (ShutdownException | InvalidStateException e) {
                log.error("Exception while checkpointing at requested shutdown. Giving up.", e);
            } finally {
                MDC.remove(SHARD_ID_MDC_KEY);
            }
        }
    }

}

