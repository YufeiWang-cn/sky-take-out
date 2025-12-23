# 1.完善"登录"功能

## 1.1 存在问题

​	员工表中的密码明文存储，安全性低。

## 1.2 解决方案

​	使用加密算法对明文密码加密后存储。

​	黑马使用**MD5**加密算法。

​	普通哈希算法虽然计算快，但容易被暴力破解或彩虹表攻击。攻击者可利用高性能GPU或ASIC加速计算，快速尝试大量密码组合。本项目使用**Argon2**加密算法，更加安全，但是速度相对较慢。

## 1.3 实现步骤

**（1）添加Maven依赖**

​	*sky-back-end/pom.xml*

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
    <version>6.0.3</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>
```

​	*sky-back-end\sky-server\pom.xml*

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-crypto</artifactId>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
</dependency>
```

**（2）修改后端登录验证的代码**

​	*sky-back-end\sky-server\src\main\java\com\sky\service\impl\EmployeeServiceImpl.java*

```java
// 原有代码验证部分
if (!password.equals(employee.getPassword())) {
    //密码错误
    throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
}
```

​	**注意**：数据库中已经保存的密码需要手动修改为Argon2算法加密后的密文，否则无法正常登录（可以去网站生成或者使用代码运行生成一次，Argon2算法每次生成的密文不同，选择任意一个即可）。同时数据库中password的长度也需要修改，改成VARCHAR(128)即可。

​	例：$argon2id$v=19$m=60000,t=10,p=1$Jnl+kxDS8CV+OeroeV4Cdg$DkIuTSaaQkKB0rGL2GBbF3Gp/AnGruT31PbQM2P/8p4

```java
// 修改后代码
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

// 使用Argon2算法加密
Argon2PasswordEncoder arg2SpringSecurity = new Argon2PasswordEncoder(16, 32, 1, 60000, 10);
if (!arg2SpringSecurity.matches(password, employee.getPassword())) {
    // 密码错误
    throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
}
```

## 1.4 TODO

（1）后续修改密码时，用户输入明文，但是存入数据库时需要使用Argon2算法加密。

（2）创建新用户时同理。

# 2.添加并完善"新增员工"功能

​	密码默认为“123456”，存入数据库前先进行Argon2算法加密。

## 2.1 存在问题

（1）录入的用户名若已经存在，抛出异常后没有处理（用户名应该是唯一的）。

（2）新增员工时，创建人和修改人id设置为了固定值10L。

## 2.2 解决方案

​	**问题1**：设置全局异常处理器。

​	**问题2**：员工登录成功后会生成JWT令牌并响应给前端，后续请求中，前端会携带JWT令牌，通过JWT令牌可以解析出当前登录员工id。但是如何从controller中将当前登录员工id传入service中仍是一个问题，需要使用ThreadLocal（ThreadLocal并不是一个Thread，而是Thread的局部变量，它为每个线程提供单独一份存储空间，具有线程隔离的效果，只有在线程内才能获取到对应的值，线程外侧不能访问）。客户端发起的每一次请求对应单独的一个线程，所以可以使用ThreadLocal解决。

## 2.3 实现步骤

​	**问题1**

​	添加一个全局异常处理器。

​	*sky-back-end\sky-server\src\main\java\com\sky\handler\GlobalExceptionHandler.java*

```java
/**
 * 处理SQL异常
 * @param ex
 * @return
 */
@ExceptionHandler
public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
    String message = ex.getMessage();
    if(message.contains("Duplicate entry")) {
        String[] split = message.split(" ");
        String username = split[2];
        String msg = username + MessageConstant.ALREADY_EXISTS;
        return Result.error(msg);
    }
    else return Result.error(MessageConstant.UNKNOWN_ERROR);
}
```

​	**问题2**

**（1）封装ThreadLocal**

​	*sky-back-end\sky-common\src\main\java\com\sky\context\BaseContext.java*

```java
package com.sky.context;

public class BaseContext {
    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }
}
```

**（2）在controller中解析并存储当前登录员工id**

​	*sky-back-end\sky-server\src\main\java\com\sky\interceptor\JwtTokenAdminInterceptor.java*

```java
BaseContext.setCurrentId(empId);
```

**（3）在service中取出id并设置**

​	*sky-back-end\sky-server\src\main\java\com\sky\service\impl\EmployeeServiceImpl.java*

```java
// 设置当前记录创建人id和修改人id
employee.setCreateUser(BaseContext.getCurrentId());
employee.setUpdateUser(BaseContext.getCurrentId());
```

# 3.添加并完善“员工分页查询”功能

## 3.1 存在问题

​	返回的时间格式错误。

```json
……
"createTime": [
  2025,
  12,
  11,
  21,
  33,
  43
],
"updateTime": [
  2025,
  12,
  11,
  21,
  33,
  43
],
……
```

## 3.2 解决方案

​	**方案1**：在属性上加入注解，对日期进行格式化。

​	**方案2**：在WebMvcConfiguration中拓展Spring MVC的消息转换器，同一对日期类型进行格式化处理。

​	**对比** ：每添加一个新属性都要重新应用方案1，而方案2可以对日期类型统一进行格式化处理。

## 3.3 实现步骤

​	**方案1**

```java
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
```

​	**方案2**

（1）自定义对象映射器

​	*sky-back-end\sky-common\src\main\java\com\sky\json\JacksonObjectMapper.java*

```java
/**
 * 对象映射器:基于jackson将Java对象转为json，或者将json转为Java对象
 * 将JSON解析为Java对象的过程称为 [从JSON反序列化Java对象]
 * 从Java对象生成JSON的过程称为 [序列化Java对象到JSON]
 */
public class JacksonObjectMapper extends ObjectMapper {
    public static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    // public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DEFAULT_DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm";
    public static final String DEFAULT_TIME_FORMAT = "HH:mm:ss";

    public JacksonObjectMapper() {
        super();
        // 收到未知属性时不报异常
        this.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);

        //反序列化时，属性不存在的兼容处理
        this.getDeserializationConfig().withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        SimpleModule simpleModule = new SimpleModule()
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)))
                .addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)))
                .addDeserializer(LocalTime.class, new LocalTimeDeserializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)))
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_TIME_FORMAT)))
                .addSerializer(LocalDate.class, new LocalDateSerializer(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT)))
                .addSerializer(LocalTime.class, new LocalTimeSerializer(DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT)));

        // 注册功能模块 例如，可以添加自定义序列化器和反序列化器
        this.registerModule(simpleModule);
    }
}
```

（2）拓展消息转换器

```java
/**
 * 拓展Spring MVC框架的消息转换器
 * @param converters
 */
@Override
protected void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
    log.info("拓展消息转换器");
    // 创建一个消息转换器对象
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    // 需要为消息转换器设置一个对象转换器，对象转换器可以将Java对象序列化为json数据
    converter.setObjectMapper(new JacksonObjectMapper());
    // 将这个消息转换器加入到容器中
    // 0表示顺序，将这个消息转换器优先使用
    converters.add(0, converter);
}
```

# 4.添加“修改员工账号状态”功能

​	员工账号分为“启用”和“禁用”两个状态，状态为“禁用”的员工账号不能登录系统。

# 5.添加“编辑员工”功能

​	首先根据id查询当前要修改的员工信息（注意查询的时候要屏蔽密码），然后编辑员工信息并存入数据库。

# 6.实现”分类管理“模块

​	添加“分类分页查询”、“新增分类”、“根据id删除分类”、”修改分类状态“、“修改分类”功能。

​	分类名称必须是唯一的，分类按照类型可以分为菜品分类和套餐分类，新添加的分类状态默认为”禁用“，排序字段数值越大优先级越高。

## 6.1 “根据id删除分类”功能

​	根据id删除分类的时候需要注意当前分类下是否有关联的菜品或者套餐，需要先到对应的表里查询再确定是否删除，如果有关联的菜品或套餐，则抛出异常，不执行删除操作，如果没有关联的菜品或套餐，则可以正常执行删除操作。

​	*sky-back-end\sky-server\src\main\java\com\sky\service\impl\CategoryServiceImpl.java*

```java
/**
 * 根据id删除分类
 * @param id
 */
@Override
public void deleteById(Long id) {
    // 查询当前菜品分类是否关联了菜品，如果关联了就抛出业务异常
    Integer count = dishMapper.countByCategoryId(id);
    if(count > 0){
        // 当前茶品分类下有菜品，不能删除
        throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
    }

    // 查询当前套餐分类是否关联了套餐，如果关联了就抛出业务异常
    count = setmealMapper.countByCategoryId(id);
    if(count > 0){
        // 当前套餐分类下有套餐，不能删除
        throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
    }
    categoryMapper.deleteById(id);
}
```

## 6.2 “修改分类”功能

​	测试时发现，即使未修改分类的信息也会操作成功，会导致其他信息未变的情况下修改操作时间。解决这个问题需要添加自定义的异常类，并在service中加入一个判断条件，如果信息未修改则抛出异常。

​	*sky-back-end\sky-server\src\main\java\com\sky\service\impl\CategoryServiceImpl.java*

```Java
/**
 * 修改分类
 * @param categoryDTO
 */
@Override
public void update(CategoryDTO categoryDTO) {
    // 根据id去数据库查询分类
    Category category_select = categoryMapper.getById(categoryDTO.getId());
    // 比较修改后的name和sort是否相同，如果相同抛出异常
    if(Objects.equals(categoryDTO.getName(), category_select.getName()) && Objects.equals(categoryDTO.getSort(), category_select.getSort())) {
        throw new InformationNotModified(MessageConstant.INFORMATION_NOT_MODIFIED);
    }

    Category category = new Category();
    BeanUtils.copyProperties(categoryDTO, category);

    // 设置修改时间、修改人id
    category.setUpdateTime(LocalDateTime.now());
    category.setUpdateUser(BaseContext.getCurrentId());

    categoryMapper.update(category);
}
```

# 7.公共字段自动填充

## 7.1 存在问题

​	”创建时间“、”创建人id“、”修改时间“、”修改人id“等字段几乎所有insert操作和update操作都要使用，每个类中都要写相应的代码，代码冗余且不易维护。

## 7.2 解决方案

​	自定义注解标识需要进行公共字段自动填充的方法，自定义切面类，统一拦截并通过反射为公共字段赋值。

## 7.3 实现步骤

（1）自定义注解AutoFill，用于标识需要进行公共字段自动填充的方法

​	*sky-back-end\sky-server\src\main\java\com\sky\annotation\AutoFill.java*

```java
/**
 * 自定义注解，用于标识某个方法需要进行公共字段自动填充处理
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    // 数据库操作类型：UPDATE INSERT
    OperationType value();  // 枚举类
}
```

（2）自定义切面类AutoFillAspect，统一拦截加入AutoFill注解的方法，通过反射为公共字段赋值

​	*sky-back-end\sky-server\src\main\java\com\sky\aspect\AutoFillAspect.java*

```java
/**
 * 自定义切面，实现公共字段自动填充处理逻辑
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {
    /**
     * 切入点
     */
    @Pointcut("execution(* com.sky.mapper.*.*(..)) && @annotation(com.sky.annotation.AutoFill)")
    public void autoFillPointcut() { }

    @Before("autoFillPointcut()")
    public void autoFill(JoinPoint joinPoint) {
        log.info("开始进行公共字段自动填充...");

        // 获取当前被拦截方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();  // 方法签名对象
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);  // 获得方法上的注解对象
        OperationType operationType = autoFill.value();  // 获得数据库操作类型

        // 获取当前被拦截方法的参数——实体对象
        Object[] args = joinPoint.getArgs();
        if(args == null || args.length == 0) {
            return;
        }
        Object entity = args[0];  // 约定AutoFill注解的方法第一个参数都为实体

        // 准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        // 根据当前不同的操作类型，通过反射为对应的属性赋值
        if(operationType == OperationType.INSERT) {
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);

                // 通过反射为对象属性赋值
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 无论是INSERT还是UPDATE都要更新updateTime和updateUser
        try {
            Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
            Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

            setUpdateTime.invoke(entity, now);
            setUpdateUser.invoke(entity, currentId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

（3）在Mapper方法上加入AutoFill注解

## 7.4 TODO

​	代码目前可读性较差，并且较繁琐，是否有优化空间？
