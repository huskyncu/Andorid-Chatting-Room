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

            @Override
            public void handleMessage(Message msg) {

                ArrayAdapter adapter;
                switch (msg.what) {
                    case 0:// show msg
                        MsgJsonFormatObj receiveObj = (MsgJsonFormatObj) msg.obj;
                        receivedMsg.add(receiveObj.getMsg_body());
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, receivedMsg.toArray());
                        receive_block.setAdapter(adapter);
                        receive_block.setSelection(adapter.getCount() - 1);
                        break;
                    case 1:// show memberList
                        Map<String, Socket> memberMap = (Map<String, Socket>) msg.obj;
                        memberList = memberMapToList(memberMap);
                        adapter = new ArrayAdapter(_package, R.layout.msg_box, memberList.toArray());
                        memberList_block.setAdapter(adapter);
                        break;
                }
            }

            private Handler init(Context pac) {
                _package = pac;
                return this;
            }
        }.init(this);

        create(this);

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
                            }
                            if(!serverSocket.isClosed()) {
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

                }
                try{
                    Intent it = new Intent();
                    it.setClass(ChattingPage.this, MainActivity.class);
                    startActivity(it);
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
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                sendThread.start();
                while (sendThread.isAlive()){

                }
                sendThread.interrupt();
                et_sending_msg.setText("");
            }
        });

    }

    private void create(Context _package) {
        netThread = new Thread(new Runnable() {
            Context _pac;

            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    UUID uuid = UUID.randomUUID();
                    serverMsgObj = new MsgJsonFormatObj(uuid.toString(), name, "");
                    memberMap.clear();
                    memberList.clear();
                    memberList.add(name + "(" + hostip + ":" + port + ")");
                    refreshMemberList(memberMap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                listeningThread = new Thread(new Listening());
                listeningThread.start();
            }

            private Runnable init(Context pac) {
                _pac = pac;
                return this;
            }
        }.init(_package));
        netThread.start();
        while (netThread.isAlive()) {
        }
    }

    class Listening extends Thread implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        Socket client = serverSocket.accept();
                        if (memberMap.size() >= 10) {
                            MsgJsonFormatObj jobj = new MsgJsonFormatObj("", "", "Chatting room is full\n");
                            if (client.isConnected()) {
                                SendingMsg(client, jobj);
                            }
                        } else {
                            MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                            String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                            memberMap.put(mapKey, client);

                            serverMsgObj.setMsg_body("Hi " + clientMsgObj.get_username() + " !\n");
                            serverMsgObj.set_memberList(memberMapToList(memberMap));
                            SendingMsg(client, serverMsgObj);
                            Thread.sleep(1000);
                            serverMsgObj.setMsg_body("Welcome " + clientMsgObj.get_username() + " join us!\n");
                            showMsg(serverMsgObj);


                            refreshMemberList(memberMap);
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
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        MsgJsonFormatObj clientMsgObj = jsonObjToMsgObj(receiveFromClient(client));
                        if(clientMsgObj!=null){
                            if(clientMsgObj.isAlive()){
                                showMsg(clientMsgObj);
                            }else{
                                String mapKey = clientMsgObj.get_id() + "," + clientMsgObj.get_username();
                                memberMap.remove(mapKey);
                                refreshMemberList(memberMap);
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
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }


    private static JSONObject receiveFromClient(Socket client) throws Exception {
        InputStream in = client.getInputStream();
        RunnableCallable rct = new RunnableCallable(in);
        Thread parsThread = new Thread(rct);
        parsThread.start();
        while (parsThread.isAlive()) {
            //Main Thread do nothing wait for internet thread
        }
        String receiveStr = rct.call();
        return new JSONObject(receiveStr);
    }

    private void showMsg(MsgJsonFormatObj msgObj) {
        String txt = msgObj.get_username() + " : " + msgObj.getMsg_body();
        serverMsgObj.setMsg_body(txt);
        try {
            Message msg = Message.obtain();
            msg.what = 0;
            msg.obj = serverMsgObj;
            mMainHandler.sendMessage(msg);
            SendingMsg(serverMsgObj);
        } catch (Exception e) {
            Log.e("Error", e.getMessage());
        }
    }

    private void refreshMemberList(Map<String, Socket> memberMap) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = memberMap;
        mMainHandler.sendMessage(msg);
    }

    private void SendingMsg(MsgJsonFormatObj msgObj) {
        try {
            for (Map.Entry<String, Socket> item : memberMap.entrySet()) {
                if (!item.getKey().equals(serverMsgObj.get_id() + "," + serverMsgObj.get_username())) {
                    SendingMsg(item.getValue(), serverMsgObj);
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

    private static String parseInfo(InputStream in) {
        String str = "";
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            str = br.readLine();
            Log.d("parseInfo() : ", str);
        } catch (IOException e) {
            Log.e("parseInfo Error", e.getMessage());
            e.printStackTrace();
        }
        return str;
    }

    static class RunnableCallable implements Callable<String>, Runnable {
        InputStream _in;
        String resultStr;

        @Override
        public void run() {
            resultStr = parseInfo(_in);
        }

        public RunnableCallable(InputStream in) {
            _in = in;
        }

        @Override
        public String call() {
            return resultStr;
        }
    }

    private List<String> memberMapToList(Map<String, Socket> memberMap) {
        List<String> resultList = new ArrayList<>();
        resultList.add(name + "(" + hostip + ":" + port + ")");
        for (Map.Entry<String, Socket> item : memberMap.entrySet()) {
            String[] id_name = item.getKey().split(",");
            resultList.add(id_name[1] + "(" + item.getValue().getRemoteSocketAddress().toString().split("/")[1] + ")");
        }
        return resultList;
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