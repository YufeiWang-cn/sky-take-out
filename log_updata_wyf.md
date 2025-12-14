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

​	添加“分类分页查询”、“新增分类”功能。

​	分类名称必须是唯一的，分类按照类型可以分为菜品分类和套餐分类，新添加的分类状态默认为”禁用“，排序字段越大显示优先级越高。
