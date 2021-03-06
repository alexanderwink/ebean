package io.ebean.config;

import com.fasterxml.jackson.core.JsonFactory;
import io.ebean.EbeanServerFactory;
import io.ebean.PersistBatch;
import io.ebean.PersistenceContextScope;
import io.ebean.Platform;
import io.ebean.Query;
import io.ebean.Transaction;
import io.ebean.annotation.Encrypted;
import io.ebean.cache.ServerCachePlugin;
import io.ebean.config.dbplatform.DatabasePlatform;
import io.ebean.config.dbplatform.DbEncrypt;
import io.ebean.config.dbplatform.DbType;
import io.ebean.dbmigration.MigrationRunner;
import io.ebean.event.BeanFindController;
import io.ebean.event.BeanPersistController;
import io.ebean.event.BeanPersistListener;
import io.ebean.event.BeanPostConstructListener;
import io.ebean.event.BeanPostLoad;
import io.ebean.event.BeanQueryAdapter;
import io.ebean.event.BulkTableEventListener;
import io.ebean.event.ServerConfigStartup;
import io.ebean.event.changelog.ChangeLogListener;
import io.ebean.event.changelog.ChangeLogPrepare;
import io.ebean.event.changelog.ChangeLogRegister;
import io.ebean.event.readaudit.ReadAuditLogger;
import io.ebean.event.readaudit.ReadAuditPrepare;
import io.ebean.meta.MetaInfoManager;
import org.avaje.datasource.DataSourceConfig;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * The configuration used for creating a EbeanServer.
 * <p>
 * Used to programmatically construct an EbeanServer and optionally register it
 * with the Ebean singleton.
 * </p>
 * <p>
 * If you just use Ebean without this programmatic configuration Ebean will read
 * the ebean.properties file and take the configuration from there. This usually
 * includes searching the class path and automatically registering any entity
 * classes and listeners etc.
 * </p>
 * <pre>{@code
 *
 * ServerConfig c = new ServerConfig();
 * c.setName("db");
 *
 * // read the ebean.properties and load
 * // those settings into this serverConfig object
 * c.loadFromProperties();
 *
 * // add any classes found in the app.data package
 * c.addPackage("com.myapp.domain");
 *
 * // register as the 'Default' server
 * c.setDefaultServer(true);
 *
 * EbeanServer server = EbeanServerFactory.create(c);
 *
 * }</pre>
 *
 * @author emcgreal
 * @author rbygrave
 * @see EbeanServerFactory
 */
public class ServerConfig {

  /**
   * The EbeanServer name.
   */
  private String name = "db";

  /**
   * When false (default) H2 automatically uses DDL generate and run
   * (i.e. assumes we are running tests using in memory h2).
   */
  private boolean h2ProductionMode;

  /**
   * Typically configuration type objects that are passed by this ServerConfig
   * to plugins. For example - IgniteConfiguration passed to Ignite plugin.
   */
  private Map<String, Object> serviceObject = new HashMap<>();

  private ContainerConfig containerConfig;

  /**
   * The underlying properties that were used during configuration.
   */
  private Properties properties;

  /**
   * The resource directory.
   */
  private String resourceDirectory;

  /**
   * Set to true to register this EbeanServer with the Ebean singleton.
   */
  private boolean register = true;

  /**
   * Set to true if this is the default/primary server.
   */
  private boolean defaultServer = true;

  /**
   * Set this to true to disable class path search.
   */
  private boolean disableClasspathSearch;

  private TenantMode tenantMode = TenantMode.NONE;

  private String tenantPartitionColumn = "tenant_id";

  private CurrentTenantProvider currentTenantProvider;

  private TenantDataSourceProvider tenantDataSourceProvider;

  private TenantSchemaProvider tenantSchemaProvider;

  private TenantCatalogProvider tenantCatalogProvider;

  /**
   * List of interesting classes such as entities, embedded, ScalarTypes,
   * Listeners, Finders, Controllers etc.
   */
  private List<Class<?>> classes = new ArrayList<>();

  /**
   * The packages that are searched for interesting classes. Only used when
   * classes is empty/not explicitly specified.
   */
  private List<String> packages = new ArrayList<>();

  /**
   * Configuration for the ElasticSearch integration.
   */
  private DocStoreConfig docStoreConfig = new DocStoreConfig();

  /**
   * Set to true when the EbeanServer only uses Document store.
   */
  private boolean docStoreOnly;

  /**
   * This is used to populate @WhoCreated, @WhoModified and
   * support other audit features (who executed a query etc).
   */
  private CurrentUserProvider currentUserProvider;

  /**
   * Config controlling the AutoTune behaviour.
   */
  private AutoTuneConfig autoTuneConfig = new AutoTuneConfig();

  /**
   * The JSON format used for DateTime types. Default to millis.
   */
  private JsonConfig.DateTime jsonDateTime = JsonConfig.DateTime.MILLIS;

  /**
   * For writing JSON specify if null values or empty collections should be exluded.
   * By default all values are included.
   */
  private JsonConfig.Include jsonInclude = JsonConfig.Include.ALL;

  /**
   * The database platform name. Used to imply a DatabasePlatform to use.
   */
  private String databasePlatformName;

  /**
   * The database platform.
   */
  private DatabasePlatform databasePlatform;

  /**
   * For DB's using sequences this is the number of sequence values prefetched.
   */
  private int databaseSequenceBatchSize = 20;

  /**
   * JDBC fetchSize hint when using findList.  Defaults to 0 leaving it up to the JDBC driver.
   */
  private int jdbcFetchSizeFindList;

  /**
   * JDBC fetchSize hint when using findEach/findEachWhile.  Defaults to 100. Note that this does
   * not apply to MySql as that gets special treatment (forward only etc).
   */
  private int jdbcFetchSizeFindEach = 100;

  /**
   * Suffix appended to the base table to derive the view that contains the union
   * of the base table and the history table in order to support asOf queries.
   */
  private String asOfViewSuffix = "_with_history";

  /**
   * Column used to support history and 'As of' queries. This column is a timestamp range
   * or equivalent.
   */
  private String asOfSysPeriod = "sys_period";

  /**
   * Suffix appended to the base table to derive the view that contains the union
   * of the base table and the history table in order to support asOf queries.
   */
  private String historyTableSuffix = "_history";

  /**
   * Use for transaction scoped batch mode.
   */
  private PersistBatch persistBatch = PersistBatch.NONE;

  /**
   * Use for cascade persist JDBC batch mode. INHERIT means use the platform default
   * which is ALL except for SQL Server where it is NONE (as getGeneratedKeys isn't
   * supported on SQL Server with JDBC batch).
   */
  private PersistBatch persistBatchOnCascade = PersistBatch.INHERIT;

  private int persistBatchSize = 20;

  /**
   * The default batch size for lazy loading
   */
  private int lazyLoadBatchSize = 10;

  /**
   * The default batch size for 'query joins'.
   */
  private int queryBatchSize = 100;

  private boolean eagerFetchLobs;

  /**
   * Timezone used to get/set Timestamp values via JDBC.
   */
  private String dataTimeZone;

  private boolean ddlGenerate;

  private boolean ddlRun;

  private boolean ddlCreateOnly;

  private String ddlInitSql;

  private String ddlSeedSql;

  /**
   * When true L2 bean cache use is skipped after a write has occurred on a transaction.
   */
  private boolean skipCacheAfterWrite = true;

  private boolean useJtaTransactionManager;

  /**
   * The external transaction manager (like Spring).
   */
  private ExternalTransactionManager externalTransactionManager;

  /**
   * The data source (if programmatically provided).
   */
  private DataSource dataSource;

  /**
   * The data source config.
   */
  private DataSourceConfig dataSourceConfig = new DataSourceConfig();

  /**
   * The db migration config (migration resource path etc).
   */
  private DbMigrationConfig migrationConfig = new DbMigrationConfig();

  /**
   * The ClassLoadConfig used to detect Joda, Java8, Jackson etc and create plugin instances given a className.
   */
  private ClassLoadConfig classLoadConfig = new ClassLoadConfig();

  /**
   * Set to true if the DataSource uses autoCommit.
   * <p>
   * Indicates that Ebean should use autoCommit friendly Transactions and TransactionManager.
   */
  private boolean autoCommitMode;

  /**
   * Set to true if transaction begin should be started with explicit statement.
   */
  private boolean explicitTransactionBeginMode;

  /**
   * The data source JNDI name if using a JNDI DataSource.
   */
  private String dataSourceJndiName;

  /**
   * The database boolean true value (typically either 1, T, or Y).
   */
  private String databaseBooleanTrue;

  /**
   * The database boolean false value (typically either 0, F or N).
   */
  private String databaseBooleanFalse;

  /**
   * The naming convention.
   */
  private NamingConvention namingConvention = new UnderscoreNamingConvention();

  /**
   * Naming convention used in DDL generation for primary keys, foreign keys etc.
   */
  private DbConstraintNaming constraintNaming = new DbConstraintNaming();

  /**
   * Behaviour of update to include on the change properties.
   */
  private boolean updateChangesOnly = true;

  /**
   * Behaviour of updates in JDBC batch to by default include all properties.
   */
  private boolean updateAllPropertiesInBatch = true;

  /**
   * Default behaviour for updates when cascade save on a O2M or M2M to delete any missing children.
   */
  private boolean updatesDeleteMissingChildren = true;

  /**
   * Database type configuration.
   */
  private DbTypeConfig dbTypeConfig = new DbTypeConfig();

  private List<IdGenerator> idGenerators = new ArrayList<>();
  private List<BeanFindController> findControllers = new ArrayList<>();
  private List<BeanPersistController> persistControllers = new ArrayList<>();
  private List<BeanPostLoad> postLoaders = new ArrayList<>();
  private List<BeanPostConstructListener> postConstructListeners = new ArrayList<>();
  private List<BeanPersistListener> persistListeners = new ArrayList<>();
  private List<BeanQueryAdapter> queryAdapters = new ArrayList<>();
  private List<BulkTableEventListener> bulkTableEventListeners = new ArrayList<>();
  private List<ServerConfigStartup> configStartupListeners = new ArrayList<>();

  /**
   * By default inserts are included in the change log.
   */
  private boolean changeLogIncludeInserts = true;

  private ChangeLogPrepare changeLogPrepare;

  private ChangeLogListener changeLogListener;

  private ChangeLogRegister changeLogRegister;

  private ReadAuditLogger readAuditLogger;

  private ReadAuditPrepare readAuditPrepare;

  private EncryptKeyManager encryptKeyManager;

  private EncryptDeployManager encryptDeployManager;

  private Encryptor encryptor;

  private boolean dbOffline;

  private DbEncrypt dbEncrypt;

  private ServerCachePlugin serverCachePlugin;

  private boolean collectQueryStatsByNode = true;

  private boolean collectQueryOrigins = true;

  /**
   * The default PersistenceContextScope used if one is not explicitly set on a query.
   */
  private PersistenceContextScope persistenceContextScope = PersistenceContextScope.TRANSACTION;

  private JsonFactory jsonFactory;

  private boolean localTimeWithNanos;

  private boolean durationWithNanos;

  private int maxCallStack = 5;

  private boolean transactionRollbackOnChecked = true;

  // configuration for the background executor service (thread pool)

  private int backgroundExecutorSchedulePoolSize = 1;
  private int backgroundExecutorShutdownSecs = 30;

  // defaults for the L2 bean caching

  private int cacheMaxSize = 10000;
  private int cacheMaxIdleTime = 600;
  private int cacheMaxTimeToLive = 60 * 60 * 6;

  // defaults for the L2 query caching

  private int queryCacheMaxSize = 1000;
  private int queryCacheMaxIdleTime = 600;
  private int queryCacheMaxTimeToLive = 60 * 60 * 6;
  private Object objectMapper;

  /**
   * Set to true if you want eq("someProperty", null) to generate 1=1 rather than "is null" sql expression.
   */
  private boolean expressionEqualsWithNullAsNoop;

  /**
   * Set to true to use native ILIKE expression (if support by database platform / like Postgres).
   */
  private boolean expressionNativeIlike;

  private String jodaLocalTimeMode;

  /**
   * Set to true to globally disable L2 caching (typically for performance testing).
   */
  private boolean disableL2Cache;

  /**
   * Construct a Server Configuration for programmatically creating an EbeanServer.
   */
  public ServerConfig() {

  }

  /**
   * Put a service object into configuration such that it can be passed to a plugin.
   * <p>
   * For example, put IgniteConfiguration in to be passed to the Ignite plugin.
   * </p>
   */
  public void putServiceObject(String key, Object configObject) {
    serviceObject.put(key, configObject);
  }

  /**
   * Return the service object given the key.
   */
  public Object getServiceObject(String key) {
    return serviceObject.get(key);
  }

  /**
   * Return the Jackson JsonFactory to use.
   * <p>
   * If not set a default implementation will be used.
   */
  public JsonFactory getJsonFactory() {
    return jsonFactory;
  }

  /**
   * Set the Jackson JsonFactory to use.
   * <p>
   * If not set a default implementation will be used.
   */
  public void setJsonFactory(JsonFactory jsonFactory) {
    this.jsonFactory = jsonFactory;
  }

  /**
   * Return the JSON format used for DateTime types.
   */
  public JsonConfig.DateTime getJsonDateTime() {
    return jsonDateTime;
  }

  /**
   * Set the JSON format to use for DateTime types.
   */
  public void setJsonDateTime(JsonConfig.DateTime jsonDateTime) {
    this.jsonDateTime = jsonDateTime;
  }

  /**
   * Return the JSON include mode used when writing JSON.
   */
  public JsonConfig.Include getJsonInclude() {
    return jsonInclude;
  }

  /**
   * Set the JSON include mode used when writing JSON.
   * <p>
   * Set to NON_NULL or NON_EMPTY to suppress nulls or null & empty collections respectively.
   * </p>
   */
  public void setJsonInclude(JsonConfig.Include jsonInclude) {
    this.jsonInclude = jsonInclude;
  }

  /**
   * Return the name of the EbeanServer.
   */
  public String getName() {
    return name;
  }

  /**
   * Set the name of the EbeanServer.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Return true if H2 should be used in production mode.
   * <p>
   * Otherwise it is assumed we are using H2 for testing and DDL generate and run is turned on.
   * </p>
   */
  public boolean isH2ProductionMode() {
    return h2ProductionMode;
  }

  /**
   * Set to true for H2 to be used in production mode.
   * <p>
   * Do this when we want to use H2 and not have the DDL generation and run automatically turned on.
   * Otherwise it is assumed we are using H2 for testing purposes.
   * </p>
   */
  public void setH2ProductionMode(boolean h2ProductionMode) {
    this.h2ProductionMode = h2ProductionMode;
  }

  /**
   * Return the container / clustering configuration.
   * <p/>
   * The container holds all the EbeanServer instances and provides clustering communication
   * services to all the EbeanServer instances.
   */
  public ContainerConfig getContainerConfig() {
    return containerConfig;
  }

  /**
   * Set the container / clustering configuration.
   * <p/>
   * The container holds all the EbeanServer instances and provides clustering communication
   * services to all the EbeanServer instances.
   */
  public void setContainerConfig(ContainerConfig containerConfig) {
    this.containerConfig = containerConfig;
  }

  /**
   * Return true if this server should be registered with the Ebean singleton
   * when it is created.
   * <p>
   * By default this is set to true.
   * </p>
   */
  public boolean isRegister() {
    return register;
  }

  /**
   * Set to false if you do not want this server to be registered with the Ebean
   * singleton when it is created.
   * <p>
   * By default this is set to true.
   * </p>
   */
  public void setRegister(boolean register) {
    this.register = register;
  }

  /**
   * Return true if this server should be registered as the "default" server
   * with the Ebean singleton.
   * <p>
   * This is only used when {@link #setRegister(boolean)} is also true.
   * </p>
   */
  public boolean isDefaultServer() {
    return defaultServer;
  }

  /**
   * Set false if you do not want this EbeanServer to be registered as the "default" server
   * with the Ebean singleton.
   * <p>
   * This is only used when {@link #setRegister(boolean)} is also true.
   * </p>
   */
  public void setDefaultServer(boolean defaultServer) {
    this.defaultServer = defaultServer;
  }

  /**
   * Return the CurrentUserProvider. This is used to populate @WhoCreated, @WhoModified and
   * support other audit features (who executed a query etc).
   */
  public CurrentUserProvider getCurrentUserProvider() {
    return currentUserProvider;
  }

  /**
   * Set the CurrentUserProvider. This is used to populate @WhoCreated, @WhoModified and
   * support other audit features (who executed a query etc).
   */
  public void setCurrentUserProvider(CurrentUserProvider currentUserProvider) {
    this.currentUserProvider = currentUserProvider;
  }

  /**
   * Return the tenancy mode used.
   */
  public TenantMode getTenantMode() {
    return tenantMode;
  }

  /**
   * Set the tenancy mode to use.
   */
  public void setTenantMode(TenantMode tenantMode) {
    this.tenantMode = tenantMode;
  }

  /**
   * Return the column name used for TenantMode.PARTITION.
   */
  public String getTenantPartitionColumn() {
    return tenantPartitionColumn;
  }

  /**
   * Set the column name used for TenantMode.PARTITION.
   */
  public void setTenantPartitionColumn(String tenantPartitionColumn) {
    this.tenantPartitionColumn = tenantPartitionColumn;
  }

  /**
   * Return the current tenant provider.
   */
  public CurrentTenantProvider getCurrentTenantProvider() {
    return currentTenantProvider;
  }

  /**
   * Set the current tenant provider.
   */
  public void setCurrentTenantProvider(CurrentTenantProvider currentTenantProvider) {
    this.currentTenantProvider = currentTenantProvider;
  }

  /**
   * Return the tenancy datasource provider.
   */
  public TenantDataSourceProvider getTenantDataSourceProvider() {
    return tenantDataSourceProvider;
  }

  /**
   * Set the tenancy datasource provider.
   */
  public void setTenantDataSourceProvider(TenantDataSourceProvider tenantDataSourceProvider) {
    this.tenantDataSourceProvider = tenantDataSourceProvider;
  }

  /**
   * Return the tenancy schema provider.
   */
  public TenantSchemaProvider getTenantSchemaProvider() {
    return tenantSchemaProvider;
  }

  /**
   * Set the tenancy schema provider.
   */
  public void setTenantSchemaProvider(TenantSchemaProvider tenantSchemaProvider) {
    this.tenantSchemaProvider = tenantSchemaProvider;
  }

  /**
   * Return the tenancy catalog provider.
   */
  public TenantCatalogProvider getTenantCatalogProvider() {
    return tenantCatalogProvider;
  }

  /**
   * Set the tenancy catalog provider.
   */
  public void setTenantCatalogProvider(TenantCatalogProvider tenantCatalogProvider) {
    this.tenantCatalogProvider = tenantCatalogProvider;
  }

  /**
   * Return the PersistBatch mode to use by default at the transaction level.
   * <p>
   * When INSERT or ALL is used then save(), delete() etc do not execute immediately but instead go into
   * a JDBC batch execute buffer that is flushed. The buffer is flushed if a query is executed, transaction ends
   * or the batch size is meet.
   * </p>
   */
  public PersistBatch getPersistBatch() {
    return persistBatch;
  }

  /**
   * Set the JDBC batch mode to use at the transaction level.
   * <p>
   * When INSERT or ALL is used then save(), delete() etc do not execute immediately but instead go into
   * a JDBC batch execute buffer that is flushed. The buffer is flushed if a query is executed, transaction ends
   * or the batch size is meet.
   * </p>
   */
  public void setPersistBatch(PersistBatch persistBatch) {
    this.persistBatch = persistBatch;
  }

  /**
   * Return the JDBC batch mode to use per save(), delete(), insert() or update() request.
   * <p>
   * This makes sense when a save() or delete() cascades and executes multiple child statements. The best case
   * for this is when saving a master/parent bean this cascade inserts many detail/child beans.
   * </p>
   * <p>
   * This only takes effect when the persistBatch mode at the transaction level does not take effect.
   * </p>
   */
  public PersistBatch getPersistBatchOnCascade() {
    return persistBatchOnCascade;
  }

  /**
   * Set the JDBC batch mode to use per save(), delete(), insert() or update() request.
   * <p>
   * This makes sense when a save() or delete() etc cascades and executes multiple child statements. The best caase
   * for this is when saving a master/parent bean this cascade inserts many detail/child beans.
   * </p>
   * <p>
   * This only takes effect when the persistBatch mode at the transaction level does not take effect.
   * </p>
   */
  public void setPersistBatchOnCascade(PersistBatch persistBatchOnCascade) {
    this.persistBatchOnCascade = persistBatchOnCascade;
  }

  /**
   * Deprecated, please migrate to using setPersistBatch().
   * <p>
   * Set to true if you what to use JDBC batching for persisting and deleting
   * beans.
   * </p>
   * <p>
   * With this Ebean will batch up persist requests and use the JDBC batch api.
   * This is a performance optimisation designed to reduce the network chatter.
   * </p>
   * <p>
   * When true this is equivalent to {@code setPersistBatch(PersistBatch.ALL)} or
   * when false to {@code setPersistBatch(PersistBatch.NONE)}
   * </p>
   */
  public void setPersistBatching(boolean persistBatching) {
    this.persistBatch = (persistBatching) ? PersistBatch.ALL : PersistBatch.NONE;
  }

  /**
   * Return the batch size used for JDBC batching. This defaults to 20.
   */
  public int getPersistBatchSize() {
    return persistBatchSize;
  }

  /**
   * Set the batch size used for JDBC batching. If unset this defaults to 20.
   * <p>
   * You can also set the batch size on the transaction.
   * </p>
   *
   * @see Transaction#setBatchSize(int)
   */
  public void setPersistBatchSize(int persistBatchSize) {
    this.persistBatchSize = persistBatchSize;
  }

  /**
   * Gets the query batch size. This defaults to 100.
   *
   * @return the query batch size
   */
  public int getQueryBatchSize() {
    return queryBatchSize;
  }

  /**
   * Sets the query batch size. This defaults to 100.
   *
   * @param queryBatchSize the new query batch size
   */
  public void setQueryBatchSize(int queryBatchSize) {
    this.queryBatchSize = queryBatchSize;
  }

  /**
   * Return the default batch size for lazy loading of beans and collections.
   */
  public int getLazyLoadBatchSize() {
    return lazyLoadBatchSize;
  }

  /**
   * Set the default batch size for lazy loading.
   * <p>
   * This is the number of beans or collections loaded when lazy loading is
   * invoked by default.
   * </p>
   * <p>
   * The default value is for this is 10 (load 10 beans or collections).
   * </p>
   * <p>
   * You can explicitly control the lazy loading batch size for a given join on
   * a query using +lazy(batchSize) or JoinConfig.
   * </p>
   */
  public void setLazyLoadBatchSize(int lazyLoadBatchSize) {
    this.lazyLoadBatchSize = lazyLoadBatchSize;
  }

  /**
   * Set the number of sequences to fetch/preallocate when using DB sequences.
   * <p>
   * This is a performance optimisation to reduce the number times Ebean
   * requests a sequence to be used as an Id for a bean (aka reduce network
   * chatter).
   * </p>
   */
  public void setDatabaseSequenceBatchSize(int databaseSequenceBatchSize) {
    this.databaseSequenceBatchSize = databaseSequenceBatchSize;
  }

  /**
   * Return the default JDBC fetchSize hint for findList queries.
   */
  public int getJdbcFetchSizeFindList() {
    return jdbcFetchSizeFindList;
  }

  /**
   * Set the default JDBC fetchSize hint for findList queries.
   */
  public void setJdbcFetchSizeFindList(int jdbcFetchSizeFindList) {
    this.jdbcFetchSizeFindList = jdbcFetchSizeFindList;
  }

  /**
   * Return the default JDBC fetchSize hint for findEach/findEachWhile queries.
   */
  public int getJdbcFetchSizeFindEach() {
    return jdbcFetchSizeFindEach;
  }

  /**
   * Set the default JDBC fetchSize hint for findEach/findEachWhile queries.
   */
  public void setJdbcFetchSizeFindEach(int jdbcFetchSizeFindEach) {
    this.jdbcFetchSizeFindEach = jdbcFetchSizeFindEach;
  }

  /**
   * Return the ChangeLogPrepare.
   * <p>
   * This is used to set user context information to the ChangeSet in the
   * foreground thread prior to the logging occurring in a background thread.
   * </p>
   */
  public ChangeLogPrepare getChangeLogPrepare() {
    return changeLogPrepare;
  }

  /**
   * Set the ChangeLogPrepare.
   * <p>
   * This is used to set user context information to the ChangeSet in the
   * foreground thread prior to the logging occurring in a background thread.
   * </p>
   */
  public void setChangeLogPrepare(ChangeLogPrepare changeLogPrepare) {
    this.changeLogPrepare = changeLogPrepare;
  }

  /**
   * Return the ChangeLogListener which actually performs the logging of change sets
   * in the background.
   */
  public ChangeLogListener getChangeLogListener() {
    return changeLogListener;
  }

  /**
   * Set the ChangeLogListener which actually performs the logging of change sets
   * in the background.
   */
  public void setChangeLogListener(ChangeLogListener changeLogListener) {
    this.changeLogListener = changeLogListener;
  }

  /**
   * Return the ChangeLogRegister which controls which ChangeLogFilter is used for each
   * bean type and in this way provide fine grained control over which persist requests
   * are included in the change log.
   */
  public ChangeLogRegister getChangeLogRegister() {
    return changeLogRegister;
  }

  /**
   * Set the ChangeLogRegister which controls which ChangeLogFilter is used for each
   * bean type and in this way provide fine grained control over which persist requests
   * are included in the change log.
   */
  public void setChangeLogRegister(ChangeLogRegister changeLogRegister) {
    this.changeLogRegister = changeLogRegister;
  }

  /**
   * Return true if inserts should be included in the change log by default.
   */
  public boolean isChangeLogIncludeInserts() {
    return changeLogIncludeInserts;
  }

  /**
   * Set if inserts should be included in the change log by default.
   */
  public void setChangeLogIncludeInserts(boolean changeLogIncludeInserts) {
    this.changeLogIncludeInserts = changeLogIncludeInserts;
  }

  /**
   * Return the ReadAuditLogger to use.
   */
  public ReadAuditLogger getReadAuditLogger() {
    return readAuditLogger;
  }

  /**
   * Set the ReadAuditLogger to use. If not set the default implementation is used
   * which logs the read events in JSON format to a standard named SLF4J logger
   * (which can be configured in say logback to log to a separate log file).
   */
  public void setReadAuditLogger(ReadAuditLogger readAuditLogger) {
    this.readAuditLogger = readAuditLogger;
  }

  /**
   * Return the ReadAuditPrepare to use.
   */
  public ReadAuditPrepare getReadAuditPrepare() {
    return readAuditPrepare;
  }

  /**
   * Set the ReadAuditPrepare to use.
   * <p>
   * It is expected that an implementation is used that read user context information
   * (user id, user ip address etc) and sets it on the ReadEvent bean before it is sent
   * to the ReadAuditLogger.
   * </p>
   */
  public void setReadAuditPrepare(ReadAuditPrepare readAuditPrepare) {
    this.readAuditPrepare = readAuditPrepare;
  }

  /**
   * Return the DB migration configuration.
   */
  public DbMigrationConfig getMigrationConfig() {
    return migrationConfig;
  }

  /**
   * Set the DB migration configuration.
   */
  public void setMigrationConfig(DbMigrationConfig migrationConfig) {
    this.migrationConfig = migrationConfig;
  }

  /**
   * Return the Geometry SRID.
   */
  public int getGeometrySRID() {
    return dbTypeConfig.getGeometrySRID();
  }

  /**
   * Set the Geometry SRID.
   */
  public void setGeometrySRID(int geometrySRID) {
    dbTypeConfig.setGeometrySRID(geometrySRID);
  }

  /**
   * Return the time zone to use when reading/writing Timestamps via JDBC.
   * <p>
   * When set a Calendar object is used in JDBC calls when reading/writing Timestamp objects.
   * </p>
   */
  public String getDataTimeZone() {
    return System.getProperty("ebean.dataTimeZone", dataTimeZone);
  }

  /**
   * Set the time zone to use when reading/writing Timestamps via JDBC.
   */
  public void setDataTimeZone(String dataTimeZone) {
    this.dataTimeZone = dataTimeZone;
  }

  /**
   * Return the suffix appended to the base table to derive the view that contains the union
   * of the base table and the history table in order to support asOf queries.
   */
  public String getAsOfViewSuffix() {
    return asOfViewSuffix;
  }

  /**
   * Set the suffix appended to the base table to derive the view that contains the union
   * of the base table and the history table in order to support asOf queries.
   */
  public void setAsOfViewSuffix(String asOfViewSuffix) {
    this.asOfViewSuffix = asOfViewSuffix;
  }

  /**
   * Return the database column used to support history and 'As of' queries. This column is a timestamp range
   * or equivalent.
   */
  public String getAsOfSysPeriod() {
    return asOfSysPeriod;
  }

  /**
   * Set the database column used to support history and 'As of' queries. This column is a timestamp range
   * or equivalent.
   */
  public void setAsOfSysPeriod(String asOfSysPeriod) {
    this.asOfSysPeriod = asOfSysPeriod;
  }

  /**
   * Return the history table suffix (defaults to _history).
   */
  public String getHistoryTableSuffix() {
    return historyTableSuffix;
  }

  /**
   * Set the history table suffix.
   */
  public void setHistoryTableSuffix(String historyTableSuffix) {
    this.historyTableSuffix = historyTableSuffix;
  }

  /**
   * Return true if we are running in a JTA Transaction manager.
   */
  public boolean isUseJtaTransactionManager() {
    return useJtaTransactionManager;
  }

  /**
   * Set to true if we are running in a JTA Transaction manager.
   */
  public void setUseJtaTransactionManager(boolean useJtaTransactionManager) {
    this.useJtaTransactionManager = useJtaTransactionManager;
  }

  /**
   * Return the external transaction manager.
   */
  public ExternalTransactionManager getExternalTransactionManager() {
    return externalTransactionManager;
  }

  /**
   * Set the external transaction manager.
   */
  public void setExternalTransactionManager(ExternalTransactionManager externalTransactionManager) {
    this.externalTransactionManager = externalTransactionManager;
  }

  /**
   * Return the ServerCachePlugin.
   */
  public ServerCachePlugin getServerCachePlugin() {
    return serverCachePlugin;
  }

  /**
   * Set the ServerCachePlugin to use.
   */
  public void setServerCachePlugin(ServerCachePlugin serverCachePlugin) {
    this.serverCachePlugin = serverCachePlugin;
  }

  /**
   * Return true if LOB's should default to fetch eager.
   * By default this is set to false and LOB's must be explicitly fetched.
   */
  public boolean isEagerFetchLobs() {
    return eagerFetchLobs;
  }

  /**
   * Set to true if you want LOB's to be fetch eager by default.
   * By default this is set to false and LOB's must be explicitly fetched.
   */
  public void setEagerFetchLobs(boolean eagerFetchLobs) {
    this.eagerFetchLobs = eagerFetchLobs;
  }

  /**
   * Return the max call stack to use for origin location.
   */
  public int getMaxCallStack() {
    return maxCallStack;
  }

  /**
   * Set the max call stack to use for origin location.
   */
  public void setMaxCallStack(int maxCallStack) {
    this.maxCallStack = maxCallStack;
  }

  /**
   * Return true if transactions should rollback on checked exceptions.
   */
  public boolean isTransactionRollbackOnChecked() {
    return transactionRollbackOnChecked;
  }

  /**
   * Set to true if transactions should by default rollback on checked exceptions.
   */
  public void setTransactionRollbackOnChecked(boolean transactionRollbackOnChecked) {
    this.transactionRollbackOnChecked = transactionRollbackOnChecked;
  }

  /**
   * Return the Background executor schedule pool size. Defaults to 1.
   */
  public int getBackgroundExecutorSchedulePoolSize() {
    return backgroundExecutorSchedulePoolSize;
  }

  /**
   * Set the Background executor schedule pool size.
   */
  public void setBackgroundExecutorSchedulePoolSize(int backgroundExecutorSchedulePoolSize) {
    this.backgroundExecutorSchedulePoolSize = backgroundExecutorSchedulePoolSize;
  }

  /**
   * Return the Background executor shutdown seconds. This is the time allowed for the pool to shutdown nicely
   * before it is forced shutdown.
   */
  public int getBackgroundExecutorShutdownSecs() {
    return backgroundExecutorShutdownSecs;
  }

  /**
   * Set the Background executor shutdown seconds. This is the time allowed for the pool to shutdown nicely
   * before it is forced shutdown.
   */
  public void setBackgroundExecutorShutdownSecs(int backgroundExecutorShutdownSecs) {
    this.backgroundExecutorShutdownSecs = backgroundExecutorShutdownSecs;
  }

  /**
   * Return the L2 cache default max size.
   */
  public int getCacheMaxSize() {
    return cacheMaxSize;
  }

  /**
   * Set the L2 cache default max size.
   */
  public void setCacheMaxSize(int cacheMaxSize) {
    this.cacheMaxSize = cacheMaxSize;
  }

  /**
   * Return the L2 cache default max idle time in seconds.
   */
  public int getCacheMaxIdleTime() {
    return cacheMaxIdleTime;
  }

  /**
   * Set the L2 cache default max idle time in seconds.
   */
  public void setCacheMaxIdleTime(int cacheMaxIdleTime) {
    this.cacheMaxIdleTime = cacheMaxIdleTime;
  }

  /**
   * Return the L2 cache default max time to live in seconds.
   */
  public int getCacheMaxTimeToLive() {
    return cacheMaxTimeToLive;
  }

  /**
   * Set the L2 cache default max time to live in seconds.
   */
  public void setCacheMaxTimeToLive(int cacheMaxTimeToLive) {
    this.cacheMaxTimeToLive = cacheMaxTimeToLive;
  }

  /**
   * Return the L2 query cache default max size.
   */
  public int getQueryCacheMaxSize() {
    return queryCacheMaxSize;
  }

  /**
   * Set the L2 query cache default max size.
   */
  public void setQueryCacheMaxSize(int queryCacheMaxSize) {
    this.queryCacheMaxSize = queryCacheMaxSize;
  }

  /**
   * Return the L2 query cache default max idle time in seconds.
   */
  public int getQueryCacheMaxIdleTime() {
    return queryCacheMaxIdleTime;
  }

  /**
   * Set the L2 query cache default max idle time in seconds.
   */
  public void setQueryCacheMaxIdleTime(int queryCacheMaxIdleTime) {
    this.queryCacheMaxIdleTime = queryCacheMaxIdleTime;
  }

  /**
   * Return the L2 query cache default max time to live in seconds.
   */
  public int getQueryCacheMaxTimeToLive() {
    return queryCacheMaxTimeToLive;
  }

  /**
   * Set the L2 query cache default max time to live in seconds.
   */
  public void setQueryCacheMaxTimeToLive(int queryCacheMaxTimeToLive) {
    this.queryCacheMaxTimeToLive = queryCacheMaxTimeToLive;
  }

  /**
   * Return the NamingConvention.
   * <p>
   * If none has been set the default UnderscoreNamingConvention is used.
   * </p>
   */
  public NamingConvention getNamingConvention() {
    return namingConvention;
  }

  /**
   * Set the NamingConvention.
   * <p>
   * If none is set the default UnderscoreNamingConvention is used.
   * </p>
   */
  public void setNamingConvention(NamingConvention namingConvention) {
    this.namingConvention = namingConvention;
  }

  /**
   * Return true if this EbeanServer is a Document store only instance (has no JDBC DB).
   */
  public boolean isDocStoreOnly() {
    return docStoreOnly;
  }

  /**
   * Set to true if this EbeanServer is Document store only instance (has no JDBC DB).
   */
  public void setDocStoreOnly(boolean docStoreOnly) {
    this.docStoreOnly = docStoreOnly;
  }

  /**
   * Return the configuration for the ElasticSearch integration.
   */
  public DocStoreConfig getDocStoreConfig() {
    return docStoreConfig;
  }

  /**
   * Set the configuration for the ElasticSearch integration.
   */
  public void setDocStoreConfig(DocStoreConfig docStoreConfig) {
    this.docStoreConfig = docStoreConfig;
  }

  /**
   * Return the constraint naming convention used in DDL generation.
   */
  public DbConstraintNaming getConstraintNaming() {
    return constraintNaming;
  }

  /**
   * Set the constraint naming convention used in DDL generation.
   */
  public void setConstraintNaming(DbConstraintNaming constraintNaming) {
    this.constraintNaming = constraintNaming;
  }

  /**
   * Return the configuration for AutoTune.
   */
  public AutoTuneConfig getAutoTuneConfig() {
    return autoTuneConfig;
  }

  /**
   * Set the configuration for AutoTune.
   */
  public void setAutoTuneConfig(AutoTuneConfig autoTuneConfig) {
    this.autoTuneConfig = autoTuneConfig;
  }

  /**
   * Return the DataSource.
   */
  public DataSource getDataSource() {
    return dataSource;
  }

  /**
   * Set a DataSource.
   */
  public void setDataSource(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  /**
   * Return the configuration to build a DataSource using Ebean's own DataSource
   * implementation.
   */
  public DataSourceConfig getDataSourceConfig() {
    return dataSourceConfig;
  }

  /**
   * Set the configuration required to build a DataSource using Ebean's own
   * DataSource implementation.
   */
  public void setDataSourceConfig(DataSourceConfig dataSourceConfig) {
    this.dataSourceConfig = dataSourceConfig;
  }

  /**
   * Return the JNDI name of the DataSource to use.
   */
  public String getDataSourceJndiName() {
    return dataSourceJndiName;
  }

  /**
   * Set the JNDI name of the DataSource to use.
   * <p>
   * By default a prefix of "java:comp/env/jdbc/" is used to lookup the
   * DataSource. This prefix is not used if dataSourceJndiName starts with
   * "java:".
   * </p>
   */
  public void setDataSourceJndiName(String dataSourceJndiName) {
    this.dataSourceJndiName = dataSourceJndiName;
  }

  /**
   * Return true if autoCommit mode is on. This indicates to Ebean to use autoCommit friendly Transactions and TransactionManager.
   */
  public boolean isAutoCommitMode() {
    return autoCommitMode;
  }

  /**
   * Set to true if autoCommit mode is on and Ebean should use autoCommit friendly Transactions and TransactionManager.
   */
  public void setAutoCommitMode(boolean autoCommitMode) {
    this.autoCommitMode = autoCommitMode;
  }

  /**
   * Return true if transaction begin should be started with explicit statement.
   */
  public boolean isExplicitTransactionBeginMode() {
    return explicitTransactionBeginMode;
  }

  /**
   * Set to true if transaction begin should be started with explicit statement.
   * <p>
   * This works for H2 and Postgres but not for Oracle - only use this if you first name
   * is Daryl or you have explicitly talked to Rob about this feature.
   * </p>
   * <p>
   * This is generally not expected to be turned on but instead allow transactions to start
   * implicitly which is generally the standard approach.
   * </p>
   */
  public void setExplicitTransactionBeginMode(boolean explicitTransactionBeginMode) {
    this.explicitTransactionBeginMode = explicitTransactionBeginMode;
  }

  /**
   * Return a value used to represent TRUE in the database.
   * <p>
   * This is used for databases that do not support boolean natively.
   * </p>
   * <p>
   * The value returned is either a Integer or a String (e.g. "1", or "T").
   * </p>
   */
  public String getDatabaseBooleanTrue() {
    return databaseBooleanTrue;
  }

  /**
   * Set the value to represent TRUE in the database.
   * <p>
   * This is used for databases that do not support boolean natively.
   * </p>
   * <p>
   * The value set is either a Integer or a String (e.g. "1", or "T").
   * </p>
   */
  public void setDatabaseBooleanTrue(String databaseTrue) {
    this.databaseBooleanTrue = databaseTrue;
  }

  /**
   * Return a value used to represent FALSE in the database.
   * <p>
   * This is used for databases that do not support boolean natively.
   * </p>
   * <p>
   * The value returned is either a Integer or a String (e.g. "0", or "F").
   * </p>
   */
  public String getDatabaseBooleanFalse() {
    return databaseBooleanFalse;
  }

  /**
   * Set the value to represent FALSE in the database.
   * <p>
   * This is used for databases that do not support boolean natively.
   * </p>
   * <p>
   * The value set is either a Integer or a String (e.g. "0", or "F").
   * </p>
   */
  public void setDatabaseBooleanFalse(String databaseFalse) {
    this.databaseBooleanFalse = databaseFalse;
  }

  /**
   * Return the number of DB sequence values that should be preallocated.
   */
  public int getDatabaseSequenceBatchSize() {
    return databaseSequenceBatchSize;
  }

  /**
   * Set the number of DB sequence values that should be preallocated and cached
   * by Ebean.
   * <p>
   * This is only used for DB's that use sequences and is a performance
   * optimisation. This reduces the number of times Ebean needs to get a
   * sequence value from the Database reducing network chatter.
   * </p>
   * <p>
   * By default this value is 10 so when we need another Id (and don't have one
   * in our cache) Ebean will fetch 10 id's from the database. Note that when
   * the cache drops to have full (which is 5 by default) Ebean will fetch
   * another batch of Id's in a background thread.
   * </p>
   */
  public void setDatabaseSequenceBatch(int databaseSequenceBatchSize) {
    this.databaseSequenceBatchSize = databaseSequenceBatchSize;
  }

  /**
   * Return the database platform name (can be null).
   * <p>
   * If null then the platform is determined automatically via the JDBC driver
   * information.
   * </p>
   */
  public String getDatabasePlatformName() {
    return databasePlatformName;
  }

  /**
   * Explicitly set the database platform name
   * <p>
   * If none is set then the platform is determined automatically via the JDBC
   * driver information.
   * </p>
   * <p>
   * This can be used when the Database Platform can not be automatically
   * detected from the JDBC driver (possibly 3rd party JDBC driver). It is also
   * useful when you want to do offline DDL generation for a database platform
   * that you don't have access to.
   * </p>
   * <p>
   * Values are oracle, h2, postgres, mysql, mssqlserver2005.
   * </p>
   */
  public void setDatabasePlatformName(String databasePlatformName) {
    this.databasePlatformName = databasePlatformName;
  }

  /**
   * Return the database platform to use for this server.
   */
  public DatabasePlatform getDatabasePlatform() {
    return databasePlatform;
  }

  /**
   * Explicitly set the database platform to use.
   * <p>
   * If none is set then the platform is determined via the databasePlatformName
   * or automatically via the JDBC driver information.
   * </p>
   */
  public void setDatabasePlatform(DatabasePlatform databasePlatform) {
    this.databasePlatform = databasePlatform;
    if (!h2ProductionMode && databasePlatform != null && databasePlatform.isPlatform(Platform.H2)) {
      // we are using H2 to run tests so turn on DDL generation and run
      this.ddlGenerate = true;
      this.ddlRun = true;
    }
  }

  /**
   * Return the EncryptKeyManager.
   */
  public EncryptKeyManager getEncryptKeyManager() {
    return encryptKeyManager;
  }

  /**
   * Set the EncryptKeyManager.
   * <p>
   * This is required when you want to use encrypted properties.
   * </p>
   * <p>
   * You can also set this in ebean.proprerties:
   * </p>
   * <p>
   * <pre>{@code
   * # set via ebean.properties
   * ebean.encryptKeyManager=org.avaje.tests.basic.encrypt.BasicEncyptKeyManager
   * }</pre>
   */
  public void setEncryptKeyManager(EncryptKeyManager encryptKeyManager) {
    this.encryptKeyManager = encryptKeyManager;
  }

  /**
   * Return the EncryptDeployManager.
   * <p>
   * This is optionally used to programmatically define which columns are
   * encrypted instead of using the {@link Encrypted} Annotation.
   * </p>
   */
  public EncryptDeployManager getEncryptDeployManager() {
    return encryptDeployManager;
  }

  /**
   * Set the EncryptDeployManager.
   * <p>
   * This is optionally used to programmatically define which columns are
   * encrypted instead of using the {@link Encrypted} Annotation.
   * </p>
   */
  public void setEncryptDeployManager(EncryptDeployManager encryptDeployManager) {
    this.encryptDeployManager = encryptDeployManager;
  }

  /**
   * Return the Encryptor used to encrypt data on the java client side (as
   * opposed to DB encryption functions).
   */
  public Encryptor getEncryptor() {
    return encryptor;
  }

  /**
   * Set the Encryptor used to encrypt data on the java client side (as opposed
   * to DB encryption functions).
   * <p>
   * Ebean has a default implementation that it will use if you do not set your
   * own Encryptor implementation.
   * </p>
   */
  public void setEncryptor(Encryptor encryptor) {
    this.encryptor = encryptor;
  }

  /**
   * Return true if the EbeanServer instance should be created in offline mode.
   */
  public boolean isDbOffline() {
    return dbOffline;
  }

  /**
   * Set to true if the EbeanServer instance should be created in offline mode.
   * <p>
   * Typically used to create an EbeanServer instance for DDL Migration generation
   * without requiring a real DataSource / Database to connect to.
   * </p>
   */
  public void setDbOffline(boolean dbOffline) {
    this.dbOffline = dbOffline;
  }

  /**
   * Return the DbEncrypt used to encrypt and decrypt properties.
   * <p>
   * Note that if this is not set then the DbPlatform may already have a
   * DbEncrypt set and that will be used.
   * </p>
   */
  public DbEncrypt getDbEncrypt() {
    return dbEncrypt;
  }

  /**
   * Set the DbEncrypt used to encrypt and decrypt properties.
   * <p>
   * Note that if this is not set then the DbPlatform may already have a
   * DbEncrypt set (H2, MySql, Postgres and Oracle platforms have a DbEncrypt)
   * </p>
   */
  public void setDbEncrypt(DbEncrypt dbEncrypt) {
    this.dbEncrypt = dbEncrypt;
  }

  /**
   * Return the configuration for DB types (such as UUID and custom mappings).
   */
  public DbTypeConfig getDbTypeConfig() {
    return dbTypeConfig;
  }

  /**
   * Set the DB type used to store UUID.
   */
  public void setDbUuid(DbUuid dbUuid) {
    this.dbTypeConfig.setDbUuid(dbUuid);
  }

  /**
   * Return true if LocalTime should be persisted with nanos precision.
   */
  public boolean isLocalTimeWithNanos() {
    return localTimeWithNanos;
  }

  /**
   * Set to true if LocalTime should be persisted with nanos precision.
   * <p>
   * Otherwise it is persisted using java.sql.Time which is seconds precision.
   * </p>
   */
  public void setLocalTimeWithNanos(boolean localTimeWithNanos) {
    this.localTimeWithNanos = localTimeWithNanos;
  }

  /**
   * Return true if Duration should be persisted with nanos precision (SQL DECIMAL).
   * <p>
   * Otherwise it is persisted with second precision (SQL INTEGER).
   * </p>
   */
  public boolean isDurationWithNanos() {
    return durationWithNanos;
  }

  /**
   * Set to true if Duration should be persisted with nanos precision (SQL DECIMAL).
   * <p>
   * Otherwise it is persisted with second precision (SQL INTEGER).
   * </p>
   */
  public void setDurationWithNanos(boolean durationWithNanos) {
    this.durationWithNanos = durationWithNanos;
  }

  /**
   * Set to true to generate the "create all" DDL on startup.
   * <p>
   * Typically we want this on when we are running tests locally (and often using H2)
   * and we want to create the full DB schema from scratch to run tests.
   */
  public void setDdlGenerate(boolean ddlGenerate) {
    this.ddlGenerate = ddlGenerate;
  }

  /**
   * Set to true to run the generated "create all DDL" on startup.
   * <p>
   * Typically we want this on when we are running tests locally (and often using H2)
   * and we want to create the full DB schema from scratch to run tests.
   */
  public void setDdlRun(boolean ddlRun) {
    this.ddlRun = ddlRun;
  }

  /**
   * Return true if the "drop all ddl" should be skipped.
   * <p>
   * Typically we want to do this when using H2 (in memory) as our test database and the drop statements
   * are not required so skipping the drop table statements etc makes it faster with less noise in the logs.
   */
  public boolean isDdlCreateOnly() {
    return ddlCreateOnly;
  }

  /**
   * Set to true if the "drop all ddl" should be skipped.
   * <p>
   * Typically we want to do this when using H2 (in memory) as our test database and the drop statements
   * are not required so skipping the drop table statements etc makes it faster with less noise in the logs.
   */
  public void setDdlCreateOnly(boolean ddlCreateOnly) {
    this.ddlCreateOnly = ddlCreateOnly;
  }

  /**
   * Return SQL script to execute after the "create all" DDL has been run.
   * <p>
   * Typically this is a sql script that inserts test seed data when running tests.
   * Place a sql script in src/test/resources that inserts test seed data.
   * </p>
   */
  public String getDdlSeedSql() {
    return ddlSeedSql;
  }

  /**
   * Set a SQL script to execute after the "create all" DDL has been run.
   * <p>
   * Typically this is a sql script that inserts test seed data when running tests.
   * Place a sql script in src/test/resources that inserts test seed data.
   * </p>
   */
  public void setDdlSeedSql(String ddlSeedSql) {
    this.ddlSeedSql = ddlSeedSql;
  }

  /**
   * Return a SQL script to execute before the "create all" DDL has been run.
   */
  public String getDdlInitSql() {
    return ddlInitSql;
  }

  /**
   * Set a SQL script to execute before the "create all" DDL has been run.
   */
  public void setDdlInitSql(String ddlInitSql) {
    this.ddlInitSql = ddlInitSql;
  }

  /**
   * Return true if the DDL should be generated.
   */
  public boolean isDdlGenerate() {
    return ddlGenerate;
  }

  /**
   * Return true if the DDL should be run.
   */
  public boolean isDdlRun() {
    return ddlRun;
  }

  /**
   * Return true if the class path search should be disabled.
   */
  public boolean isDisableClasspathSearch() {
    return disableClasspathSearch;
  }

  /**
   * Set to true to disable the class path search even for the case where no entity bean classes
   * have been registered. This can be used to start an EbeanServer instance just to use the
   * SQL functions such as SqlQuery, SqlUpdate etc.
   */
  public void setDisableClasspathSearch(boolean disableClasspathSearch) {
    this.disableClasspathSearch = disableClasspathSearch;
  }

  /**
   * Return the mode to use for Joda LocalTime support 'normal' or 'utc'.
   */
  public String getJodaLocalTimeMode() {
    return jodaLocalTimeMode;
  }

  /**
   * Set the mode to use for Joda LocalTime support 'normal' or 'utc'.
   */
  public void setJodaLocalTimeMode(String jodaLocalTimeMode) {
    this.jodaLocalTimeMode = jodaLocalTimeMode;
  }

  /**
   * Programmatically add classes (typically entities) that this server should
   * use.
   * <p>
   * The class can be an Entity, Embedded type, ScalarType, BeanPersistListener,
   * BeanFinder or BeanPersistController.
   * </p>
   * <p>
   * If no classes are specified then the classes are found automatically via
   * searching the class path.
   * </p>
   * <p>
   * Alternatively the classes can be added via {@link #setClasses(List)}.
   * </p>
   *
   * @param cls the entity type (or other type) that should be registered by this
   *            server.
   */
  public void addClass(Class<?> cls) {
    if (classes == null) {
      classes = new ArrayList<>();
    }
    classes.add(cls);
  }

  /**
   * Add a package to search for entities via class path search.
   * <p>
   * This is only used if classes have not been explicitly specified.
   * </p>
   */
  public void addPackage(String packageName) {
    if (packages == null) {
      packages = new ArrayList<>();
    }
    packages.add(packageName);
  }

  /**
   * Return packages to search for entities via class path search.
   * <p>
   * This is only used if classes have not been explicitly specified.
   * </p>
   */
  public List<String> getPackages() {
    return packages;
  }

  /**
   * Set packages to search for entities via class path search.
   * <p>
   * This is only used if classes have not been explicitly specified.
   * </p>
   */
  public void setPackages(List<String> packages) {
    this.packages = packages;
  }

  /**
   * Set the list of classes (entities, listeners, scalarTypes etc) that should
   * be used for this server.
   * <p>
   * If no classes are specified then the classes are found automatically via
   * searching the class path.
   * </p>
   * <p>
   * Alternatively the classes can contain added via {@link #addClass(Class)}.
   * </p>
   */
  public void setClasses(List<Class<?>> classes) {
    this.classes = classes;
  }

  /**
   * Return the classes registered for this server. Typically this includes
   * entities and perhaps listeners.
   */
  public List<Class<?>> getClasses() {
    return classes;
  }

  /**
   * Return true if L2 bean cache should be skipped once writes have occurred on a transaction.
   * <p>
   * This defaults to true and means that for "find by id" and "find by natural key"
   * queries that normally hit L2 bean cache automatically will not do so after a write/persist
   * on the transaction.
   * </p>
   * <p>
   * <pre>{@code
   *
   *   // assume Customer has L2 bean caching enabled ...
   *
   *   Transaction transaction = Ebean.beginTransaction();
   *   try {
   *
   *     // this uses L2 bean cache as the transaction
   *     // ... is considered "query only" at this point
   *     Customer.find.byId(42);
   *
   *     // transaction no longer "query only" once
   *     // ... a bean has been saved etc
   *     Ebean.save(someBean);
   *
   *     // will NOT use L2 bean cache as the transaction
   *     // ... is no longer considered "query only"
   *     Customer.find.byId(55);
   *
   *
   *
   *     // explicit control - please use L2 bean cache
   *
   *     transaction.setSkipCache(false);
   *     Customer.find.byId(77); // hit the l2 bean cache
   *
   *
   *     // explicit control - please don't use L2 bean cache
   *
   *     transaction.setSkipCache(true);
   *     Customer.find.byId(99); // skips l2 bean cache
   *
   *
   *   } finally {
   *     transaction.end();
   *   }
   *
   * }</pre>
   *
   * @see Transaction#setSkipCache(boolean)
   */
  public boolean isSkipCacheAfterWrite() {
    return skipCacheAfterWrite;
  }

  /**
   * Set to false when we still want to hit the cache after a write has occurred on a transaction.
   */
  public void setSkipCacheAfterWrite(boolean skipCacheAfterWrite) {
    this.skipCacheAfterWrite = skipCacheAfterWrite;
  }

  /**
   * Return true to only update changed properties.
   */
  public boolean isUpdateChangesOnly() {
    return updateChangesOnly;
  }

  /**
   * Set to true to only update changed properties.
   */
  public void setUpdateChangesOnly(boolean updateChangesOnly) {
    this.updateChangesOnly = updateChangesOnly;
  }

  /**
   * Returns true if updates in JDBC batch default to include all properties by default.
   */
  public boolean isUpdateAllPropertiesInBatch() {
    return updateAllPropertiesInBatch;
  }

  /**
   * Set to false if by default updates in JDBC batch should not include all properties.
   * <p>
   * This mode can be explicitly set per transaction.
   * </p>
   *
   * @see Transaction#setUpdateAllLoadedProperties(boolean)
   */
  public void setUpdateAllPropertiesInBatch(boolean updateAllPropertiesInBatch) {
    this.updateAllPropertiesInBatch = updateAllPropertiesInBatch;
  }

  /**
   * Return true if updates by default delete missing children when cascading save to a OneToMany or
   * ManyToMany. When not set this defaults to true.
   */
  public boolean isUpdatesDeleteMissingChildren() {
    return updatesDeleteMissingChildren;
  }

  /**
   * Set if updates by default delete missing children when cascading save to a OneToMany or
   * ManyToMany. When not set this defaults to true.
   */
  public void setUpdatesDeleteMissingChildren(boolean updatesDeleteMissingChildren) {
    this.updatesDeleteMissingChildren = updatesDeleteMissingChildren;
  }

  /**
   * Return true if the ebeanServer should collection query statistics by ObjectGraphNode.
   */
  public boolean isCollectQueryStatsByNode() {
    return collectQueryStatsByNode;
  }

  /**
   * Set to true to collection query execution statistics by ObjectGraphNode.
   * <p>
   * These statistics can be used to highlight code/query 'origin points' that result in lots of lazy loading.
   * </p>
   * <p>
   * It is considered safe/fine to have this set to true for production.
   * </p>
   * <p>
   * This information can be later retrieved via {@link MetaInfoManager}.
   * </p>
   *
   * @see MetaInfoManager
   */
  public void setCollectQueryStatsByNode(boolean collectQueryStatsByNode) {
    this.collectQueryStatsByNode = collectQueryStatsByNode;
  }

  /**
   * Return true if query plans should also collect their 'origins'. This means for a given query plan you
   * can identify the code/origin points where this query resulted from including lazy loading origins.
   */
  public boolean isCollectQueryOrigins() {
    return collectQueryOrigins;
  }

  /**
   * Set to true if query plans should collect their 'origin' points. This means for a given query plan you
   * can identify the code/origin points where this query resulted from including lazy loading origins.
   * <p>
   * This information can be later retrieved via {@link MetaInfoManager}.
   * </p>
   *
   * @see MetaInfoManager
   */
  public void setCollectQueryOrigins(boolean collectQueryOrigins) {
    this.collectQueryOrigins = collectQueryOrigins;
  }

  /**
   * Returns the resource directory.
   */
  public String getResourceDirectory() {
    return resourceDirectory;
  }

  /**
   * Sets the resource directory.
   */
  public void setResourceDirectory(String resourceDirectory) {
    this.resourceDirectory = resourceDirectory;
  }

  /**
   * Add a custom type mapping.
   * <p>
   * <pre>{@code
   *
   *   // set the default mapping for BigDecimal.class/decimal
   *   serverConfig.addCustomMapping(DbType.DECIMAL, "decimal(18,6)");
   *
   *   // set the default mapping for String.class/varchar but only for Postgres
   *   serverConfig.addCustomMapping(DbType.VARCHAR, "text", Platform.POSTGRES);
   *
   * }</pre>
   *
   * @param type             The DB type this mapping should apply to
   * @param columnDefinition The column definition that should be used
   * @param platform         Optionally specify the platform this mapping should apply to.
   */
  public void addCustomMapping(DbType type, String columnDefinition, Platform platform) {
    dbTypeConfig.addCustomMapping(type, columnDefinition, platform);
  }

  /**
   * Add a custom type mapping that applies to all platforms.
   * <p>
   * <pre>{@code
   *
   *   // set the default mapping for BigDecimal/decimal
   *   serverConfig.addCustomMapping(DbType.DECIMAL, "decimal(18,6)");
   *
   *   // set the default mapping for String/varchar
   *   serverConfig.addCustomMapping(DbType.VARCHAR, "text");
   *
   * }</pre>
   *
   * @param type             The DB type this mapping should apply to
   * @param columnDefinition The column definition that should be used
   */
  public void addCustomMapping(DbType type, String columnDefinition) {
    dbTypeConfig.addCustomMapping(type, columnDefinition);
  }

  /**
   * Register a BeanQueryAdapter instance.
   * <p>
   * Note alternatively you can use {@link #setQueryAdapters(List)} to set all
   * the BeanQueryAdapter instances.
   * </p>
   */
  public void add(BeanQueryAdapter beanQueryAdapter) {
    queryAdapters.add(beanQueryAdapter);
  }

  /**
   * Return the BeanQueryAdapter instances.
   */
  public List<BeanQueryAdapter> getQueryAdapters() {
    return queryAdapters;
  }

  /**
   * Register all the BeanQueryAdapter instances.
   * <p>
   * Note alternatively you can use {@link #add(BeanQueryAdapter)} to add
   * BeanQueryAdapter instances one at a time.
   * </p>
   */
  public void setQueryAdapters(List<BeanQueryAdapter> queryAdapters) {
    this.queryAdapters = queryAdapters;
  }

  /**
   * Return the custom IdGenerator instances.
   */
  public List<IdGenerator> getIdGenerators() {
    return idGenerators;
  }

  /**
   * Set the custom IdGenerator instances.
   */
  public void setIdGenerators(List<IdGenerator> idGenerators) {
    this.idGenerators = idGenerators;
  }

  /**
   * Register a customer IdGenerator instance.
   */
  public void add(IdGenerator idGenerator) {
    idGenerators.add(idGenerator);
  }

  /**
   * Register a BeanPersistController instance.
   * <p>
   * Note alternatively you can use {@link #setPersistControllers(List)} to set
   * all the BeanPersistController instances.
   * </p>
   */
  public void add(BeanPersistController beanPersistController) {
    persistControllers.add(beanPersistController);
  }

  /**
   * Register a BeanPostLoad instance.
   * <p>
   * Note alternatively you can use {@link #setPostLoaders(List)} to set
   * all the BeanPostLoad instances.
   * </p>
   */
  public void add(BeanPostLoad postLoad) {
    postLoaders.add(postLoad);
  }

  /**
   * Register a BeanPostConstructListener instance.
   * <p>
   * Note alternatively you can use {@link #setPostConstructListeners(List)} to set
   * all the BeanPostConstructListener instances.
   * </p>
   */
  public void add(BeanPostConstructListener listener) {
    postConstructListeners.add(listener);
  }

  /**
   * Return the list of BeanFindController instances.
   */
  public List<BeanFindController> getFindControllers() {
    return findControllers;
  }

  /**
   * Set the list of BeanFindController instances.
   */
  public void setFindControllers(List<BeanFindController> findControllers) {
    this.findControllers = findControllers;
  }

  /**
   * Return the list of BeanPostLoader instances.
   */
  public List<BeanPostLoad> getPostLoaders() {
    return postLoaders;
  }

  /**
   * Set the list of BeanPostLoader instances.
   */
  public void setPostLoaders(List<BeanPostLoad> postLoaders) {
    this.postLoaders = postLoaders;
  }

  /**
   * Return the list of BeanPostLoader instances.
   */
  public List<BeanPostConstructListener> getPostConstructListeners() {
    return postConstructListeners;
  }

  /**
   * Set the list of BeanPostLoader instances.
   */
  public void setPostConstructListeners(List<BeanPostConstructListener> listeners) {
    this.postConstructListeners = listeners;
  }

  /**
   * Return the BeanPersistController instances.
   */
  public List<BeanPersistController> getPersistControllers() {
    return persistControllers;
  }

  /**
   * Register all the BeanPersistController instances.
   * <p>
   * Note alternatively you can use {@link #add(BeanPersistController)} to add
   * BeanPersistController instances one at a time.
   * </p>
   */
  public void setPersistControllers(List<BeanPersistController> persistControllers) {
    this.persistControllers = persistControllers;
  }

  /**
   * Register a BeanPersistListener instance.
   * <p>
   * Note alternatively you can use {@link #setPersistListeners(List)} to set
   * all the BeanPersistListener instances.
   * </p>
   */
  public void add(BeanPersistListener beanPersistListener) {
    persistListeners.add(beanPersistListener);
  }

  /**
   * Return the BeanPersistListener instances.
   */
  public List<BeanPersistListener> getPersistListeners() {
    return persistListeners;
  }

  /**
   * Add a BulkTableEventListener
   */
  public void add(BulkTableEventListener bulkTableEventListener) {
    bulkTableEventListeners.add(bulkTableEventListener);
  }

  /**
   * Return the list of BulkTableEventListener instances.
   */
  public List<BulkTableEventListener> getBulkTableEventListeners() {
    return bulkTableEventListeners;
  }

  /**
   * Add a ServerConfigStartup.
   */
  public void addServerConfigStartup(ServerConfigStartup configStartupListener) {
    configStartupListeners.add(configStartupListener);
  }

  /**
   * Return the list of ServerConfigStartup instances.
   */
  public List<ServerConfigStartup> getServerConfigStartupListeners() {
    return configStartupListeners;
  }

  /**
   * Register all the BeanPersistListener instances.
   * <p>
   * Note alternatively you can use {@link #add(BeanPersistListener)} to add
   * BeanPersistListener instances one at a time.
   * </p>
   */
  public void setPersistListeners(List<BeanPersistListener> persistListeners) {
    this.persistListeners = persistListeners;
  }

  /**
   * Return the default PersistenceContextScope to be used if one is not explicitly set on a query.
   * <p/>
   * The PersistenceContextScope can specified on each query via {@link io.ebean
   * .Query#setPersistenceContextScope(io.ebean.PersistenceContextScope)}. If it
   * is not set on the query this default scope is used.
   *
   * @see Query#setPersistenceContextScope(PersistenceContextScope)
   */
  public PersistenceContextScope getPersistenceContextScope() {
    // if somehow null return TRANSACTION scope
    return persistenceContextScope == null ? PersistenceContextScope.TRANSACTION : persistenceContextScope;
  }

  /**
   * Set the PersistenceContext scope to be used if one is not explicitly set on a query.
   * <p/>
   * This defaults to {@link PersistenceContextScope#TRANSACTION}.
   * <p/>
   * The PersistenceContextScope can specified on each query via {@link io.ebean
   * .Query#setPersistenceContextScope(io.ebean.PersistenceContextScope)}. If it
   * is not set on the query this scope is used.
   *
   * @see Query#setPersistenceContextScope(PersistenceContextScope)
   */
  public void setPersistenceContextScope(PersistenceContextScope persistenceContextScope) {
    this.persistenceContextScope = persistenceContextScope;
  }

  /**
   * Return the ClassLoadConfig which is used to detect Joda, Java8 types etc and also
   * create new instances of plugins given a className.
   */
  public ClassLoadConfig getClassLoadConfig() {
    return classLoadConfig;
  }

  /**
   * Set the ClassLoadConfig which is used to detect Joda, Java8 types etc and also
   * create new instances of plugins given a className.
   */
  public void setClassLoadConfig(ClassLoadConfig classLoadConfig) {
    this.classLoadConfig = classLoadConfig;
  }

  /**
   * Return the service loader using the classLoader defined in ClassLoadConfig.
   */
  public <T> ServiceLoader<T> serviceLoad(Class<T> spiService) {

    return ServiceLoader.load(spiService, classLoadConfig.getClassLoader());
  }

  /**
   * Return the first service using the service loader (or null).
   */
  public <T> T service(Class<T> spiService) {
    ServiceLoader<T> load = serviceLoad(spiService);
    Iterator<T> serviceInstances = load.iterator();
    return serviceInstances.hasNext() ? serviceInstances.next() : null;
  }

  /**
   * Load settings from ebean.properties.
   */
  public void loadFromProperties() {
    loadFromProperties(PropertyMap.defaultProperties());
  }

  /**
   * Load the settings from the given properties
   */
  public void loadFromProperties(Properties properties) {
    // keep the properties used for configuration so that these are available for plugins
    this.properties = properties;
    PropertiesWrapper p = new PropertiesWrapper("ebean", name, properties);
    loadSettings(p);
  }

  /**
   * Load settings from test-ebean.properties and do nothing if the properties is not found.
   * <p>
   * This is typically used when test-ebean.properties is put into the test class path and used
   * to configure Ebean for running tests.
   * </p>
   */
  public void loadTestProperties() {
    Properties properties = PropertyMap.testProperties();
    if (!properties.isEmpty()) {
      PropertiesWrapper p = new PropertiesWrapper("ebean", name, properties);
      loadSettings(p);
    }
  }

  /**
   * Return the properties that we used for configuration and were set via a call to loadFromProperties().
   */
  public Properties getProperties() {
    return properties;
  }

  /**
   * Return the instance to use (can be null) for the given plugin.
   *
   * @param properties the properties
   * @param pluginType the type of plugin
   * @param key        properties key
   * @param instance   existing instance
   */
  protected <T> T createInstance(PropertiesWrapper properties, Class<T> pluginType, String key, T instance) {

    if (instance != null) {
      return instance;
    }
    String classname = properties.get(key, null);
    return createInstance(pluginType, classname);
  }

  /**
   * Return the instance to use (can be null) for the given plugin.
   *
   * @param pluginType the type of plugin
   * @param classname  the implementation class as per properties
   */
  @SuppressWarnings("unchecked")
  protected <T> T createInstance(Class<T> pluginType, String classname) {
    return classname == null ? null : (T) classLoadConfig.newInstance(classname);
  }

  /**
   * loads the data source settings to preserve existing behaviour. IMHO, if someone has set the datasource config already,
   * they don't want the settings to be reloaded and reset. This allows a descending class to override this behaviour and prevent it
   * from happening.
   *
   * @param p - The defined property source passed to load settings
   */
  protected void loadDataSourceSettings(PropertiesWrapper p) {
    dataSourceConfig.loadSettings(p.properties, name);
  }

  /**
   * This is broken out to allow overridden behaviour.
   */
  protected void loadDocStoreSettings(PropertiesWrapper p) {
    docStoreConfig.loadSettings(p);
  }

  /**
   * This is broken out to allow overridden behaviour.
   */
  protected void loadAutoTuneSettings(PropertiesWrapper p) {
    autoTuneConfig.loadSettings(p);
  }

  /**
   * Load the configuration settings from the properties file.
   */
  protected void loadSettings(PropertiesWrapper p) {

    migrationConfig.loadSettings(p, name);

    namingConvention = createNamingConvention(p, namingConvention);
    if (namingConvention != null) {
      namingConvention.loadFromProperties(p);
    }
    if (autoTuneConfig == null) {
      autoTuneConfig = new AutoTuneConfig();
    }
    loadAutoTuneSettings(p);

    if (dataSourceConfig == null) {
      dataSourceConfig = new DataSourceConfig();
    }
    loadDataSourceSettings(p);

    if (docStoreConfig == null) {
      docStoreConfig = new DocStoreConfig();
    }
    loadDocStoreSettings(p);

    int srid = p.getInt("geometrySRID", 0);
    if (srid > 0) {
      dbTypeConfig.setGeometrySRID(srid);
    }

    docStoreOnly = p.getBoolean("docStoreOnly", docStoreOnly);
    disableL2Cache = p.getBoolean("disableL2Cache", disableL2Cache);
    explicitTransactionBeginMode = p.getBoolean("explicitTransactionBeginMode", explicitTransactionBeginMode);
    autoCommitMode = p.getBoolean("autoCommitMode", autoCommitMode);
    useJtaTransactionManager = p.getBoolean("useJtaTransactionManager", useJtaTransactionManager);

    backgroundExecutorSchedulePoolSize = p.getInt("backgroundExecutorSchedulePoolSize", backgroundExecutorSchedulePoolSize);
    backgroundExecutorShutdownSecs = p.getInt("backgroundExecutorShutdownSecs", backgroundExecutorShutdownSecs);
    disableClasspathSearch = p.getBoolean("disableClasspathSearch", disableClasspathSearch);
    currentUserProvider = createInstance(p, CurrentUserProvider.class, "currentUserProvider", currentUserProvider);
    databasePlatform = createInstance(p, DatabasePlatform.class, "databasePlatform", databasePlatform);
    encryptKeyManager = createInstance(p, EncryptKeyManager.class, "encryptKeyManager", encryptKeyManager);
    encryptDeployManager = createInstance(p, EncryptDeployManager.class, "encryptDeployManager", encryptDeployManager);
    encryptor = createInstance(p, Encryptor.class, "encryptor", encryptor);
    dbEncrypt = createInstance(p, DbEncrypt.class, "dbEncrypt", dbEncrypt);
    dbOffline = p.getBoolean("dbOffline", dbOffline);
    serverCachePlugin = createInstance(p, ServerCachePlugin.class, "serverCachePlugin", serverCachePlugin);

    if (packages != null) {
      String packagesProp = p.get("search.packages", p.get("packages", null));
      packages = getSearchJarsPackages(packagesProp);
    }

    collectQueryStatsByNode = p.getBoolean("collectQueryStatsByNode", collectQueryStatsByNode);
    collectQueryOrigins = p.getBoolean("collectQueryOrigins", collectQueryOrigins);

    skipCacheAfterWrite = p.getBoolean("skipCacheAfterWrite", skipCacheAfterWrite);
    updateAllPropertiesInBatch = p.getBoolean("updateAllPropertiesInBatch", updateAllPropertiesInBatch);
    updateChangesOnly = p.getBoolean("updateChangesOnly", updateChangesOnly);

    boolean defaultDeleteMissingChildren = p.getBoolean("defaultDeleteMissingChildren", updatesDeleteMissingChildren);
    updatesDeleteMissingChildren = p.getBoolean("updatesDeleteMissingChildren", defaultDeleteMissingChildren);

    if (p.get("batch.mode") != null || p.get("persistBatching") != null) {
      throw new IllegalArgumentException("Property 'batch.mode' or 'persistBatching' is being set but no longer used. Please change to use 'persistBatchMode'");
    }

    persistBatch = p.getEnum(PersistBatch.class, "persistBatch", persistBatch);
    persistBatchOnCascade = p.getEnum(PersistBatch.class, "persistBatchOnCascade", persistBatchOnCascade);

    int batchSize = p.getInt("batch.size", persistBatchSize);
    persistBatchSize = p.getInt("persistBatchSize", batchSize);

    persistenceContextScope = PersistenceContextScope.valueOf(p.get("persistenceContextScope", "TRANSACTION"));

    changeLogIncludeInserts = p.getBoolean("changeLogIncludeInserts", changeLogIncludeInserts);
    expressionEqualsWithNullAsNoop = p.getBoolean("expressionEqualsWithNullAsNoop", expressionEqualsWithNullAsNoop);
    expressionNativeIlike = p.getBoolean("expressionNativeIlike", expressionNativeIlike);

    dataTimeZone = p.get("dataTimeZone", dataTimeZone);
    asOfViewSuffix = p.get("asOfViewSuffix", asOfViewSuffix);
    asOfSysPeriod = p.get("asOfSysPeriod", asOfSysPeriod);
    historyTableSuffix = p.get("historyTableSuffix", historyTableSuffix);
    dataSourceJndiName = p.get("dataSourceJndiName", dataSourceJndiName);
    jdbcFetchSizeFindEach = p.getInt("jdbcFetchSizeFindEach", jdbcFetchSizeFindEach);
    jdbcFetchSizeFindList = p.getInt("jdbcFetchSizeFindList", jdbcFetchSizeFindList);
    databaseSequenceBatchSize = p.getInt("databaseSequenceBatchSize", databaseSequenceBatchSize);
    databaseBooleanTrue = p.get("databaseBooleanTrue", databaseBooleanTrue);
    databaseBooleanFalse = p.get("databaseBooleanFalse", databaseBooleanFalse);
    databasePlatformName = p.get("databasePlatformName", databasePlatformName);

    DbUuid dbUuid = p.getEnum(DbUuid.class, "dbuuid", null);
    if (dbUuid != null) {
      dbTypeConfig.setDbUuid(dbUuid);
    }
    if (p.getBoolean("uuidStoreAsBinary", false)) {
      dbTypeConfig.setDbUuid(DbUuid.BINARY);
    }
    localTimeWithNanos = p.getBoolean("localTimeWithNanos", localTimeWithNanos);
    jodaLocalTimeMode = p.get("jodaLocalTimeMode", jodaLocalTimeMode);

    lazyLoadBatchSize = p.getInt("lazyLoadBatchSize", lazyLoadBatchSize);
    queryBatchSize = p.getInt("queryBatchSize", queryBatchSize);

    jsonInclude = p.getEnum(JsonConfig.Include.class, "jsonInclude", jsonInclude);
    String jsonDateTimeFormat = p.get("jsonDateTime", null);
    if (jsonDateTimeFormat != null) {
      jsonDateTime = JsonConfig.DateTime.valueOf(jsonDateTimeFormat);
    } else {
      jsonDateTime = JsonConfig.DateTime.MILLIS;
    }

    h2ProductionMode = p.getBoolean("h2ProductionMode", h2ProductionMode);
    ddlGenerate = p.getBoolean("ddl.generate", ddlGenerate);
    ddlRun = p.getBoolean("ddl.run", ddlRun);
    ddlCreateOnly = p.getBoolean("ddl.createOnly", ddlCreateOnly);
    ddlInitSql = p.get("ddl.initSql", ddlInitSql);
    ddlSeedSql = p.get("ddl.seedSql", ddlSeedSql);

    classes = getClasses(p);
  }

  private NamingConvention createNamingConvention(PropertiesWrapper properties, NamingConvention namingConvention) {

    NamingConvention nc = createInstance(properties, NamingConvention.class, "namingconvention", null);
    return (nc != null) ? nc : namingConvention;
  }

  /**
   * Build the list of classes from the comma delimited string.
   *
   * @param properties the properties
   * @return the classes
   */
  private List<Class<?>> getClasses(PropertiesWrapper properties) {

    String classNames = properties.get("classes", null);
    if (classNames == null) {
      return classes;
    }

    List<Class<?>> classes = new ArrayList<>();

    String[] split = classNames.split("[ ,;]");
    for (String aSplit : split) {
      String cn = aSplit.trim();
      if (!cn.isEmpty() && !"class".equalsIgnoreCase(cn)) {
        try {
          classes.add(Class.forName(cn));
        } catch (ClassNotFoundException e) {
          String msg = "Error registering class [" + cn + "] from [" + classNames + "]";
          throw new RuntimeException(msg, e);
        }
      }
    }
    return classes;
  }

  private List<String> getSearchJarsPackages(String searchPackages) {

    List<String> hitList = new ArrayList<>();

    if (searchPackages != null) {

      String[] entries = searchPackages.split("[ ,;]");
      for (String entry : entries) {
        hitList.add(entry.trim());
      }
    }
    return hitList;
  }

  /**
   * Return the PersistBatch mode to use for 'batchOnCascade' taking into account if the database
   * platform supports getGeneratedKeys in batch mode.
   */
  public PersistBatch appliedPersistBatchOnCascade() {

    if (persistBatchOnCascade == PersistBatch.INHERIT) {
      // use the platform default (ALL except SQL Server which has NONE)
      return databasePlatform.getPersistBatchOnCascade();
    }
    return persistBatchOnCascade;
  }

  /**
   * Return the Jackson ObjectMapper.
   * <p>
   * Note that this is not strongly typed as Jackson ObjectMapper is an optional dependency.
   * </p>
   */
  public Object getObjectMapper() {
    return objectMapper;
  }

  /**
   * Set the Jackson ObjectMapper.
   * <p>
   * Note that this is not strongly typed as Jackson ObjectMapper is an optional dependency.
   * </p>
   */
  public void setObjectMapper(Object objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Return true if eq("someProperty", null) should to generate "1=1" rather than "is null" sql expression.
   */
  public boolean isExpressionEqualsWithNullAsNoop() {
    return expressionEqualsWithNullAsNoop;
  }

  /**
   * Set to true if you want eq("someProperty", null) to generate "1=1" rather than "is null" sql expression.
   * <p>
   * Setting this to true has the effect that eq(propertyName, value), ieq(propertyName, value) and
   * ne(propertyName, value) have no effect when the value is null. The expression factory adds a NoopExpression
   * which will add "1=1" into the SQL rather than "is null".
   * </p>
   */
  public void setExpressionEqualsWithNullAsNoop(boolean expressionEqualsWithNullAsNoop) {
    this.expressionEqualsWithNullAsNoop = expressionEqualsWithNullAsNoop;
  }

  /**
   * Return true if native ILIKE expression should be used if supported by the database platform (e.g. Postgres).
   */
  public boolean isExpressionNativeIlike() {
    return expressionNativeIlike;
  }

  /**
   * Set to true to use native ILIKE expression if supported by the database platform (e.g. Postgres).
   */
  public void setExpressionNativeIlike(boolean expressionNativeIlike) {
    this.expressionNativeIlike = expressionNativeIlike;
  }

  /**
   * Return true if L2 cache is disabled.
   */
  public boolean isDisableL2Cache() {
    return disableL2Cache;
  }

  /**
   * Set to true to disable L2 caching. Typically useful in performance testing.
   */
  public void setDisableL2Cache(boolean disableL2Cache) {
    this.disableL2Cache = disableL2Cache;
  }

  /**
   * Run the DB migration against the DataSource.
   */
  public DataSource runDbMigration(DataSource dataSource) {
    if (migrationConfig.isRunMigration()) {
      MigrationRunner runner = migrationConfig.createRunner(getClassLoadConfig().getClassLoader());
      runner.run(dataSource);
    }
    return dataSource;
  }

  /**
   * Specify how UUID is stored.
   */
  public enum DbUuid {

    /**
     * Store using native UUID in H2 and Postgres and otherwise fallback to VARCHAR(40).
     */
    AUTO_VARCHAR(true, false),

    /**
     * Store using native UUID in H2 and Postgres and otherwise fallback to BINARY(16).
     */
    AUTO_BINARY(true, true),

    /**
     * Store using DB VARCHAR(40).
     */
    VARCHAR(false, false),

    /**
     * Store using DB BINARY(16).
     */
    BINARY(false, true);

    boolean nativeType;
    boolean binary;

    DbUuid(boolean nativeType, boolean binary) {
      this.nativeType = nativeType;
      this.binary = binary;
    }

    /**
     * Return true if native UUID type is preferred.
     */
    public boolean useNativeType() {
      return nativeType;
    }

    /**
     * Return true if BINARY(16) storage is preferred over VARCHAR(40).
     */
    public boolean useBinary() {
      return binary;
    }
  }
}
