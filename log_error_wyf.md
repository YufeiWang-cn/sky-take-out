# 一、nginx

## <font color=red>nginx: [emerg] CreateDirectory() "xxx\nginx/temp/client_body_temp" failed (3: The system cannot find the path specified)</font>

​	nginx启动失败，无法找到temp文件，需要在nginx目录下创建一个名为“temp”的文件夹。

# 二、IDEA

## <font color=red>Cannot resolve symbol 'xxx'</font>

​	似乎是缓存问题，跟项目本身无关，点击菜单栏**File → Invalidate Caches**，勾选**“Clear file system cache and Local History”** 和 **“Clear VCS Log caches and indexes”**，点击 **Invalidate and Restart**，这会强制 IDEA 重新索引整个项目，解决因缓存损坏导致的“幽灵红波浪线”。