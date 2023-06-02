---
title: "Skywalking 8.7.0 源码分析学习笔记"
date: 2023-03-20T02:42:30+08:00

tags: ["Skywalking"]
categories: ["Skywalking"]
---
# Skywalking 8.7.0 源码分析学习笔记

> https://www.bilibili.com/video/BV1dy4y1V7ck



## 一、源码环境准备

>  下载地址: `https://archive.apache.org/dist/skywalking/8.7.0/apache-skywalking-apm-8.7.0-src.tgz`



###  **禁用两个插件**



代码风格检查插件

![image-20221203204714520](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203204714520.png)



前端项目编译插件

![image-20221203204747323](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203204747323.png)

### mac M1芯片编译问题

此时在根目录执行` mvn clean package '-Dmaven.test.skip=true'`

![image-20221203205152669](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203205152669.png)

大佬解释了原因，并给出解决办法



#### 原因

`apm-protocol`下的`apm-network`模块引用的插件`os-maven-plugin`, 在m1芯片下是没有的,但是inter芯片版本的,并且inter版本的，m1也是可以用的。所以要把这个变量写死为inter版本的`(大概意思)`

![image-20221203205413109](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203205413109.png)



#### 解决办法

在maven的setting.xml文件中，将变量固定

![image-20221203210945282](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203210945282.png)



之后再编译，就没有问题了

![image-20221203211247383](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203211247383.png)

### 将`protocol`生成的文件加入`classPath`



skywalking采用grpc通信，需使用`protocol`生成通信用的实体类。将这些生成的代码，加入classPath中，可以在源码中使用，在调试过程中，才不会报错。



![image-20221203214217332](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221203214217332.png)



## 二、 Agent



### 原理概述

在目标类中插入它自己的监控代码(插桩)



### 启动方式



![image-20221205171330539](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221205171330539.png)



SkyWalking 只支持静态启动方式。 入口`SkyWalkingAgent.premain()`

这个模块下只有这一个类。

![image-20221205171756377](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221205171756377.png)

### 启动流程

![SkyWalking源码分析](https://blog-1257196793.cos.ap-beijing.myqcloud.com/SkyWalking源码分析.png)

#### 1、初始化配置

涉及到类

- `SkyWalkingAgent`

- `SnifferConfigInitializer`

  

```java

/**
 * The main entrance of sky-walking agent, based on javaagent mechanism.
 */
public class SkyWalkingAgent {
    private static ILog LOGGER = LogManager.getLogger(SkyWalkingAgent.class);

    /**
     * Main entrance. Use byte-buddy transform to enhance all classes, which define in plugins.
     * //传参方式。等号后面
     * -javaagent:/path/to/agent.jar=xxxxxx
     * // 机构话传参
     * -javaagent:/path/to/agent.jar=aaa=xx,bbb=xxx
     * -
     * 我常用的skyWalking的启动配置
     * -
     * java   -javaagent:skywalking-agent/skywalking-agent.jar \
     * -Dskywalking.agent.service_name=${SKYWALKING_AGENT_SERVICE_NAME} \
     * -Dskywalking.collector.backend_service=${SKYWALKING_COLLECTOR_BACKEND_SERVICE} \
     * -jar ${JAR_NAME}
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        final PluginFinder pluginFinder;
        // 初始化配置
        try {
            SnifferConfigInitializer.initializeCoreConfig(agentArgs);
        } catch (Exception e) {

            // 初始化配置时，重定义了一个logger,所以这里要重新获取一下
            // try to resolve a new logger, and use the new logger to write the error log here
            LogManager.getLogger(SkyWalkingAgent.class)
                    .error(e, "SkyWalking agent initialized failure. Shutting down.");
            return;
        } finally {
            // refresh logger again after initialization finishes
            LOGGER = LogManager.getLogger(SkyWalkingAgent.class);
        }
      
      // ----
    }
}

```



```java

/**
 * The <code>SnifferConfigInitializer</code> initializes all configs in several way.
 */
public class SnifferConfigInitializer {
    private static ILog LOGGER = LogManager.getLogger(SnifferConfigInitializer.class);
    private static final String SPECIFIED_CONFIG_PATH = "skywalking_config";
    private static final String DEFAULT_CONFIG_FILE_NAME = "/config/agent.config";
    private static final String ENV_KEY_PREFIX = "skywalking.";
    private static Properties AGENT_SETTINGS;
    private static boolean IS_INIT_COMPLETED = false;

    /**
     * If the specified agent config path is set, the agent will try to locate the specified agent config. If the
     * specified agent config path is not set , the agent will try to locate `agent.config`, which should be in the
     * /config directory of agent package.
     * <p>
     * Also try to override the config by system.properties. All the keys in this place should start with {@link
     * #ENV_KEY_PREFIX}. e.g. in env `skywalking.agent.service_name=yourAppName` to override `agent.service_name` in
     * config file.
     * <p>
     * At the end, `agent.service_name` and `collector.servers` must not be blank.
     */
    public static void initializeCoreConfig(String agentOptions) {
        AGENT_SETTINGS = new Properties();

        try (final InputStreamReader configFileStream = loadConfig()) {
            // 将流 load 到 Properties 中
            AGENT_SETTINGS.load(configFileStream);
            // 替换占位符
            // aaa = xxx
            // bbb = ${aaa}-yyy ==替换==> xxx-yyy
            for (String key : AGENT_SETTINGS.stringPropertyNames()) {
                String value = (String) AGENT_SETTINGS.get(key);
                AGENT_SETTINGS.put(key, PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value, AGENT_SETTINGS));
            }

        } catch (Exception e) {
            LOGGER.error(e, "Failed to read the config file, skywalking is going to run in default config.");
        }

        // 如果在环境变量里配置了一些变量，要覆盖 Properties中的变量
        // 环境变量优先级更高
        try {
            overrideConfigBySystemProp();
        } catch (Exception e) {
            LOGGER.error(e, "Failed to read the system properties.");
        }

        // agent 参数替换
        // agent参数 优先级更高
        agentOptions = StringUtil.trim(agentOptions, ',');
        if (!StringUtil.isEmpty(agentOptions)) {
            try {
                agentOptions = agentOptions.trim();
                LOGGER.info("Agent options is {}.", agentOptions);

                overrideConfigByAgentOptions(agentOptions);
            } catch (Exception e) {
                LOGGER.error(e, "Failed to parse the agent options, val is {}.", agentOptions);
            }
        }
        // AGENT_SETTINGS 中的配置数据。映射到 Config类中
        initializeConfig(Config.class);

        // 重新配置logger
        // 根据配置的日志解析模式，重新生成一个logger。   模式 JSON  PATTERN
        // reconfigure logger after config initialization
        configureLogger();
        LOGGER = LogManager.getLogger(SnifferConfigInitializer.class);

        // 使用agent，要传入server_name .这里做效验
        if (StringUtil.isEmpty(Config.Agent.SERVICE_NAME)) {
            throw new ExceptionInInitializerError("`agent.service_name` is missing.");
        }
        if (StringUtil.isEmpty(Config.Collector.BACKEND_SERVICE)) {
            throw new ExceptionInInitializerError("`collector.backend_service` is missing.");
        }

        // PEER 可以理解为链接
        // APPLICATION -> Redis
        // PEER 就是 redis 地址

        if (Config.Plugin.PEER_MAX_LENGTH <= 3) {
            LOGGER.warn(
                    "PEER_MAX_LENGTH configuration:{} error, the default value of 200 will be used.",
                    Config.Plugin.PEER_MAX_LENGTH
            );
            Config.Plugin.PEER_MAX_LENGTH = 200;
        }
        // 配置加载完成
        IS_INIT_COMPLETED = true;
    }

    /**
     * Initialize field values of any given config class.
     *
     * @param configClass to host the settings for code access.
     */
    public static void initializeConfig(Class configClass) {
        if (AGENT_SETTINGS == null) {
            LOGGER.error("Plugin configs have to be initialized after core config initialization.");
            return;
        }
        try {
            ConfigInitializer.initialize(AGENT_SETTINGS, configClass);
        } catch (IllegalAccessException e) {
            LOGGER.error(e,
                         "Failed to set the agent settings {}"
                             + " to Config={} ",
                         AGENT_SETTINGS, configClass
            );
        }
    }

    private static void overrideConfigByAgentOptions(String agentOptions) throws IllegalArgumentException {
        for (List<String> terms : parseAgentOptions(agentOptions)) {
            if (terms.size() != 2) {
                throw new IllegalArgumentException("[" + terms + "] is not a key-value pair.");
            }
            AGENT_SETTINGS.put(terms.get(0), terms.get(1));
        }
    }

    private static List<List<String>> parseAgentOptions(String agentOptions) {
        List<List<String>> options = new ArrayList<>();
        List<String> terms = new ArrayList<>();
        boolean isInQuotes = false;
        StringBuilder currentTerm = new StringBuilder();
        for (char c : agentOptions.toCharArray()) {
            if (c == '\'' || c == '"') {
                isInQuotes = !isInQuotes;
            } else if (c == '=' && !isInQuotes) {   // key-value pair uses '=' as separator
                terms.add(currentTerm.toString());
                currentTerm = new StringBuilder();
            } else if (c == ',' && !isInQuotes) {   // multiple options use ',' as separator
                terms.add(currentTerm.toString());
                currentTerm = new StringBuilder();

                options.add(terms);
                terms = new ArrayList<>();
            } else {
                currentTerm.append(c);
            }
        }
        // add the last term and option without separator
        terms.add(currentTerm.toString());
        options.add(terms);
        return options;
    }

    public static boolean isInitCompleted() {
        return IS_INIT_COMPLETED;
    }

    /**
     * Override the config by system properties. The property key must start with `skywalking`, the result should be as
     * same as in `agent.config`
     * <p>
     * such as: Property key of `agent.service_name` should be `skywalking.agent.service_name`
     */
    private static void overrideConfigBySystemProp() throws IllegalAccessException {
        Properties systemProperties = System.getProperties();
        for (final Map.Entry<Object, Object> prop : systemProperties.entrySet()) {
            String key = prop.getKey().toString();
            if (key.startsWith(ENV_KEY_PREFIX)) {
                String realKey = key.substring(ENV_KEY_PREFIX.length());
                AGENT_SETTINGS.put(realKey, prop.getValue());
            }
        }
    }

    /**
     * Load the specified config file or default config file
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static InputStreamReader loadConfig() throws AgentPackageNotFoundException, ConfigNotFoundException {
        // 配置文件地址
        String specifiedConfigPath = System.getProperty(SPECIFIED_CONFIG_PATH);

        // 如果为空，就加载 默认配置文件 /config/agent.config
        // AgentPackagePath.getPath() 用来查找agent的绝对目录
        File configFile = StringUtil.isEmpty(specifiedConfigPath) ? new File(
                AgentPackagePath.getPath(), DEFAULT_CONFIG_FILE_NAME) : new File(specifiedConfigPath);

        if (configFile.exists() && configFile.isFile()) {
            try {
                LOGGER.info("Config file found in {}.", configFile);

                return new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Failed to load agent.config", e);
            }
        }
        throw new ConfigNotFoundException("Failed to load agent.config.");
    }

    static void configureLogger() {
        switch (Config.Logging.RESOLVER) {
            case JSON:
                LogManager.setLogResolver(new JsonLogResolver());
                break;
            case PATTERN:
            default:
                LogManager.setLogResolver(new PatternLogResolver());
        }
    }
}

```





#### 2、加载插件

> SkyWalking 通过自定义类加载器的方式，去加载指定目录下的jar包(插件)。
>
> 自定义类加载器 `AgentClassLoader`



##### 开启类加载器的并行加载模式



```java
/**
 * The <code>AgentClassLoader</code> represents a classloader, which is in charge of finding plugins and interceptors.
 *
 * 自定义类加载器
 *
 */
public class AgentClassLoader extends ClassLoader {

    static {
        /*
         * Try to solve the classloader dead lock. See https://github.com/apache/skywalking/pull/2016
         * 开启类加载器的并行加载模式。
         * jdk1.7 之前 类加载是串行的。1.7后，改为并行，
         * 原理： super.loadClass() 中锁，从锁当前类加载器，改为锁正在加载的类。
         *
         *
         */
        registerAsParallelCapable();
    }
}
```





```java
		// ClassLoader 类的构造器
		private ClassLoader(Void unused, String name, ClassLoader parent) {
        this.name = name;
        this.parent = parent;
        this.unnamedModule = new Module(this);
      	// 上面静态代码块中的方法，就是将clss注册为并行加载类
      	// 为parallelLockMap赋值 
        if (ParallelLoaders.isRegistered(this.getClass())) {
            parallelLockMap = new ConcurrentHashMap<>();
            assertionLock = new Object();
        } else {
            // no finer-grained lock; lock on the classloader instance
            parallelLockMap = null;
            assertionLock = this;
        }
        this.package2certs = new ConcurrentHashMap<>();
        this.nameAndId = nameAndId(this);
    }

		// ClassLoader类的 loadClass方法
    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
    {
        // getClassLoadingLock() 获取锁
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                long t0 = System.nanoTime();
                try {
                    if (parent != null) {
                        c = parent.loadClass(name, false);
                    } else {
                        c = findBootstrapClassOrNull(name);
                    }
                } catch (ClassNotFoundException e) {
                    // ClassNotFoundException thrown if class not found
                    // from the non-null parent class loader
                }

                if (c == null) {
                    // If still not found, then invoke findClass in order
                    // to find the class.
                    long t1 = System.nanoTime();
                    c = findClass(name);

                    // this is the defining class loader; record the stats
                    PerfCounter.getParentDelegationTime().addTime(t1 - t0);
                    PerfCounter.getFindClassTime().addElapsedTimeFrom(t1);
                    PerfCounter.getFindClasses().increment();
                }
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
    
    // 重点。获取锁的方法
    // 
    protected Object getClassLoadingLock(String className) {
        Object lock = this;
        // 并行加载map 不为空
        if (parallelLockMap != null) {
          	// 创建一个新的锁
            Object newLock = new Object();
            lock = parallelLockMap.putIfAbsent(className, newLock);
            if (lock == null) {
                lock = newLock;
            }
        }
      	// 这里就是 以类加载器 为锁。线性加载
        return lock;
    }

```



##### 从指定目录加载插件



**简述：**

`AgentClassLoader`初始化后，将`agent插件根目录 的 "plugins", "activations"`加入到classPath,后面就是从这两个目录下加载插件(jar包)。

`findClass()`方法就是加载类的方法，加载完成后，会检查一下类上有没有`@PluginConfig`注解。有的话，就将相关配置信息，拷给这个类。

```java

    // 构造函数
    public AgentClassLoader(ClassLoader parent) throws AgentPackageNotFoundException {
        super(parent);
        File agentDictionary = AgentPackagePath.getPath();
        classpath = new LinkedList<>();
        // Config.Plugin.MOUNT 用来指定被加载类的目录
        // 默认值 "plugins", "activations"
        Config.Plugin.MOUNT.forEach(mountFolder -> classpath.add(new File(agentDictionary, mountFolder)));
    }


		// findClass方法
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // 拿到所有的jar
        List<Jar> allJars = getAllJars();
        String path = name.replace('.', '/').concat(".class");
        for (Jar jar : allJars) {
            JarEntry entry = jar.jarFile.getJarEntry(path);
            if (entry == null) {
                continue;
            }
            try {
                URL classFileUrl = new URL("jar:file:" + jar.sourceFile.getAbsolutePath() + "!/" + path);
                byte[] data;
                try (final BufferedInputStream is = new BufferedInputStream(
                    classFileUrl.openStream()); final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    int ch;
                    while ((ch = is.read()) != -1) {
                        baos.write(ch);
                    }
                    data = baos.toByteArray();
                }
                // 包装一下。进行配置信息加载。将配置文件传给插件
                return processLoadedClass(defineClass(name, data, 0, data.length));
            } catch (IOException e) {
                LOGGER.error(e, "find class fail.");
            }
        }
        throw new ClassNotFoundException("Can't find " + name);
    }

		// processLoadedClass 
    private Class<?> processLoadedClass(Class<?> loadedClass) {
        final PluginConfig pluginConfig = loadedClass.getAnnotation(PluginConfig.class);
        if (pluginConfig != null) {
            // Set up the plugin config when loaded by class loader at the first time.
            // Agent class loader just loaded limited classes in the plugin jar(s), so the cost of this
            // isAssignableFrom would be also very limited.
            SnifferConfigInitializer.initializeConfig(pluginConfig.root());
        }

        return loadedClass;
    }
```



#### 3、插件定义体系

> 首先自己看SkyWalking官网的插件开发指南

##### 从doubbe插件看开发流程

![image-20221205224426951](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221205224426951.png)

具体注释，看代码

![image-20221205221245087](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221205221245087.png)

#### 4、 插件加载流程

涉及到类

- `PluginBootstrap`
- `PluginFinder`

***简述:***

`new PluginBootstrap().loadPlugins()` 加载所有插件，通过读取skywalking-plugin.def文件，将定义的class 实例化。

`PluginFinder` 将这些类分类存放到`nameMatchDefine,signatureMatchDefine,bootstrapClassMatchDefine`。并且提供find方法，可以通过类查找到`可以对这个类生效的插件`



```java
  // SkyWalkingAgent   

	public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
        final PluginFinder pluginFinder;
			
      	// -----

        // 加载插件
        try {
            // 插件查找器
            // new PluginBootstrap().loadPlugins()  插件加载器
            // 构造函数。对插件进行分类。
            pluginFinder = new PluginFinder(new PluginBootstrap().loadPlugins());
        } catch (AgentPackageNotFoundException ape) {
            LOGGER.error(ape, "Locate agent.jar failure. Shutting down.");
            return;
        } catch (Exception e) {
            LOGGER.error(e, "SkyWalking agent initialized failure. Shutting down.");
            return;
        }
        
        }
        
```

```java
public class PluginBootstrap {
    private static final ILog LOGGER = LogManager.getLogger(PluginBootstrap.class);

    /**
     * load all plugins.
     * 加载所有的插件
     *
     * @return plugin definition list.
     */
    public List<AbstractClassEnhancePluginDefine> loadPlugins() throws AgentPackageNotFoundException {
        // 初始化自定义类加载器实例
        AgentClassLoader.initDefaultLoader();

        // 插件资源转换器
        PluginResourcesResolver resolver = new PluginResourcesResolver();

        //拿到所有 skywalking-plugin.def 的资源
        List<URL> resources = resolver.getResources();

        if (resources == null || resources.size() == 0) {
            LOGGER.info("no plugin files (skywalking-plugin.def) found, continue to start application.");
            return new ArrayList<AbstractClassEnhancePluginDefine>();
        }

        for (URL pluginUrl : resources) {
            try {
                // url转换成流，然后load（解析.def文件）
                PluginCfg.INSTANCE.load(pluginUrl.openStream());
            } catch (Throwable t) {
                LOGGER.error(t, "plugin file [{}] init failure.", pluginUrl);
            }
        }

        // load后的数据
        List<PluginDefine> pluginClassList = PluginCfg.INSTANCE.getPluginClassList();

        List<AbstractClassEnhancePluginDefine> plugins = new ArrayList<AbstractClassEnhancePluginDefine>();
        // 实例化
        for (PluginDefine pluginDefine : pluginClassList) {
            try {
                LOGGER.debug("loading plugin class {}.", pluginDefine.getDefineClass());
                // 创建实例
                AbstractClassEnhancePluginDefine plugin = (AbstractClassEnhancePluginDefine) Class.forName(pluginDefine.getDefineClass(), true, AgentClassLoader
                    .getDefault()).newInstance();
                plugins.add(plugin);
            } catch (Throwable t) {
                LOGGER.error(t, "load plugin [{}] failure.", pluginDefine.getDefineClass());
            }
        }
        // DynamicPluginLoader.INSTANCE.load(AgentClassLoader.getDefault()) 加载一些通过xml定义的插件
        plugins.addAll(DynamicPluginLoader.INSTANCE.load(AgentClassLoader.getDefault()));


        return plugins;

    }

}


```



```java
public class PluginFinder {

    /**
     * 为什么这里map 泛型是<String,List>
     * 因为对于一个类，可能有多个插件都要对他进行字节码增强
     * KEY => 目标类
     * VAL => 所有可以对这个类生效的插件
     */
    private final Map<String, LinkedList<AbstractClassEnhancePluginDefine>> nameMatchDefine = new HashMap<String, LinkedList<AbstractClassEnhancePluginDefine>>();

    // 这里是因为 间接匹配的，无法确定到具体的类
    private final List<AbstractClassEnhancePluginDefine> signatureMatchDefine = new ArrayList<AbstractClassEnhancePluginDefine>();
    private final List<AbstractClassEnhancePluginDefine> bootstrapClassMatchDefine = new ArrayList<AbstractClassEnhancePluginDefine>();

    /**
     * @param plugins 所有的插件
     */
    public PluginFinder(List<AbstractClassEnhancePluginDefine> plugins) {
        // 分类。放到不同的map中
        for (AbstractClassEnhancePluginDefine plugin : plugins) {
            // 拿到匹配器
            ClassMatch match = plugin.enhanceClass();

            if (match == null) {
                continue;
            }
            // nameMatch
            if (match instanceof NameMatch) {
                NameMatch nameMatch = (NameMatch) match;
                LinkedList<AbstractClassEnhancePluginDefine> pluginDefines = nameMatchDefine.get(nameMatch.getClassName());
                if (pluginDefines == null) {
                    pluginDefines = new LinkedList<AbstractClassEnhancePluginDefine>();
                    nameMatchDefine.put(nameMatch.getClassName(), pluginDefines);
                }
                pluginDefines.add(plugin);
            } else {
                // 间接匹配
                signatureMatchDefine.add(plugin);
            }

            // 对jdk类库进行增强的插件
            if (plugin.isBootstrapInstrumentation()) {
                bootstrapClassMatchDefine.add(plugin);
            }
        }
    }

    /**
     * 根据类查找 插件
     * 查找要对这个类生效的插件
     *  1.从命名插件中找
     *  2. 从间接匹配插件中找
     * @param typeDescription 类的描述信息
     * @return
     */
    public List<AbstractClassEnhancePluginDefine> find(TypeDescription typeDescription) {
        List<AbstractClassEnhancePluginDefine> matchedPlugins = new LinkedList<AbstractClassEnhancePluginDefine>();
        String typeName = typeDescription.getTypeName();
        if (nameMatchDefine.containsKey(typeName)) {
            matchedPlugins.addAll(nameMatchDefine.get(typeName));
        }

        // 间接匹配。使用匹配器进行验证
        for (AbstractClassEnhancePluginDefine pluginDefine : signatureMatchDefine) {
            IndirectMatch match = (IndirectMatch) pluginDefine.enhanceClass();
            if (match.isMatch(typeDescription)) {
                matchedPlugins.add(pluginDefine);
            }
        }

        return matchedPlugins;
    }

    /***
     * 用来告诉byteBuddy要拦截的类
     * @return
     */
    public ElementMatcher<? super TypeDescription> buildMatch() {
        ElementMatcher.Junction judge = new AbstractJunction<NamedElement>() {
            @Override
            public boolean matches(NamedElement target) {
                return nameMatchDefine.containsKey(target.getActualName());
            }
        };
        // 不能是接口
        judge = judge.and(not(isInterface()));
        for (AbstractClassEnhancePluginDefine define : signatureMatchDefine) {
            ClassMatch match = define.enhanceClass();
            if (match instanceof IndirectMatch) {
                judge = judge.or(((IndirectMatch) match).buildJunction());
            }
        }
        // 封装一下，避免和其他字节码增强工具的兼容性问题。
        return new ProtectiveShieldMatcher(judge);
    }

    public List<AbstractClassEnhancePluginDefine> getBootstrapClassMatchDefine() {
        return bootstrapClassMatchDefine;
    }
}
```



#### 5、 定制agent

![image-20221206113314349](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221206113314349.png)



```java
 public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {

   
   		// ----
     
        // 定制化agent
        // Config.Agent.IS_OPEN_DEBUGGING_CLASS 是否打开调试类
        final ByteBuddy byteBuddy = new ByteBuddy().with(TypeValidation.of(Config.Agent.IS_OPEN_DEBUGGING_CLASS));

        AgentBuilder agentBuilder = new AgentBuilder.Default(byteBuddy)
                // 被忽略的类
                .ignore(nameStartsWith("net.bytebuddy.")
                        .or(nameStartsWith("org.slf4j."))
                        .or(nameStartsWith("org.groovy."))
                        .or(nameContains("javassist"))
                        .or(nameContains(".asm."))
                        .or(nameContains(".reflectasm."))
                        .or(nameStartsWith("sun.reflect"))
                        .or(allSkyWalkingAgentExcludeToolkit())
                        // Synthetic  关键字， 用来标识，生成的字节码类
                        .or(ElementMatchers.isSynthetic())
                );

        JDK9ModuleExporter.EdgeClasses edgeClasses = new JDK9ModuleExporter.EdgeClasses();
        try {
            // 将 edgeClasses  注入到 Bootstrap ClassLoader
            agentBuilder = BootstrapInstrumentBoost.inject(pluginFinder, instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            LOGGER.error(e, "SkyWalking agent inject bootstrap instrumentation failure. Shutting down.");
            return;
        }

        try {
            // 打开读边界
            // jdK9之后，出现模块化加载新技术，这里是绕过 模块化加载
            agentBuilder = JDK9ModuleExporter.openReadEdge(instrumentation, agentBuilder, edgeClasses);
        } catch (Exception e) {
            LOGGER.error(e, "SkyWalking agent open read edge in JDK 9+ failure. Shutting down.");
            return;
        }
        // 为true 的话， 将修改后的字节码，保存到磁盘或者内存上
        if (Config.Agent.IS_CACHE_ENHANCED_CLASS) {
            try {
                agentBuilder = agentBuilder.with(new CacheableTransformerDecorator(Config.Agent.CLASS_CACHE_MODE));
                LOGGER.info("SkyWalking agent class cache [{}] activated.", Config.Agent.CLASS_CACHE_MODE);
            } catch (Exception e) {
                LOGGER.error(e, "SkyWalking agent can't active class cache.");
            }
        }

        // pluginFinder.buildMatch()  构造出一个巨大的条件，用来匹配插件类
        // type 指定byteBuddy要拦截的类
        agentBuilder.type(pluginFinder.buildMatch())
                .transform(new Transformer(pluginFinder))
                // 增强的模式: redefine 和 retransform 的区别就在于 是否保留修改前的内容
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                // 注册监听器
                .with(new RedefinitionListener())
                .with(new Listener())
                // 安装到 instrumentation
                .installOn(instrumentation);
 		// ----
 }

}
```

##### `synthetic`

略

##### `NBAC`

略

#### 6、加载服务

![image-20221207124816764](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221207124816764.png)



***简述:***

通过 服务管理器`ServiceManager`  基于 `ServiceLoader(SPI)` 加载所有 `BootService` 的实现类。

根据`@DefaultImplementor，@OverrideImplementor`注解，来决定 服务最终选择的实现类。规则如上思维导图。



```java
public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
			
				// ----
        // 启动服务
        try {
            ServiceManager.INSTANCE.boot();
        } catch (Exception e) {
            LOGGER.error(e, "Skywalking agent boot failure.");
        }
				// ---
    }

```







```java

/**
 * The <code>ServiceManager</code> bases on {@link ServiceLoader}, load all {@link BootService} implementations.
 * 服务管理器。  基于 ServiceLoader(SPI) 加载所有 BootService 的实现类
 */
public enum ServiceManager {
    INSTANCE;

    private static final ILog LOGGER = LogManager.getLogger(ServiceManager.class);
    private Map<Class, BootService> bootedServices = Collections.emptyMap();

    public void boot() {
        // 加载服务
        bootedServices = loadAllServices();

        prepare();
        startup();
        onComplete();
    }

    public void shutdown() {
        // 倒序。 根据依赖关系。优雅关闭
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority).reversed()).forEach(service -> {
            try {
                service.shutdown();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to shutdown [{}] fail.", service.getClass().getName());
            }
        });
    }

    private Map<Class, BootService> loadAllServices() {
        Map<Class, BootService> bootedServices = new LinkedHashMap<>();
        List<BootService> allServices = new LinkedList<>();
        // spi 去加载服务类
        load(allServices);
        // 根据 默认实现、覆盖实现。为服务分类
        // 一个服务class, 有 默认实现、覆盖实现。 覆盖实现的优先级高。
        // 如果一个服务class,只有一个实现，可以不用加注解
        for (final BootService bootService : allServices) {
            Class<? extends BootService> bootServiceClass = bootService.getClass();
            boolean isDefaultImplementor = bootServiceClass.isAnnotationPresent(DefaultImplementor.class);
            if (isDefaultImplementor) {
                if (!bootedServices.containsKey(bootServiceClass)) {
                    bootedServices.put(bootServiceClass, bootService);
                } else {
                    //ignore the default service
                }
            } else {
                OverrideImplementor overrideImplementor = bootServiceClass.getAnnotation(OverrideImplementor.class);
                if (overrideImplementor == null) {
                    if (!bootedServices.containsKey(bootServiceClass)) {
                        bootedServices.put(bootServiceClass, bootService);
                    } else {
                        // 服务不能重复定义，抛异常
                        throw new ServiceConflictException("Duplicate service define for :" + bootServiceClass);
                    }
                } else {
                    Class<? extends BootService> targetService = overrideImplementor.value();
                    if (bootedServices.containsKey(targetService)) {
                        boolean presentDefault = bootedServices.get(targetService)
                                                               .getClass()
                                                               .isAnnotationPresent(DefaultImplementor.class);
                        if (presentDefault) {
                            bootedServices.put(targetService, bootService);
                        } else {
                            throw new ServiceConflictException(
                                "Service " + bootServiceClass + " overrides conflict, " + "exist more than one service want to override :" + targetService);
                        }
                    } else {
                        // 当前 覆盖实现 要覆盖的 默认实现 还没有被加载出来。这时候，就把这个 覆盖实现 当做是其服务的 默认实现。
                        // @OverrideImplementor 有个value字段，value就是 要覆盖的服务。 这里 把这个 要覆盖的服务，当成了key.
                        bootedServices.put(targetService, bootService);
                    }
                }
            }

        }
        return bootedServices;
    }

    private void prepare() {
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority)).forEach(service -> {
            try {
                service.prepare();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to pre-start [{}] fail.", service.getClass().getName());
            }
        });
    }

    private void startup() {
        bootedServices.values().stream().sorted(Comparator.comparingInt(BootService::priority)).forEach(service -> {
            try {
                service.boot();
            } catch (Throwable e) {
                LOGGER.error(e, "ServiceManager try to start [{}] fail.", service.getClass().getName());
            }
        });
    }

    private void onComplete() {
        for (BootService service : bootedServices.values()) {
            try {
                service.onComplete();
            } catch (Throwable e) {
                LOGGER.error(e, "Service [{}] AfterBoot process fails.", service.getClass().getName());
            }
        }
    }

    /**
     * Find a {@link BootService} implementation, which is already started.
     *
     * @param serviceClass class name.
     * @param <T>          {@link BootService} implementation class.
     * @return {@link BootService} instance
     */
    public <T extends BootService> T findService(Class<T> serviceClass) {
        return (T) bootedServices.get(serviceClass);
    }

    /**
     * spi 去加载 服务
     *
     * @param allServices
     */
    void load(List<BootService> allServices) {
        for (final BootService bootService : ServiceLoader.load(BootService.class, AgentClassLoader.getDefault())) {
            allServices.add(bootService);
        }
    }
}

```





#### 7 注册关闭钩子

```java
    public static void premain(String agentArgs, Instrumentation instrumentation) throws PluginException {
			
				// ----
        // 注册关闭钩子，优雅关机
        Runtime.getRuntime()
                .addShutdownHook(new Thread(ServiceManager.INSTANCE::shutdown, "skywalking service shutdown thread"));
    }
```



### 插件工作原理

![image-20221207154649835](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221207154649835.png)

#### `witness机制`

![image-20221207154703060](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221207154703060.png)

#### 插件工作流程

<img src="https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221207154720722.png" alt="image-20221207154720722" style="zoom:200%;" />



>  这块理解的不好。可以看这个博客，很清晰。就不复制人家的笔记了，等后面把这块重新看看，再自己总计。 https://blog.csdn.net/qq_40378034/article/details/122278500
>
> 



**入口**

`org/apache/skywalking/apm/agent/SkyWalkingAgent.java:156`

![image-20221207193803771](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221207193803771.png)





>  `SkyWalkingAgent.Transformer` ： `agent`自定义的增强逻辑。重点看 transform方法

```java
  /**
     * 自己定义的插桩逻辑
     */
    private static class Transformer implements AgentBuilder.Transformer {
        private PluginFinder pluginFinder;

        /**
         *
         * @param pluginFinder 插件查找器
         */
        Transformer(PluginFinder pluginFinder) {
            this.pluginFinder = pluginFinder;
        }

        /**
         * @param builder         当前拦截到的类的字节码
         * @param typeDescription 简单当成 Class ,它包含了类的描述信息
         * @param classLoader     加载 【当前拦截到的类】的类加载器
         * @param module          The class's module or {@code null} if the current VM does not support modules.
         * @return
         */
        @Override
        public DynamicType.Builder<?> transform(final DynamicType.Builder<?> builder,
                                                final TypeDescription typeDescription,
                                                final ClassLoader classLoader,
                                                final JavaModule module) {
            // 加载UrlClassLoader 类。 用于构造JVM信息
            LoadedLibraryCollector.registerURLClassLoader(classLoader);
            // 找到所有 对这个类生效的插件
            List<AbstractClassEnhancePluginDefine> pluginDefines = pluginFinder.find(typeDescription);
            if (pluginDefines.size() > 0) {
                // 遍历这些差价，构造 newBuilder
                DynamicType.Builder<?> newBuilder = builder;
                // context 记录一些标记
                EnhanceContext context = new EnhanceContext();
                for (AbstractClassEnhancePluginDefine define : pluginDefines) {
                    // 去做增强 【核心步骤】 define.define()
                    DynamicType.Builder<?> possibleNewBuilder = define.define(
                            typeDescription, newBuilder, classLoader, context);
                    // 如果增强了，就不会为null
                    if (possibleNewBuilder != null) {
                        newBuilder = possibleNewBuilder;
                    }
                }
                if (context.isEnhanced()) {
                    LOGGER.debug("Finish the prepare stage for {}.", typeDescription.getName());
                }
                // 被所有可用插件修改完的  最终字节码
                return newBuilder;
            }

            LOGGER.debug("Matched class {}, but ignore by finding mechanism.", typeDescription.getTypeName());
            return builder;
        }
    }
```

重点看这串代码: 

```java
for (AbstractClassEnhancePluginDefine define : pluginDefines) {
	   // 去做增强 【核心步骤】 define.define()
	   DynamicType.Builder<?> possibleNewBuilder = define.define(
	           typeDescription, newBuilder, classLoader, context);
	   // 如果增强了，就不会为null
	   if (possibleNewBuilder != null) {
	       newBuilder = possibleNewBuilder;
	   }
}
```



> ``AbstractClassEnhancePluginDefine ` 是所有插件的父类。`define`方法先进行 `witness`版本识别，然后调用`enhance`方法，根据 `静态方法、实例方法/构造器、JDK类库中类`分类进行不同的增强。然后具体的增强实现要看 `ClassEnhancePluginDefine[子类] 的 enhanceInstance() 和enhanceClass() `这两个方法`

```java
/**
 * 所有插件的父类
 * <p>
 * Basic abstract class of all sky-walking auto-instrumentation plugins.
 * <p>
 * It provides the outline of enhancing the target class. If you want to know more about enhancing, you should go to see
 * {@link ClassEnhancePluginDefine}
 */
public abstract class AbstractClassEnhancePluginDefine {
    private static final ILog LOGGER = LogManager.getLogger(AbstractClassEnhancePluginDefine.class);

    /**
     * New field name.
     */
    public static final String CONTEXT_ATTR_NAME = "_$EnhancedClassField_ws";

    /**
     * Main entrance of enhancing the class.
     *
     * @param builder         当前拦截到的类的字节码
     * @param typeDescription 简单当成 Class ,它包含了类的描述信息
     * @param classLoader     加载 【当前拦截到的类】的类加载器
     * @param typeDescription target class description.
     * @param builder         byte-buddy's builder to manipulate target class's bytecode.
     * @param classLoader     load the given transformClass
     * @return the new builder, or <code>null</code> if not be enhanced.
     * @throws PluginException when set builder failure.
     */
    public DynamicType.Builder<?> define(TypeDescription typeDescription, DynamicType.Builder<?> builder,
        ClassLoader classLoader, EnhanceContext context) throws PluginException {
        // 当前插件的名字
        String interceptorDefineClassName = this.getClass().getName();
        // 被拦截的类的 className
        String transformClassName = typeDescription.getTypeName();

        if (StringUtil.isEmpty(transformClassName)) {
            LOGGER.warn("classname of being intercepted is not defined by {}.", interceptorDefineClassName);
            return null;
        }

        LOGGER.debug("prepare to enhance class {} by {}.", transformClassName, interceptorDefineClassName);

        // witness 版本识别 [细节可以不去了解]
        // 来确认这个插件是不是可以用
        WitnessFinder finder = WitnessFinder.INSTANCE;
        /**
         * find witness classes for enhance class
         */
        // 根据类来识别
        String[] witnessClasses = witnessClasses();
        if (witnessClasses != null) {
            for (String witnessClass : witnessClasses) {
                if (!finder.exist(witnessClass, classLoader)) {
                    LOGGER.warn("enhance class {} by plugin {} is not working. Because witness class {} is not existed.", transformClassName, interceptorDefineClassName, witnessClass);
                    return null;
                }
            }
        }
        // 根据方法来识别
        List<WitnessMethod> witnessMethods = witnessMethods();
        if (!CollectionUtil.isEmpty(witnessMethods)) {
            for (WitnessMethod witnessMethod : witnessMethods) {
                if (!finder.exist(witnessMethod, classLoader)) {
                    LOGGER.warn("enhance class {} by plugin {} is not working. Because witness method {} is not existed.", transformClassName, interceptorDefineClassName, witnessMethod);
                    return null;
                }
            }
        }

        /**
         * find origin class source code for interceptor
         */
        // 进行字节码增强 【重点】
        DynamicType.Builder<?> newClassBuilder = this.enhance(typeDescription, builder, classLoader, context);

        // 设置 增强标记
        context.initializationStageCompleted();
        LOGGER.debug("enhance class {} by {} completely.", transformClassName, interceptorDefineClassName);

        return newClassBuilder;
    }


    /**
     * 增强方法
     *
     * Begin to define how to enhance class. After invoke this method, only means definition is finished.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     *
     */
    protected DynamicType.Builder<?> enhance(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
                                             ClassLoader classLoader, EnhanceContext context) throws PluginException {
        // 增强类的静态方法
        newClassBuilder = this.enhanceClass(typeDescription, newClassBuilder, classLoader);

        // 增强实例和构造器
        newClassBuilder = this.enhanceInstance(typeDescription, newClassBuilder, classLoader, context);

        return newClassBuilder;
    }

    /**
     * 增强类以拦截构造函数和类实例方法。
     * 看实现类 {@link   ClassEnhancePluginDefine#enhanceInstance(TypeDescription, DynamicType.Builder, ClassLoader, EnhanceContext)}
     *
     * Enhance a class to intercept constructors and class instance methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected abstract DynamicType.Builder<?> enhanceInstance(TypeDescription typeDescription,
                                                     DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
                                                     EnhanceContext context) throws PluginException;

    /**
     *
     * 增强类以拦截类静态方法。
     * 看实现类 {@link   ClassEnhancePluginDefine#enhanceClass(TypeDescription, DynamicType.Builder, ClassLoader)}
     * Enhance a class to intercept class static methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected abstract DynamicType.Builder<?> enhanceClass(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
                                                  ClassLoader classLoader) throws PluginException;

}
```



> `ClassEnhancePluginDefine` 是 `AbstractClassEnhancePluginDefine`子类。实现了具体的增强逻辑。

```java
public abstract class ClassEnhancePluginDefine extends AbstractClassEnhancePluginDefine {
    private static final ILog LOGGER = LogManager.getLogger(ClassEnhancePluginDefine.class);

    /**
     * 增强类的静态方法
     * Enhance a class to intercept constructors and class instance methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected DynamicType.Builder<?> enhanceInstance(TypeDescription typeDescription,
        DynamicType.Builder<?> newClassBuilder, ClassLoader classLoader,
        EnhanceContext context) throws PluginException {
        // 获取拦截点 (构造方法拦截点，实例方法拦截点)
        ConstructorInterceptPoint[] constructorInterceptPoints = getConstructorsInterceptPoints();
        InstanceMethodsInterceptPoint[] instanceMethodsInterceptPoints = getInstanceMethodsInterceptPoints();

        // 被拦截的类的类名
        String enhanceOriginClassName = typeDescription.getTypeName();

        boolean existedConstructorInterceptPoint = false;
        if (constructorInterceptPoints != null && constructorInterceptPoints.length > 0) {
            existedConstructorInterceptPoint = true;
        }
        boolean existedMethodsInterceptPoints = false;
        if (instanceMethodsInterceptPoints != null && instanceMethodsInterceptPoints.length > 0) {
            existedMethodsInterceptPoints = true;
        }

        /**
         * nothing need to be enhanced in class instance, maybe need enhance static methods.
         */
        if (!existedConstructorInterceptPoint && !existedMethodsInterceptPoints) {
            return newClassBuilder;
        }

        /**
         *
         * 修改类的源码
         * Manipulate class source code.<br/>
         *
         * new class need:<br/>
         * 1.Add field, name {@link #CONTEXT_ATTR_NAME}.
         * 2.Add a field accessor for this field.
         *
         * And make sure the source codes manipulation only occurs once.
         *
         */
        if (!typeDescription.isAssignableTo(EnhancedInstance.class)) {
            if (!context.isObjectExtended()) {
                // 增加字段或者方法
                newClassBuilder = newClassBuilder
                        // 增加这个字段
                        .defineField(CONTEXT_ATTR_NAME, Object.class, ACC_PRIVATE | ACC_VOLATILE)
                        // 实现这个类
                        .implement(EnhancedInstance.class)
                        .intercept(FieldAccessor.ofField(CONTEXT_ATTR_NAME));
                context.extendObjectCompleted();
            }
        }

        /**
         *
         * 存在 构造器拦截点
         * 2. enhance constructors
         */
        if (existedConstructorInterceptPoint) {
            for (ConstructorInterceptPoint constructorInterceptPoint : constructorInterceptPoints) {
                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher())
                                                     .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.withDefaultConfiguration()
                                                                                                                 .to(BootstrapInstrumentBoost
                                                                                                                     .forInternalDelegateClass(constructorInterceptPoint
                                                                                                                         .getConstructorInterceptor()))));
                } else {
                    newClassBuilder = newClassBuilder.constructor(constructorInterceptPoint.getConstructorMatcher())
                            // 代理的逻辑
                                                     .intercept(SuperMethodCall.INSTANCE.andThen(MethodDelegation.withDefaultConfiguration()
                                                                                                                 .to(new ConstructorInter(constructorInterceptPoint
                                                                                                                     .getConstructorInterceptor(), classLoader))));
                }
            }
        }

        /**
         * 存在实例方法 拦截前
         *
         * 3. enhance instance methods
         */
        if (existedMethodsInterceptPoints) {
            for (InstanceMethodsInterceptPoint instanceMethodsInterceptPoint : instanceMethodsInterceptPoints) {
                String interceptor = instanceMethodsInterceptPoint.getMethodsInterceptor();
                if (StringUtil.isEmpty(interceptor)) {
                    throw new EnhanceException("no InstanceMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
                }
                ElementMatcher.Junction<MethodDescription> junction = not(isStatic()).and(instanceMethodsInterceptPoint.getMethodsMatcher());

                // DeclaredInstanceMethodsInterceptPoint 声明式拦截点。一般用于判断spring 的注解
                if (instanceMethodsInterceptPoint instanceof DeclaredInstanceMethodsInterceptPoint) {
                    // 辅助判断。
                    junction = junction.and(ElementMatchers.<MethodDescription>isDeclaredBy(typeDescription));
                }
                if (instanceMethodsInterceptPoint.isOverrideArgs()) {
                    // 重写入参？
                    if (isBootstrapInstrumentation()) {
                        newClassBuilder = newClassBuilder.method(junction)
                                                         .intercept(MethodDelegation.withDefaultConfiguration()
                                                                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                    } else {
                        newClassBuilder = newClassBuilder.method(junction)
                                                         .intercept(MethodDelegation.withDefaultConfiguration()
                                                                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                                                                    .to(new InstMethodsInterWithOverrideArgs(interceptor, classLoader)));
                    }
                } else {
                    // 没有重新入参
                    if (isBootstrapInstrumentation()) {
                        newClassBuilder = newClassBuilder.method(junction)
                                                         .intercept(MethodDelegation.withDefaultConfiguration()
                                                                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                    } else {
                        newClassBuilder = newClassBuilder.method(junction)
                                                         .intercept(MethodDelegation.withDefaultConfiguration()
                                                                                    .to(new InstMethodsInter(interceptor, classLoader)));
                    }
                }
            }
        }

        return newClassBuilder;
    }

    /**
     * 静态方法增强逻辑
     * Enhance a class to intercept class static methods.
     *
     * @param typeDescription target class description
     * @param newClassBuilder byte-buddy's builder to manipulate class bytecode.
     * @return new byte-buddy's builder for further manipulation.
     */
    protected DynamicType.Builder<?> enhanceClass(TypeDescription typeDescription, DynamicType.Builder<?> newClassBuilder,
        ClassLoader classLoader) throws PluginException {

        // 获取静态方法拦截点。 调用的 AbstractClassEnhancePluginDefine 的方法
        // 其实就是插件定中，写的那个方法
        StaticMethodsInterceptPoint[] staticMethodsInterceptPoints = getStaticMethodsInterceptPoints();

        // 简单认为获取className
        String enhanceOriginClassName = typeDescription.getTypeName();
        if (staticMethodsInterceptPoints == null || staticMethodsInterceptPoints.length == 0) {
            return newClassBuilder;
        }

        for (StaticMethodsInterceptPoint staticMethodsInterceptPoint : staticMethodsInterceptPoints) {
            String interceptor = staticMethodsInterceptPoint.getMethodsInterceptor();
            // 没有拦截器，就报错
            if (StringUtil.isEmpty(interceptor)) {
                throw new EnhanceException("no StaticMethodsAroundInterceptor define to enhance class " + enhanceOriginClassName);
            }

            // 是否修改原方法入参
            if (staticMethodsInterceptPoint.isOverrideArgs()) {
                // BootstrapClassLoader 加载器加载的插件吗？
                // 换个说法就是  是JDK类库中的类吗

                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))
                            // intercept 拦截
                            .intercept(MethodDelegation.withDefaultConfiguration()
                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                } else {
                    // 修改参数的，静态方法增强逻辑。
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))
                            .intercept(MethodDelegation.withDefaultConfiguration()
                                    .withBinders(Morph.Binder.install(OverrideCallable.class))
                                    .to(new StaticMethodsInterWithOverrideArgs(interceptor)));
                }
            } else {
                // BootstrapClassLoader 加载器加载的插件吗？
                // 换个说法就是  是JDK类库中的类吗
                if (isBootstrapInstrumentation()) {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))
                            .intercept(MethodDelegation.withDefaultConfiguration()
                                    .to(BootstrapInstrumentBoost.forInternalDelegateClass(interceptor)));
                } else {
                    newClassBuilder = newClassBuilder.method(isStatic().and(staticMethodsInterceptPoint.getMethodsMatcher()))

                            // intercept 拦截
                            // StaticMethodsInter 包装的一个代理类
                            .intercept(MethodDelegation.withDefaultConfiguration().to(new StaticMethodsInter(interceptor)));
                }
            }

        }

        return newClassBuilder;
    }

    /**
     * @return null, means enhance no v2 instance methods.
     */
    @Override
    public InstanceMethodsInterceptV2Point[] getInstanceMethodsInterceptV2Points() {
        return null;
    }

    /**
     * @return null, means enhance no v2 static methods.
     */
    @Override
    public StaticMethodsInterceptV2Point[] getStaticMethodsInterceptV2Points() {
        return null;
    }

}
```







### 服务



#### `BootService`

 是所有服务的顶层接口，定义了服务的声明周期

- 准备阶段 `prepare`

- 启动阶段 `boot`

- 启动完成阶段 `onComplete`

- 关闭阶段 `shutdown`

- 优先级 `priority`

  优先级高的服务先启动，后关闭



#### `GRPCChannelManager`

>  Agent到 OAP 的大动脉，也就是网络链接



`GRPCChannelManager`服务负责Agent和OAP直接的网络通信。他的职责是创建链接、通信检查和通知。

重点逻辑在`run`方法里



1. 根据 `IS_RESOLVE_DNS_PERIODICALLY`、`reconnect` 属性，判断是否要刷新DNS 或者重连。条件成立就重新解析 OAP的网络地址

2. 根据`reconnect` 属性进行重连操作，在`OAP`网络地址 >0 的情况下。

3. 假设有多个OAP地址，那就换个地址进行连接，

   1. 假设有多个OAP地址，那就换个地址进行连接，
      1. 先关闭上次的链接，如果有的话。
      2. 新建连接，
      3. 通知所有用到这个连接的服务，这个网络可以用了。`GRPCChannelManager提供了注册监听的功能，有需要的服务可以接收到网络状态变动的通知`
      4. 设置网络重连册数 为0
      5. 设置重连标志为false

   1. 不巧，只有一个，那只能依靠GRPC的重连策略了。
      1. 成功了,发通知。没成功，就等下一轮吧

```java

/**
 *
 * 这个服务是 Agent到 OAP 的大动脉，也就是网络链接
 */
@DefaultImplementor
public class GRPCChannelManager implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(GRPCChannelManager.class);

    // 网络连接
    private volatile GRPCChannel managedChannel = null;

    // 网络连接状态定时检查调度器
    private volatile ScheduledFuture<?> connectCheckFuture;

    // 当前链接 是否需要重连
    private volatile boolean reconnect = true;
    private final Random random = new Random();
    private final List<GRPCChannelListener> listeners = Collections.synchronizedList(new LinkedList<>());

    // oap 地址列表
    private volatile List<String> grpcServers;

    // 上次选择的 OAP 地址的 下标索引
    private volatile int selectedIdx = -1;
    // 设置网络重连次数
    private volatile int reconnectCount = 0;

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
        // 检查 OPA 地址
        if (Config.Collector.BACKEND_SERVICE.trim().length() == 0) {
            LOGGER.error("Collector server addresses are not set.");
            LOGGER.error("Agent will not uplink any data.");
            return;
        }
        grpcServers = Arrays.asList(Config.Collector.BACKEND_SERVICE.split(","));

        // 创建线程池。
        // 添加一个定时任务
        connectCheckFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("GRPCChannelManager")
        ).scheduleAtFixedRate(
                // 任务
            new RunnableWithExceptionProtection(
                this,
                t -> LOGGER.error("unexpected exception.", t)
            ), 0, Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL, TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
        // 关闭调度器
        if (connectCheckFuture != null) {
            connectCheckFuture.cancel(true);
        }
        // 关闭连接
        if (managedChannel != null) {
            managedChannel.shutdownNow();
        }
        LOGGER.debug("Selected collector grpc service shutdown.");
    }

    @Override
    public void run() {
        LOGGER.debug("Selected collector grpc service running, reconnect:{}.", reconnect);
        // 需要刷新DNS 并且 需要重连。
        if (IS_RESOLVE_DNS_PERIODICALLY && reconnect) {
            // 第一个后端地址
            String backendService = Config.Collector.BACKEND_SERVICE.split(",")[0];
            try {
                String[] domainAndPort = backendService.split(":");
                // 找到 domain 对应的所有IP
                List<String> newGrpcServers = Arrays
                        .stream(InetAddress.getAllByName(domainAndPort[0]))
                        .map(InetAddress::getHostAddress)
                        .map(ip -> String.format("%s:%s", ip, domainAndPort[1]))
                        .collect(Collectors.toList());

                grpcServers = newGrpcServers;
            } catch (Throwable t) {
                LOGGER.error(t, "Failed to resolve {} of backend service.", backendService);
            }
        }


        if (reconnect) {
            if (grpcServers.size() > 0) {
                String server = "";
                try {
                    // 计算索引下标
                    int index = Math.abs(random.nextInt()) % grpcServers.size();

                    // 不等于上次的下标。 表示要换个OAP地址。 去重连
                    if (index != selectedIdx) {
                        selectedIdx = index;

                        server = grpcServers.get(index);
                        String[] ipAndPort = server.split(":");

                        // 关闭上次的链接
                        if (managedChannel != null) {
                            managedChannel.shutdownNow();
                        }

                        // 新建链接
                        managedChannel = GRPCChannel.newBuilder(ipAndPort[0], Integer.parseInt(ipAndPort[1]))
                                                    .addManagedChannelBuilder(new StandardChannelBuilder())
                                                    .addManagedChannelBuilder(new TLSChannelBuilder())
                                                    .addChannelDecorator(new AgentIDDecorator())
                                                    .addChannelDecorator(new AuthenticationDecorator())
                                                    .build();
                        // 通知所有使用到这个网络连接的服务。 这个网络可以用了
                        notify(GRPCChannelStatus.CONNECTED);
                        // 设置网络重连册数 为0
                        reconnectCount = 0;
                        // 设置 不需要重连了
                        reconnect = false;
                    } else if (managedChannel.isConnected(++reconnectCount > Config.Agent.FORCE_RECONNECTION_PERIOD)) {
                        // 是否已经连接上了(原地址)。可以理解为 恢复了

                        // Reconnect to the same server is automatically done by GRPC,
                        // therefore we are responsible to check the connectivity and
                        // set the state and notify listeners
                        reconnectCount = 0;
                        notify(GRPCChannelStatus.CONNECTED);
                        reconnect = false;
                    }

                    return;
                } catch (Throwable t) {
                    LOGGER.error(t, "Create channel to {} fail.", server);
                }
            }

            LOGGER.debug(
                "Selected collector grpc service is not available. Wait {} seconds to retry",
                Config.Collector.GRPC_CHANNEL_CHECK_INTERVAL
            );
        }
    }

    public void addChannelListener(GRPCChannelListener listener) {
        listeners.add(listener);
    }

    public Channel getChannel() {
        return managedChannel.getChannel();
    }

    /**
     * If the given exception is triggered by network problem, connect in background.
     * 报告一个错误。如果是网络异常 。1.设置重连,2.通知所有监听者
     */
    public void reportError(Throwable throwable) {
        if (isNetworkError(throwable)) {
            reconnect = true;
            notify(GRPCChannelStatus.DISCONNECT);
        }
    }

    private void notify(GRPCChannelStatus status) {
        // 通知所有的监听器
        for (GRPCChannelListener listener : listeners) {
            try {
                listener.statusChanged(status);
            } catch (Throwable t) {
                LOGGER.error(t, "Fail to notify {} about channel connected.", listener.getClass().getName());
            }
        }
    }

    private boolean isNetworkError(Throwable throwable) {
        if (throwable instanceof StatusRuntimeException) {
            StatusRuntimeException statusRuntimeException = (StatusRuntimeException) throwable;
            return statusEquals(
                statusRuntimeException.getStatus(), Status.UNAVAILABLE, Status.PERMISSION_DENIED,
                Status.UNAUTHENTICATED, Status.RESOURCE_EXHAUSTED, Status.UNKNOWN
            );
        }
        return false;
    }

    private boolean statusEquals(Status sourceStatus, Status... potentialStatus) {
        for (Status status : potentialStatus) {
            if (sourceStatus.getCode() == status.getCode()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }
}

```



#### `ServiceManagementClient`

> 负责数据汇报和心跳

和 `GRPCChannelManager`一样，使用线程池执行定时任务， ``逻辑主要在`run`方法中。

实现了 `GRPCChannelListener`接口，监听网络的变动，如果连接是通的，则实例化`managementServiceBlockingStub`属性，准备向OAP发送数据。

**`run`方法的逻辑**

首先，定时任务的周期是30秒。根据周期和配置中的汇报频率因子进行判断`(默认是5分钟)`，条件成立的话，汇报数据。否则发送心跳`(也就是30秒发送一次心跳)`。



```java


/**
 * 建立连接后 打招呼/自报家门
 * 1. 将当前 Agent Client 的基本信息汇报给 OAP
 * 2. 和OAP 保持心跳
 */
@DefaultImplementor
public class ServiceManagementClient implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(ServiceManagementClient.class);

    /**
     * Agent Client 信息
     */
    private static List<KeyStringValuePair> SERVICE_INSTANCE_PROPERTIES;

    /**
     * 当前网络连接状态
     */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    /**
     * grpc 的 Stub 可以理解为 在 protobuf 中定义的 XxxService
     * 这里是  网络服务
     */
    private volatile ManagementServiceGrpc.ManagementServiceBlockingStub managementServiceBlockingStub;
    private volatile ScheduledFuture<?> heartbeatFuture;


    /**
     * Agent Client 信息发送计数器
     */
    private volatile AtomicInteger sendPropertiesCounter = new AtomicInteger(0);

    /**
     * 网络状态变动
     *
     * @param status
     */
    @Override
    public void statusChanged(GRPCChannelStatus status) {
        // 是连接的？
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            // 找到ServiceManager 服务，拿到网络连接
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            //  grpc 的 Stub 可以理解为 在 protobuf 中定义的 XxxService
            managementServiceBlockingStub = ManagementServiceGrpc.newBlockingStub(channel);
        } else {
            managementServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void prepare() {
        // 把自身注册为监听器
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);

        SERVICE_INSTANCE_PROPERTIES = new ArrayList<>();

        // 从配置文件中读取数据  Config.Agent.INSTANCE_PROPERTIES
        // 把 配置文件中的 Agent Client 信息放入集合、等待发送
        for (String key : Config.Agent.INSTANCE_PROPERTIES.keySet()) {
            SERVICE_INSTANCE_PROPERTIES.add(KeyStringValuePair.newBuilder()
                    .setKey(key)
                    .setValue(Config.Agent.INSTANCE_PROPERTIES.get(key))
                    .build());
        }
    }

    @Override
    public void boot() {
        // 创建线程池
        // 和 GRPCChannelManager  一样
        heartbeatFuture = Executors.newSingleThreadScheduledExecutor(
                new DefaultNamedThreadFactory("ServiceManagementClient")
        ).scheduleAtFixedRate(
                // 放入this。 看run方法
                new RunnableWithExceptionProtection(
                        this,
                        t -> LOGGER.error("unexpected exception.", t)
                ), 0, Config.Collector.HEARTBEAT_PERIOD,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        heartbeatFuture.cancel(true);
    }

    @Override
    public void run() {
        LOGGER.debug("ServiceManagementClient running, status:{}.", status);
        // 是否已连接
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            try {
                if (managementServiceBlockingStub != null) {
                    // 取绝对值？ 发送次数过多的情况下，可能超过int最大值

                    // 心跳周期是30秒，信息汇报频率因子 = 10  ==> 以此推论。每5分钟 向OAP 汇报一次 Agent Client 的 Properties
                    // Round 1, counter = 0 0%10 =0  //条件成立
                    // Round 2, counter = 1 1%10 =1
                    if (Math.abs(sendPropertiesCounter.getAndAdd(1)) % Config.Collector.PROPERTIES_REPORT_PERIOD_FACTOR == 0) {

                        // 组装数据
                        managementServiceBlockingStub
                                .withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS)
                                .reportInstanceProperties(
                                        InstanceProperties.newBuilder()
                                                .setService(Config.Agent.SERVICE_NAME)
                                                .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                                .addAllProperties(OSUtil.buildOSInfo(
                                                        Config.OsInfo.IPV4_LIST_SIZE))
                                                .addAllProperties(SERVICE_INSTANCE_PROPERTIES)
                                                .addAllProperties(LoadedLibraryCollector.buildJVMInfo())
                                                .build()
                                );
                    } else {
                        // 发送心跳
                        final Commands commands = managementServiceBlockingStub.withDeadlineAfter(
                                GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                        ).keepAlive(InstancePingPkg.newBuilder()
                                .setService(Config.Agent.SERVICE_NAME)
                                .setServiceInstance(Config.Agent.INSTANCE_NAME)
                                .build());

                        // 处理服务端相应数据
                        ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                    }
                }
            } catch (Throwable t) {
                LOGGER.error(t, "ServiceManagementClient execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }
}
```





#### `CommandService`

> 可以理解为 Command Scheduler 命令的调度器
>
> 收集 OAP 返回的 CommandS， 然后分发给不同的处理器去处理



***属性字段***

```java
/**
 * 命名的处理流程，是否在运行
 */
private volatile boolean isRunning = true;

/**
 * 初始化 单线程线程池
 */
private ExecutorService executorService = Executors.newSingleThreadExecutor(
    new DefaultNamedThreadFactory("CommandService")
);

/**
 * 待处理命令列表
 */
private LinkedBlockingQueue<BaseCommand> commands = new LinkedBlockingQueue<>(64);

/**
 * 序列号缓存
 * 里面是个先入先出的队列，默认能放64个，
 */
private CommandSerialNumberCache serialNumberCache = new CommandSerialNumberCache();
```



***逻辑***

1. 生命周期

   启动阶段`boot()`向线程池提交任务，是自己的`run()`方法。是一个循环，在`isRunning = true`的情况下，不停的从待处理命令队列`this.commands`中取出数据，通过 命令序列号缓存`serialNumberCache`验证，保证命令不会重复进行，最后交由`CommandExecutorService`进行处理。

2. `receiveCommand`

   被`ServiceManagementClient`调用的方法，用来接收服务端返回的`command`数据，进行反序列化，最后放到`this.commands`中，供`CommandExecutorService`方法处理。



***源码***



```java
/**
 * 可以理解为 Command Scheduler 命令的调度器
 *
 * 收集 OAP 返回的 CommandS， 然后分发给不同的处理器去处理
 *
 */
@DefaultImplementor
public class CommandService implements BootService, Runnable {

    private static final ILog LOGGER = LogManager.getLogger(CommandService.class);

    /**
     * 命名的处理流程，是否在运行
     */
    private volatile boolean isRunning = true;

    /**
     * 初始化 单线程线程池
     */
    private ExecutorService executorService = Executors.newSingleThreadExecutor(
        new DefaultNamedThreadFactory("CommandService")
    );

    /**
     * 待处理命令列表
     */
    private LinkedBlockingQueue<BaseCommand> commands = new LinkedBlockingQueue<>(64);

    /**
     * 序列号缓存
     * 里面是个先入先出的队列，默认能放64个，
     */
    private CommandSerialNumberCache serialNumberCache = new CommandSerialNumberCache();

    @Override
    public void prepare() throws Throwable {
    }


    /**
     * 启动阶段。提交一个自己到线程池
     * @throws Throwable
     */
    @Override
    public void boot() throws Throwable {
        executorService.submit(
            new RunnableWithExceptionProtection(this, t -> LOGGER.error(t, "CommandService failed to execute commands"))
        );
    }

    /**
     * 不断从命令队列(任务队列)中取出任务，交给执行器去执行。
     */
    @Override
    public void run() {
        final CommandExecutorService commandExecutorService = ServiceManager.INSTANCE.findService(CommandExecutorService.class);

        // isRunning 命名的处理流程，是否在运行
        while (isRunning) {
            try {
                BaseCommand command = commands.take();

                // 命令是否已经执行了  ==》 有没有缓存这个序列号
                // 保证 同一个命令，不重复执行
                if (isCommandExecuted(command)) {
                    continue;
                }
                // 交给 commandExecutorService 处理命令
                commandExecutorService.execute(command);
                // 命令序列号缓存
                serialNumberCache.add(command.getSerialNumber());
            } catch (InterruptedException e) {
                LOGGER.error(e, "Failed to take commands.");
            } catch (CommandExecutionException e) {
                LOGGER.error(e, "Failed to execute command[{}].", e.command().getCommand());
            } catch (Throwable e) {
                LOGGER.error(e, "There is unexpected exception");
            }
        }
    }

    private boolean isCommandExecuted(BaseCommand command) {
        return serialNumberCache.contain(command.getSerialNumber());
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        isRunning = false;
        // 清空
        commands.drainTo(new ArrayList<>());
        executorService.shutdown();
    }

    /**
     *  被 ServiceManagementClient  调用的方法
     * @param commands 接收到命令s
     */
    public void receiveCommand(Commands commands) {


        for (Command command : commands.getCommandsList()) {
            try {
                // 反序列化
                BaseCommand baseCommand = CommandDeserializer.deserialize(command);

                // 已经执行过了
                if (isCommandExecuted(baseCommand)) {
                    LOGGER.warn("Command[{}] is executed, ignored", baseCommand.getCommand());
                    continue;
                }
                // 放入 this.commands  待处理的命令队列
                boolean success = this.commands.offer(baseCommand);

                if (!success && LOGGER.isWarnEnable()) {
                    LOGGER.warn(
                        "Command[{}, {}] cannot add to command list. because the command list is full.",
                        baseCommand.getCommand(), baseCommand.getSerialNumber()
                    );
                }
            } catch (UnsupportedCommandException e) {
                if (LOGGER.isWarnEnable()) {
                    LOGGER.warn("Received unsupported command[{}].", e.getCommand().getCommand());
                }
            }
        }
    }
}
```



***涉及到的类***



##### `CommandSerialNumberCache`

> 命令序列号缓存。
>
> 服务端返回的`command`数据，每一条都有一个序列号。这里进行缓存，主要是保证每一个`command`只执行一次，避免重复执行。
>
> 内部结构是一个容量是64的先入先出队列。

```java

/**
 * 命令的序列号缓存。序列号被放到一个队列里面，
 * 容量控制为64，先入先出
 */
public class CommandSerialNumberCache {
    private static final int DEFAULT_MAX_CAPACITY = 64;
    private final Deque<String> queue;
    private final int maxCapacity;

    public CommandSerialNumberCache() {
        this(DEFAULT_MAX_CAPACITY);
    }

    public CommandSerialNumberCache(int maxCapacity) {
        queue = new LinkedBlockingDeque<String>(maxCapacity);
        this.maxCapacity = maxCapacity;
    }

    public void add(String number) {
        if (queue.size() >= maxCapacity) {
            queue.pollFirst();
        }

        queue.add(number);
    }

    public boolean contain(String command) {
        return queue.contains(command);
    }
}

```



##### `CommandDeserializer`

在`receiveCommand`方法中，有一步反序列的操作，就是使用`CommandDeserializer` 完成的。其实这个也是分发，根据`commandName`交给不同的反序列化器再去反序列化。目前有两个

- `ProfileTaskCommand`: 性能追踪命令反序列化器
- `ConfigurationDiscoveryCommand`: 配置发现命令反序列化器



```java
/**
 * 反序列化器
 */
public class CommandDeserializer {

    public static BaseCommand deserialize(final Command command) {
        final String commandName = command.getCommand();
        // 做性能追踪的命令？用这个序列化
        // 在SkyWalking UI性能剖析功能中，新建任务，会下发给Agent性能追踪任务
        if (ProfileTaskCommand.NAME.equals(commandName)) {
            return ProfileTaskCommand.DESERIALIZER.deserialize(command);
        } else if (ConfigurationDiscoveryCommand.NAME.equals(commandName)) {
            // 改配置的命令？ 用这个序列化
            // 当前版本SkyWalking Agent支持运行时动态调整配置
            return ConfigurationDiscoveryCommand.DESERIALIZER.deserialize(command);
        }
        throw new UnsupportedCommandException(command);
    }

}

```





##### `ConfigurationDiscoveryCommand`

配置发现命令反序列化器,主要看一下`deserialize()`。最后就是解析`command`返回一个 `ConfigurationDiscoveryCommand`对象。

```java
    @Override
    public ConfigurationDiscoveryCommand deserialize(Command command) {
        String serialNumber = null;
        String uuid = null;
        List<KeyStringValuePair> config = new ArrayList<>();

        for (final KeyStringValuePair pair : command.getArgsList()) {
            // 序列号
            if (SERIAL_NUMBER_CONST_NAME.equals(pair.getKey())) {
                serialNumber = pair.getValue();
            } else if (UUID_CONST_NAME.equals(pair.getKey())) {
                // UUID

                uuid = pair.getValue();
            } else {
                config.add(pair);
            }
        }
        return new ConfigurationDiscoveryCommand(serialNumber, uuid, config);
    }
```



##### `CommandExecutorService`

这个比较重要。`CommandService.run()`中，将`BaseCommand`取出后，先用`命令序列号缓存`验证一下。然后就交给`CommandExecutorService.execute()`了。

当然，这个类也是主要做分发作用😅，和反序列类似：

- 如果是性能追踪的命令，交给`ProfileTaskCommandExecutor.execute()`
- 如果是配置发现的命令，交给`ConfigurationDiscoveryCommandExecutor.execute()`



```java

/**
 * Command executor service, acts like a routing executor that controls all commands' execution, is responsible for
 * managing all the mappings between commands and their executors, one can simply invoke {@link #execute(BaseCommand)}
 * and it will routes the command to corresponding executor.
 * <p>
 * Registering command executor for new command in {@link #commandExecutorMap} is required to support new command.
 *
 * 根据命令，再分发到具体的执行器
 * 看 ${@link ConfigurationDiscoveryCommandExecutor}
 *
 */
@DefaultImplementor
public class CommandExecutorService implements BootService, CommandExecutor {

    /**
     * 命令执行器 map
     */
    private Map<String, CommandExecutor> commandExecutorMap;

    @Override
    public void prepare() throws Throwable {
        // 初始化。 也是分为 性能追踪、配置变化

        commandExecutorMap = new HashMap<String, CommandExecutor>();

        // Profile task executor
        commandExecutorMap.put(ProfileTaskCommand.NAME, new ProfileTaskCommandExecutor());

        //Get ConfigurationDiscoveryCommand executor.
        commandExecutorMap.put(ConfigurationDiscoveryCommand.NAME, new ConfigurationDiscoveryCommandExecutor());
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }

    @Override
    public void execute(final BaseCommand command) throws CommandExecutionException {
        // 拿到执行器。 执行
        executorForCommand(command).execute(command);
    }

    private CommandExecutor executorForCommand(final BaseCommand command) {
        final CommandExecutor executor = commandExecutorMap.get(command.getCommand());
        if (executor != null) {
            return executor;
        }
        return NoopCommandExecutor.INSTANCE;
    }
}

```



##### `ConfigurationDiscoveryCommandExecutor`

巧了，依然是转发。下面咱们看`ConfigurationDiscoveryService.handleConfigurationDiscoveryCommand()`。

```java
/**
 * 配置命令解析
 */
public class ConfigurationDiscoveryCommandExecutor implements CommandExecutor {

    private static final ILog LOGGER = LogManager.getLogger(ConfigurationDiscoveryCommandExecutor.class);

    @Override
    public void execute(BaseCommand command) throws CommandExecutionException {
        try {
            ConfigurationDiscoveryCommand agentDynamicConfigurationCommand = (ConfigurationDiscoveryCommand) command;

            // 再拿一个 ConfigurationDiscoveryService
            ServiceManager.INSTANCE.findService(ConfigurationDiscoveryService.class)
                        // 调用的方法
                                   .handleConfigurationDiscoveryCommand(agentDynamicConfigurationCommand);
        } catch (Exception e) {
            LOGGER.error(e, "Handle ConfigurationDiscoveryCommand error, command:{}", command.toString());
        }
    }
}
```



##### `ConfigurationDiscoveryService`

注意，这是一个`service`。它身上有三条逻辑线：

- `网络监听器` ： 它实现了`GRPCChannelListener`,监听网络变化。在网络联通的情况下，组装`configurationDiscoveryServiceBlockingStub`,用于向服务端发请求，拉取配置信息。

- `处理服务端返回的command`: 从`CommandService.receiveCommand()`开始，经过反序列化，各个类的转发，最终这个类的`handleConfigurationDiscoveryCommand()`来做最后的处理。`仅限配置发现的command`

- `拉取配置`：在生命周期方法`boot`中，创建了一个单线程线程池，每20秒调用`getAgentDynamicConfig()`向服务端发送请求，拉取配置。

  特别说一下，发送请求后，将返回`command`交给了`CommandService.receiveCommand()`。这就闭环了啊，最终一定是传到`handleConfigurationDiscoveryCommand()`来处理。



###### `getAgentDynamicConfig`



首先是组装请求数据，然后进行`watcherSize`验证，在通过在网络监听器中实例化的`configurationDiscoveryServiceBlockingStub`发送数据，拿到返回值后，交给`CommandService.receiveCommand()`处理。其中特别说一下 `watcher`这个东西。



`watcherSize验证`

首先说一下``watch``,在`ConfigurationDiscoveryService.Register`中封装了一个map。value是`WatcherHolder`。而`WatcherHolder`又封装了 `AgentConfigChangeWatcher`。咱们直接看`AgentConfigChangeWatcher`。`AgentConfigChangeWatcher` 用于监控配置信息的变动。

这里的验证就是说，如果需要被监听的配置key发生了变化，那我就要清空uuid,重新加载配置，`uuid和command的uuid一样，就表示配置没变，不需要刷新配置`，清空的意思就是不管变不变，在`handleConfigurationDiscoveryCommand()`处理返回信息时，一定是进入到变更的逻辑中的。



##### `handleConfigurationDiscoveryCommand`

处理的逻辑就比较清晰了。

1. 验证一下uuid,没变就不处理了。
2. 读取配置；其实是对返回值进行处理、过滤。将没有`watcher`的配置key过滤掉。
3. 然后遍历，发通知； 遍历被watcher的key,根据key拿到`wahter`对象，调用`watcher`对象的`notify()`发送通知。



```java
/**
 * 配置的解析service
 */
@DefaultImplementor
public class ConfigurationDiscoveryService implements BootService, GRPCChannelListener {

    /**
     * UUID of the last return value.
     */
    private String uuid;

    /**
     *
     */
    private final Register register = new Register();

    /**
     * 上一次计算的 watcher 数量
     */
    private volatile int lastRegisterWatcherSize;

    private volatile ScheduledFuture<?> getDynamicConfigurationFuture;
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;
    private volatile ConfigurationDiscoveryServiceGrpc.ConfigurationDiscoveryServiceBlockingStub configurationDiscoveryServiceBlockingStub;

    private static final ILog LOGGER = LogManager.getLogger(ConfigurationDiscoveryService.class);

    /**
     * 网络监听 处理
     * @param status
     */
    @Override
    public void statusChanged(final GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            configurationDiscoveryServiceBlockingStub = ConfigurationDiscoveryServiceGrpc.newBlockingStub(channel);
        } else {
            configurationDiscoveryServiceBlockingStub = null;
        }
        this.status = status;
    }

    @Override
    public void prepare() throws Throwable {
        // 注册监听
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        // 定时任务，看run方法
        getDynamicConfigurationFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("ConfigurationDiscoveryService")
        ).scheduleAtFixedRate(
            new RunnableWithExceptionProtection(
                this::getAgentDynamicConfig,
                t -> LOGGER.error("Sync config from OAP error.", t)
            ),
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL,
            Config.Collector.GET_AGENT_DYNAMIC_CONFIG_INTERVAL,
            TimeUnit.SECONDS
        );
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        if (getDynamicConfigurationFuture != null) {
            getDynamicConfigurationFuture.cancel(true);
        }
    }

    /**
     *
     * 注册 配置变更  监听
     *
     * Register dynamic configuration watcher.
     *
     * @param watcher dynamic configuration watcher
     */
    public void registerAgentConfigChangeWatcher(AgentConfigChangeWatcher watcher) {
        WatcherHolder holder = new WatcherHolder(watcher);
        if (register.containsKey(holder.getKey())) {
            throw new IllegalStateException("Duplicate register, watcher=" + watcher);
        }
        register.put(holder.getKey(), holder);
    }

    /**
     *
     * 处理 服务端 返回的 配置信息 command
     *
     * Process ConfigurationDiscoveryCommand and notify each configuration watcher.
     *
     * @param configurationDiscoveryCommand Describe dynamic configuration information
     */
    public void handleConfigurationDiscoveryCommand(ConfigurationDiscoveryCommand configurationDiscoveryCommand) {
        // uuid
        // 配置没有变动的话。uuid 也是不会变的
        final String responseUuid = configurationDiscoveryCommand.getUuid();
        if (responseUuid != null && Objects.equals(this.uuid, responseUuid)) {
            return;
        }

        // 配置变了，去读取
        List<KeyStringValuePair> config = readConfig(configurationDiscoveryCommand);

        // 配置变更
        config.forEach(property -> {
            String propertyKey = property.getKey();
            WatcherHolder holder = register.get(propertyKey);
            if (holder != null) {
                AgentConfigChangeWatcher watcher = holder.getWatcher();
                String newPropertyValue = property.getValue();
                if (StringUtil.isBlank(newPropertyValue)) {
                    if (watcher.value() != null) {

                        // 发通知，有变更。 把新值发出去
                        // Notify watcher, the new value is null with delete event type.
                        watcher.notify(
                            new AgentConfigChangeWatcher.ConfigChangeEvent(
                                null, AgentConfigChangeWatcher.EventType.DELETE
                            ));
                    } else {
                        // Don't need to notify, stay in null.
                    }
                } else {
                    if (!newPropertyValue.equals(watcher.value())) {
                        watcher.notify(new AgentConfigChangeWatcher.ConfigChangeEvent(
                            newPropertyValue, AgentConfigChangeWatcher.EventType.MODIFY
                        ));
                    } else {
                        // Don't need to notify, stay in the same config value.
                    }
                }
            } else {
                LOGGER.warn("Config {} from OAP, doesn't match any watcher, ignore.", propertyKey);
            }
        });
        this.uuid = responseUuid;

        LOGGER.trace("Current configurations after the sync, configurations:{}", register.toString());
    }

    /**
     *
     * 读取服务端返回的配置信息
     * Read the registered dynamic configuration, compare it with the dynamic configuration information returned by the
     * service, and complete the dynamic configuration that has been deleted on the OAP.
     *
     * @param configurationDiscoveryCommand Describe dynamic configuration information
     * @return Adapted dynamic configuration information
     */
    private List<KeyStringValuePair> readConfig(ConfigurationDiscoveryCommand configurationDiscoveryCommand) {
        Map<String, KeyStringValuePair> commandConfigs = configurationDiscoveryCommand.getConfig()
                                                                                      .stream()
                                                                                      .collect(Collectors.toMap(
                                                                                          KeyStringValuePair::getKey,
                                                                                          Function.identity()
                                                                                      ));
        List<KeyStringValuePair> configList = Lists.newArrayList();
        for (final String name : register.keys()) {
            KeyStringValuePair command = commandConfigs.getOrDefault(name, KeyStringValuePair.newBuilder()
                                                                                             .setKey(name)
                                                                                             .build());
            configList.add(command);
        }
        return configList;
    }

    /**
     * get agent dynamic config through gRPC.
     * 通知grpc 从 OAP 拿动态的配置信息
     */
    private void getAgentDynamicConfig() {
        LOGGER.debug("ConfigurationDiscoveryService running, status:{}.", status);

        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            try {
                ConfigurationSyncRequest.Builder builder = ConfigurationSyncRequest.newBuilder();
                builder.setService(Config.Agent.SERVICE_NAME);

                // 计算有多少 Watcher
                // Some plugin will register watcher later.
                final int size = register.keys().size();

                //  上一次计算的 watcher 数量
                // 这里说明， 数据监听出现变化。
                if (lastRegisterWatcherSize != size) {
                    // reset uuid, avoid the same uuid causing the configuration not to be updated.
                    uuid = null;
                    lastRegisterWatcherSize = size;
                }

                if (null != uuid) {
                    builder.setUuid(uuid);
                }

                // 向服务端发请求，拿配置信息
                if (configurationDiscoveryServiceBlockingStub != null) {
                    final Commands commands = configurationDiscoveryServiceBlockingStub.withDeadlineAfter(
                        GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
                    ).fetchConfigurations(builder.build());
                    // 再 交给 CommandService 去处理返回信息
                    // 可以预见，最终会到 handleConfigurationDiscoveryCommand 方法
                    // 因为 服务端的返回的是 配置信息 command 。反序列一步步到handleConfigurationDiscoveryCommand。
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "ConfigurationDiscoveryService execute fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    /**
     * Local dynamic configuration center.
     */
    public static class Register {
        /**
         * WatcherHolder   对 AgentConfigChangeWatcher 封装。 方便使用
         * AgentConfigChangeWatcher 是 针对某项 配置 的值，进行监听
         */
        private final Map<String, WatcherHolder> register = new HashMap<>();

        private boolean containsKey(String key) {
            return register.containsKey(key);
        }

        private void put(String key, WatcherHolder holder) {
            register.put(key, holder);
        }

        public WatcherHolder get(String name) {
            return register.get(name);
        }

        public Set<String> keys() {
            return register.keySet();
        }

        @Override
        public String toString() {
            ArrayList<String> registerTableDescription = new ArrayList<>(register.size());
            register.forEach((key, holder) -> {
                AgentConfigChangeWatcher watcher = holder.getWatcher();
                registerTableDescription.add(new StringBuilder().append("key:")
                                                                .append(key)
                                                                .append("value(current):")
                                                                .append(watcher.value()).toString());
            });
            return registerTableDescription.stream().collect(Collectors.joining(",", "[", "]"));
        }
    }

    /**
     * 对 AgentConfigChangeWatcher 封装。 方便使用
     */
    @Getter
    private static class WatcherHolder {
        private final AgentConfigChangeWatcher watcher;
        private final String key;

        public WatcherHolder(AgentConfigChangeWatcher watcher) {
            this.watcher = watcher;
            this.key = watcher.getPropertyKey();
        }
    }
}
```



```java
/**
 * 监听  agent 的 某项配置值的变化
 *代表 原来的值
 *
 * 举例
 *
 * 采样率 = 10% 变更好 = 50%
 *
 * value() = 10%
 *
 */
@Getter
public abstract class AgentConfigChangeWatcher {
    // Config key, should match KEY in the Table of Agent Configuration Properties.

    // 这个key 来源于  agent 配置文件， 也就是说 只有 agent 配置文件中合法的key 才能在这里被使用
    private final String propertyKey;

    public AgentConfigChangeWatcher(String propertyKey) {
        this.propertyKey = propertyKey;
    }

    /**
     * Notify the watcher, the new value received.
     *
     * @param value of new.
     *
     *
     *              value() 变更时，发送通知
     */
    public abstract void notify(ConfigChangeEvent value);

    /**
     * @return current value of current config.
     * 当前的配置值
     */
    public abstract String value();

    @Override
    public String toString() {
        return "AgentConfigChangeWatcher{" +
            "propertyKey='" + propertyKey + '\'' +
            '}';
    }

    @Getter
    @RequiredArgsConstructor
    public static class ConfigChangeEvent {
        private final String newValue;
        private final EventType eventType;
    }

    public enum EventType {
        ADD, MODIFY, DELETE
    }
}
```



#### `SamplingService`

> 采样服务。 控制链路是否被上报到OAP

![image-20221211131300366](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221211131300366.png)

`Agent`会对应用的链路进行采集。但是全部上报到OAP,数据过大。`SamplingService`就是用来控制具体要不要上报的服务。

首先`SamplingService` 会通过`SamplingRateWatcher`对配置项`Config.sample_n_per_3_secs`进行watcher。如果采样率发生变化，会通知到`SamplingService.handleSamplingRateChanged()`。

然后`SamplingService`在启动阶段，先初始化了`SamplingRateWatcher`实例，然后主动调用了`handleSamplingRateChanged()`。



- `Config.sample_n_per_3_secs`: 3秒内最多采集多少条链路。-1或0，表示 采样机制关闭。 全部上报
- `SamplingRateWatcher`: 继承了`AgentConfigChangeWatcher` 了，主要监听采样率的变化。上面已经说了。
- `handleSamplingRateChanged()`: 检查配置中的采样率：
  - 大于0，并且服务未运行`on!=true`： 首先设置标志位`on=true`。重置`3秒内已经采集到的次数`。然后开启定时任务，每3秒重置一次`3秒内已经采集到的次数`。 注: 定时任务前的处理，可以理解为初始化操作`只会在启动时执行一次`。定时任务中的逻辑，才是日常任务。
  - 小于等于0： 把服务关了。因为按照`Config.sample_n_per_3_secs`的说法，小于等于0，就全部上报。所以这里就不管了。当然，如果后面OAP发送开启上报的配置到Agent。`SamplingRateWatcher`就会调用`handleSamplingRateChanged()`。再走上面的逻辑。
- `trySampling()`:用来检查链路是否上报。规则一样 `采样率配置大于0，就按照采样率来算是否上报。如果小于等于0，就上报。`



```java
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.agent.core.sampling;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.conf.dynamic.watcher.SamplingRateWatcher;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.RunnableWithExceptionProtection;

/**
 * The <code>SamplingService</code> take charge of how to sample the {@link TraceSegment}. Every {@link TraceSegment}s
 * have been traced, but, considering CPU cost of serialization/deserialization, and network bandwidth, the agent do NOT
 * send all of them to collector, if SAMPLING is on.
 * <p>
 * By default, SAMPLING is on, and  {@link Config.Agent#SAMPLE_N_PER_3_SECS }
 *
 * 采样服务
 *
 */
@DefaultImplementor
public class SamplingService implements BootService {
    private static final ILog LOGGER = LogManager.getLogger(SamplingService.class);

    // 是否运行
    private volatile boolean on = false;

    /**
     * 累加3秒内已经采样的次数
     */
    private volatile AtomicInteger samplingFactorHolder;

    /**
     * 每三秒重置一次 samplingFactorHolder
     */
    private volatile ScheduledFuture<?> scheduledFuture;

    /**
     *针对采样率这个配置，进行watcher监听
     */
    private SamplingRateWatcher samplingRateWatcher;

    @Override
    public void prepare() {
    }

    @Override
    public void boot() {
        // 初始化 watcher
        samplingRateWatcher = new SamplingRateWatcher("agent.sample_n_per_3_secs", this);

        // 注册监听
        // 当服务端下发 采样率 配置变更的时候。会通过到 samplingRateWatcher.notify()
        // samplingRateWatcher.notify()  会将数据传递到 this.handleSamplingRateChanged()
        ServiceManager.INSTANCE.findService(ConfigurationDiscoveryService.class)
                               .registerAgentConfigChangeWatcher(samplingRateWatcher);

        // 处理采样率更改
        // 为什么在boot 中调用这个方法？
        // 因为把初始化逻辑也放在 handleSamplingRateChanged 中了。
        handleSamplingRateChanged();
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
        }
    }

    /**
     * 8.9.0 之前，这个方法的注释都是过时的
     *
     * 准确的描述是 如果采样机制没有开启， 即 on = false， 那么就表示每一条采集到的链路都会上报给 OAP
     *
     *
     * @param operationName The first operation name of the new tracing context.
     * @return true, if sampling mechanism is on, and getDefault the sampling factor successfully.
     */
    public boolean trySampling(String operationName) {
        if (on) {
            // 开启采样，根据配置的采样率 来决定 链路数据是否上报

            int factor = samplingFactorHolder.get();
            if (factor < samplingRateWatcher.getSamplingRate()) {
                return samplingFactorHolder.compareAndSet(factor, factor + 1);
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 强制采样
     *
     * Increase the sampling factor by force, to avoid sampling too many traces. If many distributed traces require
     * sampled, the trace beginning at local, has less chance to be sampled.
     */
    public void forceSampled() {
        if (on) {
            samplingFactorHolder.incrementAndGet();
        }
    }

    private void resetSamplingFactor() {
        samplingFactorHolder = new AtomicInteger(0);
    }

    /**
     * 处理 采样率变更
     * Handle the samplingRate changed.
     */
    public void handleSamplingRateChanged() {
        // 获取当前采样率，如果大于0，根据配置的采样率进行采样
        if (samplingRateWatcher.getSamplingRate() > 0) {
            // 如果没工作
            if (!on) {
                on = true;
                // 重置 `累加3秒内已经采样的次数` 为 0
                this.resetSamplingFactor();

                // 每3秒重置一次 `累加3秒内已经采样的次数`   resetSamplingFactor

                ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor(
                    new DefaultNamedThreadFactory("SamplingService"));
                scheduledFuture = service.scheduleAtFixedRate(new RunnableWithExceptionProtection(
                    this::resetSamplingFactor, t -> LOGGER.error("unexpected exception.", t)), 0, 3, TimeUnit.SECONDS);
                LOGGER.debug(
                    "Agent sampling mechanism started. Sample {} traces in 3 seconds.",
                    samplingRateWatcher.getSamplingRate()
                );
            }
        } else {
            // 不开启 采样。 就给他关了
            if (on) {
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(true);
                }
                on = false;
            }
        }
    }
}

```



```java
/**
 * 针对采样率这个配置，进行watcher
 *
 */
public class SamplingRateWatcher extends AgentConfigChangeWatcher {
    private static final ILog LOGGER = LogManager.getLogger(SamplingRateWatcher.class);

    /**
     * 每三秒能够采集的最大链路数
     */
    private final AtomicInteger samplingRate;

    /**
     * 持有引用
     * 两个类互相持有引用
     */
    private final SamplingService samplingService;

    public SamplingRateWatcher(final String propertyKey, SamplingService samplingService) {
        super(propertyKey);
        this.samplingRate = new AtomicInteger(getDefaultValue());
        this.samplingService = samplingService;
    }

    /**
     * 通知
     * @param value of new.
     *
     *    value() 变更时，发送通知
     */
    @Override
    public void notify(final ConfigChangeEvent value) {
        // 删除了采样率，设置为默认值
        if (EventType.DELETE.equals(value.getEventType())) {
            activeSetting(String.valueOf(getDefaultValue()));
        } else {
            activeSetting(value.getNewValue());
        }
    }
    private void activeSetting(String config) {
        if (LOGGER.isDebugEnable()) {
            LOGGER.debug("Updating using new static config: {}", config);
        }
        try {
            this.samplingRate.set(Integer.parseInt(config));
            /*
             * We need to notify samplingService the samplingRate changed.
             */
            samplingService.handleSamplingRateChanged();
        } catch (NumberFormatException ex) {
            LOGGER.error(ex, "Cannot load {} from: {}", getPropertyKey(), config);
        }
    }


    @Override
    public String value() {
        return String.valueOf(samplingRate.get());
    }

    private int getDefaultValue() {
        return Config.Agent.SAMPLE_N_PER_3_SECS;
    }

    public int getSamplingRate() {
        return samplingRate.get();
    }
}
```



####  `JVMService`

<img src="https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221211143647084.png" alt="image-20221211143647084" style="zoom:150%;" />

1. 两个定时任务
   1. 采集：1秒一次，通过各种`produce`采集jvm相关数据指标。`CPU,内存,内存池,GC,线程,class`
   2. 发送：1秒一次，调用`JVMMetricsSender`。
2. 各种采集器：定义了一系列`Produce`,去做实际的数据采集工作。`感兴趣可以仔细看看`
3. `JVMMetricsSender`: 用来发送指标数据到OAP
   1. 网络状态监听： 实现监听器，在网络状态畅通的情况下，发送数据。
   2. `offer()`: 接收数据的入口。接到数据后，并没有立即发送，而是放到`指标信息队列`中。是在上面的定时任务中，执行`run()`，进行发送的。



```java
/**
 * The <code>JVMService</code> represents a timer, which collectors JVM cpu, memory, memorypool, gc, thread and class info,
 * and send the collected info to Collector through the channel provided by {@link GRPCChannelManager}
 *
 * JVMService代表一个定时器，收集JVM cpu, memory, memorypool, gc, thread and class信息，
 * 并通过GRPCChannelManager提供的通道发送给Collector采集JVM相关信息
 *
 *
 * 采集JVM相关信息
 */
@DefaultImplementor
public class JVMService implements BootService, Runnable {
    private static final ILog LOGGER = LogManager.getLogger(JVMService.class);

    /**
     * 收集JVM信息的定时任务
     */
    private volatile ScheduledFuture<?> collectMetricFuture;

    /**
     * 发送JVM信息的定时任务
     */
    private volatile ScheduledFuture<?> sendMetricFuture;

    /**
     * JVM信息的发送工具
     */
    private JVMMetricsSender sender;

    @Override
    public void prepare() throws Throwable {
        // 初始化
        sender = ServiceManager.INSTANCE.findService(JVMMetricsSender.class);
    }

    @Override
    public void boot() throws Throwable {
        // 一秒执行一次 this.run()
        collectMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-produce"))
                                       .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                           this,
                                           new RunnableWithExceptionProtection.CallbackWhenException() {
                                               @Override
                                               public void handle(Throwable t) {
                                                   LOGGER.error("JVMService produces metrics failure.", t);
                                               }
                                           }
                                       ), 0, 1, TimeUnit.SECONDS);

        // 1秒执行一次 sender.run()
        sendMetricFuture = Executors.newSingleThreadScheduledExecutor(
            new DefaultNamedThreadFactory("JVMService-consume"))
                                    .scheduleAtFixedRate(new RunnableWithExceptionProtection(
                                        sender,
                                        new RunnableWithExceptionProtection.CallbackWhenException() {
                                            @Override
                                            public void handle(Throwable t) {
                                                LOGGER.error("JVMService consumes and upload failure.", t);
                                            }
                                        }
                                    ), 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        collectMetricFuture.cancel(true);
        sendMetricFuture.cancel(true);
    }

    @Override
    public void run() {
        // 当前时间
        long currentTimeMillis = System.currentTimeMillis();
        try {
            // 构造数据
            // 重点是各种 Provider
            JVMMetric.Builder jvmBuilder = JVMMetric.newBuilder();
            jvmBuilder.setTime(currentTimeMillis);
            // CPU
            jvmBuilder.setCpu(CPUProvider.INSTANCE.getCpuMetric());
            // 内存
            jvmBuilder.addAllMemory(MemoryProvider.INSTANCE.getMemoryMetricList());
            // 内存池
            jvmBuilder.addAllMemoryPool(MemoryPoolProvider.INSTANCE.getMemoryPoolMetricsList());
            // GC
            jvmBuilder.addAllGc(GCProvider.INSTANCE.getGCList());
            // 线程
            jvmBuilder.setThread(ThreadProvider.INSTANCE.getThreadMetrics());
            // class
            jvmBuilder.setClazz(ClassProvider.INSTANCE.getClassMetrics());

            // 交给sender
            sender.offer(jvmBuilder.build());
        } catch (Exception e) {
            LOGGER.error(e, "Collect JVM info fail.");
        }
    }
}
```

```java
/**
 * JVM信息的发送工具
 */
@DefaultImplementor
public class JVMMetricsSender implements BootService, Runnable, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(JVMMetricsSender.class);

    /**
     * 网络状态
     */
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    /**
     * 请求构造器
     */
    private volatile JVMMetricReportServiceGrpc.JVMMetricReportServiceBlockingStub stub = null;

    /**
     * 指标信息队列
     */
    private LinkedBlockingQueue<JVMMetric> queue;

    @Override
    public void prepare() {
        // 队列的数量限制
        queue = new LinkedBlockingQueue<>(Config.Jvm.BUFFER_SIZE);
        // 监听
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {

    }

    /**
     * 插入数据
     * @param metric
     */
    public void offer(JVMMetric metric) {
        // drop last message and re-deliver
        if (!queue.offer(metric)) {
            queue.poll();
            queue.offer(metric);
        }
    }

    @Override
    public void run() {
        // 网络连接是通的？
        if (status == GRPCChannelStatus.CONNECTED) {
            try {

                JVMMetricCollection.Builder builder = JVMMetricCollection.newBuilder();
                LinkedList<JVMMetric> buffer = new LinkedList<>();
                queue.drainTo(buffer);
                if (buffer.size() > 0) {
                    builder.addAllMetrics(buffer);
                    builder.setService(Config.Agent.SERVICE_NAME);
                    builder.setServiceInstance(Config.Agent.INSTANCE_NAME);
                    // 发送
                    Commands commands = stub.withDeadlineAfter(GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS)
                                            .collect(builder.build());
                    // 交给 CommandService 处理
                    ServiceManager.INSTANCE.findService(CommandService.class).receiveCommand(commands);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "send JVM metrics to Collector fail.");
                ServiceManager.INSTANCE.findService(GRPCChannelManager.class).reportError(t);
            }
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (GRPCChannelStatus.CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            stub = JVMMetricReportServiceGrpc.newBlockingStub(channel);
        }
        this.status = status;
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }
}
```





#### `KafkaXxxService`

> 为了减轻Agent直连OAP的压力。skywlking实现了一条基于kafka的通信模式`当然GRPC直接通信还是有的，只是大部分数据走kafka`。
>
> 这涉及到了很多类。但是逻辑和上面的基本类似。 
>
> `GRPCChannelManager  ==对应=> 还KafkaProducerManager`
>
> 感兴趣可以去仔细看看



#### `StatusCheckService`

> 状态检查服务，用来判断那些异常不算异常

- `Config.StatusCheck.IGNORED_EXCEPTIONS`: 有些异常是用来控制流程的,不应该当做错误的异常

- `Config.StatusCheck.MAX_RECURSIVE_DEPTH`: 最大递归程度。

- `StatusChecker`: 是个枚举，里面是具体的检查代码。`这里用到了策略模式`

  - `OFF`: 全部当做错误来处理

  - `HIERARCHY_MATCH`: 两个策略，`HierarchyMatch,AnnotationMatch`

    这里还用到`ExceptionCheckContext`。用来做忽略逻辑处理。

```java
/**
 * The <code>StatusCheckService</code> determines whether the span should be tagged in error status if an exception
 * captured in the scope.
 * <p>
 * StatusCheckService确定如果范围内捕获异常，是否应将跨度标记为错误状态。状态检查服务
 * <p>
 * 状态检查服务
 *
 * 用来判断那些异常不算异常
 */
@DefaultImplementor
public class StatusCheckService implements BootService {

    @Getter
    private String[] ignoredExceptionNames;

    /**
     * 枚举
     */
    private StatusChecker statusChecker;

    @Override
    public void prepare() throws Throwable {

        // 一条链路如果某个环节出现了异常,默认情况会把异常信息发送给OAP,在SkyWalking UI中看到链路中那个地方出现了异常,方便排查问题
        // Config.StatusCheck.IGNORED_EXCEPTIONS. 不应该当做错误的异常
        // ignoredExceptionNames 需要忽略的异常
        ignoredExceptionNames = Arrays.stream(Config.StatusCheck.IGNORED_EXCEPTIONS.split(","))
                                      .filter(StringUtil::isNotEmpty)
                                      .toArray(String[]::new);

        // 检查异常时的最大递归程度
        // AException
        //  BException
        //   CException
        // 如果IGNORED_EXCEPTIONS配置的是AException,此时抛出的是CException需要递归找一下是否属于AException的子类
        statusChecker = Config.StatusCheck.MAX_RECURSIVE_DEPTH > 0 ? HIERARCHY_MATCH : OFF;
    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }

    /**
     * 去检查
     *
     * @param e
     * @return
     */
    public boolean isError(Throwable e) {
        return statusChecker.checkStatus(e);
    }
}
```



```java
@AllArgsConstructor
public enum StatusChecker {

    /**
     * All exceptions would make the span tagged as the error status.
     * <p>
     * 任何异常都当做错误
     */
    OFF(
        Collections.singletonList(new OffExceptionCheckStrategy()),
        (isError, throwable) -> {
        }
    ),

    /**
     * Hierarchy check the status of the traced exception.
     * 层次结构检查跟踪异常的状态
     *
     * @see HierarchyMatchExceptionCheckStrategy
     * @see AnnotationMatchExceptionCheckStrategy
     */
    HIERARCHY_MATCH(

            Arrays.asList(
                    // 继承策略
                    new HierarchyMatchExceptionCheckStrategy(),
                    // 注解匹配策略
                    new AnnotationMatchExceptionCheckStrategy()
            ),
            (isError, throwable) -> {
                // 是异常？
                if (isError) {
                    ExceptionCheckContext.INSTANCE.registerErrorStatusException(throwable);
                } else {
                    ExceptionCheckContext.INSTANCE.registerIgnoredException(throwable);
                }
                // 放到了context中。
            }
    );

    /**
     * 策略
     */
    private final List<ExceptionCheckStrategy> strategies;

    /**
     * 回调
     */
    private final ExceptionCheckCallback callback;

    public boolean checkStatus(Throwable e) {
        int maxDepth = Config.StatusCheck.MAX_RECURSIVE_DEPTH;
        boolean isError = true;
        while (isError && Objects.nonNull(e) && maxDepth-- > 0) {
            isError = check(e);
            e = e.getCause();
        }
        return isError;
    }

    private boolean check(final Throwable e) {
        // 根据策略。调用context
        boolean isError = ExceptionCheckContext.INSTANCE.isChecked(e)
            ? ExceptionCheckContext.INSTANCE.isError(e)
            : strategies.stream().allMatch(item -> item.isError(e));
        callback.onChecked(isError, e);
        return isError;
    }

    /**
     * The callback function would be triggered after an exception is checked by StatusChecker.
     */
    @FunctionalInterface
    private interface ExceptionCheckCallback {
        void onChecked(Boolean isError, Throwable throwable);
    }

}
```



```java
/**
 * ExceptionCheckContext contains the exceptions that have been checked by the exceptionCheckStrategies.
 */
public enum ExceptionCheckContext {
    /**
     *
     */
    INSTANCE;

    private final Set<Class<? extends Throwable>> ignoredExceptions = ConcurrentHashMap.newKeySet(32);
    private final Set<Class<? extends Throwable>> errorStatusExceptions = ConcurrentHashMap.newKeySet(128);

    public boolean isChecked(Throwable throwable) {
        return ignoredExceptions.contains(throwable.getClass()) || errorStatusExceptions.contains(throwable.getClass());
    }

    public boolean isError(Throwable throwable) {
        Class<? extends Throwable> clazz = throwable.getClass();
        return errorStatusExceptions.contains(clazz) || (!ignoredExceptions.contains(clazz));
    }

    public void registerIgnoredException(Throwable throwable) {
        ignoredExceptions.add(throwable.getClass());
    }

    public void registerErrorStatusException(Throwable throwable) {
        errorStatusExceptions.add(throwable.getClass());
    }

}
```



### 链路追踪



#### 概念

- `Trace`:  表示一整条链路，(跨线程、跨进程的所有 Segment 的集合)
  - 在skywalking中， Trace 不是一个具体的数据模型，而是多个 Segment 串起来表示的逻辑对象

- `segment` : 表示一个JVM进程内的一个线程中的所有操作的集合。`(可以将 一个JVM进程 理解为 一个 微服务)`

- `Span`: 表示一个具体`操作`。

![image-20221211202317710](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221211202317710.png)

#### 链路ID的生成 


![image-20221213211722391](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213211722391.png)



- `DistributedTraceId` 顶级抽象父类。

- `PropagatedTraceId`: 跨线程时用的id生成器

- `NewDistributedTraceId`: 跨进程时用的id生成器
- `GlobalIdGenerator`:  `NewDistributedTraceId`构造函数调用的方法，以此创建id.



```java
@RequiredArgsConstructor
@ToString
@EqualsAndHashCode
public abstract class DistributedTraceId {
    @Getter
    private final String id;
}

public class PropagatedTraceId extends DistributedTraceId {
    public PropagatedTraceId(String id) {
        super(id);
    }
}

public class NewDistributedTraceId extends DistributedTraceId {
    public NewDistributedTraceId() {
        super(GlobalIdGenerator.generate());
    }
}

```

***`GlobalIdGenerator`***

id 生成器 

```java
/**
 * id 生成
 */
public final class GlobalIdGenerator {
    private static final String PROCESS_ID = UUID.randomUUID().toString().replaceAll("-", "");
    // 这里初始化了 IDContext
    private static final ThreadLocal<IDContext> THREAD_ID_SEQUENCE = ThreadLocal.withInitial(
        () -> new IDContext(System.currentTimeMillis(), (short) 0));

    private GlobalIdGenerator() {
    }


    public static String generate() {

        // 1. 应用实例id
        // 2. 线程id
        // 3. 有两部分，1）一个时间戳，以毫秒为单位 2）一个序列，在当前线程中，在 0（包括）和 9999（包括）之间

        return StringUtil.join(
            '.',
            PROCESS_ID,
            String.valueOf(Thread.currentThread().getId()),
            String.valueOf(THREAD_ID_SEQUENCE.get().nextSeq())
        );
    }

    private static class IDContext {

        /**
         * 上次生成 sequence 的时间戳
         */
        private long lastTimestamp;

        /**
         * 线程的序列号
         */
        private short threadSeq;

        // Just for considering time-shift-back only.
        // 时钟回拨
        private long lastShiftTimestamp;
        private int lastShiftValue;

        private IDContext(long lastTimestamp, short threadSeq) {
            this.lastTimestamp = lastTimestamp;
            this.threadSeq = threadSeq;
        }

        /**
         * 生成序号
         * 有两部分，1）一个时间戳，以毫秒为单位 2）一个序列，在当前线程中，在 0（包括）和 9999（包括）之间
         * @return
         */
        private long nextSeq() {
            return timestamp() * 10000 + nextThreadSeq();
        }

        private long timestamp() {
            long currentTimeMillis = System.currentTimeMillis();

            // 发生了时钟回拨
            if (currentTimeMillis < lastTimestamp) {
                // Just for considering time-shift-back by Ops or OS. @hanahmily 's suggestion.
                // 只是为了考虑 Ops 或 OS 的时间倒退。
                if (lastShiftTimestamp != currentTimeMillis) {
                    // 时钟回拨次数+1
                    lastShiftValue++;
                    lastShiftTimestamp = currentTimeMillis;
                }
                return lastShiftValue;
            } else {
                // 正常逻辑
                lastTimestamp = currentTimeMillis;
                return lastTimestamp;
            }
        }

        private short nextThreadSeq() {
            if (threadSeq == 10000) {
                threadSeq = 0;
            }
            return threadSeq++;
        }
    }
}
```





#### `TraceSegment`

![image-20221213213656275](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213213656275.png)

链路追踪的重要概念模型。前面说了，skywalking没有将Trace设计为数据模型。Trace只是概念，由多个segment串联而成的,`relatedGlobalTraceId`就是所属的trace的id,代码中可以看到，在构造函数就会生成一个id,但也可以调用`relatedGlobalTrace()`将该`Segment`关联到其他Trace上。

```java
/**
 * segment
 * <p>
 * Trace的组合部分。 多个segment组成一个trace
 * Trace 不是一个具体的数据模型，而是多个 Segment 串起来表示的逻辑对象
 */
public class TraceSegment {
    /**
     * The id of this trace segment. Every segment has its unique-global-id.
     * 全局唯一的 segmentId
     */
    private String traceSegmentId;

    /**
     * 指针，指向当前segment的parent segment 的指针
     */
    private TraceSegmentRef ref;

    /**
     * <p>
     * span
     */
    private List<AbstractTracingSpan> spans;

    /**
     *当前segment 所在 Trace 的 ID
     */
    private DistributedTraceId relatedGlobalTraceId;

    private boolean ignore = false;

    private boolean isSizeLimited = false;

    private final long createTime;

    /**
     * Create a default/empty trace segment, with current time as start time, and generate a new segment id.
     */
    public TraceSegment() {
        this.traceSegmentId = GlobalIdGenerator.generate();
        this.spans = new LinkedList<>();
        // 在 skywalking 中，Trace 不是一个具体的数据模型，而是多个 Segment 串起来表示的逻辑对象
        // 这里在生成 Segment时，就创建了 traceId
        this.relatedGlobalTraceId = new NewDistributedTraceId();
        this.createTime = System.currentTimeMillis();
    }

    /**
     * Establish the link between this segment and its parents.
     *
     * @param refSegment {@link TraceSegmentRef}
     */
    public void ref(TraceSegmentRef refSegment) {
        if (null == ref) {
            this.ref = refSegment;
        }
    }

    /**
     * Establish the line between this segment and the relative global trace id.
     * 将当前segment 关联到 一个Trace上
     * 就是把持有的traceId给换了。（relatedGlobalTraceId）
     *  但是 跨进程id才行
     */
    public void relatedGlobalTrace(DistributedTraceId distributedTraceId) {
        if (relatedGlobalTraceId instanceof NewDistributedTraceId) {
            this.relatedGlobalTraceId = distributedTraceId;
        }
    }

    /**
     *
     * 加入一个span
     */
    public void archive(AbstractTracingSpan finishedSpan) {
        spans.add(finishedSpan);
    }

    /**
     * Finish this {@link TraceSegment}. <p> return this, for chaining
     * 结束方法
     *关闭 segment时，要调用这个方法。
     *
     * span 是否到达了上限，配置中的默认值 300
     *
     */
    public TraceSegment finish(boolean isSizeLimited) {
        this.isSizeLimited = isSizeLimited;
        return this;
    }

}
```



***`TraceSegmentRef`***

是指向Parent Segment的指针。这是一个对象，里面存储了父`Segment`的基本信息

````java
@Getter
public class TraceSegmentRef {

    // 类型 跨进程、跨线程
    private SegmentRefType type;
    // traceId
    private String traceId;

    // parent 的 traceSegmentId
    private String traceSegmentId;
    private int spanId;

    // Mall -> Order 对于Order 服务来讲，parentService 就是Mail
    private String parentService;

    // parentService  的具体一个实例
    private String parentServiceInstance;

    // 进入parentService 的那个请求
    private String parentEndpoint;

    // 记录的地址信息
    private String addressUsedAtClient;
 }
````



#### `span`

`span`表示一个基本的操作，它的概念也是最多的。

![image-20221213213944937](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213213944937.png)



##### `AsyncSpan`

最顶层的span,定义了基础的 `prepareForAsync() // 准备阶段`   和 `asyncFinish() // 结束阶段`

```java
/**
 * Span could use these APIs to active and extend its lift cycle across thread.
 * <p>
 * This is typical used in async plugin, especially RPC plugins.
 *
 * // 异步span
 * 最顶层的span
 */
public interface AsyncSpan {
    /**
     * The span finish at current tracing context, but the current span is still alive, until {@link #asyncFinish}
     * called.
     * <p>
     * This method must be called
     * <p>
     * 1. In original thread(tracing context). 2. Current span is active span.
     * <p>
     * During alive, tags, logs and attributes of the span could be changed, in any thread.
     * <p>
     * The execution times of {@link #prepareForAsync} and {@link #asyncFinish()} must match.
     *
     * @return the current span
     *
     * // 准备阶段
     */
    AbstractSpan prepareForAsync();

    /**
     * Notify the span, it could be finished.
     * <p>
     * The execution times of {@link #prepareForAsync} and {@link #asyncFinish()} must match.
     *
     * @return the current span
     *
     * // 结束阶段
     */
    AbstractSpan asyncFinish();
}
```



##### `AbstractSpan`

继承 `AsyncSpan`,并定义了一些通用方法。

![image-20221213214845385](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213214845385.png)



**layer**

```java
/**
 * 指定当前Span 表示的操作所在的插件属于哪一种 skywalking 划分的类型
 * - 在skywalking中，将各种插件划分为5类，=> DB(1), RPC_FRAMEWORK(2), HTTP(3), MQ(4), CACHE(5);。这个就可以理解为层
 *
 * @param layer 枚举
 * @return
 */
AbstractSpan setLayer(SpanLayer layer);
```





```java

/**
 * The <code>AbstractSpan</code> represents the span's skeleton, which contains all open methods.
 * <p>
 * AbstractSpan表示跨度的骨架，定义了公用的方法
 */
public interface AbstractSpan extends AsyncSpan {
    /**
     * - ComponentsDefine 将插件定义为一个对象
     * - 指定当前 Span 表示的操作发生在那个插件上
     * Set the component id, which defines in {@link ComponentsDefine}
     *
     * @return the span for chaining.
     */
    AbstractSpan setComponent(Component component);

    /**
     * 指定当前Span 表示的操作所在的插件属于哪一种 skywalking 划分的类型
     * - 在skywalking中，将各种插件划分为5类，=> DB(1), RPC_FRAMEWORK(2), HTTP(3), MQ(4), CACHE(5);。这个就可以理解为层
     *
     * @param layer 枚举
     * @return
     */
    AbstractSpan setLayer(SpanLayer layer);

    /**
     * Set a key:value tag on the Span.
     *
     * @return this Span instance, for chaining
     * @deprecated use {@link #tag(AbstractTag, String)} in companion with {@link Tags#ofKey(String)} instead
     */
    @Deprecated
    AbstractSpan tag(String key, String value);

    /**
     * 打标签
     * AbstractTag 增加了一个id
     */
    AbstractSpan tag(AbstractTag<?> tag, String value);

    /**
     * 记录当前 挂钟时间 时间戳的异常事件。
     * - 挂钟时间： 本机当前时间
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    AbstractSpan log(Throwable t);

    /**
     * 抽象方法，在错误发生时执行
     *
     * @return
     */
    AbstractSpan errorOccurred();

    /**
     * @return true if the actual span is an entry span.
     */
    boolean isEntry();

    /**
     * @return true if the actual span is an exit span.
     */
    boolean isExit();

    /**
     * 在指定时间戳记录事件
     * Record an event at a specific timestamp.
     *
     * @param timestamp The explicit timestamp for the log record.
     * @param event     the events
     * @return the Span, for chaining
     */
    AbstractSpan log(long timestamp, Map<String, ?> event);

    /**
     * Sets the string name for the logical operation this span represents.
     * 如果当前Span的操作是
     * 一个 HTTP 请求，operationName 就是 请求的URL;
     * 一条 SQL 语句，operationName 就是 SQL 的类型
     * 一个 Redis 操作， operationName 就是 Redis 命令
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan setOperationName(String operationName);

    /**
     * Start a span.
     *动作开始的时候，调用这个方法
     *
     * @return this Span instance, for chaining
     */
    AbstractSpan start();

    /**
     * Get the id of span
     *
     * @return id value.
     */
    int getSpanId();

    String getOperationName();

    /**
     * 跨 Segment 时，通过 ref  将Segment 关联起来
     *
     * Reference other trace segment.
     *
     * @param ref segment ref
     */
    void ref(TraceSegmentRef ref);

    AbstractSpan start(long startTime);

    /**
     *  什么叫 peer， 就是对端地址
     *  一个请求可能跨多个进程，操作多种中间件，那么每一个RPC, 对面的服务的地址就是 remotePeer
     *  每一次中间件的操作，中间件的地址就是 remotePeer
     * @param remotePeer
     * @return
     */
    AbstractSpan setPeer(String remotePeer);

    /**
     * @return true if the span's owner(tracing context main thread) is been profiled.
     */
    boolean isProfiling();

    /**
     * 设置 span 发生到OAP后，要不要进行性能分析
     * Should skip analysis in the backend.
     */
    void skipAnalysis();
}

```





##### `AbstractTracingSpan`

![image-20221213214936597](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213214936597.png)



```java
/**
 * The <code>AbstractTracingSpan</code> represents a group of {@link AbstractSpan} implementations, which belongs a real
 * distributed trace.
 * <p>
 * AbstractTracingSpan代表了一组AbstractSpan的实现，属于真正的分布式trace。
 */
public abstract class AbstractTracingSpan implements AbstractSpan {
    /**
     * Span id starts from 0.
     */
    protected int spanId;
    /**
     * Parent span id starts from 0. -1 means no parent span.
     * 从0 开始， -1 表示没有父级
     */
    protected int parentSpanId;

    /**
     * 封装的tag
     */
    protected List<TagValuePair> tags;
    protected String operationName;
    protected SpanLayer layer;


    /**
     * The span has been tagged in async mode, required async stop to finish.
     * 表示当前异步操作，是否已经开始
     */
    protected volatile boolean isInAsyncMode = false;
    /**
     * The flag represents whether the span has been async stopped
     * 表示当前异步操作，是否已经结束
     */
    private volatile boolean isAsyncStopped = false;


    /**
     * The context to which the span belongs
     * span所属的上下文
     * 用来管理一条链路上的 segment 和  span
     */
    protected final TracingContext owner;

    /**
     * The start time of this Span.
     */
    protected long startTime;
    /**
     * The end time of this Span.
     */
    protected long endTime;
    /**
     * Error has occurred in the scope of span.
     */
    protected boolean errorOccurred = false;

    protected int componentId = 0;

    /**
     * Log is a concept from OpenTracing spec. https://github.com/opentracing/specification/blob/master/specification.md#log-structured-data
     */
    protected List<LogDataEntity> logs;

    /**
     * The refs of parent trace segments, except the primary one. For most RPC call, {@link #refs} contains only one
     * element, but if this segment is a start span of batch process, the segment faces multi parents, at this moment,
     * we use this {@link #refs} to link them.
     * <p>
     * 用于当前 Span 指定自己的所在的 Segment 的前一个Segment, 除非这个 Span 所在的Segment 是整条链路上的第一个Segment
     * - 为什么是list?
     * 正常情况下，list中只有一个元素。如果 segment 是批处理的话，就会有多个
     */
    protected List<TraceSegmentRef> refs;

    /**
     * Tracing Mode. If true means represents all spans generated in this context should skip analysis.
     * 跟踪模式。如果为真，则表示在此上下文中生成的所有跨度应跳过分析。
     */
    protected boolean skipAnalysis;

    protected AbstractTracingSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        this.operationName = operationName;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.owner = owner;
    }

    /**
     * Set a key:value tag on the Span.
     * <p>
     * {@inheritDoc}
     *
     * @return this Span instance, for chaining
     */
    @Override
    public AbstractTracingSpan tag(String key, String value) {
        return tag(Tags.ofKey(key), value);
    }

    @Override
    public AbstractTracingSpan tag(AbstractTag<?> tag, String value) {
        if (tags == null) {
            tags = new ArrayList<>(8);
        }

        if (tag.isCanOverwrite()) {
            for (TagValuePair pair : tags) {
                if (pair.sameWith(tag)) {
                    pair.setValue(value);
                    return this;
                }
            }
        }

        tags.add(new TagValuePair(tag, value));
        return this;
    }

    /**
     * Finish the active Span. When it is finished, it will be archived by the given {@link TraceSegment}, which owners
     * it.
     *
     * span 结束时，要调用一下 finish
     *
     * @param owner of the Span.
     */
    public boolean finish(TraceSegment owner) {
        this.endTime = System.currentTimeMillis();
        // 归档
        owner.archive(this);
        return true;
    }

    @Override
    public AbstractTracingSpan start() {
        this.startTime = System.currentTimeMillis();
        return this;
    }

    /**
     * Record an exception event of the current walltime timestamp.
     *
     * @param t any subclass of {@link Throwable}, which occurs in this span.
     * @return the Span, for chaining
     */
    @Override
    public AbstractTracingSpan log(Throwable t) {
        if (logs == null) {
            logs = new LinkedList<>();
        }
        if (!errorOccurred && ServiceManager.INSTANCE.findService(StatusCheckService.class).isError(t)) {
            errorOccurred();
        }
        logs.add(new LogDataEntity.Builder().add(new KeyValuePair("event", "error"))
                                            .add(new KeyValuePair("error.kind", t.getClass().getName()))
                                            .add(new KeyValuePair("message", t.getMessage()))
                                            .add(new KeyValuePair(
                                                "stack",
                                                ThrowableTransformer.INSTANCE.convert2String(t, 4000)
                                            ))
                                            .build(System.currentTimeMillis()));
        return this;
    }

    /**
     * Record a common log with multi fields, for supporting opentracing-java
     *
     * @return the Span, for chaining
     */
    @Override
    public AbstractTracingSpan log(long timestampMicroseconds, Map<String, ?> fields) {
        if (logs == null) {
            logs = new LinkedList<>();
        }
        LogDataEntity.Builder builder = new LogDataEntity.Builder();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            builder.add(new KeyValuePair(entry.getKey(), entry.getValue().toString()));
        }
        logs.add(builder.build(timestampMicroseconds));
        return this;
    }

    /**
     * In the scope of this span tracing context, error occurred, in auto-instrumentation mechanism, almost means throw
     * an exception.
     *
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan errorOccurred() {
        this.errorOccurred = true;
        return this;
    }

    /**
     * Set the operation name, just because these is not compress dictionary value for this name. Use the entire string
     * temporarily, the agent will compress this name in async mode.
     *
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setOperationName(String operationName) {
        this.operationName = operationName;
        return this;
    }

    @Override
    public int getSpanId() {
        return spanId;
    }

    @Override
    public String getOperationName() {
        return operationName;
    }

    @Override
    public AbstractTracingSpan setLayer(SpanLayer layer) {
        this.layer = layer;
        return this;
    }

    /**
     * Set the component of this span, with internal supported. Highly recommend to use this way.
     *
     * @return span instance, for chaining.
     */
    @Override
    public AbstractTracingSpan setComponent(Component component) {
        this.componentId = component.getId();
        return this;
    }

    @Override
    public AbstractSpan start(long startTime) {
        this.startTime = startTime;
        return this;
    }

    public SpanObject.Builder transform() {
        SpanObject.Builder spanBuilder = SpanObject.newBuilder();

        spanBuilder.setSpanId(this.spanId);
        spanBuilder.setParentSpanId(parentSpanId);
        spanBuilder.setStartTime(startTime);
        spanBuilder.setEndTime(endTime);
        spanBuilder.setOperationName(operationName);
        spanBuilder.setSkipAnalysis(skipAnalysis);
        if (isEntry()) {
            spanBuilder.setSpanType(SpanType.Entry);
        } else if (isExit()) {
            spanBuilder.setSpanType(SpanType.Exit);
        } else {
            spanBuilder.setSpanType(SpanType.Local);
        }
        if (this.layer != null) {
            spanBuilder.setSpanLayerValue(this.layer.getCode());
        }
        if (componentId != DictionaryUtil.nullValue()) {
            spanBuilder.setComponentId(componentId);
        }
        spanBuilder.setIsError(errorOccurred);
        if (this.tags != null) {
            for (TagValuePair tag : this.tags) {
                spanBuilder.addTags(tag.transform());
            }
        }
        if (this.logs != null) {
            for (LogDataEntity log : this.logs) {
                spanBuilder.addLogs(log.transform());
            }
        }
        if (this.refs != null) {
            for (TraceSegmentRef ref : this.refs) {
                spanBuilder.addRefs(ref.transform());
            }
        }

        return spanBuilder;
    }

    @Override
    public void ref(TraceSegmentRef ref) {
        if (refs == null) {
            refs = new LinkedList<>();
        }
        /*
         * Provide the OOM protection if the entry span hosts too many references.
         */
        if (refs.size() == Config.Agent.TRACE_SEGMENT_REF_LIMIT_PER_SPAN) {
            return;
        }
        if (!refs.contains(ref)) {
            refs.add(ref);
        }
    }

    /**
     * 异步开始前，要先调用这个方法
     * @return
     */
    @Override
    public AbstractSpan prepareForAsync() {
        if (isInAsyncMode) {
            throw new RuntimeException("Prepare for async repeatedly. Span is already in async mode.");
        }
        // 等待异步完成
        ContextManager.awaitFinishAsync(this);
        isInAsyncMode = true;
        return this;
    }

    /**
     * 异步任务结束时，要调用这个方法
     * @return
     */
    @Override
    public AbstractSpan asyncFinish() {
        if (!isInAsyncMode) {
            throw new RuntimeException("Span is not in async mode, please use '#prepareForAsync' to active.");
        }
        if (isAsyncStopped) {
            throw new RuntimeException("Can not do async finish for the span repeatedly.");
        }
        this.endTime = System.currentTimeMillis();
        owner.asyncStop(this);
        isAsyncStopped = true;
        return this;
    }

    @Override
    public boolean isProfiling() {
        return this.owner.profileStatus().isProfiling();
    }

    @Override
    public void skipAnalysis() {
        this.skipAnalysis = true;
    }
}
```



##### `StackBasedTracingSpan`

抽象类，基于栈的Span。实际上没有栈结构。而是通过`stackDepth// 当前栈深度` 来模拟。



```java
/**
 * The <code>StackBasedTracingSpan</code> represents a span with an inside stack construction.
 * <p>
 * This kind of span can start and finish multi times in a stack-like invoke line.
 *
 * 基于栈的span
 *
 */
public abstract class StackBasedTracingSpan extends AbstractTracingSpan {

    /**
     * 当前栈深
     */
    protected int stackDepth;
    protected String peer;

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.stackDepth = 0;
        this.peer = null;
    }

    protected StackBasedTracingSpan(int spanId, int parentSpanId, String operationName, String peer,
                                    TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.peer = peer;
    }

    @Override
    public SpanObject.Builder transform() {
        SpanObject.Builder spanBuilder = super.transform();
        if (StringUtil.isNotEmpty(peer)) {
            spanBuilder.setPeer(peer);
        }
        return spanBuilder;
    }

    @Override
    public boolean finish(TraceSegment owner) {
        if (--stackDepth == 0) {
            return super.finish(owner);
        } else {
            return false;
        }
    }

    @Override
    public AbstractSpan setPeer(final String remotePeer) {
        this.peer = remotePeer;
        return this;
    }
}
```





##### `EntrySpan 和 ExitSpan `

这两个才是真正干活的Span,上面的都是抽象类和接口。



**调用逻辑**

一个简单的接口请求，会经过很多框架，比如 tomcat、spring mvc ，skywalking 也针对这些框架开发了对应的插件。那么第一个运行到的插件，就会创建`EntrySpan`，而后面的插件就会复用这个`EntrySpan`，只是会覆盖一些数据。而`ExitSpan`就不是复用了，在一个`Segment`中可能存在多个。

![image-20221213220920150](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20221213220920150.png)

```java
/**
 * The <code>EntrySpan</code> represents a service provider point, such as Tomcat server entrance.
 * <p>
 * It is a start point of {@link TraceSegment}, even in a complex application, there maybe have multi-layer entry point,
 * the <code>EntrySpan</code> only represents the first one.
 * <p>
 * But with the last <code>EntrySpan</code>'s tags and logs, which have more details about a service provider.
 * <p>
 * Such as: Tomcat Embed - Dubbox The <code>EntrySpan</code> represents the Dubbox span.
 */
public class EntrySpan extends StackBasedTracingSpan {

    // 当前最大栈深
    private int currentMaxDepth;

    public EntrySpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
        this.currentMaxDepth = 0;
    }

    /**
     * Set the {@link #startTime}, when the first start, which means the first service provided.
     *
     * EntrySpan 只会由第一个插件创建， 但是后面的插件复用 EntrySpan 时 都要来调用一次 start() 方法
     * 因为每一个插件都以为自己是第一个创建这个 EntrySpan 的
     */
    @Override
    public EntrySpan start() {
        if ((currentMaxDepth = ++stackDepth) == 1) {
            super.start();
        }
        clearWhenRestart();
        return this;
    }

    @Override
    public EntrySpan tag(String key, String value) {
        if (stackDepth == currentMaxDepth || isInAsyncMode) {
            super.tag(key, value);
        }
        return this;
    }

    @Override
    public AbstractTracingSpan setLayer(SpanLayer layer) {
        if (stackDepth == currentMaxDepth || isInAsyncMode) {
            return super.setLayer(layer);
        } else {
            return this;
        }
    }

    @Override
    public AbstractTracingSpan setComponent(Component component) {
        if (stackDepth == currentMaxDepth || isInAsyncMode) {
            return super.setComponent(component);
        } else {
            return this;
        }
    }

    @Override
    public AbstractTracingSpan setOperationName(String operationName) {
        if (stackDepth == currentMaxDepth || isInAsyncMode) {
            return super.setOperationName(operationName);
        } else {
            return this;
        }
    }

    @Override
    public EntrySpan log(Throwable t) {
        super.log(t);
        return this;
    }

    @Override
    public boolean isEntry() {
        return true;
    }

    @Override
    public boolean isExit() {
        return false;
    }

    private void clearWhenRestart() {
        this.componentId = DictionaryUtil.nullValue();
        this.layer = null;
        this.logs = null;
        this.tags = null;
    }
}
```



```java
/**
 * The <code>ExitSpan</code> represents a service consumer point, such as Feign, Okhttp client for an Http service.
 * <p>
 * It is an exit point or a leaf span(our old name) of trace tree. In a single rpc call, because of a combination of
 * discovery libs, there maybe contain multi-layer exit point:
 * <p>
 * The <code>ExitSpan</code> only presents the first one.
 * <p>
 * Such as: Dubbox - Apache Httpcomponent - ...(Remote) The <code>ExitSpan</code> represents the Dubbox span, and ignore
 * the httpcomponent span's info.
 *
 * 退出span  代表消费侧
 * 区别就是
 *  EntrySpan 代表的是更靠近服务这一侧的信息
 *  ExitSpan 代表的是更靠近消费这一侧的信息
 *
 * -
 * ExitSpan代表一个服务消费点，比如Feign，Okhttp客户端为一个Http服务。
 * 它是跟踪树的出口点或叶子跨度（我们的旧名称）。在单个 rpc 调用中，由于发现库的组合，可能包含多层出口点：
 * ExitSpan仅显示第一个。
 * 如：Dubbox - Apache Httpcomponent - ...(Remote) ExitSpan代表Dubbox span，忽略httpcomponent span的信息。退出跨度
 *
 */
public class ExitSpan extends StackBasedTracingSpan implements ExitTypeSpan {

    public ExitSpan(int spanId, int parentSpanId, String operationName, String peer, TracingContext owner) {
        super(spanId, parentSpanId, operationName, peer, owner);
    }

    public ExitSpan(int spanId, int parentSpanId, String operationName, TracingContext owner) {
        super(spanId, parentSpanId, operationName, owner);
    }

    /**
     * Set the {@link #startTime}, when the first start, which means the first service provided.
     */
    @Override
    public ExitSpan start() {
        // 当前栈深时 是1 的情况下，才允许
        // exitSpan 刚创建时， 栈深才会是1
        if (++stackDepth == 1) {
            super.start();
        }
        return this;
    }

    @Override
    public ExitSpan tag(String key, String value) {
        if (stackDepth == 1 || isInAsyncMode) {
            super.tag(key, value);
        }
        return this;
    }

    @Override
    public AbstractTracingSpan tag(AbstractTag<?> tag, String value) {
        if (stackDepth == 1 || tag.isCanOverwrite() || isInAsyncMode) {
            super.tag(tag, value);
        }
        return this;
    }

    @Override
    public AbstractTracingSpan setLayer(SpanLayer layer) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setLayer(layer);
        } else {
            return this;
        }
    }

    @Override
    public AbstractTracingSpan setComponent(Component component) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setComponent(component);
        } else {
            return this;
        }
    }

    @Override
    public ExitSpan log(Throwable t) {
        super.log(t);
        return this;
    }

    @Override
    public AbstractTracingSpan setOperationName(String operationName) {
        if (stackDepth == 1 || isInAsyncMode) {
            return super.setOperationName(operationName);
        } else {
            return this;
        }
    }

    @Override
    public String getPeer() {
        return peer;
    }

    @Override
    public ExitSpan inject(final ContextCarrier carrier) {
        this.owner.inject(this, carrier);
        return this;
    }

    @Override
    public boolean isEntry() {
        return false;
    }

    @Override
    public boolean isExit() {
        return true;
    }
}
```



#### 链路追踪上下文

- `AbstractTracerContext`: 接口，定义了基础方法
- `TracingContext`: 核心的链路追踪逻辑控制器，管理当前 Segment 和`前后`Segment 



#####  `AbstractTracerContext`



###### 跨进程传输数据

```java
// **** 在跨进程的情况下，传递数据。 inject 打包数据。extract 解压数据 ***


/**
 * Prepare for the cross-process propagation. How to initialize the carrier, depends on the implementation.
 *
 * 注入，将一些数据放到 carrier 中
 * @param carrier to carry the context for crossing process.
 */
void inject(ContextCarrier carrier);

/**
 * Build the reference between this segment and a cross-process segment. How to build, depends on the
 * implementation.
 *
 * 提取 从 carrier 中提取一些数据
 *
 * @param carrier carried the context from a cross-process segment.
 */
void extract(ContextCarrier carrier);
```

###### 跨线程传输数据

```java
// **** 在跨线程的情况下，传递数据。 capture 打包数据。continued 解压数据 ***


/**
 * 生成快照
 * Capture a snapshot for cross-thread propagation. It's a similar concept with ActiveSpan.Continuation in
 * OpenTracing-java How to build, depends on the implementation.
 *
 * @return the {@link ContextSnapshot} , which includes the reference context.
 */
ContextSnapshot capture();

/**
 * 延续这个快照， 继续
 * Build the reference between this segment and a cross-thread segment. How to build, depends on the
 * implementation.
 *
 * @param snapshot from {@link #capture()} in the parent thread.
 */
void continued(ContextSnapshot snapshot);
```



##### `TracingContext`

>  *  一个 TracingContext 对应一个 Segment (管理)
>  *  管理当前 Segment 和自己前后的 Segment 的引用 TraceSegmentRef
>  *  当前Segment 内的所有 span



###### activeSpanStack

`activeSpanStack` 是一个重要属性，作者使用 `LinkedList` 模仿栈，用于储存 `span`。每一个创建的 Span 都会放入 `activeSpanStack`（先进后出）。以此理解，栈顶的 Span 就是 `currentSpan`（`activeSpan`） 

```java
/**
 activeSpanStack栈顶的span就是activeSpan
 * @return the active span of current context, the top element of {@link #activeSpanStack}
 */
@Override
public AbstractSpan activeSpan() {
    AbstractSpan span = peek();
    if (span == null) {
        throw new IllegalStateException("No active span.");
    }
    return span;
}

/**
 * @return the top element of 'ActiveSpanStack' only.
 */
private AbstractSpan peek() {
    if (activeSpanStack.isEmpty()) {
        return null;
    }
    return activeSpanStack.getLast();
}
```

###### `createEntrySpan()`

1. 限制检查: 如果需要限制，就创建 `NoopSpan`
2. 设置父级: 从`activeSpanStack`中取出栈顶的`Span`(`activeSpan`)，取其id作为 `parentSpanId`。如果不存在就设置为-1。
3. 数据复用：`EntrySpan`和`ExitSpan`，创建时会判断`parentSpan`也是同类型的`Span`则复用，否则才会初始化并入栈。`LocalSpan`不会做检查，直接初始化并入栈



```java
   /**
     * Create an entry span
     *
     * @param operationName most likely a service name
     * @return span instance. Ref to {@link EntrySpan}
     */
    @Override
    public AbstractSpan createEntrySpan(final String operationName) {
        // 限制机制
        // spanLimit配置项
        if (isLimitMechanismWorking()) {
            NoopSpan span = new NoopSpan();
            return push(span);
        }

        AbstractSpan entrySpan;
        TracingContext owner = this;
        // 弹出一个span作为父级。这里的peek 不会删除元素
        final AbstractSpan parentSpan = peek();
        // 拿到父级span的ID，如果不存在父级，赋值为-1
        final int parentSpanId = parentSpan == null ? -1 : parentSpan.getSpanId();
        // 不为null 复用span，覆写信息
        if (parentSpan != null && parentSpan.isEntry()) {
            /*
             * Only add the profiling recheck on creating entry span,
             * as the operation name could be overrided.
             */
            profilingRecheck(parentSpan, operationName);
            parentSpan.setOperationName(operationName);
            entrySpan = parentSpan;
            return entrySpan.start();
        } else {
            // 巧了，没有父级，创建 EntrySpan。并入栈
            entrySpan = new EntrySpan(
                spanIdGenerator++, parentSpanId,
                operationName, owner
            );
            entrySpan.start();
            return push(entrySpan);
        }
    }
```

###### `stopSpan()`



1. 传入的Span必须是activeSpanStack栈顶的Span，否则抛出异常
2. 栈顶的Span出栈，如果栈顶的Span是AbstractTracingSpan，调用Span自身的finish方法
3. 如果栈已经空了且当前TracingContext还在运行状态
   1. 关闭当前TraceSegment
   2. 将当前TraceSegment交给TracingContextListener去处理，TracingContextListener会将TraceSegment发送到OAP
   3. 修改当前TracingContext运行状态为false

> https://blog.csdn.net/qq_40378034/article/details/125040223?spm=1001.2014.3001.5502

```java
/**
 *停止， 只能停止栈顶的span。
 * >按照子父级的概念 要先把子级关闭，才能去关闭父级
 * Stop the given span, if and only if this one is the top element of {@link #activeSpanStack}. Because the tracing
 * core must make sure the span must match in a stack module, like any program did.
 *
 * @param span to finish
 */
@Override
public boolean stopSpan(AbstractSpan span) {
    AbstractSpan lastSpan = peek();
    if (lastSpan == span) {
        if (lastSpan instanceof AbstractTracingSpan) {
            AbstractTracingSpan toFinishSpan = (AbstractTracingSpan) lastSpan;
            if (toFinishSpan.finish(segment)) {
                pop();
            }
        } else {
            pop();
        }
    } else {
        throw new IllegalStateException("Stopping the unexpected span = " + span);
    }

    finish();

    return activeSpanStack.isEmpty();
}
```



#### 上下文适配器 `ContextManager`



```java

/**
 * {@link ContextManager} controls the whole context of {@link TraceSegment}. Any {@link TraceSegment} relates to
 * single-thread, so this context use {@link ThreadLocal} to maintain the context, and make sure, since a {@link
 * TraceSegment} starts, all ChildOf spans are in the same context. <p> What is 'ChildOf'?
 * https://github.com/opentracing/specification/blob/master/specification.md#references-between-spans
 *
 * <p> Also, {@link ContextManager} delegates to all {@link AbstractTracerContext}'s major methods.
 * <p>
 * ContextManager代理了AbstractTracerContext主要的方法
 * TraceSegment及其所包含的Span都在同一个线程内，ContextManager使用ThreadLocal来管理TraceSegment的上下文（也就是AbstractTracerContext）
 */
public class ContextManager implements BootService {
    private static final String EMPTY_TRACE_CONTEXT_ID = "N/A";
    private static final ILog LOGGER = LogManager.getLogger(ContextManager.class);
    private static ThreadLocal<AbstractTracerContext> CONTEXT = new ThreadLocal<AbstractTracerContext>();
    private static ThreadLocal<RuntimeContext> RUNTIME_CONTEXT = new ThreadLocal<RuntimeContext>();
    private static ContextManagerExtendService EXTEND_SERVICE;

    private static AbstractTracerContext getOrCreate(String operationName, boolean forceSampling) {
        // 从 threadLocal 中获取 AbstractTracerContext, 存在就返回，不存在就创建。
        AbstractTracerContext context = CONTEXT.get();
        if (context == null) {
            // operationName为空创建IgnoredTracerContext
            if (StringUtil.isEmpty(operationName)) {
                if (LOGGER.isDebugEnable()) {
                    LOGGER.debug("No operation name, ignore this trace.");
                }
                context = new IgnoredTracerContext();
            } else {
                // 初始化 ContextManagerExtendService
                // 调用ContextManagerExtendService的createTraceContext方法创建AbstractTracerContext,并设置到ThreadLocal中
                if (EXTEND_SERVICE == null) {
                    EXTEND_SERVICE = ServiceManager.INSTANCE.findService(ContextManagerExtendService.class);
                }
                context = EXTEND_SERVICE.createTraceContext(operationName, forceSampling);

            }
            CONTEXT.set(context);
        }
        return context;
    }

    private static AbstractTracerContext get() {
        return CONTEXT.get();
    }

    /**
     * @return the first global trace id when tracing. Otherwise, "N/A".
     */
    public static String getGlobalTraceId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getReadablePrimaryTraceId() : EMPTY_TRACE_CONTEXT_ID;
    }

    /**
     * @return the current segment id when tracing. Otherwise, "N/A".
     */
    public static String getSegmentId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getSegmentId() : EMPTY_TRACE_CONTEXT_ID;
    }

    /**
     * @return the current span id when tracing. Otherwise, the value is -1.
     */
    public static int getSpanId() {
        AbstractTracerContext context = CONTEXT.get();
        return Objects.nonNull(context) ? context.getSpanId() : -1;
    }

    public static AbstractSpan createEntrySpan(String operationName, ContextCarrier carrier) {
        AbstractSpan span;
        AbstractTracerContext context;
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        if (carrier != null && carrier.isValid()) {
            SamplingService samplingService = ServiceManager.INSTANCE.findService(SamplingService.class);
            samplingService.forceSampled();
            // 一定要强制采样,因为链路中的前置TraceSegment已经存在,否则链路就可能会断开
            context = getOrCreate(operationName, true);
            span = context.createEntrySpan(operationName);
            context.extract(carrier);
        } else {
            // 不需要强制采样,根据采样率来决定当前链路是否要采样
            context = getOrCreate(operationName, false);
            span = context.createEntrySpan(operationName);
        }
        return span;
    }

    public static AbstractSpan createLocalSpan(String operationName) {
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        AbstractTracerContext context = getOrCreate(operationName, false);
        return context.createLocalSpan(operationName);
    }

    public static AbstractSpan createExitSpan(String operationName, ContextCarrier carrier, String remotePeer) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        AbstractTracerContext context = getOrCreate(operationName, false);
        AbstractSpan span = context.createExitSpan(operationName, remotePeer);
        context.inject(carrier);
        return span;
    }

    public static AbstractSpan createExitSpan(String operationName, String remotePeer) {
        operationName = StringUtil.cut(operationName, OPERATION_NAME_THRESHOLD);
        AbstractTracerContext context = getOrCreate(operationName, false);
        return context.createExitSpan(operationName, remotePeer);
    }

    public static void inject(ContextCarrier carrier) {
        get().inject(carrier);
    }

    public static void extract(ContextCarrier carrier) {
        if (carrier == null) {
            throw new IllegalArgumentException("ContextCarrier can't be null.");
        }
        if (carrier.isValid()) {
            get().extract(carrier);
        }
    }

    public static ContextSnapshot capture() {
        return get().capture();
    }

    public static void continued(ContextSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("ContextSnapshot can't be null.");
        }
        if (!snapshot.isFromCurrent()) {
            get().continued(snapshot);
        }
    }

    public static AbstractTracerContext awaitFinishAsync(AbstractSpan span) {
        final AbstractTracerContext context = get();
        AbstractSpan activeSpan = context.activeSpan();
        if (span != activeSpan) {
            throw new RuntimeException("Span is not the active in current context.");
        }
        return context.awaitFinishAsync();
    }

    /**
     * If not sure has the active span, use this method, will be cause NPE when has no active span, use
     * ContextManager::isActive method to determine whether there has the active span.
     */
    public static AbstractSpan activeSpan() {
        return get().activeSpan();
    }

    /**
     * Recommend use ContextManager::stopSpan(AbstractSpan span), because in that way, the TracingContext core could
     * verify this span is the active one, in order to avoid stop unexpected span. If the current span is hard to get or
     * only could get by low-performance way, this stop way is still acceptable.
     */
    public static void stopSpan() {
        final AbstractTracerContext context = get();
        stopSpan(context.activeSpan(), context);
    }

    public static void stopSpan(AbstractSpan span) {
        stopSpan(span, get());
    }

    private static void stopSpan(AbstractSpan span, final AbstractTracerContext context) {
        if (context.stopSpan(span)) {
            CONTEXT.remove();
            RUNTIME_CONTEXT.remove();
        }
    }

    @Override
    public void prepare() {

    }

    @Override
    public void boot() {
    }

    @Override
    public void onComplete() {

    }

    @Override
    public void shutdown() {

    }

    public static boolean isActive() {
        return get() != null;
    }

    public static RuntimeContext getRuntimeContext() {
        RuntimeContext runtimeContext = RUNTIME_CONTEXT.get();
        if (runtimeContext == null) {
            runtimeContext = new RuntimeContext(RUNTIME_CONTEXT);
            RUNTIME_CONTEXT.set(runtimeContext);
        }

        return runtimeContext;
    }

    public static CorrelationContext getCorrelationContext() {
        final AbstractTracerContext tracerContext = get();
        if (tracerContext == null) {
            return null;
        }

        return tracerContext.getCorrelationContext();
    }

}

```



#### ` DataCarrier`

> Agent采集到的链路数据会先放到DataCarrier中，由消费者线程读取DataCarrier中的数据上报到OAP

![image-20230102202626977](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102202626977.png)





***相关数据的结构图示***

![image-20230102215335270](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102215335270.png)



##### `基础Buffer`

 底层是一个数组![image-20230102212012381](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102212012381.png)

###### `Buffer`

```java
/**
 * Self implementation ring queue.
 * 自行实现环形队列。
 * // jdk 知识。 jdk 9 之后
 * AtomicIntegerArray 中 VarHandle 替代以往的直接使用 Unsafe, 目的是为了更安全的去操作内存，提升性能
 * 屏蔽了 Unsafe 的危险性
 */
public class Buffer<T> implements QueueBuffer<T> {
    // 数据的数组
    private final Object[] buffer;
    // 策略
    private BufferStrategy strategy;
    // 数组 buffer 的索引
    private AtomicRangeInteger index;

    Buffer(int bufferSize, BufferStrategy strategy) {
        buffer = new Object[bufferSize];
        this.strategy = strategy;
        index = new AtomicRangeInteger(0, bufferSize);
    }

    @Override
    public void setStrategy(BufferStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * 环状队列。
     * getAndIncrement(),会为data分配下标。如果数组已经满了，会从0开始。
     * 在arr[index] 的 value 不为空的情况下，根据策略来决定是否覆盖。
     *
     * @param data to add.
     * @return
     */
    @Override
    public boolean save(T data) {
        int i = index.getAndIncrement();
        if (buffer[i] != null) {
            // 策略
            switch (strategy) {
                case IF_POSSIBLE:
                    return false;
                default:
            }
        }
        buffer[i] = data;
        return true;
    }

    @Override
    public int getBufferSize() {
        return buffer.length;
    }

    @Override
    public void obtain(List<T> consumeList) {
        this.obtain(consumeList, 0, buffer.length);
    }

    void obtain(List<T> consumeList, int start, int end) {
        for (int i = start; i < end; i++) {
            if (buffer[i] != null) {
                consumeList.add((T) buffer[i]);
                buffer[i] = null;
            }
        }
    }

}
```

```java
public enum BufferStrategy {
    /**
     * 阻塞，等待队列有空位置
     */
    BLOCKING,
    /**
     * 能放就放，不能放就算了
     */
    IF_POSSIBLE
}
```

###### `ArrayBlockingQueueBuffer`

```java
/**
 * The buffer implementation based on JDK ArrayBlockingQueue.
 * <p>
 * This implementation has better performance in server side. We are still trying to research whether this is suitable
 * for agent side, which is more sensitive about blocks.
 *
 * 阻塞队列实现的 Buffer
 * 作者说 在 OAP 中 使用 ArrayBlockingQueue 拥有更高的性能。就想在agent 端试试
 *
 */
public class ArrayBlockingQueueBuffer<T> implements QueueBuffer<T> {
    private BufferStrategy strategy;
    private ArrayBlockingQueue<T> queue;
    private int bufferSize;

    ArrayBlockingQueueBuffer(int bufferSize, BufferStrategy strategy) {
        this.strategy = strategy;
        this.queue = new ArrayBlockingQueue<T>(bufferSize);
        this.bufferSize = bufferSize;
    }

    @Override
    public boolean save(T data) {
        //only BufferStrategy.BLOCKING
        try {
            queue.put(data);
        } catch (InterruptedException e) {
            // Ignore the error
            return false;
        }
        return true;
    }

    @Override
    public void setStrategy(BufferStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public void obtain(List<T> consumeList) {
        queue.drainTo(consumeList);
    }

    @Override
    public int getBufferSize() {
        return bufferSize;
    }
}
```

##### `Channels`

![image-20230102215615091](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102215615091.png)

>  对一组 Buffer  进行管理

```java
/**
 * Channels of Buffer It contains all buffer data which belongs to this channel. It supports several strategy when
 * buffer is full. The Default is BLOCKING <p> Created by wusheng on 2016/10/25.
 *
 * Buffer Channels 包含属于该通道的所有缓冲区数据。当缓冲区已满时，它支持多种策略。默认为阻塞
 */
public class Channels<T> {
    // 被管理的 buffer
    private final QueueBuffer<T>[] bufferChannels;
    // 分区器 1. 滚动分区。2.线程id取模
    private IDataPartitioner<T> dataPartitioner;
    // 策略
    private final BufferStrategy strategy;
    // 数量
    private final long size;

    public Channels(int channelSize, int bufferSize, IDataPartitioner<T> partitioner, BufferStrategy strategy) {
        this.dataPartitioner = partitioner;
        this.strategy = strategy;
        bufferChannels = new QueueBuffer[channelSize];
        for (int i = 0; i < channelSize; i++) {
            if (BufferStrategy.BLOCKING.equals(strategy)) {
                bufferChannels[i] = new ArrayBlockingQueueBuffer<>(bufferSize, strategy);
            } else {
                bufferChannels[i] = new Buffer<>(bufferSize, strategy);
            }
        }
        // noinspection PointlessArithmeticExpression
        size = 1L * channelSize * bufferSize; // it's not pointless, it prevents numeric overflow before assigning an integer to a long
    }

    public boolean save(T data) {
        // Buffer 的索引。即选择那个 Buffer 来储存数据
        int index = dataPartitioner.partition(bufferChannels.length, data);
        int retryCountDown = 1;
        if (BufferStrategy.IF_POSSIBLE.equals(strategy)) {
            int maxRetryCount = dataPartitioner.maxRetryCount();
            if (maxRetryCount > 1) {
                retryCountDown = maxRetryCount;
            }
        }
        for (; retryCountDown > 0; retryCountDown--) {
            if (bufferChannels[index].save(data)) {
                return true;
            }
        }
        return false;
    }

    public void setPartitioner(IDataPartitioner<T> dataPartitioner) {
        this.dataPartitioner = dataPartitioner;
    }

    /**
     * override the strategy at runtime. Notice, this will override several channels one by one. So, when running
     * setStrategy, each channel may use different BufferStrategy
     */
    public void setStrategy(BufferStrategy strategy) {
        for (QueueBuffer<T> buffer : bufferChannels) {
            buffer.setStrategy(strategy);
        }
    }

    /**
     * get channelSize
     */
    public int getChannelSize() {
        return this.bufferChannels.length;
    }

    public long size() {
        return size;
    }

    public QueueBuffer<T> getBuffer(int index) {
        return this.bufferChannels[index];
    }
}
```





##### `消费 Consumer`

![image-20230102215631893](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102215631893.png)

消费者读取DataCarrier中的数据上报到OAP，IConsumer是消费者的顶层接口，定义了基本方案。



###### `ConsumerThread`

>  一个ConsumerThread中包含多个DataSource，DataSource里包装了Buffer。同时一个ConsumerThread绑定了一个Consumer，Consumer会消费ConsumerThread中的DataSource





```java

/**
 * 一个线程，绑定一个消费者
 * 一个消费者，绑定多个 Buffer
 * @param <T>
 */
public class ConsumerThread<T> extends Thread {
    private volatile boolean running;
    private IConsumer<T> consumer;
    private List<DataSource> dataSources;

    // 本次消费没有取到数据时，现成 sleep 的时间
    private long consumeCycle;

    ConsumerThread(String threadName, IConsumer<T> consumer, long consumeCycle) {
        super(threadName);
        this.consumer = consumer;
        running = false;
        dataSources = new ArrayList<DataSource>(1);
        this.consumeCycle = consumeCycle;
    }

    /**
     * add whole buffer to consume
     */
    void addDataSource(QueueBuffer<T> sourceBuffer) {
        this.dataSources.add(new DataSource(sourceBuffer));
    }

    @Override
    public void run() {
        running = true;

        final List<T> consumeList = new ArrayList<T>(1500);
        while (running) {
            // 没取到数据？ 睡一会
            if (!consume(consumeList)) {
                try {
                    Thread.sleep(consumeCycle);
                } catch (InterruptedException e) {
                }
            }
        }

        // consumer thread is going to stop
        // consume the last time
        // 在结束时，再消费一次
        consume(consumeList);

        consumer.onExit();
    }

    /**
     * 将数据 放到 consumeList。 并消费
     * @param consumeList
     * @return
     */
    private boolean consume(List<T> consumeList) {
        for (DataSource dataSource : dataSources) {
            dataSource.obtain(consumeList);
        }

        if (!consumeList.isEmpty()) {
            try {
                consumer.consume(consumeList);
            } catch (Throwable t) {
                consumer.onError(consumeList, t);
            } finally {
                consumeList.clear();
            }
            return true;
        }
        consumer.nothingToConsume();
        return false;
    }

    void shutdown() {
        running = false;
    }

    /**
     * 适配器
     * DataSource is a refer to {@link Buffer}.
     */
    class DataSource {
        private QueueBuffer<T> sourceBuffer;

        DataSource(QueueBuffer<T> sourceBuffer) {
            this.sourceBuffer = sourceBuffer;
        }

        void obtain(List<T> consumeList) {
            sourceBuffer.obtain(consumeList);
        }
    }
}
```

 

###### `MultipleChannelsConsumer`

> 一个单消费者线程，但支持多个Channels和它们的消费者。
>
> 
>
> 一个Group中包含一个Consumer和一个Channels，一个Channels包含多个Buffer，Consumer会消费Channels中所有的Buffer
>
> 一个MultipleChannelsConsumer包含多个Group，实际上是管理多个Consumer以及它们对应的Buffer



```java
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.commons.datacarrier.consumer;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.commons.datacarrier.buffer.Channels;
import org.apache.skywalking.apm.commons.datacarrier.buffer.QueueBuffer;

/**
 * MultipleChannelsConsumer represent a single consumer thread, but support multiple channels with their {@link
 * IConsumer}s
 * 一个单消费者线程,但支持多个channels和它们的消费者
 */
public class MultipleChannelsConsumer extends Thread {
    private volatile boolean running;
    private volatile ArrayList<Group> consumeTargets;
    @SuppressWarnings("NonAtomicVolatileUpdate")
    private volatile long size;
    private final long consumeCycle;

    public MultipleChannelsConsumer(String threadName, long consumeCycle) {
        super(threadName);
        this.consumeTargets = new ArrayList<Group>();
        this.consumeCycle = consumeCycle;
    }

    @Override
    public void run() {
        running = true;

        final List consumeList = new ArrayList(2000);
        while (running) {
            boolean hasData = false;

            for (Group target : consumeTargets) {
                boolean consume = consume(target, consumeList);
                hasData = hasData || consume;
            }

            if (!hasData) {
                try {
                    Thread.sleep(consumeCycle);
                } catch (InterruptedException e) {
                }
            }
        }

        // consumer thread is going to stop
        // consume the last time
        for (Group target : consumeTargets) {
            consume(target, consumeList);

            target.consumer.onExit();
        }
    }

    private boolean consume(Group target, List consumeList) {
        // 遍历channels中的buffer,将buffer中的数据放到consumeList中,并清空buffer
        for (int i = 0; i < target.channels.getChannelSize(); i++) {
            QueueBuffer buffer = target.channels.getBuffer(i);
            buffer.obtain(consumeList);
        }

        if (!consumeList.isEmpty()) {
            try {
                // 调用消费者的消费逻辑
                target.consumer.consume(consumeList);
            } catch (Throwable t) {
                target.consumer.onError(consumeList, t);
            } finally {
                consumeList.clear();
            }
            return true;
        }
        target.consumer.nothingToConsume();
        return false;
    }

    /**
     * Add a new target channels.
     */
    public void addNewTarget(Channels channels, IConsumer consumer) {
        Group group = new Group(channels, consumer);
        // Recreate the new list to avoid change list while the list is used in consuming.
        ArrayList<Group> newList = new ArrayList<Group>();
        for (Group target : consumeTargets) {
            newList.add(target);
        }
        newList.add(group);
        consumeTargets = newList;
        size += channels.size();
    }

    public long size() {
        return size;
    }

    void shutdown() {
        running = false;
    }

    private static class Group {
        // 一个channels对应多个buffer
        private Channels channels;
        // consumer会消费channels中所有的buffer
        private IConsumer consumer;

        public Group(Channels channels, IConsumer consumer) {
            this.channels = channels;
            this.consumer = consumer;
        }
    }
}

```





##### `消费驱动 Drive`

![image-20230102220905069](https://blog-1257196793.cos.ap-beijing.myqcloud.com/image-20230102220905069.png)

###### `ConsumeDriver`



> 一个ConsumeDriver包含多个ConsumerThread

```java

/**
 * Pool of consumers <p> Created by wusheng on 2016/10/25.
 *
 *  一堆消费者线程，拿着一堆 buffer ， 按照 allocateBuffer2Thread() 的策略 进行分配消费。
 */
public class ConsumeDriver<T> implements IDriver {
    private boolean running;
    private ConsumerThread[] 
      ;
    private Channels<T> channels;
    private ReentrantLock lock;

    public ConsumeDriver(String name, Channels<T> channels, Class<? extends IConsumer<T>> consumerClass, int num,
        long consumeCycle) {
        this(channels, num);
        for (int i = 0; i < num; i++) {
            consumerThreads[i] = new ConsumerThread("DataCarrier." + name + ".Consumer." + i + ".Thread", getNewConsumerInstance(consumerClass), consumeCycle);
            consumerThreads[i].setDaemon(true);
        }
    }

    public ConsumeDriver(String name, Channels<T> channels, IConsumer<T> prototype, int num, long consumeCycle) {
        this(channels, num);
        prototype.init();
        for (int i = 0; i < num; i++) {
            consumerThreads[i] = new ConsumerThread("DataCarrier." + name + ".Consumer." + i + ".Thread", prototype, consumeCycle);
            consumerThreads[i].setDaemon(true);
        }

    }

    private ConsumeDriver(Channels<T> channels, int num) {
        running = false;
        this.channels = channels;
        consumerThreads = new ConsumerThread[num];
        lock = new ReentrantLock();
    }

    private IConsumer<T> getNewConsumerInstance(Class<? extends IConsumer<T>> consumerClass) {
        try {
            IConsumer<T> inst = consumerClass.getDeclaredConstructor().newInstance();
            inst.init();
            return inst;
        } catch (InstantiationException e) {
            throw new ConsumerCannotBeCreatedException(e);
        } catch (IllegalAccessException e) {
            throw new ConsumerCannotBeCreatedException(e);
        } catch (NoSuchMethodException e) {
            throw new ConsumerCannotBeCreatedException(e);
        } catch (InvocationTargetException e) {
            throw new ConsumerCannotBeCreatedException(e);
        }
    }

    @Override
    public void begin(Channels channels) {
        // begin只能调用一次
        if (running) {
            return;
        }
        lock.lock();
        try {
            this.allocateBuffer2Thread();
            for (ConsumerThread consumerThread : consumerThreads) {
                consumerThread.start();
            }
            running = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRunning(Channels channels) {
        return running;
    }

    private void allocateBuffer2Thread() {
        int channelSize = this.channels.getChannelSize();
        /**
         *
         * 因为channels里面有多个buffer,同时这里也有多个消费者线程
         * 这一步的操作就是将这些buffer分配给不同的消费者线程去消费
         * 
         * if consumerThreads.length < channelSize
         * each consumer will process several channels.
         *
         * if consumerThreads.length == channelSize
         * each consumer will process one channel.
         *
         * if consumerThreads.length > channelSize
         * there will be some threads do nothing.
         */
        for (int channelIndex = 0; channelIndex < channelSize; channelIndex++) {
            // 消费者线程索引 = buffer的下标和消费者线程数取模
            int consumerIndex = channelIndex % consumerThreads.length;
            consumerThreads[consumerIndex].addDataSource(channels.getBuffer(channelIndex));
        }

    }

    @Override
    public void close(Channels channels) {
        lock.lock();
        try {
            this.running = false;
            for (ConsumerThread consumerThread : consumerThreads) {
                consumerThread.shutdown();
            }
        } finally {
            lock.unlock();
        }
    }
}

```



###### `BulkConsumePool`

> 一个BulkConsumePool包含多个MultipleChannelsConsumer

```java
/**
 * BulkConsumePool works for consuming data from multiple channels(DataCarrier instances), with multiple {@link
 * MultipleChannelsConsumer}s.
 * <p>
 * In typical case, the number of {@link MultipleChannelsConsumer} should be less than the number of channels.
 * 
 * BulkConsumePool 用于使用多个MultipleChannelsConsumer消耗来自多个通道（DataCarrier 实例）的数据。
 * 在典型情况下， MultipleChannelsConsumer的数量应该小于通道的数量
 */
public class BulkConsumePool implements ConsumerPool {
    private List<MultipleChannelsConsumer> allConsumers;
    private volatile boolean isStarted = false;

    public BulkConsumePool(String name, int size, long consumeCycle) {
        size = EnvUtil.getInt(name + "_THREAD", size);
        allConsumers = new ArrayList<MultipleChannelsConsumer>(size);
        // 创建消费者线程
        for (int i = 0; i < size; i++) {
            MultipleChannelsConsumer multipleChannelsConsumer = new MultipleChannelsConsumer("DataCarrier." + name + ".BulkConsumePool." + i + ".Thread", consumeCycle);
            multipleChannelsConsumer.setDaemon(true);
            allConsumers.add(multipleChannelsConsumer);
        }
    }

    @Override
    synchronized public void add(String name, Channels channels, IConsumer consumer) {
        // 拿到负载最低的线程
        MultipleChannelsConsumer multipleChannelsConsumer = getLowestPayload();
        multipleChannelsConsumer.addNewTarget(channels, consumer);
    }

    /**
     * Get the lowest payload consumer thread based on current allocate status.
     *
     * @return the lowest consumer.
     */
    private MultipleChannelsConsumer getLowestPayload() {
        MultipleChannelsConsumer winner = allConsumers.get(0);

        // 找出持有 buffer 数量最少的线程
        for (int i = 1; i < allConsumers.size(); i++) {
            MultipleChannelsConsumer option = allConsumers.get(i);
            if (option.size() < winner.size()) {
                winner = option;
            }
        }
        return winner;
    }

    /**
     *
     */
    @Override
    public boolean isRunning(Channels channels) {
        return isStarted;
    }

    @Override
    public void close(Channels channels) {
        for (MultipleChannelsConsumer consumer : allConsumers) {
            consumer.shutdown();
        }
    }

    @Override
    public void begin(Channels channels) {
        if (isStarted) {
            return;
        }
        for (MultipleChannelsConsumer consumer : allConsumers) {
            consumer.start();
        }
        isStarted = true;
    }

    /**
     * The creator for {@link BulkConsumePool}.
     */
    public static class Creator implements Callable<ConsumerPool> {
        private String name;
        private int size;
        private long consumeCycle;

        public Creator(String name, int poolSize, long consumeCycle) {
            this.name = name;
            this.size = poolSize;
            this.consumeCycle = consumeCycle;
        }

        @Override
        public ConsumerPool call() {
            return new BulkConsumePool(name, size, consumeCycle);
        }

        public static int recommendMaxSize() {
            return Runtime.getRuntime().availableProcessors() * 2;
        }
    }
}
```

#### 链路数据发送的 OAP



TracingContext的`finish()`方法关闭当前TraceSegment后，会调用ListenerManager的`notifyFinish()`方法传入当前关闭的TraceSegment。ListenerManager的`notifyFinish()`方法会迭代所有注册的TracingContextListener调用它们的`afterFinished()`方法



```java
public class TracingContext implements AbstractTracerContext {

    /**
     * 结束TracingContext
     * Finish this context, and notify all {@link TracingContextListener}s, managed by {@link
     * TracingContext.ListenerManager} and {@link TracingContext.TracingThreadListenerManager}
     */
    private void finish() {
        if (isRunningInAsyncMode) {
            asyncFinishLock.lock();
        }
        try {
            // 栈已经空了 且 当前TracingContext还在运行状态
            boolean isFinishedInMainThread = activeSpanStack.isEmpty() && running;
            if (isFinishedInMainThread) {
                /*
                 * Notify after tracing finished in the main thread.
                 */
                TracingThreadListenerManager.notifyFinish(this);
            }

            if (isFinishedInMainThread && (!isRunningInAsyncMode || asyncSpanCounter == 0)) {
                // 关闭当前TraceSegment
                TraceSegment finishedSegment = segment.finish(isLimitMechanismWorking());
                // 将当前TraceSegment交给TracingContextListener去处理,TracingContextListener会将TraceSegment发送到OAP
                TracingContext.ListenerManager.notifyFinish(finishedSegment);
                // 修改当前TracingContext运行状态为false
                running = false;
            }
        } finally {
            if (isRunningInAsyncMode) {
                asyncFinishLock.unlock();
            }
        }
    }
  
    /**
     * The <code>ListenerManager</code> represents an event notify for every registered listener, which are notified
     * when the <code>TracingContext</code> finished, and {@link #segment} is ready for further process.
     */
    public static class ListenerManager {
        private static List<TracingContextListener> LISTENERS = new LinkedList<>();

        /**
         * Add the given {@link TracingContextListener} to {@link #LISTENERS} list.
         *
         * @param listener the new listener.
         */
        public static synchronized void add(TracingContextListener listener) {
            LISTENERS.add(listener);
        }

        /**
         * Notify the {@link TracingContext.ListenerManager} about the given {@link TraceSegment} have finished. And
         * trigger {@link TracingContext.ListenerManager} to notify all {@link #LISTENERS} 's {@link
         * TracingContextListener#afterFinished(TraceSegment)}
         *
         * @param finishedSegment the segment that has finished
         */
        static void notifyFinish(TraceSegment finishedSegment) {
            for (TracingContextListener listener : LISTENERS) {
                listener.afterFinished(finishedSegment);
            }
        }

        /**
         * Clear the given {@link TracingContextListener}
         */
        public static synchronized void remove(TracingContextListener listener) {
            LISTENERS.remove(listener);
        }

    }  
```



###### `TraceSegmentServiceClient`



> `TraceSegmentServiceClient` 注册了 `TracingContextListener`的监听。
>
> 在 TracingContext.finish() 方法 会通过监听器的逻辑，调用到这个方法。
>   即，一个Segment 要关闭的时候，会把自己传到这里，这里会将其放入carrier。最后消费 



```java
/**
 * 向OAP 发送数据
 */
@DefaultImplementor
public class TraceSegmentServiceClient implements BootService, IConsumer<TraceSegment>, TracingContextListener, GRPCChannelListener {
    private static final ILog LOGGER = LogManager.getLogger(TraceSegmentServiceClient.class);
    // 上一次打印传输traceSegment情况的日志的时间
    private long lastLogTime;
    // 成功发送的traceSegment数量
    private long segmentUplinkedCounter;
    // 因网络原因丢弃的traceSegment数量
    private long segmentAbandonedCounter;
    private volatile DataCarrier<TraceSegment> carrier;
    private volatile TraceSegmentReportServiceGrpc.TraceSegmentReportServiceStub serviceStub;
    private volatile GRPCChannelStatus status = GRPCChannelStatus.DISCONNECT;

    @Override
    public void prepare() {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() {
        lastLogTime = System.currentTimeMillis();
        segmentUplinkedCounter = 0;
        segmentAbandonedCounter = 0;
        carrier = new DataCarrier<>(CHANNEL_SIZE, BUFFER_SIZE, BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
    }

    @Override
    public void onComplete() {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void shutdown() {
        TracingContext.ListenerManager.remove(this);
        carrier.shutdownConsumers();
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(List<TraceSegment> data) {
        if (CONNECTED.equals(status)) {
            final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
            StreamObserver<SegmentObject> upstreamSegmentStreamObserver = serviceStub.withDeadlineAfter(
                Config.Collector.GRPC_UPSTREAM_TIMEOUT, TimeUnit.SECONDS
            ).collect(new StreamObserver<Commands>() {
                @Override
                public void onNext(Commands commands) {
                    ServiceManager.INSTANCE.findService(CommandService.class)
                                           .receiveCommand(commands);
                }

                @Override
                public void onError(
                    Throwable throwable) {
                    status.finished();
                    if (LOGGER.isErrorEnable()) {
                        LOGGER.error(
                            throwable,
                            "Send UpstreamSegment to collector fail with a grpc internal exception."
                        );
                    }
                    ServiceManager.INSTANCE
                        .findService(GRPCChannelManager.class)
                        .reportError(throwable);
                }

                @Override
                public void onCompleted() {
                    status.finished();
                }
            });

            try {
                for (TraceSegment segment : data) {
                    SegmentObject upstreamSegment = segment.transform();
                    // 发送到OAP
                    upstreamSegmentStreamObserver.onNext(upstreamSegment);
                }
            } catch (Throwable t) {
                LOGGER.error(t, "Transform and send UpstreamSegment to collector fail.");
            }

            upstreamSegmentStreamObserver.onCompleted();
            // 强制等待所有的traceSegment都发送完成
            status.wait4Finish();
            segmentUplinkedCounter += data.size();
        } else {
            segmentAbandonedCounter += data.size();
        }

        printUplinkStatus();
    }

    private void printUplinkStatus() {
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastLogTime > 30 * 1000) {
            lastLogTime = currentTimeMillis;
            if (segmentUplinkedCounter > 0) {
                LOGGER.debug("{} trace segments have been sent to collector.", segmentUplinkedCounter);
                segmentUplinkedCounter = 0;
            }
            if (segmentAbandonedCounter > 0) {
                LOGGER.debug(
                    "{} trace segments have been abandoned, cause by no available channel.", segmentAbandonedCounter);
                segmentAbandonedCounter = 0;
            }
        }
    }

    @Override
    public void onError(List<TraceSegment> data, Throwable t) {
        LOGGER.error(t, "Try to send {} trace segments to collector, with unexpected exception.", data.size());
    }

    @Override
    public void onExit() {

    }

    /**
     * 监听方法。 TracingContext.finish() 方法 会通过监听器的逻辑，调用到这个方法。
     * 即，一个Segment 要关闭的时候，会把自己传到这里，这里会将其放入carrier。最后消费
     * @param traceSegment
     */
    @Override
    public void afterFinished(TraceSegment traceSegment) {
        if (traceSegment.isIgnore()) {
            return;
        }
        // 将traceSegment放到dataCarrier中
        if (!carrier.produce(traceSegment)) {
            if (LOGGER.isDebugEnable()) {
                LOGGER.debug("One trace segment has been abandoned, cause by buffer is full.");
            }
        }
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (CONNECTED.equals(status)) {
            Channel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getChannel();
            serviceStub = TraceSegmentReportServiceGrpc.newStub(channel);
        }
        this.status = status;
    }
}
```
