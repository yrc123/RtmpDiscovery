package com.fzu.rtmpdiscovery.service;

import com.fzu.rtmpdiscovery.pojo.HlsLive;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 从nginx rtmp推流服务器获取rtmp推流列表
 * @author yrc
 */
@Service
public class RtmpService {
    @Autowired
    HttpClient client;
    @Autowired
    Set<String> ipSet;

    Logger log = LoggerFactory.getLogger(RtmpService.class);

    /**
     * 将ip包装成统一的获取客户端hls流状态的url
     * @param ip 客户端服务器ip
     * @return 获取客户端hls流状态的url
     */
    private String getStatusUrl(String ip){
        return "http://"+ip+"/stat";
    }

    /**
     * 将ip包装成统一的hls流url
     * @param ip 客户端服务器ip
     * @param appName nginx rtmp应用名
     * @param liveName 直播间名
     * @return hls流url
     */
    private String getLiveUrl(String ip,String appName,String liveName){
        return "http://"+ip+"/live/"+liveName+".m3u8";
    }
    /**
     * 将单个服务器的所有流信息的xml转换成JSONObject
     * @return 返回一个从服务器获取的xml对应的JSONObject
     */
    private JSONObject getRtmpJsonObject(String ip){
    	String statusUrl=getStatusUrl(ip);
        HttpGet httpGet = new HttpGet(statusUrl);
        try {
            HttpResponse response = client.execute(httpGet);
            HttpEntity entity = response.getEntity();
            String res = EntityUtils.toString(entity, "utf-8");
            JSONObject jsonObject = XML.toJSONObject(res);
            return jsonObject;
        } catch (IOException e) {
            log.error("连接rtmp服务："+ statusUrl +" 失败");
        }
        return null;
    }

    /**
     * 获取所有服务器的hls流信息，不经过包装
     * @return 原始JSONObject转换成的map
     */
    public Map<String,Object> getRtmpInfo(){
        HashMap<String, Object> res = new HashMap<>();
        for (String ip : ipSet) {
        	res.put(ip,getRtmpJsonObject(ip).toMap());
        }
        return  res;

    }

    /**
     * 获取所有服务器的hls流信息，经过包装
     * @return HlsLive
     */
    public List<HlsLive> getAllHlsList(){
        ArrayList<HlsLive> res = new ArrayList<>();
        for (String ip : ipSet) {
            List<HlsLive> hlsList = getHlsList(ip);
            res.addAll(hlsList);
        }
        return res;
    }
    /**
     * 将从服务器获取的xml转换成HlsLive
     * @return HlsLive类的列表
     */
    private List<HlsLive> getHlsList(String ip){
        JSONObject jsonObject = getRtmpJsonObject(ip);
        List<HlsLive> resList = new ArrayList<>();
        try {
            jsonObject=jsonObject.getJSONObject("rtmp").getJSONObject("server");
            //获取application数组
            if(!jsonObject.has("application")){
            	return resList;
            }
            List<Object> appList=new ArrayList<>();
            Object application = jsonObject.get("application");
            if(application.getClass()==JSONObject.class){
                appList.add(((JSONObject)application).toMap());
            }else{
                JSONArray apps = jsonObject.getJSONArray("application");
                appList=apps.toList();
            }
            //遍历application数组
            for (Object appItem : appList) {
                String appName = (String) ((Map) appItem).get("name");
                HashMap live = (HashMap) ((Map) appItem).get("live");
                Object streams = live.get("stream");
                if(streams==null) {
                    continue;
                }
                List<Object>streamList;
                if(streams.getClass()==HashMap.class){
                    streamList= Collections.singletonList(streams);
                }else{
                    streamList= (List<Object>) streams;
                }
                for (Object streamItem : streamList) {
                    String liveName = (String) ((Map) streamItem).get("name");
                    HashMap meta = (HashMap) ((Map) streamItem).get("meta");
                    HashMap video = (HashMap) meta.get("video");
                    String height = ((Integer) video.get("height")).toString();
                    String width = ((Integer) video.get("width")).toString();
                    resList.add(new HlsLive(appName,liveName,getLiveUrl(ip,appName,liveName),height,width,ip));
                }
            }
        }catch (NullPointerException e){
            log.error("xml无法解析为json");
        }
        return resList;
    }

    /**
     * 将HlsLive列表转换成map
     * @return HlsLive的map，key为hlsLive中的liveName，value为HlsLive类
     */
    public Map<String,Object> getHlsLiveMap(){
        List<HlsLive> hlsList = getAllHlsList();
        Map<String, Object> res = new HashMap<>();
        for (HlsLive hlsLive : hlsList) {
            res.put(hlsLive.getIp()+":"+hlsLive.getLiveName(),hlsLive.toMap());
        }
        return res;
    }
}
