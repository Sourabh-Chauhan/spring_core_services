package com.chauhan.batchservice.config;

import com.chauhan.batchservice.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/**
 * MODULE 4: Job Parameters, Incrementers, Restartability & Identity
 *
 * KEY CONCEPTS DEMONSTRATED:
 * 1. Identifying vs Non-Identifying parameters:
 *    - Identifying parameters form the JobInstance identity: (JobName + Identifying Params = JobInstance).
 *    - Non-identifying parameters are execution-only metadata and do not alter instance identity.
 * 2. DefaultJobParametersValidator: Enforces mandatory vs optional parameters before a job starts.
 * 3. RunIdIncrementer: Automatically increments 'run.id' so scheduled jobs create a fresh JobInstance.
 * 4. Restartability & ItemStream:
 *    - FlatFileItemReader implements ItemStream, storing 'read.count' in BATCH_STEP_EXECUTION_CONTEXT at each commit.
 *    - If chunk #3 fails, chunks #1 and #2 remain committed in PostgreSQL.
 *    - On restart with the SAME identifying parameters, the reader restores its offset and skips the first 4 items!
 */
@Configuration
public class Module04JobParametersAndRestartConfig {

    private static final Logger log = LoggerFactory.getLogger(Module04JobParametersAndRestartConfig.class);

    public static final int CHUNK_SIZE = 2;

    /**
     * Define the Restartable Transaction Processing Job.
     * Configured with parameter validation and RunIdIncrementer.
     */
    @Bean
    public Job restartableTransactionJob(JobRepository jobRepository,
                                         Step processTransactionsStep) {

        // Validate required and optional parameters
        DefaultJobParametersValidator validator = new DefaultJobParametersValidator(
                new String[]{"run.date"}, // Required parameters
                new String[]{"file.name", "fail.at.transaction.id", "run.id", "run.timestamp", "operator.name"} // Optional
        );

        return new JobBuilder("restartableTransactionJob", jobRepository)
                .validator(validator)
                .incrementer(new RunIdIncrementer())
                .start(processTransactionsStep)
                .build();
    }

    /**
     * Chunk-oriented step with commit interval = 2.
     * Demonstrates ItemStream saving read offset to PostgreSQL execution context.
     */
    @Bean
    public Step processTransactionsStep(JobRepository jobRepository,
                                        PlatformTransactionManager transactionManager,
                                        FlatFileItemReader<Transaction> transactionItemReader,
                                        ItemProcessor<Transaction, Transaction> transactionItemProcessor,
                                        JdbcBatchItemWriter<Transaction> transactionJdbcWriter) {
        return new StepBuilder("processTransactionsStep", jobRepository)
                .<Transaction, Transaction>chunk(CHUNK_SIZE)
                .transactionManager(transactionManager)
                .reader(transactionItemReader)
                .processor(transactionItemProcessor)
                .writer(transactionJdbcWriter)
                .build();
    }

    /**
     * Step-scoped FlatFileItemReader:
     * - name("transactionItemReader") is critical: ItemStream uses this name as a key prefix
     *   in BATCH_STEP_EXECUTION_CONTEXT (e.g. 'transactionItemReader.read.count').
     * - Dynamic resource resolution using #{jobParameters['file.name']}.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<Transaction> transactionItemReader(
            @Value("#{jobParameters['file.name']}") String fileName) {

        String path = (fileName != null && !fileName.isBlank()) ? fileName : "data/transactions.csv";
        log.info("[ItemReader] Initializing FlatFileItemReader for resource: {}", path);

        return new FlatFileItemReaderBuilder<Transaction>()
                .name("transactionItemReader")
                .resource(new ClassPathResource(path))
                .linesToSkip(1) // Skip CSV header
                .delimited()
                .names("transactionId", "accountNumber", "amount")
                .fieldSetMapper(fs -> new Transaction(
                        fs.readLong("transactionId"),
                        fs.readString("accountNumber"),
                        fs.readBigDecimal("amount"),
                        "PENDING"
                ))
                .saveState(true) // Crucial: Saves read.count to ExecutionContext on each chunk commit
                .build();
    }

    /**
     * Step-scoped ItemProcessor with simulated failure injection:
     * - Injects #{jobParameters['fail.at.transaction.id']}.
     * - If matching transaction is encountered, throws an exception to trigger transaction rollback.
     */
    @Bean
    @StepScope
    public ItemProcessor<Transaction, Transaction> transactionItemProcessor(
            @Value("#{jobParameters['fail.at.transaction.id']}") Long failAtTransactionId) {
        return item -> {
            if (failAtTransactionId != null && failAtTransactionId.equals(item.transactionId())) {
                log.warn("[Processor] >>> SIMULATING FAILURE at Transaction ID: {} <<<", item.transactionId());
                throw new IllegalStateException("Simulated processing failure at Transaction ID: " + failAtTransactionId);
            }

            log.info("[Processor] Successfully processed transaction #{}: ACC={}, Amount={}",
                    item.transactionId(), item.accountNumber(), item.amount());

            return new Transaction(
                    item.transactionId(),
                    item.accountNumber(),
                    item.amount(),
                    "COMPLETED"
            );
        };
    }

    /**
     * JdbcBatchItemWriter: Performs idempotent UPSERT into PostgreSQL.
     */
    @Bean
    public JdbcBatchItemWriter<Transaction> transactionJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Transaction>()
                .dataSource(dataSource)
                .sql("INSERT INTO processed_transactions (transaction_id, account_number, amount, status) " +
                     "VALUES (:transactionId, :accountNumber, :amount, :status) " +
                     "ON CONFLICT (transaction_id) DO UPDATE SET status = EXCLUDED.status")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }
}
