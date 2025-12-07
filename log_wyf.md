# 一、nginx

> ## <font color=red>nginx: [emerg] CreateDirectory() "xxx\nginx/temp/client_body_temp" failed (3: The system cannot find the path specified)</font>

​	nginx启动失败，无法找到temp文件，需要在nginx目录下创建一个名为“temp”的文件。