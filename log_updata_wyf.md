# 1.完善登录功能

## 问题：员工表中的密码明文存储，安全性低。

## 解决方案：使用加密算法对明文密码加密后存储。

​	黑马使用**MD5**加密算法。

​	普通哈希算法虽然计算快，但容易被暴力破解或彩虹表攻击。攻击者可利用高性能GPU或ASIC加速计算，快速尝试大量密码组合。本项目使用**Argon2**加密算法，更加安全，但是速度相对较慢。

## 解决步骤：

**（1）添加Maven依赖**

​	sky-back-end/pom.xml

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

​	sky-back-end\sky-server\pom.xml

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

​	sky-back-end\sky-server\src\main\java\com\sky\service\impl\EmployeeServiceImpl.java

```java
// 原有代码验证部分
if (!password.equals(employee.getPassword())) {
    //密码错误
    throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
}
```

​	**注意：**数据库中已经保存的密码需要手动修改为Argon2算法加密后的密文，否则无法正常登录（可以去网站生成或者使用代码运行生成一次，Argon2算法每次生成的密文不同，选择任意一个即可）。同时数据库中password的长度也需要修改，改成VARCHAR(128)即可。

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

## TODO：

（1）后续修改密码时，用户输入明文，但是存入数据库时需要使用Argon2算法加密后存入。

（2）创建新用户时同理。