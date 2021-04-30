# RtmpDiscovery

一个可动态拓展的nginx rtmp流发现服务

## 具体功能

部署完毕后，可以动态注册nginx rtmp 服务器，并将服务器信息发布到指定的redis上

## 组件图

![image-20210428163321969](https://gitee.com/lin_haoran/Picgo/raw/master/img/image-20210428163321969.png)

## 配置图

![image-20210428163213463](https://gitee.com/lin_haoran/Picgo/raw/master/img/image-20210428163213463.png)

## 实现思路

### 概述

​	通过Rtmp推流发现服务客户端（rtmpDiscoveryClient）将nginx rtmp服务器的ip设置到redis中。之后Rtmp推流发现服务服务端（rtmpDiscoveryService）从redis中获取所有在线的服务器ip，通过这些ip去对应的nginx服务器轮询其rtmp流的信息。每次轮询都会将新获取到的信息与redis中已发布的信息对比，如果不相同就将*服务器变更*的信息发布到redis的channel中。

### RtmpDiscoveryClient

​	client端首先要通过一个外网服务器来获取本机的公网ip，获取到ip之后，每过一段时间（默认是3s）就给redis服务器设置一个键值对，键值对也会在一段时间后过期（默认是9s）。如果服务端在键值对过期前没有重新设置（在这里就是连续三次没有设置存活），redis中的键值对就过期，service认为其下线。

### RtmpDiscoveryService

​	service每次轮询首先从redis中获取所有在线服务器ip，之后一一访问约定的获取rtmp流信息的地址获取对应服务器上的流信息。最后整合，与redis之前存在的rtmp流信息对比，如果相同就略过，如果出现差异，就更新，并且发布订阅消息，通知所有订阅的客户端。

## 部署方式

​	client客户端要和nginx部署在同一设备上，然后获取ip的地址要改成对应service部署的地址

### Nginx服务器配置

```
http{
	server{
		listen 80;
		server_name 127.0.0.1;
		#开启rtmp模块中自带的
		location /stat {
			rtmp_stat all;
		}
		location /live{
			#Cors配置
			add_header 'Access-Control-Allow-Origin' '*';
			add_header 'Access-Control-Allow-Methods' 'GET, POST, OPTIONS, PUT, DELETE';
			add_header 'Access-Control-Allow-Headers' 'Content-Type';
			if ($request_method = 'OPTIONS') {
				add_header 'Access-Control-Allow-Origin' '*';
				add_header 'Access-Control-Max-Age' 1728000;
				add_header 'Content-Type' 'text/plain charset=UTF-8';
				add_header 'Content-Length' 0;
				return 204;
			}
			alias /dev/shm/hls;
			#自动生成index
			autoindex on;
			types {
				application/dash+xml mpd;
				application/vnd.apple.mpegurl m3u8;
				video/mp2t ts;
			}
			add_header Cache-Control no-cache;
		}
	}
}
rtmp{
	server{
		listen 1953;
		application rtmp{
			live on;
		}
		application hls{
			live on;
			#启用hls
			hls on;
			#缓存地址
			hls_path /dev/shm/hls/;
		}
	}
}
```

### RtmpDiscoveryClient配置

### 启动

​	直接通过java -jar启动即可

#### 自定义配置文件

```properties
#redis服务器中用来存储当前服务器ip的key前缀
rtmp.redis.ipKey=rtmp:ip:
#获取当前服务器公网ip的地址，可以使用Service服务器的部署地址的/get_client_ip方法
rtmp.service.url=http://...
#还要配置自己的redis
spring.redis.host=...
spring.redis.password=...
```

### RtmpDiscoveryService配置

### 启动

​	直接通过java -jar启动即可

#### 自定义配置文件

```properties
#获取的所有rtmp推流服务信息存储在redis中的key
rtmp.redis.key=rtmp:device
#在redis中发布服务器状态改变信息的channel
rtmp.redis.channel=deviceChannel
#获取所有在线的服务器ip的key前缀，与client的rtmp.redis.ipKey对应
rtmp.redis.ipKey=rtmp:ip:*
#还要配置自己的redis
spring.redis.host=...
spring.redis.password=...
```

### 示例

service服务器：http://121.43.147.139:8080/swagger

rtmp推流链接：`rtmp://121.43.147.139:1953/*` 或`rtmp://106.15.74.153:1953/*`   其中`*`为自定义的名称，作为设备名

两台rtmp推流服务器是通过上面的部署方式部署的，设备接入就可以在网页看到状态更新