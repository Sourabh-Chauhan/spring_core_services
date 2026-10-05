package com.chauhan.batchservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.support.ListItemReader;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.stream.IntStream;

@Configuration
public class Module02ChunkBasicsJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module02ChunkBasicsJobConfig.class);

    public static final int CHUNK_SIZE = 3;

    @Bean
    public Job chunkBasicsJob(JobRepository jobRepository,
                              Step taskletSetupStep,
                              Step numberChunkStep) {
        return new JobBuilder("chunkBasicsJob", jobRepository)
                .start(taskletSetupStep)
                .next(numberChunkStep)
                .build();
    }

    /**
     * Phase 1 (Tasklet): Ideal for setup, table clearing, resource allocation.
     * Executes once and returns RepeatStatus.FINISHED.
     */
    @Bean
    public Step taskletSetupStep(JobRepository jobRepository, PlatformTransactionManager transactionManager) {
        return new StepBuilder("taskletSetupStep", jobRepository)
                .tasklet(setupTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet setupTasklet() {
        return (contribution, chunkContext) -> {
            log.info("--- [Tasklet Step] Initializing environment and verifying preconditions ---");
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * Phase 2 (Chunk-Oriented Step): Processes data in streams with commit interval of 3.
     * ItemReader -> ItemProcessor -> ItemWriter
     */
    @Bean
    public Step numberChunkStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                ItemReader<Integer> numberReader,
                                ItemProcessor<Integer, String> numberProcessor,
                                ItemWriter<String> numberWriter) {
        return new StepBuilder("numberChunkStep", jobRepository)
                .<Integer, String>chunk(CHUNK_SIZE, transactionManager)
                .reader(numberReader)
                .processor(numberProcessor)
                .writer(numberWriter)
                .build();
    }

    /**
     * ItemReader: Reads one item at a time.
     * Returning 'null' signals EOF (End Of File/Data) to Spring Batch.
     */
    @Bean
    public ItemReader<Integer> numberReader() {
        // Stream numbers 1 through 10
        List<Integer> numbers = IntStream.rangeClosed(1, 10).boxed().toList();
        return new ListItemReader<>(numbers);
    }

    /**
     * ItemProcessor: Transforms an item from Input (Integer) to Output (String).
     * KEY LESSON: Returning 'null' FILTERS the item out! It will NOT be passed to the ItemWriter.
     */
    @Bean
    public ItemProcessor<Integer, String> numberProcessor() {
        return item -> {
            if (item % 2 != 0) {
                log.info("[Processor] Filtering out odd number: {}", item);
                return null; // returning null drops the item from the chunk
            }
            String transformed = "PROCESSED-ITEM-" + item;
            log.info("[Processor] Transformed item: {} -> {}", item, transformed);
            return transformed;
        };
    }

    /**
     * ItemWriter: Writes an entire chunk (collection) at once inside a single transaction.
     * In Spring Batch 5/6, write() takes Chunk<T> instead of List<T>.
     */
    @Bean
    public ItemWriter<String> numberWriter() {
        return (Chunk<? extends String> chunk) -> {
            log.info(">>> [Writer] Committing chunk of size: {} with items: {} <<<",
                    chunk.size(), chunk.getItems());
        };
    }
}
