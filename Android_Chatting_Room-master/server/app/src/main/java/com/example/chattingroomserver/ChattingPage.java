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
            //???????????????context???????????????????????????
            //handler????????? https://blog.csdn.net/yztbydh/article/details/122990688?spm=1001.2101.3001.6650.13&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7EOPENSEARCH%7ERate-13-122990688-blog-80844290.pc_relevant_multi_platform_whitelistv3&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7EOPENSEARCH%7ERate-13-122990688-blog-80844290.pc_relevant_multi_platform_whitelistv3&utm_relevant_index=14
            // handler???????????????????????????????????????looper???????????????????????????messqge queue??????????????????
            @Override
            public void handleMessage(Message msg) {

                ArrayAdapter adapter;
                switch (msg.what) {
                    //???????????????????????????
                    //https://blog.csdn.net/qq_33210042/article/details/112521010#:~:text=4%20message.obj%20%E5%AE%9A%E4%B9%89%E4%BC%A0%E9%80%92%E7%9A%84%E5%80%BC%E7%94%B1%E4%BA%8E%E7%B1%BB%E5%9E%8B%E6%98%AFobject%20%28%E5%AF%B9%E8%B1%A1%29%20%E6%89%80%E4%BB%A5%E6%AF%94%E8%BE%83%E5%B8%B8%E7%94%A8%2C%E5%8F%AF%E4%BB%A5%E4%BC%A0%E9%80%92%E5%90%84%E7%A7%8D%E5%80%BC%205%20handler.obtainMessage,%28%29%20%E5%B8%A6%E5%8F%82%E6%95%B0%E5%BD%A2%E5%BC%8F%E5%8F%91%E9%80%81%E6%B6%88%E6%81%AF%2C%E4%B8%BB%E8%A6%81%E7%9A%84%E5%8D%B4%E5%88%AB%E4%BB%A3%E4%BB%B7%E5%8F%AF%E4%BB%A5%E9%80%9A%E8%BF%87%E4%B8%8B%E9%9D%A2%E7%9A%84demo%20%E5%8C%BA%E5%88%86%2C%206%20message.setData%20%28%29%20%E4%BD%BF%E7%94%A8bundle%20%E7%9A%84%E5%AE%9E%E8%A1%8C%E4%BC%A0%E5%8F%82
                    case 0:// show msg ????????????
                        MsgJsonFormatObj receiveObj = (MsgJsonFormatObj) msg.obj;
                        //?????????msg???obj??????????????????showMsg??????
                        //??????????????????????????????????????????
                        receivedMsg.add(receiveObj.getMsg_body());
                        //?????????list receivedMsg
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, receivedMsg.toArray());
                        //https://www.jianshu.com/p/3b2da5604c40
                        //????????????????????????
                        receive_block.setAdapter(adapter);
                        //https://blog.csdn.net/abc512427549/article/details/79785336
                        receive_block.setSelection(adapter.getCount() - 1);
                        //????????????????????????????????????????????????????????????????????????
                        //https://blog.csdn.net/szyangzhen/article/details/47972509
                        break;
                    case 1:// show memberList ??????????????????
                        Map<String, Socket> memberMap = (Map<String, Socket>) msg.obj;
                        //?????????msg???obj???membermap?????????refreshmemeberlist??????
                        memberList = memberMapToList(memberMap);
                        //?????????map????????????list
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, memberList.toArray());
                        memberList_block.setAdapter(adapter);
                        break;
                }
            }

            private Handler init(Context pac) {
                _package = pac;
                return this;
                //?????????
            }
        }.init(this);
        //?????????????????????????????????????????????????????????????????????????????????????????????????????????
        // ??????????????????????????????init???????????????

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
                                //??????????????????????????????
                            }
                            if(!serverSocket.isClosed()) {
                                //????????????????????????????????????
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
                    //????????????????????????
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
                            //??????server??????????????????
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
                //??????????????????????????????
            }
        });

    }

    private void connect(Context _package) {
        //??????????????????????????????
        netThread = new Thread(new Runnable() {
            Context _pac;

            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    //??????
                    UUID uuid = UUID.randomUUID();
                    //??????????????????????????????????????????????????????id???
                    //https://zh.wikipedia.org/zh-tw/%E9%80%9A%E7%94%A8%E5%94%AF%E4%B8%80%E8%AF%86%E5%88%AB%E7%A0%81
                    serverMsgObj = new MsgJsonFormatObj(uuid.toString(), name, "");
                    memberMap.clear();
                    memberList.clear();
                    //?????????????????????
                    memberList.add(name + "(" + hostip + ":" + port + ")");
                    refreshMemberList(memberMap);
                    //???server??????????????????????????????memberlist????????????memmbermap???
                } catch (Exception e) {
                    e.printStackTrace();
                }
                listeningThread = new Thread(new Listening());
                //???thread??????listening???
                //listen?????????????????????????????????????????????
                //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                listeningThread.start();
            }

            private Runnable init(Context pac) {
                _pac = pac;
                return this;
            }
        }.init(this/*_package*/));//?????????
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
                        //???????????????
                        if (memberMap.size() >= 10) {
                            //????????????10???????????????server?????????????????????????????????
                            MsgJsonFormatObj jobj = new MsgJsonFormatObj("", "", "Chatting room is full\n");
                            if (client.isConnected()) {
                                SendingMsg(client, jobj);
                                //???????????????????????????????????????????????????
                            }
                        } else {
                            MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                            String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                            memberMap.put(mapKey, client);
                            //?????????uuid ??????????????????+???????????????key???client????????????socket???
                            serverMsgObj.setMsg_body("Hi " + clientMsgObj.get_username() + " !\n");
                            //Hi , ????
                            serverMsgObj.set_memberList(memberMapToList(memberMap));
                            //?????????memberMapToList?????????list???
                            SendingMsg(client, serverMsgObj);
                            //???????????????
                            Thread.sleep(1000);
                            //??????1?????????
                            serverMsgObj.setMsg_body("Welcome " + clientMsgObj.get_username() + " join us!\n");
                            showMsg(serverMsgObj);
                            //???????????????????????????????????????????????????????????????????????????
                            refreshMemberList(memberMap);
                            //server?????????????????????????????????
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
        //?????????
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                        //JSONObject???msgObject???message???
                        if(clientMsgObj!=null){
                            if(clientMsgObj.isAlive()){
                                showMsg(clientMsgObj);
                                //???????????????
                            }else{
                                //??????????????????????????????????????????
                                String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                                memberMap.remove(mapKey);
                                refreshMemberList(memberMap);
                                //??????????????????????????????????????????
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
                //client?????????
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
        //???????????????????????????(????????????)???what??????????????????handler???
    }


    private void showMsg(MsgJsonFormatObj msgObj) {
        String txt = msgObj.get_username() + " : " + msgObj.getMsg_body();
        //????????????????????????
        serverMsgObj.setMsg_body(txt);
        try {
            Message msg = Message.obtain();
            msg.what = 0;
            msg.obj = serverMsgObj;
            mMainHandler.sendMessage(msg);
            //???????????????????????????(????????????)???what??????????????????handler???
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
                    //????????????????????????????????????????????????
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
            //??????json?????????client???
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
        //hashmap??????Object??????map???
    }

    private static JSONObject receiveFromClient(Socket client) throws Exception {
        InputStream in = client.getInputStream();
        RunnableCallable rct = new RunnableCallable(in);
        Thread parsThread = new Thread(rct);
        parsThread.start();
        while (parsThread.isAlive()) {
            //Main Thread do nothing wait for internet thread
            //?????????????????????
        }
        String receiveStr = rct.call();
        //??????runnablecallable??????????????????
        return new JSONObject(receiveStr);
        //???????????????JSON??????(????????????)????????????JSONObject??????????????????
    }

    static class RunnableCallable implements Callable<String>, Runnable {
        InputStream _in;
        String resultStr;

        @Override
        public void run() {
            resultStr = parseInfo(_in);
            //parseinfo?????????????????????
        }

        public RunnableCallable(InputStream in) {
            _in = in;
            //?????????
        }

        @Override
        public String call() {
            return resultStr;
            //????????????????????????
        }
    }

    private static String parseInfo(InputStream in) {
        String str = "";
        try {
            //???????????????
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
        //???map??????list????????????
        //String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
        //line299 and line249
        List<String> resultList = new ArrayList<>();
        resultList.add(name + "(" + hostip + ":" + port + ")");
        for (Map.Entry<String, Socket> item : memberMap.entrySet()) {
            String[] id_name = item.getKey().split(",");
            //????????????ip?????????uuid????????????
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