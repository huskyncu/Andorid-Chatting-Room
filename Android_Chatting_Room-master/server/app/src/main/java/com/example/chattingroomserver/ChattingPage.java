package com.example.chattingroomserver;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public class ChattingPage extends AppCompatActivity {
    static final int port = 7100;
    private TextView tv_welcome_msg;

    private ListView receive_block;
    private ListView memberList_block;
    private EditText et_sending_msg;
    private Button btn_send;
    private Button btn_leave;

    private String hostip;
    private String name;
    private Handler mMainHandler;
    Thread netThread;
    Thread listeningThread;
    Thread receivingThread;

    ServerSocket serverSocket = null;
    MsgJsonFormatObj serverMsgObj;
    List<String> receivedMsg = new ArrayList<>();

    Map<String, Socket> memberMap = new HashMap<>();
    List<String> memberList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatting_page);
        initViewElement();
        //hostip="127.0.0.1";
        hostip = getLocalIpAddress();
        Intent it = this.getIntent();
        Bundle bundle = it.getExtras();
        name = bundle.getString("name");
        tv_welcome_msg.setText("Hi! " + name);

        mMainHandler = new Handler(Looper.myLooper()) {
            Context _package;
            //https://blog.csdn.net/a5489888/article/details/6650608
            //簡單來說，context可以說是一個容器。
            //handler說明： https://blog.csdn.net/yztbydh/article/details/122990688?spm=1001.2101.3001.6650.13&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7EOPENSEARCH%7ERate-13-122990688-blog-80844290.pc_relevant_multi_platform_whitelistv3&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7EOPENSEARCH%7ERate-13-122990688-blog-80844290.pc_relevant_multi_platform_whitelistv3&utm_relevant_index=14
            // handler就是物流中心，會配送訊息，looper是會將所有訊息放進messqge queue，依序配送。
            @Override
            public void handleMessage(Message msg) {

                ArrayAdapter adapter;
                switch (msg.what) {
                    //使用者輸入的代號。
                    //https://blog.csdn.net/qq_33210042/article/details/112521010#:~:text=4%20message.obj%20%E5%AE%9A%E4%B9%89%E4%BC%A0%E9%80%92%E7%9A%84%E5%80%BC%E7%94%B1%E4%BA%8E%E7%B1%BB%E5%9E%8B%E6%98%AFobject%20%28%E5%AF%B9%E8%B1%A1%29%20%E6%89%80%E4%BB%A5%E6%AF%94%E8%BE%83%E5%B8%B8%E7%94%A8%2C%E5%8F%AF%E4%BB%A5%E4%BC%A0%E9%80%92%E5%90%84%E7%A7%8D%E5%80%BC%205%20handler.obtainMessage,%28%29%20%E5%B8%A6%E5%8F%82%E6%95%B0%E5%BD%A2%E5%BC%8F%E5%8F%91%E9%80%81%E6%B6%88%E6%81%AF%2C%E4%B8%BB%E8%A6%81%E7%9A%84%E5%8D%B4%E5%88%AB%E4%BB%A3%E4%BB%B7%E5%8F%AF%E4%BB%A5%E9%80%9A%E8%BF%87%E4%B8%8B%E9%9D%A2%E7%9A%84demo%20%E5%8C%BA%E5%88%86%2C%206%20message.setData%20%28%29%20%E4%BD%BF%E7%94%A8bundle%20%E7%9A%84%E5%AE%9E%E8%A1%8C%E4%BC%A0%E5%8F%82
                    case 0:// show msg 顯示訊息
                        MsgJsonFormatObj receiveObj = (MsgJsonFormatObj) msg.obj;
                        //這裡的msg的obj是訊息，詳見showMsg函式
                        //使用者傳遞的物件，給他宣告。
                        receivedMsg.add(receiveObj.getMsg_body());
                        //增加給list receivedMsg
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, receivedMsg.toArray());
                        //https://www.jianshu.com/p/3b2da5604c40
                        //中間那個是模板。
                        receive_block.setAdapter(adapter);
                        //https://blog.csdn.net/abc512427549/article/details/79785336
                        receive_block.setSelection(adapter.getCount() - 1);
                        //讓第幾項在最上層，也就是最新的訊息顯示在最上方。
                        //https://blog.csdn.net/szyangzhen/article/details/47972509
                        break;
                    case 1:// show memberList 顯示成員名單
                        Map<String, Socket> memberMap = (Map<String, Socket>) msg.obj;
                        //這裡的msg的obj是membermap，詳見refreshmemeberlist函式
                        memberList = memberMapToList(memberMap);
                        //將成員map轉成成員list
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, memberList.toArray());
                        memberList_block.setAdapter(adapter);
                        break;
                }
            }

            private Handler init(Context pac) {
                _package = pac;
                return this;
                //建構子
            }
        }.init(this);
        //在多線程運用中，在啟動一個線程之前要對一個對象進行一些初始化操作的話，
        // 那麼你可以把代碼寫在init方法來里！

        connect(this);

        btn_leave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverMsgObj.set_isAlive(false);
                Thread sendThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SendingMsg(serverMsgObj);
                            while(receivingThread.isAlive()){
                                //after all client receiving server is closing, the receivingThread will end at the same time
                            }
                            if(listeningThread.isAlive()){
                                listeningThread.interrupt();
                                //如果還在監聽就中斷。
                            }
                            if(!serverSocket.isClosed()) {
                                //如果還沒被關掉，就關掉。
                                serverSocket.close();
                                serverSocket=null;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                sendThread.start();
                while (sendThread.isAlive()){
                    //do nothing
                }
                try{
                    Intent it = new Intent();
                    it.setClass(ChattingPage.this, MainActivity.class);
                    startActivity(it);
                    //跳回原本的頁面。
                }catch (Exception e){
                    Log.e("Error",e.getMessage());
                }
            }
        });

        btn_send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverMsgObj.setMsg_body(et_sending_msg.getText().toString()+"\n");
                Thread sendThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            showMsg(serverMsgObj);
                            //顯示server傳送的訊息。
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                sendThread.start();
                while (sendThread.isAlive()){
                    //do nothing
                }
                sendThread.interrupt();
                et_sending_msg.setText("");
                //清空剛才傳送的訊息。
            }
        });

    }

    private void connect(Context _package) {
        //這邊是創建新伺服器。
        netThread = new Thread(new Runnable() {
            Context _pac;

            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    //連線
                    UUID uuid = UUID.randomUUID();
                    //創建唯一識別碼，不會被搞混，也就是給id。
                    //https://zh.wikipedia.org/zh-tw/%E9%80%9A%E7%94%A8%E5%94%AF%E4%B8%80%E8%AF%86%E5%88%AB%E7%A0%81
                    serverMsgObj = new MsgJsonFormatObj(uuid.toString(), name, "");
                    memberMap.clear();
                    memberList.clear();
                    //一開始先清空。
                    memberList.add(name + "(" + hostip + ":" + port + ")");
                    refreshMemberList(memberMap);
                    //將server進來的資訊，除了加進memberlist後也加入memmbermap。
                } catch (Exception e) {
                    e.printStackTrace();
                }
                listeningThread = new Thread(new Listening());
                //用thread開始listening。
                //listen是監聽，看是否還有其他用戶端。
                //會等待客户端连接请求，在没有客户端连接请求到来之前，程序会一直阻塞在这个函数里。
                listeningThread.start();
            }

            private Runnable init(Context pac) {
                _pac = pac;
                return this;
            }
        }.init(this/*_package*/));//初始化
        netThread.start();
        while (netThread.isAlive()) {
            //do nothing
        }
    }

    class Listening extends Thread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        Socket client = serverSocket.accept();
                        //接受用戶端
                        if (memberMap.size() >= 10) {
                            //如果超過10個用戶包括server，就跟客戶說已經滿了。
                            MsgJsonFormatObj jobj = new MsgJsonFormatObj("", "", "Chatting room is full\n");
                            if (client.isConnected()) {
                                SendingMsg(client, jobj);
                                //如果客戶端有連到就傳送已滿的訊息。
                            }
                        } else {
                            MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                            String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                            memberMap.put(mapKey, client);
                            //每一個uuid 的唯一識別碼+用戶名稱是key，client是客戶端socket。
                            serverMsgObj.setMsg_body("Hi " + clientMsgObj.get_username() + " !\n");
                            //Hi , ????
                            serverMsgObj.set_memberList(memberMapToList(memberMap));
                            //這裡的memberMapToList型態是list。
                            SendingMsg(client, serverMsgObj);
                            //傳送訊息。
                            Thread.sleep(1000);
                            //先停1秒鐘。
                            serverMsgObj.setMsg_body("Welcome " + clientMsgObj.get_username() + " join us!\n");
                            showMsg(serverMsgObj);
                            //如果有用戶加進來就顯示「歡迎誰誰誰加進來」的訊息。
                            refreshMemberList(memberMap);
                            //server端這邊會更新成員名單。
                            receivingThread = new Thread(new Receiving(client));
                            receivingThread.start();
                        }
                    } catch (Exception ex) {
                        System.out.println("Error: " + ex.getMessage());
                        break;
                    }
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }


    class Receiving extends Thread implements Runnable {
        Socket client;
        public Receiving(Socket _client) {
            client = _client;
        }
        //建構子
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                        //JSONObject轉msgObject，message。
                        if(clientMsgObj!=null){
                            if(clientMsgObj.isAlive()){
                                showMsg(clientMsgObj);
                                //顯示訊息。
                            }else{
                                //如果沒活著，就做以下的事情：
                                String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                                memberMap.remove(mapKey);
                                refreshMemberList(memberMap);
                                //更新成員名單，刪除離開的人。
                                serverMsgObj.setMsg_body(clientMsgObj.get_username() + " is leave...\n");
                                showMsg(serverMsgObj);
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        System.out.println("Error: " + ex.getMessage());
                        break;
                    }
                }
                client.close();
                //client關閉。
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private void refreshMemberList(Map<String, Socket> memberMap) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = memberMap;
        mMainHandler.sendMessage(msg);
        //獲取使用者傳的物件(成員名單)跟what訊息後，啟用handler。
    }


    private void showMsg(MsgJsonFormatObj msgObj) {
        String txt = msgObj.get_username() + " : " + msgObj.getMsg_body();
        //收到訊息就顯示。
        serverMsgObj.setMsg_body(txt);
        try {
            Message msg = Message.obtain();
            msg.what = 0;
            msg.obj = serverMsgObj;
            mMainHandler.sendMessage(msg);
            //獲取使用者傳的物件(訊息物件)跟what訊息後，啟用handler。
            SendingMsg(serverMsgObj);

        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
    }

    private void SendingMsg(MsgJsonFormatObj msgObj) {
        try {
            for (Map.Entry<String, Socket> item : memberMap.entrySet()) {
                if (!item.getKey().equals(serverMsgObj.get_id() + "," + serverMsgObj.get_username())) {
                    SendingMsg(item.getValue(), serverMsgObj);
                    //就是在下方的函式，兩個要一起看。
                }
            }
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
    }

    private static void SendingMsg(Socket socket, MsgJsonFormatObj msgObj) {
        try {
            JSONObject json = new JSONObject(msgObjToMap(msgObj));
            byte[] jsonByte = (json.toString()+"\n").getBytes();
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            outputStream.write(jsonByte);
            outputStream.flush();
            //輸出json格式至client。
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
    }

    private static MsgJsonFormatObj jsonObjToMsgObj(JSONObject jsonObj) throws JSONException {

        String id = jsonObj.getString("Id");
        String name = jsonObj.getString("Username");
        String msg_body = jsonObj.getString("Msg_body");
        boolean isAlive = Boolean.parseBoolean(jsonObj.getString("IsAlive"));

        return new MsgJsonFormatObj(id, name, msg_body, isAlive);
    }

    private static HashMap<String, Object> msgObjToMap(MsgJsonFormatObj msgObj) {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("Id", msgObj.get_id());
        map.put("Username", msgObj.get_username());
        map.put("Msg_body", msgObj.getMsg_body());
        map.put("IsAlive", msgObj.isAliveStr());
        map.put("MemberList",msgObj.getmemberList());
        return map;
        //hashmap，將Object變成map。
    }

    private static JSONObject receiveFromClient(Socket client) throws Exception {
        InputStream in = client.getInputStream();
        RunnableCallable rct = new RunnableCallable(in);
        Thread parsThread = new Thread(rct);
        parsThread.start();
        while (parsThread.isAlive()) {
            //Main Thread do nothing wait for internet thread
            //等待接收訊息。
        }
        String receiveStr = rct.call();
        //接收runnablecallable回傳的訊息。
        return new JSONObject(receiveStr);
        //因為本來是JSON格式(還沒轉換)，所以以JSONObject的方式回傳。
    }

    static class RunnableCallable implements Callable<String>, Runnable {
        InputStream _in;
        String resultStr;

        @Override
        public void run() {
            resultStr = parseInfo(_in);
            //parseinfo的函式在下方。
        }

        public RunnableCallable(InputStream in) {
            _in = in;
            //建構。
        }

        @Override
        public String call() {
            return resultStr;
            //回傳收到的訊息。
        }
    }

    private static String parseInfo(InputStream in) {
        String str = "";
        try {
            //讀取訊息。
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            str = br.readLine();
            Log.d("parseInfo() : ", str);
        } catch (IOException e) {
            Log.e("parseInfo Error", e.getMessage());
            e.printStackTrace();
        }
        return str;
    }

    private List<String> memberMapToList(Map<String, Socket> memberMap) {
        //將map轉成list的形式。
        //String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
        //line299 and line249
        List<String> resultList = new ArrayList<>();
        resultList.add(name + "(" + hostip + ":" + port + ")");
        for (Map.Entry<String, Socket> item : memberMap.entrySet()) {
            String[] id_name = item.getKey().split(",");
            //取後面的ip，前面uuid不要取。
            resultList.add(id_name[1] + "(" + item.getValue().getRemoteSocketAddress().toString().split("/")[1] + ")");
            //example: google.com/ip
            //https://www.javatpoint.com/java-serversocket-getremotesocketaddress-method#:~:text=The%20getRemoteSocketAddress%20%28%29%20method%20of%20ServerSocket%20class%20uses,or%20null%20if%20it%20is%20not%20connected%20yet.
        }
        return resultList;
    }

    @SuppressLint("LongLogTag")
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            Log.e("WifiPreference IpAddress", ex.toString());
        }
        return null;
    }

    private void initViewElement() {
        tv_welcome_msg = (TextView) findViewById(R.id.tv_chatting_page_welcome_msg);
        receive_block = (ListView) findViewById(R.id.lv_chatting_page_receive_block);
        memberList_block = (ListView) findViewById(R.id.lv_chatting_page_member_block);
        et_sending_msg = (EditText) findViewById(R.id.et_chatting_page_sending_msg);
        btn_send = (Button) findViewById(R.id.btn_chatting_page_send);
        btn_leave = (Button) findViewById(R.id.btn_chatting_page_btn_leave);
    }
}