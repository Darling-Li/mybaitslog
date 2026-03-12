# [![Mybatislog](logo-bird-ninja.svg)](https://github.com/Link-Kou/intellij-mybaitslog) Mybatislog

### Mybatislog能做什么？

> Mybatislog是基于IntelliJ 开发的项目，用来格式化输出Mybatis的Sql。


```sql

--  1  2020.04.10 23:30:19 CST DEBUG com.cms.dao.ProductTypeConfigTitleDao.queryAll - ==>
select f_id, f_name, f_preId, f_type, createtime, updatedtime
FROM cms.t_product_type_config_title
WHERE f_type = 2;
------------------------------------------------------------------------------------------------------------------------
--  2  2020.04.10 23:30:20 CST DEBUG com.cms.dao.ProductTypeConfigGroupDao.queryAll - ==>
select f_id, f_titleId, f_name, f_preId, f_type, createtime, updatedtime
FROM cms.t_product_type_config_group
WHERE f_type = 2;
------------------------------------------------------------------------------------------------------------------------
--  3  2020.04.10 23:30:20 CST DEBUG com.cms.dao.ProductTypeConfigItemDao.queryAll - ==>
select f_id, f_groupId, f_preId, f_name, f_type, createtime, updatedtime
FROM cms.t_product_type_config_item
WHERE f_type = 2;
------------------------------------------------------------------------------------------------------------------------

```

---

### 使用环境

`IntelliJ IDEA Ultimate版（172+）`

### 源代码构建

    项目管理：Gradle

### 在线安装(搜索)

不提供在线插件库安装

### 手动安装

2020.2 以上版本都支持


> ##### 说明文档：

    1、插件基于日志来进行打印，如果无法打印SQL语句。排查一下日志
    2、插件默认随项目启动而启动
    3、如果不行！试试看！无敌大法，卸载插件，然后在重新安装