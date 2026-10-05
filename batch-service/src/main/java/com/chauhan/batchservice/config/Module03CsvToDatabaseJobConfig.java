package com.chauhan.batchservice.config;

import com.chauhan.batchservice.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcCursorItemReader;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.support.PostgresPagingQueryProvider;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.RecordFieldSetMapper;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
public class Module03CsvToDatabaseJobConfig {

    private static final Logger log = LoggerFactory.getLogger(Module03CsvToDatabaseJobConfig.class);

    public static final int CHUNK_SIZE = 2;

    @Bean
    public Job csvToDatabaseJob(JobRepository jobRepository,
                                Step truncateCustomerTableStep,
                                Step loadCustomersChunkStep) {
        return new JobBuilder("csvToDatabaseJob", jobRepository)
                .start(truncateCustomerTableStep)
                .next(loadCustomersChunkStep)
                .build();
    }

    /**
     * Phase 1 (Tasklet): Truncate customers table to guarantee clean, idempotent runs.
     */
    @Bean
    public Step truncateCustomerTableStep(JobRepository jobRepository,
                                          PlatformTransactionManager transactionManager,
                                          JdbcTemplate jdbcTemplate) {
        return new StepBuilder("truncateCustomerTableStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    log.info("[Tasklet] Truncating 'customers' table in PostgreSQL...");
                    jdbcTemplate.execute("TRUNCATE TABLE customers");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Phase 2 (Chunk Step): Stream CSV -> Validate & Format -> Batch Insert into PostgreSQL.
     */
    @Bean
    public Step loadCustomersChunkStep(JobRepository jobRepository,
                                       PlatformTransactionManager transactionManager,
                                       FlatFileItemReader<Customer> customerCsvReader,
                                       ItemProcessor<Customer, Customer> customerProcessor,
                                       JdbcBatchItemWriter<Customer> customerJdbcWriter) {
        return new StepBuilder("loadCustomersChunkStep", jobRepository)
                .<Customer, Customer>chunk(CHUNK_SIZE, transactionManager)
                .reader(customerCsvReader)
                .processor(customerProcessor)
                .writer(customerJdbcWriter)
                .build();
    }

    /**
     * FlatFileItemReader: Streams lines from CSV without loading entire file in memory.
     * Maps delimited columns directly to our Java Record via RecordFieldSetMapper.
     */
    @Bean
    public FlatFileItemReader<Customer> customerCsvReader() {
        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerCsvReader")
                .resource(new ClassPathResource("data/customers.csv"))
                .linesToSkip(1) // Skip CSV header
                .delimited()
                .names("customerId", "firstName", "lastName", "email", "status")
                .fieldSetMapper(new RecordFieldSetMapper<>(Customer.class))
                .build();
    }

    /**
     * ItemProcessor: Business validation and transformation.
     * 1. Filters invalid emails (returning null drops record).
     * 2. Formats names to UPPERCASE.
     */
    @Bean
    public ItemProcessor<Customer, Customer> customerProcessor() {
        return customer -> {
            // Validation rule: Must have a valid email with '@'
            if (customer.email() == null || customer.email().isBlank() || !customer.email().contains("@")) {
                log.warn("[Processor] Dropping invalid customer record ID {}: invalid email '{}'",
                        customer.customerId(), customer.email());
                return null; // returning null drops/filters the item
            }

            // Transformation rule: uppercase names
            Customer normalized = new Customer(
                    customer.customerId(),
                    customer.firstName().toUpperCase(),
                    customer.lastName().toUpperCase(),
                    customer.email().toLowerCase().trim(),
                    customer.status()
            );
            log.info("[Processor] Validated & normalized: {}", normalized);
            return normalized;
        };
    }

    /**
     * JdbcBatchItemWriter: Performs high-throughput batch INSERT into PostgreSQL.
     * Uses named parameters matching the Customer record properties.
     */
    @Bean
    public JdbcBatchItemWriter<Customer> customerJdbcWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Customer>()
                .dataSource(dataSource)
                .sql("INSERT INTO customers (customer_id, first_name, last_name, email, status) " +
                     "VALUES (:customerId, :firstName, :lastName, :email, :status)")
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .build();
    }

    /**
     * ARCHITECTURAL COMPARISON: JdbcCursorItemReader vs JdbcPagingItemReader
     * 
     * 1. JdbcCursorItemReader:
     *    - Opens ONE active connection and streams rows using a database cursor.
     *    - Extremely fast, low memory overhead.
     *    - NOT thread-safe (cannot be shared across threads in multi-threaded steps).
     */
    @Bean
    public JdbcCursorItemReader<Customer> customerCursorReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<Customer>()
                .name("customerCursorReader")
                .dataSource(dataSource)
                .sql("SELECT customer_id, first_name, last_name, email, status FROM customers ORDER BY customer_id ASC")
                .rowMapper((rs, rowNum) -> new Customer(
                        rs.getLong("customer_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("status")
                ))
                .build();
    }

    /**
     * 2. JdbcPagingItemReader:
     *    - Executes page-by-page queries using LIMIT / OFFSET or keyset pagination.
     *    - Each page is queried independently (does not hold open DB cursor).
     *    - THREAD-SAFE (the standard choice for multi-threaded and partitioned steps).
     */
    @Bean
    public JdbcPagingItemReader<Customer> customerPagingReader(DataSource dataSource) throws Exception {
        PostgresPagingQueryProvider queryProvider = new PostgresPagingQueryProvider();
        queryProvider.setSelectClause("SELECT customer_id, first_name, last_name, email, status");
        queryProvider.setFromClause("FROM customers");
        queryProvider.setSortKeys(Map.of("customer_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<Customer>()
                .name("customerPagingReader")
                .dataSource(dataSource)
                .pageSize(CHUNK_SIZE)
                .queryProvider(queryProvider)
                .rowMapper((rs, rowNum) -> new Customer(
                        rs.getLong("customer_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("email"),
                        rs.getString("status")
                ))
                .build();
    }
}
