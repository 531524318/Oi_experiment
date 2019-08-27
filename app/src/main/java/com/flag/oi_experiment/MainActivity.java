package com.flag.oi_experiment;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.flag.oi_experiment.customComponent.CollectLayout;
import com.flag.oi_experiment.customComponent.NodeLayout;
import com.flag.oi_experiment.customComponent.view.CustomHorizontalProgresWithNum;
import com.flag.oi_experiment.tcpUtils.IOBlockedRunnable;
import com.flag.oi_experiment.tcpUtils.IOBlockedZigbeeRunnable;
import com.flag.oi_experiment.tcpUtils.MessageInfo;
import com.flag.oi_experiment.tcpUtils.ThreadPool;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "主线程";
    public static boolean isStartServer = false;        //是否启动了服务
    @BindView(R.id.openlistener)
    ImageView openlistener;
    @BindView(R.id.containerLine12)
    LinearLayout containerLine12;
    @BindView(R.id.containerLine22)
    LinearLayout containerLine22;
    @BindView(R.id.collect12)
    LinearLayout collect12;
    @BindView(R.id.collect22)
    LinearLayout collect22;
    @BindView(R.id.showNetwork)
    TextView showNetwork;
    @BindView(R.id.xietiao)
    ImageView xietiao;
    @BindView(R.id.horizontalProgress3)
    CustomHorizontalProgresWithNum progress;
    @BindView(R.id.successOpen)
    TextView successOpen;
    private Context mContext;
    private ThreadPool threadPool;          //线程池
    private ArrayList<ServerSocket> serverManager = new ArrayList<>();
    public static Map<String, IOBlockedRunnable> socketMap = Collections.synchronizedMap(new HashMap<String, IOBlockedRunnable>());    //套接字Map
    private CompositeDisposable disposableManager = new CompositeDisposable();          //通道管理器
    private Map<String, NodeLayout> nodeLayoutMap = new HashMap<>(); //键是ip#typenumber 样式，zigbee率先进来的话是#typeNumber不带ip样式
    private Map<NodeLayout, CollectLayout> collectLayoutMap = new HashMap<>();
    private Timer timerCheck = new Timer();         //定时检测网络中节点是否掉线，掉线处理
    private Socket csocketclient;                   //与C#软件建立连接的socket


    private PrintWriter outPC;                     //往电脑端发送数据对象
    private int tableWidth;     //记录屏幕宽和高
    private int tableHeight;
    public static String ZIGBEE_IP = "192.168.1.180";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //隐藏状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        ButterKnife.bind(this);
        mContext = getApplicationContext();

        initConfig();

    }
    @Override
    protected void onDestroy() {
        try {
            if (threadPool != null)
                threadPool.close();                 //关闭线程池
            if (csocketclient != null) {
                csocketclient.close();
            }
            synchronized (socketMap) {
                for (Runnable temp : socketMap.values()) {         //1、主动关闭客户端client连接
                    ((IOBlockedRunnable) temp).closeSocket();
                }
            }
            disposableManager.clear();          //退出Activitity之前先切断所有观察者模式的水管
            closeAllServerSockert();            //关闭serverSocket监听

            if (timerCheck != null) {
                timerCheck.purge();
                timerCheck.cancel();
                timerCheck = null;
            }
//            if (model_outPort != null) {
//                model_outPort.close();
//            }
//            if (config != null) {
//                config.closeSerialPort();
//            }
            super.onDestroy();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
//        for (Runnable temp:socketMap.values()){
//            ((IOBlockedRunnable)temp).notifyRunSendToMain(false);
//        }
        Log.d(TAG, "onResume: 服务端个数" + serverManager.size());
        Log.d(TAG, "onResume: socket个数" + socketMap.size());
        Log.d(TAG, "onResume: nodeLayoutMap个数" + nodeLayoutMap.size());
    }
    @OnClick(R.id.openlistener)
    public void openlistenerMethod(){
        if (isStartServer) return;
        String ip = "";
        try {
            NetworkInterface ni = NetworkInterface.getByName("eth0");
            Enumeration<InetAddress> ias = ni.getInetAddresses();
            for (; ias.hasMoreElements(); ) {
                InetAddress ia = ias.nextElement();
                if (ia instanceof InetAddress) {
                    if (ia.toString().equals("/192.168.1.200")) {
                        ip = "192.168.1.200";
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (!ip.equals("192.168.1.200")) {
            successOpen.setText("请将IP设置成静态地址：192.168.1.200!");
            return;
        }
        Log.d(TAG, "Onclick: " + ip);
        progress.setProgress(0);
        progress.setMax(100);
        final Timer timer32 = new Timer();
        timer32.schedule(new TimerTask() {
            @Override
            public void run() {
                //实时更新进度
                if (progress.getProgress() >= progress.getMax()) {//指定时间取消
                    handler.sendEmptyMessage(0x120);
                    timer32.cancel();
                }
                progress.setProgress(progress.getProgress() + 5);
            }
        }, 50, 50);
        isStartServer = !isStartServer;

        timerStart();
        //观察者模式，作为监听端口192.168.1.200:6001是wifi控制传感器
        Observable.create(new ObservableOnSubscribe<MessageInfo>() {
            @Override
            public void subscribe(ObservableEmitter<MessageInfo> emitter) throws Exception {
                if (threadPool == null) {
                    threadPool = new ThreadPool(12);            //初始化线程池
                }
                ServerSocket serverSocket = null;
                try {
                    serverSocket = new ServerSocket(6001, 6, InetAddress.getByName("192.168.1.200"));//连接wifi控制端口
                } catch (Exception e) {
                    emitter.onError(e);
                }
                if (serverSocket == null) {
                    isStartServer = !isStartServer;     //允许再次建立监听
                    emitter.onComplete();
                    return;
                }
                serverManager.add(serverSocket);
                while (true) {
                    Socket clientsocket = null;
                    try {
                        clientsocket = serverSocket.accept();
                    } catch (Exception e) {
//                        e.printStackTrace();
                        break;
                    }
                    String clientAddress = clientsocket.getInetAddress().toString().trim();
                    Log.d(TAG, "获取wifi控制节点客户端连接: " + "New connection accept" + clientsocket.getInetAddress());
                    if (!hasClientIPExite(clientAddress)) {
                        String area = "12";
                        if (containerLine12.getChildCount() == 3) area = "22";
                        IOBlockedRunnable run = new IOBlockedRunnable(clientsocket, area, emitter);
                        synchronized (socketMap) {
                            socketMap.put(clientAddress, run);
                        }
                        threadPool.execute(run);
                    } else {
                        //发现重复ip，则断开该ip原本的连接，使用新连接
                        getClientIPExite(clientAddress).closeSocket();

                        String area = "12";
                        if (containerLine12.getChildCount() == 3) area = "22";
                        IOBlockedRunnable run = new IOBlockedRunnable(clientsocket, area, emitter);
                        synchronized (socketMap) {
                            socketMap.put(clientAddress, run);
                        }
                        threadPool.execute(run);

                        handler.sendEmptyMessage(0x123);
                    }
                }
            }
        }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<MessageInfo>() {
                    private Disposable mDisposable;
//                    private NodeLayout statuChange;

                    @Override
                    public void onSubscribe(Disposable d) {
                        mDisposable = d;
                        disposableManager.add(d);
                    }

                    @Override
                    public void onNext(MessageInfo minfo) {
                        switch (minfo.getInfoType()) {
                            case "link":
                                String value = minfo.getContext();              //获取内容
                                String[] update = value.split("#");
                                String ip1 = update[1];
                                String type1 = update[3];
                                String numberLink = update[5];              //编号
                                String ipAndTypeNumber = ip1 + "#" + type1 + numberLink;     //ip和类型序号
                                NodeLayout nllink = wififindExiteNodeLayout(ipAndTypeNumber);
                                if (nllink == null) {

                                    nllink = new NodeLayout(getApplicationContext(), null);
                                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
                                    nllink.onlyIp = ipAndTypeNumber;
                                    nllink.updateHiddenText(value);

                                    nllink.setImageBackgroundCanClick();
                                    if (containerLine12.getChildCount() < 3) {      //一行三列，超出则换一行
                                        containerLine12.addView(nllink, lp);
                                    } else if (containerLine22.getChildCount() < 3) {
                                        containerLine22.addView(nllink, lp);
                                    }
                                    CollectLayout cl = new CollectLayout(getApplicationContext(), null);
                                    cl.setShowInit(type1, "wifi", ip1, numberLink);
                                    cl.setConfig(MainActivity.this, tableWidth, tableHeight);//设置屏幕参数
                                    if (collect12.getChildCount() < 3) {
                                        collect12.addView(cl, lp);
                                    } else if (collect22.getChildCount() < 3) {
                                        collect22.addView(cl, lp);
                                    }
//                                    statuChange = nllink;
                                    collectLayoutMap.put(nllink, cl);
                                    nodeLayoutMap.put(ipAndTypeNumber, nllink);
                                } else {
                                    nllink.hideZigbeeTrue();
                                    //加入事先已有zigbee链接，那么应该改变key值
                                    NodeLayout ss = nodeLayoutMap.remove("#" + type1 + numberLink);
                                    if (ss != null) {
                                    }
                                    nodeLayoutMap.put(ipAndTypeNumber, nllink);
                                    nllink.updateHiddenText(value);
                                    collectLayoutMap.get(nllink).changeStatueLink("wifi", ip1);
                                }
                                handlerData(type1, numberLink, "linkwifi");
                                break;
                            case "exit":
                                String[] areaAndipType = minfo.getContext().split("#");
                                String area = areaAndipType[0];
                                String ip = areaAndipType[1];
                                String type = areaAndipType[2];
                                String number = areaAndipType[3];
                                synchronized (socketMap) {
                                    socketMap.remove(ip);
                                }
                                //移除显示节点，退出前检测zigbee那一块是否还在连接
                                String ipType = ip + "#" + type + number;
                                NodeLayout nl = nodeLayoutMap.get(ipType);
                                if (nl == null) {

                                } else {
                                    if (nodeLayoutMap.get(ipType).hideWifi() == 2) {         //返回整数2，表示要移除整个组件
                                        NodeLayout remove = nodeLayoutMap.remove(ipType);
                                        CollectLayout cl = collectLayoutMap.remove(remove);                    //采集map集合也要移除
                                        switch (area) {
                                            case "12":
                                                collect12.removeView(cl);
                                                containerLine12.removeView(remove);
                                                break;
                                            case "22":
                                                collect22.removeView(cl);
                                                containerLine22.removeView(remove);
                                                break;
                                        }
                                    } else {
                                        nodeLayoutMap.get(ipType).updateHiddenText("wifiip##wifistatue#false");
                                        nodeLayoutMap.get(ipType).hideWifiTrue();
                                        try {
                                            collectLayoutMap.get(nl).changeStatueLink("zigbee", (String) nodeLayoutMap.get(ipType).getHiddenTextJSON().get("zigbeeip"));
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                handlerData(type, number, "exit");
                                break;
                            case "data":
                                String restr = minfo.getContext().toString().trim();
                                String numberData = restr.substring(5, 7);//编号
                                switch (restr.substring(3, 5)) {
                                    case "or":
                                        numberData = "全向红外" + numberData;
                                        break;
                                    default:
                                        break;
                                }
                                NodeLayout dataNode = null;
                                for (String dataKey : nodeLayoutMap.keySet()) {
                                    if (dataKey.contains(numberData)) {
                                        dataNode = nodeLayoutMap.get(dataKey);
                                        dataNode.setStatueControl(restr);
                                        break;
                                    }
                                }
                                //假如此时是zigbee连接显示，应该自动变为wifi连接显示
                                if (dataNode != null) {
                                    if (dataNode.getLinkType().equals("zigbee")) {
                                        dataNode.hideZigbeeTrue();
                                        //更新传感器连接网络方式
                                        String ipWIFI = "";
                                        try {
                                            ipWIFI = dataNode.getHiddenTextJSON().getString("wifiip");
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                        collectLayoutMap.get(dataNode).changeStatueLinkNoPOP("wifi", ipWIFI);
                                    }
                                }

                                //20190731增加报警系统相关的功能
//                                AlarmService.getInstance().handleSlRecvData(restr);

                                //LogUtils.i("======发送同样数据给PC客户端 restr="+restr);
                                writeMessageToPC(restr);//发送同样数据给PC客户端
                                break;
                            case "typeChange":          //传感器类型改变
                                String[] typeChangeKey = minfo.getContext().split("&");//typeChangeKey[0]是旧类型的键，typeChangeKey[1]是新类型的键,typeChangeKey[2]是中文类型
                                //下面这些顺序不能乱，乱了程序就错了
                                String ipNew = typeChangeKey[0].split("#")[0];
                                String numberNew = typeChangeKey[0].substring(typeChangeKey[0].length() - 2);
                                NodeLayout orignalNode = nodeLayoutMap.get(typeChangeKey[0]);
                                CollectLayout orignalColle = collectLayoutMap.get(orignalNode);
                                orignalColle.setShowInit(typeChangeKey[2], "wifi", ipNew, numberNew);
                                collectLayoutMap.remove(orignalColle);                   //移除
                                orignalNode.onlyIp = typeChangeKey[1];                  //采集节点作出改变
                                orignalNode.updateHiddenText("type#" + typeChangeKey[2]);//改变隐藏域内容
                                //隐藏wifi图标
//                                nodeLayoutMap.remove(typeChangeKey[0]).closeNode();                 //移除原本的键值
                                nodeLayoutMap.put(typeChangeKey[1], orignalNode);         //将新键值存进去
                                collectLayoutMap.put(orignalNode, orignalColle);
                                handlerData(typeChangeKey[2], numberNew, "linkChange");
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        successOpen.setText("打开失败，端口被占用！\n" +
                                "请重启该软件！");
                        mDisposable.dispose();
                        closeListener();
                    }
                });
        //观察者模式，作为监听端口，192.168.1.200:6002是zigbee 收集传感器数据和控制设备
        Observable.create(new ObservableOnSubscribe<MessageInfo>() {
            @Override
            public void subscribe(ObservableEmitter<MessageInfo> emitter) throws Exception {
                if (threadPool == null) {
                    threadPool = new ThreadPool(12);            //初始化线程池
                }
                ServerSocket serverSocket = null;
                try {
                    serverSocket = new ServerSocket(6002, 6, InetAddress.getByName("192.168.1.200"));//连接zigbee端口
                } catch (Exception e) {
                    emitter.onError(e);
                }
                if (serverSocket == null) {
                    isStartServer = !isStartServer;     //允许再次建立监听
                    emitter.onComplete();
                    return;
                }
                serverManager.add(serverSocket);
                while (true) {
                    Socket clientsocket = null;
                    try {
                        clientsocket = serverSocket.accept();
                    } catch (Exception e) {
                        e.printStackTrace();
                        break;
                    }
                    try {
                        String clientAddress = clientsocket.getInetAddress().toString().trim();
                        Log.d(TAG, "获取zigbee节点客户端连接: " + "New connection accept" + clientsocket.getInetAddress());
                        //发现重复ip，则断开该ip原本的连接，使用新连接
                        if (hasClientIPExite(clientAddress)) {
                            getClientIPExite(clientAddress).closeSocket();
                            handler.sendEmptyMessage(0x123);
                        }
                        String area = "";
                        final IOBlockedZigbeeRunnable run = new IOBlockedZigbeeRunnable(clientsocket, area, emitter);
                        synchronized (socketMap) {
                            socketMap.put(clientAddress, run);
                        }
                        threadPool.execute(run);
                        Message msgZigLink = new Message();
                        msgZigLink.what = 0x121;
                        msgZigLink.obj = clientAddress + ":" + clientsocket.getPort();
                        handler.sendMessage(msgZigLink);

                    } catch (Exception e) {
                    }
                }
            }
        }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<MessageInfo>() {
                    private Disposable mDisposable;
                    private CollectLayout nowCollect;

                    @Override
                    public void onSubscribe(Disposable d) {
                        mDisposable = d;
                        disposableManager.add(d);
                    }

                    @Override
                    public void onNext(MessageInfo minfo) {
                        switch (minfo.getInfoType()) {
                            case "link":
                                String orignal = minfo.getContext();
                                String[] data = orignal.split("#");
                                String type = data[1];
                                String ipZigbee = data[3];
                                String numberLink = data[5];              //编号
                                NodeLayout nllink = zigbeefindExiteNodeLayout(type + numberLink);
                                if (nllink == null) {
                                    nllink = new NodeLayout(getApplicationContext(), null);
                                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f);
                                    nllink.onlyIp = "#" + type + numberLink;//这里之所以不添加ipZigbee 作为键，是因为考虑到同时存在wifi和zigbee节点共用一个传感器，那么ip地址应该填的是wifi的ip
                                    nllink.updateHiddenText(orignal);
                                    CollectLayout cl = new CollectLayout(getApplicationContext(), null);
                                    cl.setShowInit(type, "zigbee", ipZigbee, numberLink);
                                    cl.setConfig(MainActivity.this, tableWidth, tableHeight);//设置屏幕参数
                                    //根据类型判断是控制传感器还是采集传感器
                                    boolean control = false;
                                    switch (type) {
                                        case "全向红外":
                                            control = true;
                                            break;
                                    }
                                    if (control) {                               //放入右边
                                            nllink.setImageBackgroundCanClick();
                                        if (containerLine12.getChildCount() < 3) {      //一行三列，超出则换一行
                                            containerLine12.addView(nllink, lp);
                                        } else if (containerLine22.getChildCount() < 3) {
                                            containerLine22.addView(nllink, lp);
                                        }
                                        if (collect12.getChildCount() < 3) {
                                            collect12.addView(cl, lp);
                                        } else if (collect22.getChildCount() < 3) {
                                            collect22.addView(cl, lp);
                                        }
                                    } else {                                      //放入左边
//                                        if (containerLine11.getChildCount() < 3) {      //一行三列，超出则换一行
//                                            containerLine11.addView(nllink, lp);
//                                        } else if (containerLine21.getChildCount() < 3) {
//                                            containerLine21.addView(nllink, lp);
//                                        }
//                                        if (collect11.getChildCount() < 3) {
//                                            collect11.addView(cl, lp);
//                                        } else if (collect21.getChildCount() < 3) {
//                                            collect21.addView(cl, lp);
//                                        }
                                    }
                                    collectLayoutMap.put(nllink, cl);
                                    nodeLayoutMap.put(nllink.onlyIp, nllink);
                                } else {
                                    nllink.hideWifiTrue();
                                    nllink.updateHiddenText(orignal);
                                    //更新传感器连接网络方式
                                    collectLayoutMap.get(nllink).changeStatueLink("zigbee", "/" + ZIGBEE_IP);
                                }
                                handlerData(nllink.getSensorType(), numberLink, "linkzigbee");
                                break;
                            case "exit":
                                String[] orignalZ = minfo.getContext().split("#");
                                String ip = orignalZ[1];
                                synchronized (socketMap) {
                                    socketMap.remove(ip);
                                }
                                Iterator<String> it = nodeLayoutMap.keySet().iterator();
                                ArrayList<String> outList = new ArrayList<String>();
                                while (it.hasNext()) {  //因为zigbee中一个连接地址包含多个节点，如果这个连接断开了，那么势必其下所有节点断开，所以我这里使用遍历map移除
                                    String removestr = it.next();   //键是ip#typenumber 样式，zigbee率先进来的话是#typeNumber不带ip样式
                                    NodeLayout remove = nodeLayoutMap.get(removestr);
                                    if (remove.hideZigbee() == 2) {
//                                        remove.closeNode();
                                        outList.add(removestr);
                                        CollectLayout cl = collectLayoutMap.remove(remove); //采集map集合也要移除
                                        if (cl == null)
                                            continue;
//                                        containerLine11.removeView(remove);
//                                        containerLine21.removeView(remove);
                                        containerLine12.removeView(remove);
                                        containerLine22.removeView(remove);

                                        cl.closeUIPopWindow();
//                                        collect11.removeView(cl);
//                                        collect21.removeView(cl);
                                        collect12.removeView(cl);
                                        collect22.removeView(cl);
                                    } else {

                                        nodeLayoutMap.get(removestr).updateHiddenText("zigbeeip##zigbeestatue#false");
                                        nodeLayoutMap.get(removestr).hideZigbeeTrue();
                                        //更新传感器连接状态
                                        try {
                                            if (collectLayoutMap.get(remove) != null)
                                                collectLayoutMap.get(remove).changeStatueLink("wifi", (String) nodeLayoutMap.get(removestr).getHiddenTextJSON().get("wifiip"));
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                for (String outone : outList) {
//                                    nodeLayoutMap.remove(outone).closeNode();
                                }
                                handler.sendEmptyMessage(0x122);

                                break;
                            case "exitOne":                 //仅仅是单个节点退出网络
                                String[] numberexit = minfo.getContext().split("#");//形如：01#02#
                                Iterator<String> itOrg = nodeLayoutMap.keySet().iterator();
                                int lenLess = 0;
                                ArrayList<String> list = new ArrayList<String>();
                                while (itOrg.hasNext()) {
                                    String removestr = itOrg.next();
                                    String checkStr = removestr.split("#")[1];
                                    for (String strout : numberexit) {
                                        if (checkStr.contains(strout)) {    //键包含了序号的表示掉线的，移除
                                            String offType = removestr.substring(removestr.indexOf("#") + 1, removestr.length() - 2);///192.168.1.100#温湿度01
                                            String offnumber = removestr.substring(removestr.length() - 2);
                                            handlerData(offType, offnumber, "outoff");
                                            NodeLayout remove = nodeLayoutMap.get(removestr);
                                            if (remove.hideZigbee() == 2) {
//                                                itOrg.remove();
                                                list.add(removestr);
                                                containerLine12.removeView(remove);
                                                containerLine22.removeView(remove);
                                                CollectLayout cl = collectLayoutMap.remove(remove); //采集map集合也要移除
                                                if (cl == null) continue;
                                                cl.closeUIPopWindow();
//                                                collect11.removeView(cl);
//                                                collect21.removeView(cl);
                                                collect12.removeView(cl);
                                                collect22.removeView(cl);
                                            } else {
                                                nodeLayoutMap.get(removestr).updateHiddenText("zigbeeip##zigbeestatue#false");
                                                nodeLayoutMap.get(removestr).hideZigbeeTrue();
                                                //更新传感器连接状态
                                                try {
                                                    collectLayoutMap.get(remove).changeStatueLink("wifi", (String) nodeLayoutMap.get(removestr).getHiddenTextJSON().get("wifiip"));
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                            }
                                            handlerData(remove.getSensorType(), strout, "exit");
                                            lenLess++;
                                            if (lenLess == numberexit.length)
                                                break;//已经检测完毕，下面的不需要再遍历

                                        }
                                    }
                                }
                                for (String one : list) {
//                                    nodeLayoutMap.remove(one).closeNode();
                                }

                                break;
                            case "inform":      //知会主线程，是哪一个采集传感器
                                String orignalZigbee = minfo.getContext();
                                if (!orignalZigbee.equals("")) {
                                    nowCollect = collectLayoutMap.get(nodeLayoutMap.get("#" + orignalZigbee.split("#")[1]));//首先在nodeLayoutMap中寻找是否存在
                                    if (nowCollect == null) {
                                        for (String keyValue : nodeLayoutMap.keySet()) {
                                            if (keyValue.contains(orignalZigbee.split("#")[1])) {
                                                nowCollect = collectLayoutMap.get(nodeLayoutMap.get(keyValue));
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    nowCollect = null;
                                }
                                break;
                            case "data"://数据对象
                                String dataContext = minfo.getContext();              //获取内容
                                //解析数据，得出传感器采集到的值

                                if (nowCollect != null && nowCollect.getNumber().equals(dataContext.substring(5, 7))) {
                                    nowCollect.updateChartData(dataContext);
                                }
                                String numberData = dataContext.substring(5, 7);//编号
                                switch (dataContext.substring(3, 5)) {
                                    case "or":
                                        numberData = "全向红外" + numberData;
                                        changeSwitchStatus(dataContext, numberData);
                                        break;
                                    default:
                                        break;
                                }
                                //判断此时连接状态，如果这时wifi自动更新为zigbee,我这里假设是wifi连接那么key有ip值
                                NodeLayout datanode = zigbeefindExiteNodeLayout(numberData);
                                if (datanode != null) {
                                    if (datanode.getLinkType().equals("wifi")) {
                                        datanode.hideWifiTrue();
                                        datanode.updateHiddenText("zigbeeip#" + ZIGBEE_IP + "#zigbeestatue#true");
                                        //更新传感器连接网络方式
                                        collectLayoutMap.get(datanode).changeStatueLinkNoPOP("zigbee", ZIGBEE_IP);
                                    }
                                }
                                writeMessageToPC(dataContext);
                                break;
                            case "typeChange":
                                String[] typeChangeKey = minfo.getContext().split("&");//typeChangeKey[0]是旧类型的键，typeChangeKey[1]是新类型的键,typeChangeKey[2]是中文类型
                                //下面这些顺序不能乱，乱了程序就错了
                                String ipNew = typeChangeKey[0].split("#")[0];
                                String numberNew = typeChangeKey[0].substring(typeChangeKey[0].length() - 2);
                                String maybeKey = typeChangeKey[0].split("#")[1];
                                String newKey = "#" + typeChangeKey[1].split("#")[1];
                                NodeLayout orignalNode = null;
                                String orignalKey = "";
                                for (String temp : nodeLayoutMap.keySet()) {        //遍历寻找该Node节点
                                    if (temp.contains(maybeKey)) {
                                        orignalKey = temp;
                                        orignalNode = nodeLayoutMap.get(temp);
                                    }
                                }
                                if (orignalNode == null) break;
                                CollectLayout orignalColle = collectLayoutMap.get(orignalNode);
                                orignalColle.setShowInit(typeChangeKey[2], "zigbee", ipNew, numberNew);
                                collectLayoutMap.remove(orignalColle);                   //移除
                                if (!orignalKey.split("#")[0].equals("")) {
                                    newKey = orignalKey.split("#")[0] + newKey;
                                }
                                orignalNode.onlyIp = newKey;                  //采集节点作出改变
                                orignalNode.updateHiddenText("type#" + typeChangeKey[2]);//改变隐藏域内容

//                                nodeLayoutMap.remove(orignalKey).closeNode();                 //移除原本的键值
                                nodeLayoutMap.put(newKey, orignalNode);         //将新键值存进去
                                collectLayoutMap.put(orignalNode, orignalColle);
                                handlerData(typeChangeKey[2], numberNew, "linkChange");
                                break;
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        successOpen.setText("打开失败，端口被占用！\n请重启该软件！");
                        mDisposable.dispose();
                        closeListener();
                    }
                });
//上位机C#客户端软件，服务监听地址192.168.1.200:6003
        Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                ServerSocket serverSocket = null;
                try {
                    serverSocket = new ServerSocket(6003, 1, InetAddress.getByName("192.168.1.200"));//连接zigbee端口，参数1代表最多只存在一个等待连接
                } catch (Exception e) {
                    emitter.onError(e);
                }
                if (serverSocket == null) {
                    isStartServer = !isStartServer;     //允许再次建立监听
                    emitter.onComplete();
                    return;
                }
                serverManager.add(serverSocket);
                while (true) {
                    try {
                        csocketclient = serverSocket.accept();
                        handlerData("", "", "cshar");
                        Log.d(TAG, "获取PC上位机客户端连接: " + "New connection accept" + csocketclient.getInetAddress());
                        BufferedReader br = new BufferedReader(new InputStreamReader(csocketclient.getInputStream()));
                        outPC = new PrintWriter(new OutputStreamWriter(csocketclient.getOutputStream(), "GBK"));
                        //C# 端软件第一次连接，我需要把此时所有在线设备信息发送给对方
//                        List<BaseLinkMessage> list = new ArrayList<BaseLinkMessage>();
//                        for (String ipAddress : nodeLayoutMap.keySet()) {    //遍历Map集合，过滤ip 和 类型
//                            NodeLayout tempnode = nodeLayoutMap.get(ipAddress);
//                            String orig = tempnode.onlyIp;
//                            String number = orig.substring(orig.length() - 2);
//                            String ipsub = tempnode.getLinkIP().substring(1);
//                            String deviceType = tempnode.getSensorType();
//                            String linkType = tempnode.getLinkType();
//                            list.add(new BaseLinkMessage(deviceType, ipsub, linkType, number));
//                        }
//                        Gson gson = new Gson();
//                        String jsonresult = gson.toJson(list);  //利用Gson把list 对象转成JSON格式字符串
//                        Log.d(TAG, "发送给PC在线设备: " + jsonresult);
//                        outPC.write(jsonresult);                //将当前在线设备信息发送C#软件
//                        outPC.flush();
                        char[] buf = new char[17];
                        int len = 0;
                        StringBuilder sb = new StringBuilder();
                        while ((len = br.read(buf)) != -1) {
                            Log.d(TAG, "上位机信息长度: " + len);
                            String line = new String(buf, 0, len);
                            sb.append(line);
                            if (!br.ready()) {
                                String result = sb.toString();
                                Log.d(TAG, "来自PC数据: " + sb.toString());
                                sb.setLength(0);

//                                //读取App->A53发来的控制信息
//                                if (!TextUtils.isEmpty(result) && result.length() > 3) {
//                                    //家庭门锁控制系统
//                                    if (result.startsWith("H") && result.substring(1, 3).equals(PracticalAppType.GATE_LOCK_GL_APP_TYPE) && result.endsWith("T")) {
//                                        GateLockService.getInstance().handleRecvData(result, outPC);
//                                    }
//                                    //家庭安防报警系统  家庭火灾报警系统 家庭可燃气体泄漏报警系统
//                                    else if (result.startsWith("H") && PracticalAppType.getAlarmAppType(result) && result.endsWith("T")) {
//                                        AlarmCfgService.getInstance().handleRecvData(result, outPC, mContext);
//                                    }
//                                }
                                //如果是与控制wifi或者zigbee 相关的控制命令,发送给对应
                                if (result.startsWith("H") && (result.charAt(1) == 'w' || result.charAt(1) == 'z')
                                        && result.charAt(2) == 'c' && result.endsWith("T")) {
                                    String number = result.substring(5, 7);
                                    switch (result.substring(3, 5)) {
                                        case "wp":
                                            number = "水泵" + number;
                                            break;
                                        case "af":
                                            number = "风扇" + number;
                                            break;
                                        case "rs":
                                            number = "卷帘" + number;
                                            break;
                                        case "pl":
                                            number = "植物生长灯" + number;
                                            break;
                                        case "cr":
                                            number = "加热器" + number;
                                            break;
                                        case "hr":
                                            number = "加湿器" + number;
                                            break;
                                        /**cssf新增应用**/
                                        case "el":
                                            number = "电磁锁" + number;
                                            break;
                                        case "al":
                                            number = "可调灯" + number;
                                            break;
                                        case "re":
                                            number = "继电器" + number;
                                            break;
                                        case "or":
                                            number = "全向红外" + number;
                                            break;
                                    }

                                    if (findRunThenSendMsg(number, result)) {
                                        Log.d(TAG, "PC上位机发送控制命令成功!");
                                    } else {
                                        Log.d(TAG, "PC上位机发送控制命令失败!");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
//                        e.printStackTrace();
                        if (outPC != null) {
                            outPC.close();
                            outPC = null;
                        }
                        if (csocketclient != null) {
                            csocketclient.close();
                        }

                    }
                }
            }
        }).subscribeOn(Schedulers.newThread()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Object>() {
                    private Disposable mDisposable;

                    @Override
                    public void onSubscribe(Disposable d) {
                        mDisposable = d;
                        disposableManager.add(d);
                    }

                    @Override
                    public void onNext(Object value) {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        successOpen.setText("打开失败，端口被占用！\n请重启该软件！");
                        mDisposable.dispose();
                        closeListener();
                    }
                });
    }
    //定时检查runable 的数据传输以及连接状态
    private void timerStart() {
        timerCheck.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    ArrayList<String> listOut = new ArrayList();
                    synchronized (socketMap) {
                        for (IOBlockedRunnable temp : socketMap.values()) {
                            SimpleDateFormat df = new SimpleDateFormat("mm:ss");
                            String timestr = df.format(new Date().getTime());
                            String[] times = timestr.split(":");
                            int miao = Integer.parseInt(times[0].trim()) * 60 + Integer.parseInt(times[1].trim());
                            String ipAddd = temp.getSocket().getInetAddress().toString().substring(1);
                            int nowmiao = temp.getNowTime();

                            if (ipAddd.equals(ZIGBEE_IP)) {//这个是转换器的IP地址
                                handler.sendEmptyMessage(0x211);
                                if (Math.abs(miao - temp.getNowTime()) > 30) {//三十秒没收到数据就判定真个zigbee通道掉线
                                    temp.closeSocket();
                                }
                                continue;
                            }
                            if (Math.abs(miao - nowmiao) > 12) {
//                                Log.d(TAG, "run: 超过12秒没有接受到数据" + ipAddd);
                                Process p2 = Runtime.getRuntime().exec("ping -c 1 -w 1 " + ipAddd);
                                int status2 = p2.waitFor(); // PING的状态
                                if (status2 == 0) {          //等于零表示能ping 得通，不作任何处理,否则不在线 需要作掉线处理

                                } else {

                                    String keyIP = "/" + ipAddd;
                                    listOut.add(keyIP);
                                    //移除显示节点，退出前检测zigbee那一块是否还在连接
                                    if (temp instanceof IOBlockedZigbeeRunnable) {

                                        Iterator<String> it = nodeLayoutMap.keySet().iterator();
                                        ArrayList<String> nodeOut = new ArrayList<String>();
                                        while (it.hasNext()) {  //因为zigbee中一个连接地址包含多个节点，如果这个连接断开了，那么势必其下所有节点断开，所以我这里使用遍历map移除
                                            String removestr = it.next();
                                            NodeLayout remove = nodeLayoutMap.get(removestr);
                                            if (remove.hideZigbee() == 2) {
                                                nodeOut.add(removestr);
                                                Message msgZgRM = new Message();
                                                msgZgRM.what = 0x127;
                                                msgZgRM.obj = removestr;
                                                handler.sendMessage(msgZgRM);
                                            } else {
                                                Message msgZgRM = new Message();
                                                msgZgRM.what = 0x127;
                                                msgZgRM.obj = removestr;
                                                handler.sendMessage(msgZgRM);
                                            }
                                        }
                                        for (String nodeTemp : nodeOut) {
//                                            nodeLayoutMap.remove(nodeTemp).closeNode();
                                        }
                                        handler.sendEmptyMessage(0x122);
                                    } else {

                                        String type = "", number = "";
                                        type = temp.getType();
                                        number = temp.getNumber();

                                        if (type.equals("") || number.equals("") || type == null || number == null) {

                                            listOut.add(keyIP);
                                            continue;
                                        }
                                        String ipType = keyIP + "#" + type.split("#")[1] + number;
                                        Message msgRemove = new Message();
                                        msgRemove.what = 0x126;
                                        msgRemove.obj = ipType + "&" + temp.getArea();
                                        handler.sendMessage(msgRemove);
                                        handlerData(type.split("#")[1], number, "exit");
                                    }
                                }
                            }
                        }
                        for (String temp : listOut) {
                            IOBlockedRunnable iorun = socketMap.remove(temp);
                            if (iorun != null)
                                iorun.closeSocket();
                        }

                    }
                } catch (Exception e) {

                    System.gc();
                    e.printStackTrace();

                }
            }
        }, 10000, 10000);
    }
    //依据编号number寻找到对应Runnable对象，调用输出对象属性，将信息输出
    public boolean findRunThenSendMsg(String number, String message) {
        try {
            for (String ipTypeNumber : nodeLayoutMap.keySet()) {
                if (ipTypeNumber.endsWith(number)) { //通过遍历 节点布局Map找到包含编号的ip地址
                    String ipAddress = nodeLayoutMap.get(ipTypeNumber).getLinkIP();
                    IOBlockedRunnable run = (IOBlockedRunnable) socketMap.get(ipAddress);
                    if (run != null) {
                        run.pw.write(message);
                        run.pw.flush();
                        return true;
                    } else {
                        Log.d(TAG, "等于空: ");
                    }
                    return false;
                }
            }
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    private IOBlockedRunnable getClientIPExite(String clientAddress) {
        if (socketMap.containsKey(clientAddress))
            return socketMap.get(clientAddress);
        return null;
    }
    private boolean hasClientIPExite(String clientAddress) {
        if (socketMap.containsKey(clientAddress))
            return true;
        return false;
    }
    //关闭所有serversocket
    public void closeAllServerSockert() {
        for (ServerSocket temp : serverManager) {
            try {
                if (temp != null && !temp.isClosed()) {
                    temp.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        serverManager.clear();
    }
    //关闭监听管理和serversocket
    public void closeListener() {
        disposableManager.clear();
        closeAllServerSockert();
    }
    private void handlerData(String type1, String numberLink, String linkOrExit) {
        Message msgLink = new Message();
        msgLink.what = 0x125;
        switch (linkOrExit) {
            case "linkwifi":
                msgLink.obj = "编号为" + numberLink + "的" + type1 + "wifi节点连入网络";
                break;
            case "linkzigbee":
                msgLink.obj = "编号为" + numberLink + "的" + type1 + "zigbee节点连入网络";
                break;
            case "exit":
                msgLink.obj = "编号为" + numberLink + "的" + type1 + "节点退出网络";
                break;
            case "cshar":
                //msgLink.obj = "电脑客户端上位机软件连入网络";
                msgLink.obj = "客户端软件连入网络";
                break;
            case "linkChange":
                msgLink.obj = "编号为" + numberLink + "的类型自动更改为" + type1;
                break;
            case "outoff":
                msgLink.obj = "编号为" + numberLink + "的" + type1 + "zigbee节点超过10秒未收到数据!!!";
                break;
            case "exitWifi":
                msgLink.obj = "编号为" + numberLink + "的" + type1 + "wifi节点退出网络";
                break;
            case "exitZigbee":
                msgLink.obj = "所有zigbee节点退出网络";
                break;
        }
        handler.sendMessage(msgLink);
    }
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0x120:             //更新路由器的颜色，false表示连接成功
                    if (isStartServer) {
                        openlistener.setImageResource(R.drawable.routertrue);
                        progress.setVisibility(View.GONE);
                    } else {
                        openlistener.setImageResource(R.drawable.routerpress);
                        progress.setVisibility(View.VISIBLE);
                    }
                    break;
                case 0x121:             //更新转换器和协调器的颜色表示工作进行
                    xietiao.setImageResource(R.drawable.xietiaotrue);
                    String mm = msg.obj.toString();
                    Message msgLink = new Message();
                    msgLink.what = 0x125;
                    msgLink.obj = "zigbee连入网络！" + mm;
                    handler.sendMessage(msgLink);
                    break;
                case 0x211:             //更新转换器颜色表示工作进行
                    break;
                case 0x122:             //表示协调器和转换器暂时不工作
                    xietiao.setImageResource(R.drawable.xietiaofalse);
                    handlerData("", "", "exitZigbee");
                    break;
                case 0x123:
                    Toast.makeText(mContext, "连接的wifi节点与已存在的IP产生冲突，替换旧连接", Toast.LENGTH_LONG).show();
                    break;
                case 0x124:
                    Toast.makeText(mContext, "该端口已经存在一个连接,只允许一个连接", Toast.LENGTH_LONG).show();
                    break;
                case 0x125:
                    //设置滚动文本框
                    if (msg.obj == null) break;
                    if (showNetwork != null) {
                        showNetwork.append(msg.obj.toString() + "\n");
                        int offset = showNetwork.getLineCount() * showNetwork.getLineHeight();
                        if (offset > (showNetwork.getHeight() - showNetwork.getLineHeight() - 5)) {
                            showNetwork.scrollTo(0, offset - showNetwork.getHeight() + showNetwork.getLineHeight() + 5);
                        }
                        if (showNetwork.getLineCount() > 15) {
                            showNetwork.setText("");
                            showNetwork.scrollTo(0, 0);
                        }
                    }
                    break;
                case 0x126:                 //界面上移除wifi节点图形
                    String[] rm = msg.obj.toString().split("&");
                    String ipType = rm[0];
                    String area = rm[1];
                    NodeLayout nlexit = nodeLayoutMap.get(ipType);
                    if (nlexit == null) break;
                    if (nodeLayoutMap.get(ipType).hideWifi() == 2) { //返回整数2，表示要移除整个组件
                        NodeLayout remove = nodeLayoutMap.remove(ipType);
//                        remove.closeNode();
                        CollectLayout cl = collectLayoutMap.remove(remove);                    //采集map集合也要移除
                        cl.closeUIPopWindow();
                        collect12.removeView(cl);
                        collect22.removeView(cl);
                        switch (area) {
                            case "12":
                                containerLine12.removeView(remove);
                                break;
                            case "22":
                                containerLine22.removeView(remove);
                                break;
                        }
                    } else {
                        nodeLayoutMap.get(ipType).updateHiddenText("wifiip##wifistatue#false");
                        nodeLayoutMap.get(ipType).hideWifiTrue();
                        try {
                            collectLayoutMap.get(nlexit).changeStatueLink("zigbee", (String) nodeLayoutMap.get(ipType).getHiddenTextJSON().get("zigbeeip"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case 0x127:                 //界面上移除zigbee节点图形
                    String removestr = msg.obj.toString();
                    NodeLayout remove = nodeLayoutMap.get(removestr);
                    if (remove.hideZigbee() == 2) {
//                        nodeLayoutMap.remove(removestr).closeNode();
                        containerLine12.removeView(remove);
                        containerLine22.removeView(remove);
                        CollectLayout cl = collectLayoutMap.remove(remove); //采集map集合也要移除
                        cl.closeUIPopWindow();

                        collect12.removeView(cl);
                        collect22.removeView(cl);
                    } else {
                        nodeLayoutMap.get(removestr).updateHiddenText("zigbeeip##zigbeestatue#false");
                        nodeLayoutMap.get(removestr).hideZigbeeTrue();
                        //更新传感器连接状态
                        try {
                            collectLayoutMap.get(remove).changeStatueLink("wifi", (String) nodeLayoutMap.get(removestr).getHiddenTextJSON().get("wifiip"));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
    };
    private NodeLayout zigbeefindExiteNodeLayout(String s) {//寻找NodeLayoutMap集合中已经存在的nodeLayout
        for (String temp : nodeLayoutMap.keySet()) {
            if (s.equals(temp.split("#")[1])) {
                return nodeLayoutMap.get(temp);
            }
        }
        return null;
    }
    private NodeLayout wififindExiteNodeLayout(String ipAndType) {//寻找NodeLayoutMap集合中已经存在的nodeLayout
        String[] type = ipAndType.split("#");
        for (String temp : nodeLayoutMap.keySet()) {
            if (type[1].equals(temp.split("#")[1])) {             //专门检查是否存在该类型，即 type
                return nodeLayoutMap.get(temp);
            }
        }
        return null;
    }
    //改变开关状态
    private void changeSwitchStatus(String dataContext, String numberData) {
        for (String dataKey : nodeLayoutMap.keySet()) {
            if (dataKey.contains(numberData)) {
                NodeLayout middle = nodeLayoutMap.get(dataKey);
                middle.setStatueControl(dataContext);
                break;
            }
        }
    }
    //将信息发送给C#软件
    private void writeMessageToPC(String s) {
        if (outPC != null) {
            outPC.write(s);
            outPC.flush();
        }
    }
    private void initConfig() {
        //获取窗口管理器
        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        //获得屏幕宽和高
        tableWidth = metrics.widthPixels;
        tableHeight = metrics.heightPixels;
        showNetwork.setMovementMethod(ScrollingMovementMethod.getInstance());//设置文本框可以拖拽
    }
}
